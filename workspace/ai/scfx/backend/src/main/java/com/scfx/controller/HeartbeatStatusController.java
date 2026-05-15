package com.scfx.controller;

import com.scfx.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/collector/heartbeat")
@RequiredArgsConstructor
public class HeartbeatStatusController {
    private final StringRedisTemplate redisTemplate;

    private static final String HEARTBEAT_KEY = "heartbeat:python-collector";
    private static final long TIMEOUT_MS = 120000; // 2分钟超时

    @GetMapping("/status")
    public Result<Map<String, Object>> getHeartbeatStatus() {
        String lastHeartbeat = redisTemplate.opsForValue().get(HEARTBEAT_KEY);

        Map<String, Object> result = new HashMap<>();
        result.put("enabled", true);

        if (lastHeartbeat != null) {
            long timestamp = Long.parseLong(lastHeartbeat);
            long now = System.currentTimeMillis();
            long diff = now - timestamp;

            result.put("lastHeartbeat", timestamp);
            result.put("online", diff < TIMEOUT_MS);
            result.put("diffMs", diff);
        } else {
            result.put("lastHeartbeat", null);
            result.put("online", false);
            result.put("diffMs", null);
        }

        return Result.success(result);
    }

    @PostMapping("/refresh")
    public Result<Void> refreshStatus() {
        // 强制刷新：通过触发检查来更新状态
        return Result.success();
    }
}