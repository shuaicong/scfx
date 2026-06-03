package com.scfx.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scfx.common.Result;
import com.scfx.entity.KnowledgeBase;
import com.scfx.entity.KnowledgeChunk;
import com.scfx.entity.VectorizationLog;
import com.scfx.mapper.KnowledgeBaseMapper;
import com.scfx.mapper.KnowledgeChunkMapper;
import com.scfx.mapper.VectorizationLogMapper;
import com.scfx.service.impl.KnowledgeSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库运行时统计端点。
 * <p>
 * 提供切片分布、向量化成功率等运维监控指标。
 * 数据来源：实时查询数据库，无需额外收集管道。
 */
@Slf4j
@RestController
@RequestMapping("/knowledge")
@RequiredArgsConstructor
public class KnowledgeStatsController {

    private final KnowledgeBaseMapper knowledgeMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final VectorizationLogMapper logMapper;
    private final KnowledgeSearchService knowledgeSearchService;

    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();

        // ======================== 文档统计 ========================
        long totalDocs = knowledgeMapper.selectCount(
            new LambdaQueryWrapper<KnowledgeBase>().eq(KnowledgeBase::getDeleted, 0));
        long chunkedDocs = knowledgeMapper.selectCount(
            new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getDeleted, 0)
                .gt(KnowledgeBase::getChunkCount, 0));
        long nonChunkedDocs = knowledgeMapper.selectCount(
            new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getDeleted, 0)
                .eq(KnowledgeBase::getChunkCount, 0));
        long vectorizedDocs = knowledgeMapper.selectCount(
            new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getDeleted, 0)
                .eq(KnowledgeBase::getVectorStatus, "vectorized"));
        long failedDocs = knowledgeMapper.selectCount(
            new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getDeleted, 0)
                .eq(KnowledgeBase::getVectorStatus, "failed"));

        stats.put("totalDocs", totalDocs);
        stats.put("chunkedDocs", chunkedDocs);
        stats.put("nonChunkedDocs", nonChunkedDocs);
        stats.put("shortTextRatio", totalDocs > 0
            ? Math.round((double) nonChunkedDocs / totalDocs * 10000) / 100.0 : 0);
        stats.put("vectorizedDocs", vectorizedDocs);
        stats.put("failedDocs", failedDocs);
        stats.put("vectorizationSuccessRate", (vectorizedDocs + failedDocs) > 0
            ? Math.round((double) vectorizedDocs / (vectorizedDocs + failedDocs) * 10000) / 100.0 : 0);

        // ======================== 切片分布 ========================
        List<KnowledgeBase> chunkedList = knowledgeMapper.selectList(
            new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getDeleted, 0)
                .gt(KnowledgeBase::getChunkCount, 0));

        int totalChunks = 0;
        int maxChunks = 0;
        for (KnowledgeBase kb : chunkedList) {
            int c = kb.getChunkCount() != null ? kb.getChunkCount() : 0;
            totalChunks += c;
            if (c > maxChunks) maxChunks = c;
        }
        stats.put("avgChunksPerDoc", chunkedList.size() > 0
            ? Math.round((double) totalChunks / chunkedList.size() * 100) / 100.0 : 0);
        stats.put("maxChunks", maxChunks);
        stats.put("totalChunks", totalChunks);

        // ======================== 向量化统计 ========================
        long vectorizedChunks = chunkMapper.selectCount(
            new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getVectorStatus, "vectorized")
                .eq(KnowledgeChunk::getIsActive, 1));
        long pendingChunks = chunkMapper.selectCount(
            new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getVectorStatus, "pending")
                .eq(KnowledgeChunk::getIsActive, 1));
        long failedChunks = chunkMapper.selectCount(
            new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getVectorStatus, "failed")
                .eq(KnowledgeChunk::getIsActive, 1));

        stats.put("vectorizedChunks", vectorizedChunks);
        stats.put("pendingChunks", pendingChunks);
        stats.put("failedChunks", failedChunks);

        // ======================== 向量化耗时 ========================
        LambdaQueryWrapper<VectorizationLog> avgWrapper = new LambdaQueryWrapper<>();
        avgWrapper.eq(VectorizationLog::getStatus, "success")
                   .isNotNull(VectorizationLog::getProcessTimeMs)
                   .last("ORDER BY created_at DESC LIMIT 100");
        List<VectorizationLog> recentLogs = logMapper.selectList(avgWrapper);
        double avgTime = recentLogs.stream()
            .filter(l -> l.getProcessTimeMs() != null)
            .mapToInt(VectorizationLog::getProcessTimeMs)
            .average().orElse(0);
        stats.put("recentVectorizationCount", recentLogs.size());
        stats.put("avgVectorizationTimeMs", Math.round(avgTime * 10) / 10.0);

        // ======================== 检索统计 ========================
        Map<String, Object> searchMetrics = knowledgeSearchService.getMetrics();
        stats.putAll(searchMetrics);

        log.debug("知识库统计: totalDocs={}, chunkedDocs={}, vectorized={}%, avgTime={}ms, queue={}",
            totalDocs, chunkedDocs,
            stats.get("vectorizationSuccessRate"), avgTime,
            stats.get("queueDepth"));

        return Result.success(stats);
    }
}
