package com.scfx.service.impl;

import com.scfx.service.VectorClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 向量客户端路由器
 * 根据内容是否包含图片自动选择：无图走硅基流动 BGE-M3，有图走阿里云 DashScope 多模态
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class VectorClientRouter implements VectorClient {

    private final SiliconFlowEmbeddingClient siliconFlowClient;
    private final DashScopeMultimodalEmbeddingClient dashScopeClient;

    @Override
    public VectorResult embed(String text) {
        return siliconFlowClient.embed(text);
    }

    @Override
    public VectorResult embed(String text, String contentHtml) {
        return embed(text, contentHtml, null);
    }

    @Override
    public VectorResult embed(String text, String contentHtml, Long knowledgeId) {
        boolean hasImages = contentHtml != null && contentHtml.contains("<img");
        if (hasImages) {
            log.info("VectorRouter[检索]: 检测到图片，使用 DashScope 多模态, knowledgeId={}", knowledgeId);
            return dashScopeClient.embed(text, contentHtml, knowledgeId);
        }
        return siliconFlowClient.embed(text);
    }

    @Override
    public VectorResult embedVisualization(String text, String contentHtml, Long knowledgeId) {
        boolean hasText = text != null && !text.isEmpty();
        boolean hasImages = contentHtml != null && contentHtml.contains("<img");
        boolean hasContent = contentHtml != null && !contentHtml.trim().isEmpty();

        // 无任何内容 → 返回空
        if (!hasText && !hasContent) {
            log.warn("VectorRouter[可视化]: 入参为空，跳过向量化, knowledgeId={}", knowledgeId);
            return new VectorResult("ds_empty");
        }

        // 纯文本（无图）→ DashScope 纯文本模式，不解析 HTML
        if (!hasImages) {
            log.info("VectorRouter[可视化]: 纯文本模式 → DashScope 768d, knowledgeId={}", knowledgeId);
            return dashScopeClient.embedText(text);
        }

        // 图文混合 → DashScope 多模态模式，提取图片 URL
        log.info("VectorRouter[可视化]: 图文模式 → DashScope 多模态768d, knowledgeId={}, hasText={}",
            knowledgeId, hasText);
        return dashScopeClient.embed(text, contentHtml, knowledgeId);
    }
}
