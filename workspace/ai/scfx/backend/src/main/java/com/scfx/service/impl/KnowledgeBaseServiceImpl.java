package com.scfx.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scfx.entity.KnowledgeBase;
import com.scfx.mapper.KnowledgeBaseMapper;
import com.scfx.service.FileStorageService;
import com.scfx.service.KnowledgeBaseService;
import com.scfx.service.VectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl extends ServiceImpl<KnowledgeBaseMapper, KnowledgeBase>
    implements KnowledgeBaseService {

    private final VectorStore vectorStore;
    private final FileStorageService fileStorageService;

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

        vectorStore.deleteVector(id);

        kb.setVizX(null);
        kb.setVizY(null);
        kb.setVizZ(null);
        baseMapper.updateById(kb);

        return super.removeById(id);
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