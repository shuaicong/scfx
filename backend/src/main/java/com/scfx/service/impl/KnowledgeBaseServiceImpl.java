package com.scfx.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scfx.entity.DrCoord;
import com.scfx.entity.KnowledgeBase;
import com.scfx.mapper.DrCoordMapper;
import com.scfx.mapper.KnowledgeBaseMapper;
import com.scfx.mapper.KnowledgeCategoryMapper;
import com.scfx.mapper.KnowledgeChunkMapper;
import com.scfx.mapper.KnowledgeTaskMapper;
import com.scfx.service.FileStorageService;
import com.scfx.service.ImageService;
import com.scfx.service.KnowledgeBaseService;
import com.scfx.service.VectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl extends ServiceImpl<KnowledgeBaseMapper, KnowledgeBase>
    implements KnowledgeBaseService {

    private final VectorStore vectorStore;
    private final FileStorageService fileStorageService;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final KnowledgeTaskMapper knowledgeTaskMapper;
    private final DrCoordMapper drCoordMapper;
    private final KnowledgeCategoryMapper knowledgeCategoryMapper;
    private final ImageService imageService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${qdrant.host:127.0.0.1}")
    private String qdrantHost;

    @Value("${qdrant.port:6333}")
    private int qdrantPort;

    @Value("${qdrant.collection:grain_knowledge}")
    private String qdrantCollection;

    @Override
    public boolean existsByHash(String hash) {
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeBase::getContentHash, hash);
        return baseMapper.selectCount(wrapper) > 0;
    }

    @Override
    @Transactional
    public boolean removeWithViz(Long id) {
        KnowledgeBase kb = baseMapper.selectById(id);
        if (kb == null) return false;

        // 1. 从 Qdrant 删除向量（ai-qa-service 语义搜索用）
        deleteVectorsFromQdrant(kb.getVectorIds());

        // 2. 删除可视化库向量
        vectorStore.deleteVector(id);

        // 3. 清除坐标
        kb.setVizX(null);
        kb.setVizY(null);
        kb.setVizZ(null);
        baseMapper.updateById(kb);

        // 4. 清理关联表
        knowledgeChunkMapper.updateByKnowledgeId(id, 0);        // 软删除切片
        knowledgeTaskMapper.deleteByKnowledgeId(id);             // 删除处理任务
        drCoordMapper.delete(                                    // 删除降维坐标
            new LambdaQueryWrapper<DrCoord>().eq(DrCoord::getKnowledgeId, id));
        knowledgeCategoryMapper.deleteAllByKnowledgeId(id);      // 删除分类关联

        // 5. 清理关联图片（MinIO + 数据库记录）
        imageService.deleteByKnowledgeId(id);

        // 6. 逻辑删除主记录
        return super.removeById(id);
    }

    @Override
    @Transactional
    public void updateCategory(Long knowledgeId, Long categoryId) {
        knowledgeCategoryMapper.deleteAllByKnowledgeId(knowledgeId);
        if (categoryId != null) {
            knowledgeCategoryMapper.insertBatch(knowledgeId, List.of(categoryId));
        }
        log.debug("分类关联已同步: knowledgeId={}, categoryId={}", knowledgeId, categoryId);
    }

    /**
     * 调用 Qdrant REST API 删除指定 point
     * 失败不影响主流程（只记录日志），避免 Qdrant 不可用时阻塞知识删除
     */
    private void deleteVectorsFromQdrant(String vectorIds) {
        if (vectorIds == null || vectorIds.isBlank()) return;

        try {
            String url = String.format("http://%s:%d/collections/%s/points/delete",
                    qdrantHost, qdrantPort, qdrantCollection);

            List<String> ids = Arrays.asList(vectorIds.split(","));
            Map<String, Object> body = new HashMap<>();
            body.put("points", ids);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Qdrant vectors deleted: {} points ({})", ids.size(), String.join(",", ids));
            } else {
                log.warn("Qdrant delete returned non-2xx: {} - {}", response.getStatusCode(), response.getBody());
            }
        } catch (Exception e) {
            log.error("Failed to delete vectors from Qdrant for knowledge, vector_ids={}", vectorIds, e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBase createFromUpload(MultipartFile file, Long categoryId, String title) throws IOException {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setTitle(title);
        kb.setCategoryId(categoryId);
        kb.setSourceType("upload");
        kb.setFileType("docx");
        kb.setContent("");
        kb.setVectorStatus("pending");
        save(kb);

        try {
            // 2. 存储文件到磁盘
            String filePath = fileStorageService.store(file, categoryId, kb.getId());
            kb.setFilePath(filePath);
            updateById(kb);
            // 同步分类关联表
            updateCategory(kb.getId(), categoryId);
            log.info("upload created: id={}, title={}, categoryId={}", kb.getId(), title, categoryId);
        } catch (IOException e) {
            // 文件写入失败：删除已创建的 DB 记录，避免脏数据
            log.error("file store failed, rolling back: id={}", kb.getId(), e);
            removeById(kb.getId());
            throw new IOException("文件存储失败，请检查磁盘权限", e);
        }

        return kb;
    }
}
