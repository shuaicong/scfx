package com.scfx.controller;

import com.scfx.common.Result;
import com.scfx.entity.KnowledgeBase;
import com.scfx.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 仪表板控制器
 */
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final CollectionScriptService scriptService;
    private final CollectionLogService logService;
    private final AlertService alertService;
    private final KnowledgeBaseService knowledgeBaseService;

    /**
     * 获取仪表板数据
     */
    @GetMapping
    public Result<Map<String, Object>> getDashboard() {
        Map<String, Object> data = new HashMap<>();

        // 系统状态
        Map<String, Object> systemStatus = new HashMap<>();
        systemStatus.put("runningTasks", scriptService.getAllScripts().stream().filter(s -> "enabled".equals(s.getStatus())).count());
        systemStatus.put("todaySuccess", scriptService.getTodaySuccessCount());
        systemStatus.put("todayFailed", scriptService.getTodayFailedCount());
        systemStatus.put("todayReports", knowledgeBaseService.count(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgeBase>()
                .ge(KnowledgeBase::getCreatedAt, LocalDateTime.now().withHour(0).withMinute(0).withSecond(0))
        ));

        // 计算成功率
        long success = scriptService.getTodaySuccessCount();
        long failed = scriptService.getTodayFailedCount();
        long total = success + failed;
        double successRate = total > 0 ? (double) success / total * 100 : 100.0;
        systemStatus.put("successRate", Math.round(successRate * 100.0) / 100.0);
        systemStatus.put("lastUpdate", java.time.LocalDateTime.now());

        data.put("systemStatus", systemStatus);

        // 各数据源统计
        data.put("sourceStats", scriptService.getSourceStats());

        // 最近日志
        data.put("recentLogs", logService.getRecentLogs(10));

        // 待处理告警
        data.put("pendingAlerts", alertService.getAlertStats().getData());

        return Result.success(data);
    }

    /**
     * 获取统计数据
     */
    @GetMapping("/statistics")
    public Result<Map<String, Object>> getStatistics(@RequestParam(defaultValue = "today") String period) {
        Map<String, Object> stats = new HashMap<>();

        stats.put("totalTasks", scriptService.getAllScripts().size());
        stats.put("runningTasks", scriptService.getAllScripts().stream().filter(s -> "enabled".equals(s.getStatus())).count());
        stats.put("todaySuccess", scriptService.getTodaySuccessCount());
        stats.put("todayFailed", scriptService.getTodayFailedCount());
        stats.put("todayReports", knowledgeBaseService.count(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgeBase>()
                .ge(KnowledgeBase::getCreatedAt, LocalDateTime.now().withHour(0).withMinute(0).withSecond(0))
        ));

        long success = scriptService.getTodaySuccessCount();
        long failed = scriptService.getTodayFailedCount();
        long total = success + failed;
        double successRate = total > 0 ? (double) success / total * 100 : 100.0;
        stats.put("successRate", Math.round(successRate * 100.0) / 100.0);

        // 日志统计
        stats.put("logStats", logService.getLogStats().getData());

        return Result.success(stats);
    }
}
