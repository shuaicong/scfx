package com.scfx.controller;

import com.scfx.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final RestTemplate restTemplate;

    @Value("${app.ai-qa-service.url}")
    private String aiQaServiceUrl;

    @GetMapping("/list")
    public Result<Map<?, ?>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String vectorStatus) {
        String url = aiQaServiceUrl + "/api/knowledge?page=" + page + "&size=" + size;
        if (sourceType != null) url += "&sourceType=" + sourceType;
        if (vectorStatus != null) url += "&vectorStatus=" + vectorStatus;
        Map<?, ?> data = restTemplate.getForObject(url, Map.class);
        return Result.success(data);
    }

    @PostMapping("/ingest")
    public Result<Map<?, ?>> ingest(@RequestBody Map<String, Object> payload) {
        String url = aiQaServiceUrl + "/api/knowledge/ingest";
        Map<?, ?> data = restTemplate.postForObject(url, payload, Map.class);
        return Result.success(data);
    }
}