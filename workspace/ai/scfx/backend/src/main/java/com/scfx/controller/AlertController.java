package com.scfx.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scfx.common.Result;
import com.scfx.entity.AlertRecord;
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
}
