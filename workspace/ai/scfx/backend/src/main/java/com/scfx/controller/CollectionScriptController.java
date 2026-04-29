package com.scfx.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scfx.common.Result;
import com.scfx.entity.CollectionScript;
import com.scfx.service.CollectionScriptService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 采集脚本管理控制器
 */
@RestController
@RequestMapping("/scripts")
@RequiredArgsConstructor
public class CollectionScriptController {

    private final CollectionScriptService scriptService;

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
     * 获取脚本内容
     * GET /scripts/{id}/content
     */
    @GetMapping("/{id}/content")
    public Result<Map<String, String>> getScriptContent(@PathVariable Long id) {
        CollectionScript script = scriptService.getScriptById(id).getData();
        if (script == null) {
            return Result.error("脚本不存在");
        }
        return Result.success(Map.of("content", script.getScriptContent()));
    }

    /**
     * 创建脚本
     * POST /scripts
     */
    @PostMapping
    public Result<CollectionScript> createScript(@RequestBody CollectionScript script) {
        return scriptService.createScript(script);
    }

    /**
     * 更新脚本
     * PUT /scripts/{id}
     */
    @PutMapping("/{id}")
    public Result<CollectionScript> updateScript(@PathVariable Long id, @RequestBody CollectionScript script) {
        script.setId(id);
        return scriptService.updateScript(script);
    }

    /**
     * 更新脚本内容
     * PUT /scripts/{id}/content
     */
    @PutMapping("/{id}/content")
    public Result<CollectionScript> updateScriptContent(@PathVariable Long id, @RequestBody Map<String, String> request) {
        CollectionScript script = scriptService.getScriptById(id).getData();
        if (script == null) {
            return Result.error("脚本不存在");
        }
        script.setScriptContent(request.get("content"));
        return scriptService.updateScript(script);
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
     * 手动执行脚本
     * POST /scripts/{id}/execute
     */
    @PostMapping("/{id}/execute")
    public Result<Map<String, Object>> executeScript(@PathVariable Long id) {
        return scriptService.executeScript(id);
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
    public Result<Map<String, Boolean>> validateCron(@RequestBody Map<String, String> request) {
        String cron = request.get("cron");
        boolean valid = isValidCronExpression(cron);
        return Result.success(Map.of("valid", valid));
    }

    private boolean isValidCronExpression(String cron) {
        if (cron == null || cron.isEmpty()) return false;
        // 简化验证：检查是否满足5个或6个字段
        String[] parts = cron.trim().split("\\s+");
        return parts.length >= 5 && parts.length <= 6;
    }
}
