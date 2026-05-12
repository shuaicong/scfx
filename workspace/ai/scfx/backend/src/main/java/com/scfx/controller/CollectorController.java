package com.scfx.controller;

import com.scfx.common.Result;
import com.scfx.entity.Report;
import com.scfx.entity.TaskExecution;
import com.scfx.service.ReportService;
import com.scfx.service.TaskExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 采集器控制器 - 提供给 Python 采集器调用的 REST API
 */
@RestController
@RequestMapping("/collector/exec")
@RequiredArgsConstructor
public class CollectorController {

    private final TaskExecutionService executionService;
    private final ReportService reportService;

    /**
     * 启动采集任务
     * POST /collector/exec/start
     * Body: {"taskId": 1}
     * Response: {"code":200, "data": {"executionId": "xxx", "taskId": 1, ...}}
     */
    @PostMapping("/start")
    public Result<Map<String, Object>> startExecution(@RequestBody Map<String, Object> request) {
        Long scriptId = ((Number) request.get("taskId")).longValue();
        TaskExecution execution = executionService.createExecution(scriptId, "manual");

        return Result.success(Map.of(
            "executionId", execution.getExecutionId(),
            "scriptId", execution.getScriptId(),
            "startTime", execution.getStartTime()
        ));
    }

    /**
     * 上报进度
     * POST /collector/exec/{executionId}/progress
     * Body: {"collectedCount": 5}
     */
    @PostMapping("/{executionId}/progress")
    public Result<Void> reportProgress(
            @PathVariable String executionId,
            @RequestBody Map<String, Object> request) {
        // 不需要单独存储进度，在完成时更新即可
        return Result.success();
    }

    /**
     * 上报日志
     * POST /collector/exec/{executionId}/log
     * Body: {"level": "INFO", "message": "登录成功"}
     */
    @PostMapping("/{executionId}/log")
    public Result<Void> addLog(
            @PathVariable String executionId,
            @RequestBody Map<String, Object> request) {
        String level = (String) request.getOrDefault("level", "INFO");
        String message = (String) request.get("message");
        executionService.addLog(executionId, null, level, message);
        return Result.success();
    }

    /**
     * 上报采集数据
     * POST /collector/exec/{executionId}/data
     * Body: {"title": "...", "source": "...", "url": "...", "variety": "...", "reportType": "...", "content": "...", "publishTime": "..."}
     */
    @PostMapping("/{executionId}/data")
    public Result<Void> submitData(
            @PathVariable String executionId,
            @RequestBody Map<String, Object> request) {
        Report report = new Report();
        report.setTitle((String) request.get("title"));
        report.setSource((String) request.get("source"));
        report.setOriginalUrl((String) request.get("url"));
        report.setVariety((String) request.get("variety"));
        report.setReportType((String) request.get("reportType"));
        report.setContent((String) request.get("content"));

        Object publishTimeObj = request.get("publishTime");
        if (publishTimeObj != null) {
            if (publishTimeObj instanceof String) {
                report.setPublishTime(LocalDateTime.parse((String) publishTimeObj));
            } else if (publishTimeObj instanceof Number) {
                report.setPublishTime(LocalDateTime.ofEpochSecond(((Number) publishTimeObj).longValue() / 1000, 0, java.time.ZoneOffset.ofHours(8)));
            }
        }

        // 检查是否已存在（根据URL去重）
        if (report.getOriginalUrl() != null && reportService.existsByUrl(report.getOriginalUrl())) {
            return Result.error("报告已存在");
        }

        reportService.saveReport(report);
        return Result.success();
    }

    /**
     * 上报错误
     * POST /collector/exec/{executionId}/error
     * Body: {"errorMessage": "页面元素未找到"}
     */
    @PostMapping("/{executionId}/error")
    public Result<Void> reportError(
            @PathVariable String executionId,
            @RequestBody Map<String, Object> request) {
        String errorMessage = (String) request.get("errorMessage");
        executionService.reportError(executionId, errorMessage);
        return Result.success();
    }

    /**
     * 完成执行
     * POST /collector/exec/{executionId}/complete
     * Body: {"status": "success", "collectedCount": 10}
     */
    @PostMapping("/{executionId}/complete")
    public Result<Void> completeExecution(
            @PathVariable String executionId,
            @RequestBody Map<String, Object> request) {
        String status = (String) request.get("status");
        int collectedCount = ((Number) request.getOrDefault("collectedCount", 0)).intValue();
        executionService.completeExecution(executionId, status, collectedCount);
        return Result.success();
    }

    /**
     * 获取执行状态
     * GET /collector/exec/{executionId}
     */
    @GetMapping("/{executionId}")
    public Result<TaskExecution> getExecution(@PathVariable String executionId) {
        TaskExecution execution = executionService.findByExecutionId(executionId);
        if (execution == null) {
            return Result.error("执行记录不存在");
        }
        return Result.success(execution);
    }
}
