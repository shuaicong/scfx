package com.scfx.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scfx.common.Result;
import com.scfx.entity.CollectionScript;
import com.scfx.entity.ExecutionItem;
import com.scfx.entity.TaskExecution;
import com.scfx.entity.TaskExecutionLog;
import com.scfx.service.CollectionScriptService;
import com.scfx.service.TaskExecutionService;
import com.scfx.util.CronDescriptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 采集脚本管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/scripts")
@RequiredArgsConstructor
public class CollectionScriptController {

    private final CollectionScriptService scriptService;

    @Autowired
    private TaskExecutionService executionService;

    /**
     * 获取脚本列表（分页）
     * GET /scripts?page=1&size=20&status=&source=
     */
    @GetMapping
    public Result<Page<CollectionScript>> getScripts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String source) {
        return scriptService.getScripts(page, size, status, source);
    }

    /**
     * 获取脚本详情
     * GET /scripts/{id}
     */
    @GetMapping("/{id}")
    public Result<CollectionScript> getScriptById(@PathVariable Long id) {
        return scriptService.getScriptById(id);
    }

    /**
     * 创建脚本
     * POST /scripts
     */
    @PostMapping
    public Result<CollectionScript> createScript(@RequestBody CollectionScript script) {
        if (script.getScriptName() == null || script.getScriptName().isBlank()) {
            return Result.error("脚本名称不能为空");
        }
        if (script.getSyncToKnowledgeBase() == null || Boolean.TRUE.equals(script.getSyncToKnowledgeBase())) {
            if (script.getCategoryId() == null) {
                return Result.error("关联分类不能为空");
            }
        }
        try {
            return scriptService.createScript(script);
        } catch (Exception e) {
            log.error("创建脚本失败: scriptName={}, triggerType={}, error={}",
                script.getScriptName(), script.getTriggerType(), e.getMessage(), e);
            return Result.error("创建失败: " + e.getMessage());
        }
    }

    /**
     * 更新脚本
     * PUT /scripts/{id}
     */
    @PutMapping("/{id}")
    public Result<CollectionScript> updateScript(@PathVariable Long id, @RequestBody CollectionScript script) {
        script.setId(id);
        if (script.getSyncToKnowledgeBase() == null || Boolean.TRUE.equals(script.getSyncToKnowledgeBase())) {
            if (script.getCategoryId() == null) {
                return Result.error("关联分类不能为空");
            }
        }
        try {
            return scriptService.updateScript(script);
        } catch (Exception e) {
            log.error("更新脚本失败: id={}, scriptName={}, triggerType={}, cron={}, error={}",
                id, script.getScriptName(), script.getTriggerType(), script.getCronExpression(), e.getMessage(), e);
            return Result.error("保存失败: " + e.getMessage());
        }
    }

    /**
     * 删除脚本
     * DELETE /scripts/{id}
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteScript(@PathVariable Long id) {
        return scriptService.deleteScript(id);
    }

    /**
     * 启用脚本
     * PUT /scripts/{id}/enable
     */
    @PutMapping("/{id}/enable")
    public Result<Void> enableScript(@PathVariable Long id) {
        return scriptService.enableScript(id);
    }

    /**
     * 禁用脚本
     * PUT /scripts/{id}/disable
     */
    @PutMapping("/{id}/disable")
    public Result<Void> disableScript(@PathVariable Long id) {
        return scriptService.disableScript(id);
    }

    /**
     * 立即执行脚本
     * POST /scripts/{id}/execute
     * 创建执行记录，由 CollectorAgentService 异步执行
     */
    @PostMapping("/{id}/execute")
    public Result<Map<String, Object>> executeScript(@PathVariable Long id) {
        return scriptService.executeScriptNow(id);
    }

    /**
     * 取消执行
     */
    @PostMapping("/executions/{executionId}/cancel")
    public Result<Void> cancelExecution(@PathVariable String executionId) {
        executionService.updateStatus(executionId, "cancelled", "用户取消");
        return Result.success();
    }

    /**
     * 获取执行记录列表（支持筛选）
     */
    @GetMapping("/{scriptId}/executions")
    public Result<Page<TaskExecution>> getExecutions(
        @PathVariable Long scriptId,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String triggerType) {
        return Result.success(executionService.getExecutions(scriptId, page, size, status, triggerType));
    }

    /**
     * 获取执行详情
     */
    @GetMapping("/executions/{executionId}")
    public Result<Map<String, Object>> getExecution(@PathVariable String executionId) {
        TaskExecution execution = executionService.getExecution(executionId);
        if (execution == null) {
            return Result.error("执行记录不存在");
        }
        // 关联脚本信息
        Map<String, Object> result = new HashMap<>();
        result.put("id", execution.getId());
        result.put("executionId", execution.getExecutionId());
        result.put("scriptId", execution.getScriptId());
        result.put("versionId", execution.getVersionId());
        result.put("versionNum", execution.getVersionNum());
        result.put("triggerType", execution.getTriggerType());
        result.put("status", execution.getStatus());
        result.put("startTime", execution.getStartTime());
        result.put("endTime", execution.getEndTime());
        result.put("durationMs", execution.getDurationMs());
        result.put("errorMessage", execution.getErrorMessage());
        result.put("createdAt", execution.getCreatedAt());
        result.put("collectedCount", execution.getCollectedCount());

        // 执行统计
        result.put("totalCount", execution.getTotalCount());
        result.put("successCount", execution.getSuccessCount());
        result.put("skipCount", execution.getSkipCount());
        result.put("errorCount", execution.getErrorCount());
        result.put("dataSizeMb", execution.getDataSizeMb());

        // 阶段耗时
        result.put("phaseLoginMs", execution.getPhaseLoginMs());
        result.put("phaseCrawlMs", execution.getPhaseCrawlMs());
        result.put("phaseParseMs", execution.getPhaseParseMs());
        result.put("phaseReportMs", execution.getPhaseReportMs());

        // 获取脚本信息
        if (execution.getScriptId() != null) {
            CollectionScript script = scriptService.getScriptById(execution.getScriptId()).getData();
            if (script != null) {
                result.put("scriptName", script.getScriptName());
                result.put("source", script.getSource());
            }
        }

        return Result.success(result);
    }

    /**
     * 获取执行日志
     */
    @GetMapping("/executions/{executionId}/logs")
    public Result<List<TaskExecutionLog>> getExecutionLogs(@PathVariable String executionId) {
        return Result.success(executionService.getLogs(executionId));
    }

    /**
     * 获取执行采集数据项
     * GET /scripts/executions/{executionId}/items
     */
    @GetMapping("/executions/{executionId}/items")
    public Result<List<ExecutionItem>> getExecutionItems(@PathVariable String executionId) {
        return Result.success(executionService.getItems(executionId));
    }

    /**
     * 获取统计信息
     * GET /scripts/stats
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getStatistics() {
        return scriptService.getStatistics();
    }

    /**
     * 获取所有启用的脚本
     * GET /scripts/enabled
     */
    @GetMapping("/enabled")
    public Result<?> getEnabledScripts() {
        return Result.success(scriptService.getEnabledScripts());
    }

    /**
     * 验证Cron表达式
     * POST /scripts/validate-cron
     */
    @PostMapping("/validate-cron")
    public Result<Map<String, Object>> validateCron(@RequestBody Map<String, String> request) {
        String cron = request.get("cron");
        Map<String, Object> result = new HashMap<>();

        try {
            // Normalize 5-field Unix cron to 6-field Spring cron before parsing
            String normalizedCron = CronDescriptionUtil.normalizeToSixFields(cron);
            org.springframework.scheduling.support.CronExpression.parse(normalizedCron);
            result.put("valid", true);
            result.put("description", CronDescriptionUtil.describe(cron));

            List<String> nextExecutions = CronDescriptionUtil.calculateNextExecutions(cron, 5);
            result.put("nextExecutions", nextExecutions);

        } catch (Exception e) {
            result.put("valid", false);
            result.put("error", e.getMessage());
        }

        return Result.success(result);
    }
}
