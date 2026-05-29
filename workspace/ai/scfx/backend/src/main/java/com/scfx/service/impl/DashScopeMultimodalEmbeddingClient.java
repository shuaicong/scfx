package com.scfx.service.impl;

import com.scfx.service.VectorClient;
import com.scfx.service.VectorMetrics;
import com.scfx.service.VectorProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 阿里云百炼 DashScope 多模态向量化客户端
 * 支持文本 + 图片混合向量化
 */
@Slf4j
@Component
public class DashScopeMultimodalEmbeddingClient implements VectorClient {

    private final VectorProperties.DashScope config;
    private final RestTemplate restTemplate;
    private final VectorMetrics metrics;

    private static final Pattern IMG_SRC_PATTERN =
        Pattern.compile("<img[^>]+src\\s*=\\s*['\"]([^'\"]+)['\"]");

    public DashScopeMultimodalEmbeddingClient(VectorProperties properties, VectorMetrics metrics) {
        this.config = properties.getDashscope();
        this.metrics = metrics;
        var requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(config.getConnectTimeout());
        requestFactory.setReadTimeout(config.getReadTimeout());
        this.restTemplate = new RestTemplate(requestFactory);
    }

    @Override
    public VectorResult embed(String text) {
        return callDashScope(text, null);
    }

    @Override
    public VectorResult embed(String text, String contentHtml) {
        List<String> imageUrls = contentHtml != null ? extractImages(contentHtml) : null;
        return callDashScope(text, imageUrls);
    }

    @Override
    public VectorResult embed(String text, String contentHtml, Long knowledgeId) {
        List<String> imageUrls = contentHtml != null ? extractImages(contentHtml) : null;
        return callDashScope(text, imageUrls);
    }

    /**
     * 纯文本专用入口（不解析 HTML，减少开销）
     * 用于 embedVisualization 场景中确定无图时的调用
     */
    public VectorResult embedText(String text) {
        return callDashScope(text, null);
    }

    @SuppressWarnings("unchecked")
    private VectorResult callDashScope(String text, List<String> imageUrls) {
        long start = System.currentTimeMillis();

        // 边界处理：空入参
        boolean hasText = text != null && !text.isEmpty();
        boolean hasImages = imageUrls != null && !imageUrls.isEmpty();

        if (!hasText && !hasImages) {
            log.warn("DashScope 调用跳过: 没有文本和图片输入");
            return new VectorResult("ds_empty");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(config.getApiKey());

            // 构建 contents（按 DashScope 多模态接口规范）
            List<Map<String, Object>> contents = new ArrayList<>();
            if (hasText) {
                contents.add(Map.of("text", text));
            }
            if (hasImages) {
                for (String url : imageUrls) {
                    contents.add(Map.of("image", url));
                }
            }

            Map<String, Object> input = new HashMap<>();
            input.put("contents", contents);

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("dimension", config.getDimensions());

            Map<String, Object> body = new HashMap<>();
            body.put("model", config.getModel());
            body.put("input", input);
            body.put("parameters", parameters);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            log.info("DashScope 调用: textLength={}, imageCount={}, model={}, dims={}",
                hasText ? text.length() : 0,
                hasImages ? imageUrls.size() : 0,
                config.getModel(), config.getDimensions());

            ResponseEntity<Map> response = restTemplate.postForEntity(
                config.getApiUrl(), request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> output = (Map<String, Object>) response.getBody().get("output");
                if (output != null) {
                    List<?> embeddings = (List<?>) output.get("embeddings");
                    if (embeddings != null && !embeddings.isEmpty()) {
                        Map<String, Object> first = (Map<String, Object>) embeddings.get(0);
                        List<Number> embeddingList = (List<Number>) first.get("embedding");

                        if (embeddingList != null) {
                            float[] vector = new float[embeddingList.size()];
                            for (int i = 0; i < embeddingList.size(); i++) {
                                vector[i] = embeddingList.get(i).floatValue();
                            }

                            String vectorId = "ds_" + System.currentTimeMillis();
                            long elapsed = System.currentTimeMillis() - start;
                            metrics.record("dashscope", elapsed, true);
                            log.info("DashScope embed 成功: dims={}, vectorId={}, cost={}ms",
                                vector.length, vectorId, elapsed);
                            return new VectorResult(vectorId, vector);
                        }
                    }
                }
            }

            long elapsed = System.currentTimeMillis() - start;
            metrics.record("dashscope", elapsed, false);
            log.warn("DashScope API 返回异常: status={}, cost={}ms",
                response.getStatusCode(), elapsed);
            return new VectorResult("ds_error");

        } catch (HttpClientErrorException e) {
            long elapsed = System.currentTimeMillis() - start;
            metrics.record("dashscope", elapsed, false);
            if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                log.warn("DashScope 429 rate limited: cost={}ms", elapsed);
                return new VectorResult("ds_rate_limited");
            }
            log.warn("DashScope HTTP {} error: {}, cost={}ms",
                e.getStatusCode(), e.getMessage(), elapsed);
            return new VectorResult("ds_error_http_" + e.getRawStatusCode());

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            metrics.record("dashscope", elapsed, false);
            log.error("DashScope embed 失败: {}, cost={}ms", e.getMessage(), elapsed);
            return new VectorResult("ds_error_" + System.currentTimeMillis());
        }
    }

    /**
     * 从 HTML 中提取所有图片 URL
     */
    private List<String> extractImages(String contentHtml) {
        if (contentHtml == null || contentHtml.isEmpty()) return Collections.emptyList();

        List<String> urls = new ArrayList<>();
        Matcher matcher = IMG_SRC_PATTERN.matcher(contentHtml);

        while (matcher.find()) {
            String url = matcher.group(1);
            if (url.startsWith("http://") || url.startsWith("https://")) {
                urls.add(url);
            }
        }

        return urls.stream().distinct().collect(Collectors.toList());
    }
}
