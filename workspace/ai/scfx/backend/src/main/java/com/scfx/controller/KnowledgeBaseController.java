package com.scfx.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeBaseController {

    @Autowired
    private RestTemplate restTemplate;

    private static final String AI_QA_SERVICE_URL = "http://localhost:5002";

    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String vectorStatus) {
        String url = AI_QA_SERVICE_URL + "/api/knowledge?page=" + page + "&size=" + size;
        if (sourceType != null) url += "&sourceType=" + sourceType;
        if (vectorStatus != null) url += "&vectorStatus=" + vectorStatus;
        return restTemplate.getForObject(url, Map.class);
    }

    @PostMapping("/ingest")
    public Map<String, Object> ingest(@RequestBody Map<String, Object> payload) {
        String url = AI_QA_SERVICE_URL + "/api/knowledge/ingest";
        return restTemplate.postForObject(url, payload, Map.class);
    }
}