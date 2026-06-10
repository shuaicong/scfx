package com.scfx.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * AI 问答代理 Controller
 * 将前端 /api/ai-chat/* 请求转发到 Python ai-qa-service（localhost:5002）
 * 避免前端直连 Python 服务，统一通过 Spring Boot 后端路由
 * <p>
 * 更新说明：
 * - stream/streamV2 返回 StreamingResponseBody，释放 Tomcat 线程
 * - 支持灰度路由（根据 user_id 哈希百分比决定是否走 v2）
 * - 请求透传 request_id 用于链路追踪
 */
@Slf4j
@RestController
@RequestMapping("/ai-chat")
public class AiChatProxyController {

    @Value("${app.ai-qa-service.url}")
    private String aiQaServiceUrl;

    @Value("${app.ai-qa.gray-ratio:0}")
    private int grayRatio;

    private final RestTemplate restTemplate = new RestTemplate();

    {
        restTemplate.setErrorHandler(new ResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
                return false; // 不抛出异常，让调用方自行处理错误状态码
            }
            @Override
            public void handleError(org.springframework.http.client.ClientHttpResponse response) {
                // no-op — hasError 始终返回 false，不会走到这里
            }
        });
    }

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

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.getBody());
        } catch (HttpClientErrorException e) {
            // 透传 Python 服务的 4xx 错误（如参数校验失败）
            return ResponseEntity.status(e.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(e.getResponseBodyAsString());
        }
    }

    /**
     * SSE 流式问答（v2 灰度端点）
     * 与 /stream 功能相同但指向不同的 Python 端点，用于灰度测试。
     */
    @PostMapping("/v2/stream")
    public StreamingResponseBody streamV2(@RequestBody String body,
                                          @RequestHeader(value = "X-User-Id", required = false) String userId) {
        String url = aiQaServiceUrl + "/api/chat/v2/stream";
        log.debug("Proxying POST /api/ai-chat/v2/stream -> {}", url);
        return proxyStream(url, body, userId);
    }

    /**
     * SSE 流式问答（标准端点）
     */
    @PostMapping("/stream")
    public StreamingResponseBody stream(@RequestBody String body,
                                        @RequestHeader(value = "X-User-Id", required = false) String userId) {
        String url = aiQaServiceUrl + "/api/chat/stream";
        log.debug("Proxying POST /api/ai-chat/stream -> {}", url);
        return proxyStream(url, body, userId);
    }

    /**
     * 核心流式代理方法
     * <p>
     * 使用 RestTemplate.execute() 将请求转发到目标 URL，
     * 将 Python 服务的 SSE 响应字节逐块写入 StreamingResponseBody 的 OutputStream。
     * <p>
     * 请求头中透传 request_id 和 X-User-Id 用于链路追踪与身份识别。
     */
    private StreamingResponseBody proxyStream(String targetUrl, String body, String userId) {
        String requestId = generateRequestId();
        return outputStream -> {
            try {
                restTemplate.execute(targetUrl, HttpMethod.POST,
                        req -> {
                            req.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                            req.getHeaders().set("X-Request-Id", requestId);
                            if (userId != null && !userId.isEmpty()) {
                                req.getHeaders().set("X-User-Id", userId);
                            }
                            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                            req.getBody().write(bytes);
                        },
                        res -> {
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            InputStream stream = res.getBody();
                            while ((bytesRead = stream.read(buffer)) != -1) {
                                outputStream.write(buffer, 0, bytesRead);
                                outputStream.flush();
                            }
                            return null;
                        });
            } catch (Exception e) {
                log.warn("[AI_QA] [proxy_stream_error] url={} error={}", targetUrl, e.getMessage());
                String errorEvent = "event: error\ndata: " +
                        "{\"type\":\"error\",\"code\":\"PROXY_ERROR\",\"message\":\"服务暂不可用\"}\n\n";
                outputStream.write(errorEvent.getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
            }
        };
    }

    /**
     * 判断指定 user_id 是否应被路由到 v2 灰度端点
     * <p>
     * 根据 user_id 的 hashCode 取绝对值后对 100 取模，
     * 若结果小于 grayRatio 则命中灰度流量。
     *
     * @param userId 用户标识
     * @return true 表示应路由到 v2 端点
     */
    private boolean useV2Endpoint(String userId) {
        if (grayRatio <= 0) {
            return false;
        }
        if (grayRatio >= 100) {
            return true;
        }
        int hash = Math.abs(userId.hashCode() % 100);
        return hash < grayRatio;
    }

    /**
     * 生成唯一请求 ID（用于链路追踪）
     */
    private String generateRequestId() {
        return UUID.randomUUID().toString().replace("-", "");
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
