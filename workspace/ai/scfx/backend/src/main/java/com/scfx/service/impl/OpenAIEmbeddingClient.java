package com.scfx.service.impl;

import com.scfx.service.VectorClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.*;

/**
 * OpenAI Embedding 客户端实现
 */
@Component
@ConfigurationProperties(prefix = "vector.openai")
@Data
public class OpenAIEmbeddingClient implements VectorClient {

    private String apiUrl = "https://api.openai.com/v1/embeddings";
    private String apiKey;
    private String model = "text-embedding-ada-002";
    private int dimensions = 1536;
    private int timeout = 30000;

    private final RestTemplate restTemplate;

    public OpenAIEmbeddingClient() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public VectorResult embed(String text) {
        if (text == null || text.isEmpty()) {
            return new VectorResult("vec_empty");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = new HashMap<>();
            body.put("input", text);
            body.put("model", model);
            body.put("encoding_format", "float");

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            // 调用 OpenAI API
            ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                List<?> data = (List<?>) response.getBody().get("data");
                if (data != null && !data.isEmpty()) {
                    Map<String, Object> embedding = (Map<String, Object>) data.get(0);
                    String vectorId = (String) embedding.get("id");

                    @SuppressWarnings("unchecked")
                    List<Number> embeddingList = (List<Number>) embedding.get("embedding");
                    float[] vector = new float[embeddingList.size()];
                    for (int i = 0; i < embeddingList.size(); i++) {
                        vector[i] = embeddingList.get(i).floatValue();
                    }

                    return new VectorResult(vectorId, vector);
                }
            }

            return new VectorResult("vec_error");

        } catch (Exception e) {
            // 失败时返回占位符，实际应记录日志
            System.err.println("Embedding failed: " + e.getMessage());
            return new VectorResult("vec_error_" + System.currentTimeMillis());
        }
    }
}