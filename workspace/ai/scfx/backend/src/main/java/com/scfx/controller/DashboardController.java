package com.scfx.controller;

import com.scfx.common.Result;
import com.scfx.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 仪表板控制器
 */
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final CollectionTaskService taskService;
    private final CollectionLogService logService;
    private final AlertService alertService;
    private final ReportService reportService;
    private final LiangxinwangCollector collector;

    /**
     * 获取仪表板数据
     */
    @GetMapping
    public Result<Map<String, Object>> getDashboard() {
        Map<String, Object> data = new HashMap<>();

        // 系统状态
        Map<String, Object> systemStatus = new HashMap<>();
        systemStatus.put("runningTasks", taskService.getRunningTaskCount());
        systemStatus.put("todaySuccess", taskService.getTodaySuccessCount());
        systemStatus.put("todayFailed", taskService.getTodayFailedCount());
        systemStatus.put("todayReports", reportService.getTodayReportCount());

        // 计算成功率
        long success = taskService.getTodaySuccessCount();
        long failed = taskService.getTodayFailedCount();
        long total = success + failed;
        double successRate = total > 0 ? (double) success / total * 100 : 100.0;
        systemStatus.put("successRate", Math.round(successRate * 100.0) / 100.0);
        systemStatus.put("lastUpdate", java.time.LocalDateTime.now());

        data.put("systemStatus", systemStatus);

        // 各数据源统计
        data.put("sourceStats", taskService.getSourceStats());

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

        stats.put("totalTasks", taskService.getAllTasks().size());
        stats.put("runningTasks", taskService.getRunningTaskCount());
        stats.put("todaySuccess", taskService.getTodaySuccessCount());
        stats.put("todayFailed", taskService.getTodayFailedCount());
        stats.put("todayReports", reportService.getTodayReportCount());

        long success = taskService.getTodaySuccessCount();
        long failed = taskService.getTodayFailedCount();
        long total = success + failed;
        double successRate = total > 0 ? (double) success / total * 100 : 100.0;
        stats.put("successRate", Math.round(successRate * 100.0) / 100.0);

        // 日志统计
        stats.put("logStats", logService.getLogStats().getData());

        return Result.success(stats);
    }

    /**
     * 手动触发采集
     */
    @PostMapping("/collect")
    public Result<Map<String, Object>> triggerCollection() {
        // 在新线程中执行采集，避免阻塞请求
        new Thread(() -> {
            collector.collectTodayCornReports();
        }).start();

        Map<String, Object> response = new HashMap<>();
        response.put("message", "采集任务已启动，请稍后查看日志");
        return Result.success(response);
    }
}
