package com.scfx.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scfx.common.Result;
import com.scfx.entity.CollectionScript;
import com.scfx.entity.TaskExecution;
import com.scfx.entity.TaskExecutionLog;
import com.scfx.service.CollectionScriptService;
import com.scfx.service.TaskExecutionService;
import com.scfx.util.CronDescriptionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 采集脚本管理控制器
 */
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
     * 获取脚本内容（从文件读取）
     * GET /scripts/{id}/content
     */
    @GetMapping("/{id}/content")
    public Result<String> getScriptContent(@PathVariable Long id) {
        return scriptService.getScriptContent(id);
    }

    /**
     * 创建脚本（简化版：只需名称、描述、内容）
     * POST /scripts
     */
    @PostMapping
    public Result<CollectionScript> createScript(@RequestBody Map<String, String> request) {
        String scriptName = request.get("scriptName");
        String description = request.get("description");
        String scriptContent = request.get("scriptContent");

        if (scriptName == null || scriptName.isBlank()) {
            return Result.error("脚本名称不能为空");
        }
        if (scriptContent == null || scriptContent.isBlank()) {
            return Result.error("脚本内容不能为空");
        }

        return scriptService.createScript(scriptName, description, scriptContent);
    }

    /**
     * 上传脚本文件
     * POST /scripts/upload
     */
    @PostMapping("/upload")
    public Result<CollectionScript> uploadScript(
            @RequestParam("scriptName") String scriptName,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("file") MultipartFile file) {
        return scriptService.uploadScript(scriptName, description, file);
    }

    /**
     * 更新脚本（元数据）
     * PUT /scripts/{id}
     */
    @PutMapping("/{id}")
    public Result<CollectionScript> updateScript(@PathVariable Long id, @RequestBody CollectionScript script) {
        script.setId(id);
        return scriptService.updateScript(script);
    }

    /**
     * 更新脚本内容（同时更新文件）
     * PUT /scripts/{id}/content
     */
    @PutMapping("/{id}/content")
    public Result<CollectionScript> updateScriptContent(@PathVariable Long id, @RequestBody Map<String, String> request) {
        String content = request.get("scriptContent");
        if (content == null || content.isBlank()) {
            return Result.error("脚本内容不能为空");
        }
        return scriptService.updateScriptContent(id, content);
    }

    /**
     * 删除脚本（同时删除文件）
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
     * 执行脚本（通过文件路径）
     * POST /scripts/{id}/execute
     */
    @PostMapping("/{id}/execute")
    public Result<Map<String, Object>> executeScript(@PathVariable Long id) {
        return scriptService.executeScriptByPath(id);
    }

    /**
     * 立即执行脚本
     */
    @PostMapping("/{id}/execute-now")
    public Result<Map<String, Object>> executeScriptNow(@PathVariable Long id) {
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
     * 获取执行记录列表
     */
    @GetMapping("/{scriptId}/executions")
    public Result<Page<TaskExecution>> getExecutions(
        @PathVariable Long scriptId,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int size) {
        return Result.success(executionService.getExecutions(scriptId, page, size));
    }

    /**
     * 获取执行详情
     */
    @GetMapping("/executions/{executionId}")
    public Result<TaskExecution> getExecution(@PathVariable String executionId) {
        return Result.success(executionService.getExecution(executionId));
    }

    /**
     * 获取执行日志
     */
    @GetMapping("/executions/{executionId}/logs")
    public Result<List<TaskExecutionLog>> getExecutionLogs(@PathVariable String executionId) {
        return Result.success(executionService.getLogs(executionId));
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
            org.springframework.scheduling.support.CronExpression.parse(cron);
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
