package com.scfx.controller;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

/**
 * AI 问答代理 Controller
 * 将前端 /api/ai-chat/* 请求转发到 Python ai-qa-service（localhost:5002）
 * 避免前端直连 Python 服务，统一通过 Spring Boot 后端路由
 */
@Slf4j
@RestController
@RequestMapping("/ai-chat")
public class AiChatProxyController {

    @Value("${app.ai-qa-service.url}")
    private String aiQaServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 非流式问答
     */
    @PostMapping("/chat")
    public ResponseEntity<String> chat(@RequestBody String body) {
        String url = aiQaServiceUrl + "/api/chat";
        log.debug("Proxying POST /api/ai-chat/chat -> {}", url);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
        return ResponseEntity.status(response.getStatusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(response.getBody());
    }

    /**
     * SSE 流式问答（关键：逐字转发 Python 服务的 SSE 流）
     */
    @PostMapping("/stream")
    public void stream(@RequestBody String body, HttpServletResponse response) {
        String url = aiQaServiceUrl + "/api/chat/stream";
        log.debug("Proxying POST /api/ai-chat/stream -> {}", url);

        restTemplate.execute(url, HttpMethod.POST,
                req -> {
                    req.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    req.getBody().write(bytes);
                },
                res -> {
                    response.setContentType("text/event-stream");
                    response.setCharacterEncoding("UTF-8");
                    response.setHeader("Cache-Control", "no-cache");
                    response.setHeader("X-Accel-Buffering", "no");

                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    InputStream stream = res.getBody();
                    while ((bytesRead = stream.read(buffer)) != -1) {
                        response.getOutputStream().write(buffer, 0, bytesRead);
                        response.getOutputStream().flush();
                    }
                    return null;
                });
    }

    /**
     * 知识库搜索（代理到 Python 服务的 /api/knowledge/search）
     */
    @GetMapping("/search")
    public ResponseEntity<String> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int top_k) {
        String url = aiQaServiceUrl + "/api/knowledge/search?query=" + query + "&top_k=" + top_k;
        log.debug("Proxying GET /api/ai-chat/search -> {}", url);

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.getBody());
        } catch (Exception e) {
            log.warn("搜索代理失败: {}", e.getMessage());
            return ResponseEntity.ok("{\"results\":[]}");
        }
    }

    /**
     * 获取聊天历史（返回空列表，服务端不存储历史）
     */
    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> history(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(Map.of(
                "data", Collections.emptyList(),
                "total", 0,
                "page", page,
                "size", size
        ));
    }

    /**
     * 清除聊天历史（无操作）
     */
    @DeleteMapping("/history")
    public ResponseEntity<Map<String, String>> clearHistory() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
