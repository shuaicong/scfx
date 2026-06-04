package com.scfx.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scfx.entity.KnowledgeBase;
import com.scfx.entity.KnowledgeChunk;
import com.scfx.mapper.KnowledgeBaseMapper;
import com.scfx.mapper.KnowledgeChunkMapper;
import com.scfx.service.VectorClient;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识检索服务。
 * <p>
 * 搜索流程：
 * 1. query → BGE-M3 向量化
 * 2. 加载该分类所有已向量化的切片
 * 3. 逐片余弦相似度计算
 * 4. 按 knowledge_id 聚合，MAX score，summary 切片 ×1.1
 * 5. 返回 Top-K 文档
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeSearchService {

    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeBaseMapper knowledgeMapper;
    private final VectorClient vectorClient;

    /** 检索指标计数器 */
    private final java.util.concurrent.atomic.AtomicLong searchCount = new java.util.concurrent.atomic.AtomicLong(0);
    private final java.util.concurrent.atomic.AtomicLong summaryRecallCount = new java.util.concurrent.atomic.AtomicLong(0);
    private final java.util.concurrent.atomic.AtomicLong totalRecallChunks = new java.util.concurrent.atomic.AtomicLong(0);
    private long lastMetricsResetTime = System.currentTimeMillis();

    /**
     * 返回当前检索指标快照。
     */
    public Map<String, Object> getMetrics() {
        long elapsed = (System.currentTimeMillis() - lastMetricsResetTime) / 1000;
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("totalSearches", searchCount.get());
        m.put("summaryRecallCount", summaryRecallCount.get());
        m.put("totalRecallChunks", totalRecallChunks.get());
        m.put("searchQps", elapsed > 0
            ? Math.round((double) searchCount.get() / elapsed * 100) / 100.0 : 0);
        return m;
    }

    /**
     * 语义搜索结果
     */
    @Data
    public static class SearchResult {
        private Long id;
        private String title;
        private String content;
        private String sourceType;
        private double score;
        private int chunkCount;
        private Long matchedChunkId;
        private Integer matchedChunkIndex;
        private Integer matchedStartOffset;
        private Integer matchedEndOffset;
        private boolean isSummaryMatch;
    }

    /**
     * 对指定分类执行语义搜索。
     *
     * @param query      用户查询文本
     * @param categoryId 分类 ID
     * @param topK       返回 TOP-K 条结果
     * @return 排序后的搜索结果
     */
    public List<SearchResult> search(String query, Long categoryId, int topK) {
        if (query == null || query.isBlank()) return List.of();
        if (categoryId == null) return List.of();

        long startTime = System.currentTimeMillis();

        // 1. Query 向量化
        VectorClient.VectorResult queryResult = vectorClient.embed(query);
        if (queryResult.getVector() == null || queryResult.getVector().length == 0) {
            log.warn("查询向量化失败: query={}", query);
            return List.of();
        }
        float[] queryVec = queryResult.getVector();

        // 2. 加载该分类所有已向量化的切片
        List<KnowledgeChunk> chunks = chunkMapper.selectVectorizedByCategoryId(categoryId);

        // 3. 逐片计算余弦相似度
        List<ScoredItem> scoredItems = new ArrayList<>();
        Set<Long> chunkedDocIds = new HashSet<>();

        for (KnowledgeChunk chunk : chunks) {
            if (chunk.getVectorBgeM3() == null) continue;
            double sim = cosineSimilarity(queryVec, chunk.getVectorBgeM3());
            // summary 切片加权 ×1.1
            if (chunk.getIsSummary() != null && chunk.getIsSummary() == 1) {
                sim *= 1.1;
            }
            scoredItems.add(new ScoredItem(chunk.getKnowledgeId(), sim, chunk));
            chunkedDocIds.add(chunk.getKnowledgeId());
        }

        // 4. 非切片文档（< 500 字，无 chunk 向量）：实时向量化后比较
        handleNonChunkedDocs(queryVec, categoryId, chunkedDocIds, scoredItems);

        // 5. 按 knowledge_id 聚合：MAX score
        Map<Long, ScoredItem> aggregated = new LinkedHashMap<>();
        for (ScoredItem item : scoredItems) {
            aggregated.merge(item.knowledgeId, item,
                (a, b) -> a.score >= b.score ? a : b);
        }

        // 6. 排序取 topK
        List<ScoredItem> top = aggregated.values().stream()
            .sorted((a, b) -> Double.compare(b.score, a.score))
            .limit(topK)
            .collect(Collectors.toList());

        // 7. 加载元数据组装结果
        List<SearchResult> results = buildResults(top);

        long elapsed = System.currentTimeMillis() - startTime;

        // 8. 更新检索指标
        searchCount.incrementAndGet();
        totalRecallChunks.addAndGet(chunks.size());
        for (ScoredItem item : top) {
            if (item.bestChunk != null && item.bestChunk.getIsSummary() != null
                && item.bestChunk.getIsSummary() == 1) {
                summaryRecallCount.incrementAndGet();
            }
        }

        log.info("语义搜索完成: query={}, categoryId={}, chunks={}, docs={}, topK={}, cost={}ms",
            query, categoryId, chunks.size(), aggregated.size(), top.size(), elapsed);

        return results;
    }

    /**
     * 回退搜索：从 t_knowledge_base.retrieval_vector 读取 BGE-M3 检索向量。
     * <p>
     * 对所有有 retrieval_vector 的文档进行搜索（不排除 chunked 文档），
     * 后续 step 5 的 MAX score 聚合会按 knowledge_id 去重。
     * 当 chunk 向量不可用时（vector_bge_m3 为空），此路径作为兜底。
     */
    private void handleNonChunkedDocs(float[] queryVec, Long categoryId,
                                       Set<Long> chunkedDocIds, List<ScoredItem> scoredItems) {
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<KnowledgeBase>()
            .eq(KnowledgeBase::getDeleted, 0)
            .eq(KnowledgeBase::getCategoryId, categoryId)
            .isNotNull(KnowledgeBase::getRetrievalVector);
        // 不排除 chunkedDocIds — chunked 文档也通过 retrieval_vector 参与搜索，
        // 后续 chunk 级别的 vector_bge_m3 可用后可改为仅回退非切片文档
        List<KnowledgeBase> docs = knowledgeMapper.selectList(wrapper);
        for (KnowledgeBase kb : docs) {
            if (kb.getRetrievalVector() == null) continue;
            double sim = cosineSimilarity(queryVec, kb.getRetrievalVector());
            scoredItems.add(new ScoredItem(kb.getId(), sim, null));
        }
    }

    /**
     * 组装搜索结果，加载文档元数据。
     */
    private List<SearchResult> buildResults(List<ScoredItem> top) {
        if (top.isEmpty()) return List.of();

        Set<Long> ids = top.stream().map(ScoredItem::getKnowledgeId)
            .collect(Collectors.toSet());
        Map<Long, KnowledgeBase> kbMap = knowledgeMapper.selectList(
            new LambdaQueryWrapper<KnowledgeBase>().in(KnowledgeBase::getId, ids)
        ).stream().collect(Collectors.toMap(KnowledgeBase::getId, kb -> kb));

        List<SearchResult> results = new ArrayList<>();
        for (ScoredItem item : top) {
            KnowledgeBase kb = kbMap.get(item.knowledgeId);
            if (kb == null) continue;

            SearchResult r = new SearchResult();
            r.setId(kb.getId());
            r.setTitle(kb.getTitle());
            r.setContent(kb.getContent() != null && kb.getContent().length() > 200
                ? kb.getContent().substring(0, 200) : kb.getContent());
            r.setSourceType(kb.getSourceType());
            r.setScore(Math.round(item.score * 10000) / 10000.0);
            r.setChunkCount(kb.getChunkCount() != null ? kb.getChunkCount() : 0);

            // 命中切片信息（前端高亮用）
            if (item.bestChunk != null) {
                r.setMatchedChunkId(item.bestChunk.getId());
                r.setMatchedChunkIndex(item.bestChunk.getChunkIndex());
                r.setMatchedStartOffset(item.bestChunk.getStartOffset());
                r.setMatchedEndOffset(item.bestChunk.getEndOffset());
                r.setSummaryMatch(item.bestChunk.getIsSummary() != null
                    && item.bestChunk.getIsSummary() == 1);
            }

            results.add(r);
        }
        return results;
    }

    // ======================== 内部数据结构 ========================

    @Data
    @RequiredArgsConstructor
    private static class ScoredItem {
        private final Long knowledgeId;
        private final double score;
        private final KnowledgeChunk bestChunk;
    }

    // ======================== 余弦相似度 ========================

    private double cosineSimilarity(float[] a, float[] b) {
        int len = Math.min(a.length, b.length);
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < len; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0 ? 0 : dot / denom;
    }
}
