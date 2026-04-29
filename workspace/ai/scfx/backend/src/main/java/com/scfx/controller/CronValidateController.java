package com.scfx.controller;

import com.scfx.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/scripts")
@RequiredArgsConstructor
public class CronValidateController {

    /**
     * 校验Cron表达式
     */
    @PostMapping("/validate-cron")
    public Result<Map<String, Object>> validateCron(@RequestBody Map<String, String> request) {
        String cron = request.get("cron");
        Map<String, Object> result = new HashMap<>();
        try {
            org.springframework.scheduling.support.CronExpression.parse(cron);
            result.put("valid", true);
            result.put("description", describeCron(cron));

            LocalDateTime next = LocalDateTime.now();
            String[] times = new String[5];
            for (int i = 0; i < 5; i++) {
                next = next.plusHours(1).withMinute(0).withSecond(0).withNano(0);
                times[i] = next.toString();
            }
            result.put("nextExecutions", times);
        } catch (Exception e) {
            result.put("valid", false);
            result.put("error", e.getMessage());
        }
        return Result.success(result);
    }

    private String describeCron(String cron) {
        if ("*/5 * * * *".equals(cron)) return "每5分钟";
        if ("*/15 * * * *".equals(cron)) return "每15分钟";
        if ("*/30 * * * *".equals(cron)) return "每30分钟";
        if ("0 * * * *".equals(cron)) return "每小时整点";
        if ("0 8 * * *".equals(cron)) return "每天08:00";
        if ("0 9 * * *".equals(cron)) return "每天09:00";
        if ("0 12 * * *".equals(cron)) return "每天12:00";
        if ("0 18 * * *".equals(cron)) return "每天18:00";
        if ("0 9 * * 1".equals(cron)) return "每周一09:00";
        if ("0 8 1 * *".equals(cron)) return "每月1号08:00";
        if ("0 8 15 * *".equals(cron)) return "每月15号08:00";
        return "自定义表达式";
    }
}