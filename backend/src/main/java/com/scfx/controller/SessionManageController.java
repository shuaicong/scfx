package com.scfx.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scfx.common.Result;
import com.scfx.entity.ChatSession;
import com.scfx.service.ChatSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/ai-chat/sessions")
public class SessionManageController {

    private final ChatSessionService sessionService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.ai-qa-service.url}")
    private String aiQaServiceUrl;

    public SessionManageController(ChatSessionService sessionService) {
        this.sessionService = sessionService;
    }

    /**
     * GET /api/ai-chat/sessions — 会话列表（分页+搜索）
     */
    @GetMapping
    public Result<Map<String, Object>> getSessions(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {

        Page<ChatSession> result = sessionService.getSessions(userId, page, size, keyword, start, end);

        Map<String, Object> data = new HashMap<>();
        data.put("records", result.getRecords());
        data.put("total", result.getTotal());
        data.put("page", result.getCurrent());
        data.put("size", result.getSize());
        return Result.success(data);
    }

    /**
     * GET /api/ai-chat/sessions/{id} — 会话详情
     */
    @GetMapping("/{id}")
    public Result<ChatSession> getSessionDetail(@PathVariable String id) {
        ChatSession session = sessionService.getSessionDetail(id);
        if (session == null) {
            return Result.error(404, "会话不存在");
        }
        return Result.success(session);
    }

    /**
     * PATCH /api/ai-chat/sessions/{id}/title — 更新标题
     */
    @PatchMapping("/{id}/title")
    public Result<Void> updateTitle(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {

        String title = body.get("title");
        String source = body.getOrDefault("source", "manual");

        if (title == null || title.trim().isEmpty()) {
            return Result.error(400, "标题不能为空");
        }

        boolean updated = sessionService.updateTitle(id, title.trim(), source);
        if (!updated) {
            ChatSession session = sessionService.getById(id);
            if (session == null || session.getIsDeleted() == 1) {
                return Result.error(404, "会话不存在");
            }
            return Result.error(403, "手动命名的标题不允许自动修改");
        }
        return Result.success();
    }

    /**
     * GET /api/ai-chat/sessions/{id}/messages — 获取会话历史消息（代理到 Python 服务）
     */
    @GetMapping("/{id}/messages")
    public ResponseEntity<String> getSessionMessages(
            @PathVariable String id,
            @RequestHeader("X-User-Id") String userId) {

        String url = aiQaServiceUrl + "/api/chat/messages?session_id=" + id;
        log.debug("Proxying GET /api/ai-chat/sessions/{}/messages -> {}", id, url);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", userId);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.getBody());
        } catch (Exception e) {
            log.warn("[ChatSession] messages proxy failed: {}", e.getMessage());
            return ResponseEntity.ok("{\"code\":200,\"data\":[]}");
        }
    }

    /**
     * DELETE /api/ai-chat/sessions — 批量软删除
     */
    @DeleteMapping
    public Result<Void> batchDelete(@RequestBody Map<String, List<String>> body) {
        List<String> ids = body.get("ids");
        if (ids == null || ids.isEmpty()) {
            return Result.error(400, "删除列表不能为空");
        }

        int count = sessionService.batchDelete(ids);
        log.info("[ChatSession] batch deleted: requested={}, actual={}", ids.size(), count);
        return Result.success();
    }
}
