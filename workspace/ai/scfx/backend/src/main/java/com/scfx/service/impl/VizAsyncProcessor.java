package com.scfx.service.impl;

import com.scfx.entity.KnowledgeBase;
import com.scfx.service.VectorClient;
import com.scfx.service.VectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * DashScope 可视化向量异步处理器
 * 独立 Bean 解决 @Async 自调用失效问题
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VizAsyncProcessor {

    private final VectorClient vectorClient;
    private final VectorStore vectorStore;

    /**
     * 异步调用 DashScope 多模态向量化，不阻塞 BGE-M3 主流程
     * @return CompletableFuture:
     *   TRUE  = 成功
     *   FALSE = 非 429 失败（如 API 错误）
     *   null  = 429 限流，下次批量重试
     */
    @Async("vizExecutor")
    public CompletableFuture<Boolean> asyncVizCall(KnowledgeBase kb) {
        try {
            VectorClient.VectorResult vizResult = vectorClient.embedVisualization(
                kb.getContent(), kb.getContentHtml(), kb.getId());

            if (vizResult.getVector() != null && vizResult.getVector().length > 0) {
                // 成功：保存向量
                vectorStore.saveVector(kb.getId(), vizResult.getVector());
                log.info("DashScope viz async success: knowledgeId={}, dims={}",
                    kb.getId(), vizResult.getVector().length);
                return CompletableFuture.completedFuture(Boolean.TRUE);
            }

            // 429 限流 — 标记为 rate_limited，下次批量补偿
            if ("ds_rate_limited".equals(vizResult.getVectorId())) {
                log.warn("DashScope 429 rate limited: knowledgeId={}", kb.getId());
                vectorStore.updateStatus(kb.getId(), "rate_limited");
                return CompletableFuture.completedFuture(null);
            }

            // 非 429 失败
            log.warn("DashScope viz failed: knowledgeId={}, vectorId={}",
                kb.getId(), vizResult.getVectorId());
            vectorStore.updateStatus(kb.getId(), "failed");
            return CompletableFuture.completedFuture(Boolean.FALSE);

        } catch (Exception e) {
            log.warn("DashScope viz async exception: knowledgeId={}, err={}",
                kb.getId(), e.getMessage());
            vectorStore.updateStatus(kb.getId(), "failed");
            return CompletableFuture.completedFuture(Boolean.FALSE);
        }
    }
}
