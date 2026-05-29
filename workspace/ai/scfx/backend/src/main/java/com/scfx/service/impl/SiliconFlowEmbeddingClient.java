package com.scfx.service.impl;

import com.scfx.service.VectorClient;
import com.scfx.service.VectorMetrics;
import com.scfx.service.VectorProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * 硅基流动 + BGE-M3 文本向量化客户端
 */
@Slf4j
@Component
public class SiliconFlowEmbeddingClient implements VectorClient {

    private final VectorProperties.SiliconFlow config;
    private final RestTemplate restTemplate;
    private final VectorMetrics metrics;

    public SiliconFlowEmbeddingClient(VectorProperties properties, VectorMetrics metrics) {
        this.config = properties.getSiliconflow();
        this.metrics = metrics;
        var requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(config.getConnectTimeout());
        requestFactory.setReadTimeout(config.getReadTimeout());
        this.restTemplate = new RestTemplate(requestFactory);
    }

    @Override
    public VectorResult embed(String text) {
        if (text == null || text.isEmpty()) {
            return new VectorResult("sf_empty");
        }

        long start = System.currentTimeMillis();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(config.getApiKey());

            Map<String, Object> body = new HashMap<>();
            body.put("model", config.getModel());
            body.put("input", text);
            body.put("encoding_format", "float");

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(config.getApiUrl(), request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                List<?> data = (List<?>) response.getBody().get("data");
                if (data != null && !data.isEmpty()) {
                    Map<String, Object> embedding = (Map<String, Object>) data.get(0);
                    String vectorId = "sf_" + System.currentTimeMillis();

                    @SuppressWarnings("unchecked")
                    List<Number> embeddingList = (List<Number>) embedding.get("embedding");
                    if (embeddingList == null) {
                        log.warn("SiliconFlow 返回的 embedding 为空");
                        metrics.record("siliconflow", System.currentTimeMillis() - start, false);
                        return new VectorResult("sf_error");
                    }

                    float[] vector = new float[embeddingList.size()];
                    for (int i = 0; i < embeddingList.size(); i++) {
                        vector[i] = embeddingList.get(i).floatValue();
                    }

                    long elapsed = System.currentTimeMillis() - start;
                    metrics.record("siliconflow", elapsed, true);
                    log.info("SiliconFlow embed 成功: textLength={}, dims={}, vectorId={}, cost={}ms",
                        text.length(), vector.length, vectorId, elapsed);
                    return new VectorResult(vectorId, vector);
                }
            }

            long elapsed = System.currentTimeMillis() - start;
            metrics.record("siliconflow", elapsed, false);
            log.warn("SiliconFlow API 返回异常: status={}", response.getStatusCode());
            return new VectorResult("sf_error");

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            metrics.record("siliconflow", elapsed, false);
            log.error("SiliconFlow embed 失败: {}, cost={}ms", e.getMessage(), elapsed);
            return new VectorResult("sf_error_" + System.currentTimeMillis());
        }
    }
}
