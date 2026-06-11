package com.scfx.service;

import com.scfx.entity.KnowledgeBase;
import com.scfx.entity.KnowledgeChunk;
import com.scfx.entity.KnowledgeTask;
import com.scfx.mapper.KnowledgeBaseMapper;
import com.scfx.mapper.KnowledgeChunkMapper;
import com.scfx.mapper.KnowledgeTaskMapper;
import com.scfx.util.DocxTextExtractor;
import com.scfx.util.TextSplitter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文档解析管道：解析 → 切片
 * <p>
 * 1. 从上传文件提取纯文本
 * 2. 合规检查（敏感词 + 有效文本判断）
 * 3. 语义优先切片（TextSplitter）
 * 4. 切片入库后由 VectorTaskService 异步向量化
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentPipeline {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final KnowledgeTaskMapper knowledgeTaskMapper;
    private final ContentFilter contentFilter;
    private final FileStorageService fileStorageService;
    private final VectorTaskService vectorTaskService;

    @Value("${app.ai-qa-service.url}")
    private String aiQaServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /** 最大重试次数（指数退避：10s、20s、40s） */
    static final int MAX_RETRIES = 3;

    /**
     * 启动知识处理管道（异步）
     * 解析文件提取文本 → 合规检查 → 切片入库
     * <p>
     * 失败自动重试，指数退避（10s → 20s → 40s），最终标记 failed 不阻塞主流程。
     */
    @Async("chunkExecutor")
    public void start(Long knowledgeId) {
        int attempt = 0;
        while (attempt <= MAX_RETRIES) {
            try {
                processKnowledge(knowledgeId);

                // 切片完成后顺序触发向量化
                try {
                    vectorTaskService.processSingle(knowledgeId);
                } catch (Exception e) {
                    log.warn("向量化触发失败（不影响切片结果）: knowledgeId={}", knowledgeId, e);
                }

                // 事务提交后再触发 Qdrant 同步，不拉长事务窗口
                syncQdrant(knowledgeId);

                return;
            } catch (Exception e) {
                attempt++;
                if (attempt > MAX_RETRIES) {
                    markFailed(knowledgeId, e);
                    return;
                }
                long delay = (long) Math.pow(2, attempt - 1) * 10_000;
                log.warn("文档处理失败，{}秒后重试 {}/{}: knowledgeId={}",
                    delay / 1000, attempt, MAX_RETRIES, knowledgeId, e);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    markFailed(knowledgeId, e);
                    return;
                }
            }
        }
    }

    /**
     * 核心处理逻辑（提取为独立方法，便于重试循环复用）
     * 每次调用都会从数据库重新读取 KB 和 Task，获取最新状态
     */
    private void processKnowledge(Long knowledgeId) throws Exception {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(knowledgeId);
        if (kb == null) {
            log.warn("知识不存在: knowledgeId={}", knowledgeId);
            return;
        }

        KnowledgeTask task = createTask(kb);

        // Step 1: 解析
        String content = extractContent(kb);
        if (content == null || content.isBlank()) {
            log.info("文档无内容，跳过处理: knowledgeId={}", knowledgeId);
            completeTask(task, kb, 0);
            return;
        }

        // 合规检查
        String checkResult = contentFilter.check(content);
        if (checkResult != null) {
            log.warn("合规检查未通过 | knowledgeId={} | {}", knowledgeId, checkResult);
            kb.setContent("");
            knowledgeBaseMapper.updateById(kb);
            task.setStatus("failed");
            task.setErrorMessage(checkResult);
            task.setErrorCategory("CONTENT_INVALID");
            knowledgeTaskMapper.updateById(task);
            return;
        }

        kb.setContent(content);
        knowledgeBaseMapper.updateById(kb);
        updateTask(task, "parsing", 40);
        log.info("解析完成 | knowledgeId={} | chars={}", knowledgeId, content.length());

        // Step 2: 切片
        List<TextSplitter.Chunk> segments = TextSplitter.split(content);
        if (segments.isEmpty()) {
            log.info("无需切片（短文本）| knowledgeId={}", knowledgeId);
            completeTask(task, kb, 0);
            return;
        }

        // 软删除旧切片
        knowledgeChunkMapper.updateByKnowledgeId(knowledgeId, 0);

        // 批量插入
        List<KnowledgeChunk> chunks = segments.stream()
            .map(s -> buildChunk(kb, s))
            .collect(Collectors.toList());
        int batchSize = 200;
        for (int i = 0; i < chunks.size(); i += batchSize) {
            int end = Math.min(i + batchSize, chunks.size());
            knowledgeChunkMapper.insertBatch(chunks.subList(i, end));
        }

        kb.setChunkCount(chunks.size());
        knowledgeBaseMapper.updateById(kb);

        updateTask(task, "chunking", 70);
        log.info("切片完成 | knowledgeId={} | chunks={}", knowledgeId, chunks.size());

        // Step 3: 完成（向量化由 VectorTaskService 异步执行）
        completeTask(task, kb, chunks.size());
    }

    /**
     * 重试耗尽，标记永久失败
     */
    private void markFailed(Long knowledgeId, Exception e) {
        log.error("文档处理重试耗尽: knowledgeId={}", knowledgeId, e);
        try {
            KnowledgeBase kb = knowledgeBaseMapper.selectById(knowledgeId);
            if (kb != null) {
                // 不覆盖已成功的向量化状态（BGE-M3 成功后再失败的重试不应回滚）
                if (!"vectorized".equals(kb.getVectorStatus())) {
                    kb.setVectorStatus("failed");
                    knowledgeBaseMapper.updateById(kb);
                }
            }
            KnowledgeTask task = knowledgeTaskMapper.selectByKnowledgeId(knowledgeId);
            if (task != null) {
                task.setStatus("failed");
                task.setErrorMessage("重试耗尽：" + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
                knowledgeTaskMapper.updateById(task);
            }
        } catch (Exception ex) {
            log.warn("标记失败状态异常: knowledgeId={}", knowledgeId, ex);
        }
    }

    // ======================== 内部方法 ========================

    private String extractContent(KnowledgeBase kb) throws IOException {
        String filePath = kb.getFilePath();
        if (filePath == null || filePath.isBlank()) {
            return kb.getContent();
        }

        String lower = filePath.toLowerCase();
        if (lower.endsWith(".txt") || lower.endsWith(".md")) {
            File file = fileStorageService.load(filePath);
            return Files.readString(file.toPath());
        } else if (lower.endsWith(".docx")) {
            try {
                return DocxTextExtractor.extract(filePath);
            } catch (Exception e) {
                log.warn("DocxTextExtractor 提取失败 | knowledgeId={}", kb.getId(), e);
                return "";
            }
        } else if (lower.endsWith(".pdf")) {
            try {
                File file = fileStorageService.load(filePath);
                String raw = Files.readString(file.toPath());
                // 从 PDF 原始内容中提取可见文本
                return raw.replaceAll("[^\\u4e00-\\u9fa5\\u3000-\\u303f\\uff00-\\uffefa-zA-Z0-9\\n]", " ")
                    .replaceAll("\\s+", " ").trim();
            } catch (Exception e) {
                log.warn("PDF 解析失败 | knowledgeId={}", kb.getId(), e);
                return "";
            }
        }
        return "";
    }

    private KnowledgeTask createTask(KnowledgeBase kb) {
        KnowledgeTask task = knowledgeTaskMapper.selectByKnowledgeId(kb.getId());
        if (task == null) {
            task = new KnowledgeTask();
            task.setKnowledgeId(kb.getId());
            task.setCategoryId(kb.getCategoryId());
            task.setStatus("processing");
            task.setProgress(0);
            task.setFileType(kb.getFileType());
            if (kb.getFilePath() != null) {
                try {
                    File f = fileStorageService.load(kb.getFilePath());
                    if (f.exists()) task.setFileSize(f.length());
                } catch (Exception ignored) {}
            }
            knowledgeTaskMapper.insert(task);

            kb.setVectorStatus("processing");
            knowledgeBaseMapper.updateById(kb);
        } else {
            // 重试场景：重置为处理中状态
            task.setStatus("processing");
            task.setProgress(0);
            task.setErrorMessage(null);
            task.setErrorCategory(null);
            task.setCurrentStep(null);
            knowledgeTaskMapper.updateById(task);

            kb.setVectorStatus("processing");
            knowledgeBaseMapper.updateById(kb);
        }
        return task;
    }

    private void updateTask(KnowledgeTask task, String step, int progress) {
        task.setCurrentStep(step);
        task.setProgress(progress);
        knowledgeTaskMapper.updateById(task);
    }

    /**
     * 完成处理：先提交 DB 更新（chunk_count、vector_status），再触发 Qdrant 同步。
     * DB 更新和 HTTP 调用分开，避免长事务阻塞其他线程读取最新 chunk_count。
     */
    @Transactional
    public void completeTask(KnowledgeTask task, KnowledgeBase kb, int totalChunks) {
        kb.setChunkCount(totalChunks);
        kb.setVectorStatus("vectorized");
        knowledgeBaseMapper.updateById(kb);

        task.setStatus("completed");
        task.setCurrentStep(null);
        task.setProgress(100);
        task.setTotalChunks(totalChunks);
        knowledgeTaskMapper.updateById(task);

        log.info("文档处理完成 | knowledgeId={} | totalChunks={}", kb.getId(), totalChunks);
    }

    /**
     * 事务提交后再触发 Qdrant 同步，避免 HTTP 调用拉长事务窗口。
     */
    public void syncQdrant(Long knowledgeId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>("{}", headers);
            String url = aiQaServiceUrl + "/api/knowledge/" + knowledgeId + "/revectorize";
            restTemplate.postForEntity(url, entity, String.class);
            log.info("Qdrant 同步触发: knowledgeId={}", knowledgeId);
        } catch (Exception e) {
            log.warn("Qdrant 同步失败（不影响主流程）: knowledgeId={}, err={}", knowledgeId, e.getMessage());
        }
    }

    private KnowledgeChunk buildChunk(KnowledgeBase kb, TextSplitter.Chunk seg) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setKnowledgeId(kb.getId());
        chunk.setCategoryId(kb.getCategoryId());
        chunk.setChunkIndex(seg.getIndex());
        chunk.setChunkTotal(0);
        chunk.setContent(seg.getText());
        chunk.setStartOffset(seg.getStartOffset());
        chunk.setEndOffset(seg.getEndOffset());
        chunk.setIsSummary(seg.isSummary() ? 1 : 0);
        chunk.setTokenCount(seg.getEstimatedTokens());
        chunk.setChunkType(seg.getChunkType());  // "text" | "table"
        chunk.setVectorStatus("pending");
        chunk.setIsActive(1);
        return chunk;
    }
}
