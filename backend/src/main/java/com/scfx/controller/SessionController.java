package com.scfx.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 会话管理 Controller
 * 提供会话关闭接口，同步清理 Redis 会话和 MySQL 状态
 */
@RestController
@RequestMapping("/ai-chat/session")
public class SessionController {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 关闭会话：清理 Redis 中的会话缓存，并更新 MySQL 中的会话状态为已关闭
     */
    @PostMapping("/close")
    public ResponseEntity<Map<String, String>> closeSession(
            @RequestBody Map<String, String> body) {
        String sessionId = body.get("sessionId");
        String userId = body.get("userId");

        // 检查 Redis 中是否存在会话
        boolean redisExists = redisTemplate.hasKey("chat:user:" + userId + ":session:" + sessionId);

        // 查询 MySQL 中的会话状态
        Integer mysqlStatus;
        try {
            mysqlStatus = jdbcTemplate.queryForObject(
                    "SELECT session_status FROM t_chat_history WHERE session_id = ? AND user_id = ? LIMIT 1",
                    Integer.class, sessionId, userId);
        } catch (Exception e) {
            mysqlStatus = null;
        }

        // 如果 Redis 中已不存在且 MySQL 中显示已关闭，直接返回
        if (!redisExists && mysqlStatus != null && mysqlStatus == 0) {
            return ResponseEntity.ok(Map.of(
                    "code", "SESSION_ALREADY_CLOSED",
                    "message", "会话已关闭"
            ));
        }

        // 清理 Redis 中的会话缓存
        redisTemplate.delete("chat:user:" + userId + ":session:" + sessionId);

        // 更新 MySQL 中的会话状态为已关闭
        jdbcTemplate.update(
                "UPDATE t_chat_history SET session_status = 0 WHERE session_id = ?",
                sessionId);

        return ResponseEntity.ok(Map.of(
                "code", "SESSION_CLOSED",
                "message", "会话已关闭"
        ));
    }
}
