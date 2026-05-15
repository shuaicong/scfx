package com.scfx.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scfx.common.Result;
import com.scfx.entity.AlertRecord;
import com.scfx.entity.AlertRule;
import com.scfx.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 告警控制器
 */
@RestController
@RequestMapping("/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    /**
     * 获取告警列表
     */
    @GetMapping
    public Result<Page<AlertRecord>> getAlerts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String level) {
        return alertService.getAlerts(page, size, status, level);
    }

    /**
     * 获取告警统计
     */
    @GetMapping("/stats")
    public Result<java.util.Map<String, Long>> getStats() {
        return alertService.getAlertStats();
    }

    /**
     * 处理告警
     */
    @PutMapping("/{id}/resolve")
    public Result<Void> resolveAlert(@PathVariable Long id, @RequestParam(required = false, defaultValue = "admin") String resolvedBy) {
        return alertService.resolveAlert(id, resolvedBy);
    }

    /**
     * 创建告警（测试用）
     */
    @PostMapping
    public Result<AlertRecord> createAlert(
            @RequestParam String type,
            @RequestParam String level,
            @RequestParam String title,
            @RequestParam String content) {
        return alertService.createAlert(type, level, title, content, null);
    }

    // ========== 规则管理接口 ==========

    /**
     * 获取告警规则列表
     */
    @GetMapping("/rules")
    public Result<Page<AlertRule>> getRules(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String ruleType,
            @RequestParam(required = false) Integer enabled) {
        return alertService.getRules(page, size, ruleType, enabled);
    }

    /**
     * 获取单个告警规则
     */
    @GetMapping("/rules/{id}")
    public Result<AlertRule> getRule(@PathVariable Long id) {
        return alertService.getRule(id);
    }

    /**
     * 创建告警规则
     */
    @PostMapping("/rules")
    public Result<AlertRule> createRule(@RequestBody AlertRule rule) {
        return alertService.createRule(rule);
    }

    /**
     * 更新告警规则
     */
    @PutMapping("/rules/{id}")
    public Result<Void> updateRule(@PathVariable Long id, @RequestBody AlertRule rule) {
        rule.setId(id);
        return alertService.updateRule(rule);
    }

    /**
     * 删除告警规则
     */
    @DeleteMapping("/rules/{id}")
    public Result<Void> deleteRule(@PathVariable Long id) {
        return alertService.deleteRule(id);
    }
}