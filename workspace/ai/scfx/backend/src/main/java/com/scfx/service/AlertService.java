package com.scfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scfx.common.Result;
import com.scfx.entity.AlertRecord;
import com.scfx.mapper.AlertRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 告警服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRecordMapper alertMapper;

    /**
     * 创建告警
     */
    public Result<AlertRecord> createAlert(String alertType, String alertLevel, String title, String content, String targetId) {
        AlertRecord alert = new AlertRecord();
        alert.setAlertType(alertType);
        alert.setAlertLevel(alertLevel);
        alert.setAlertTitle(title);
        alert.setAlertContent(content);
        alert.setTargetId(targetId);
        alert.setStatus("pending");
        alert.setCreatedAt(LocalDateTime.now());
        alertMapper.insert(alert);

        log.warn("创建告警 [{}]: {}", alertLevel, title);
        return Result.success(alert);
    }

    /**
     * 分页查询告警
     */
    public Result<Page<AlertRecord>> getAlerts(int page, int size, String status, String level) {
        Page<AlertRecord> pageInfo = new Page<>(page, size);
        LambdaQueryWrapper<AlertRecord> wrapper = new LambdaQueryWrapper<>();

        if (status != null && !status.isEmpty() && !"all".equals(status)) {
            wrapper.eq(AlertRecord::getStatus, status);
        }
        if (level != null && !level.isEmpty()) {
            wrapper.eq(AlertRecord::getAlertLevel, level);
        }

        wrapper.orderByDesc(AlertRecord::getCreatedAt);
        Page<AlertRecord> result = alertMapper.selectPage(pageInfo, wrapper);

        return Result.success(result);
    }

    /**
     * 处理告警
     */
    public Result<Void> resolveAlert(Long id, String resolvedBy) {
        AlertRecord alert = new AlertRecord();
        alert.setId(id);
        alert.setStatus("resolved");
        alert.setResolvedBy(resolvedBy);
        alert.setResolvedAt(LocalDateTime.now());
        alertMapper.updateById(alert);
        return Result.success();
    }

    /**
     * 获取告警统计
     */
    public Result<Map<String, Long>> getAlertStats() {
        Map<String, Long> stats = new HashMap<>();

        LambdaQueryWrapper<AlertRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertRecord::getStatus, "pending");

        stats.put("critical", alertMapper.selectCount(new LambdaQueryWrapper<AlertRecord>()
                .eq(AlertRecord::getAlertLevel, "critical").eq(AlertRecord::getStatus, "pending")));
        stats.put("error", alertMapper.selectCount(new LambdaQueryWrapper<AlertRecord>()
                .eq(AlertRecord::getAlertLevel, "error").eq(AlertRecord::getStatus, "pending")));
        stats.put("warning", alertMapper.selectCount(new LambdaQueryWrapper<AlertRecord>()
                .eq(AlertRecord::getAlertLevel, "warning").eq(AlertRecord::getStatus, "pending")));
        stats.put("info", alertMapper.selectCount(new LambdaQueryWrapper<AlertRecord>()
                .eq(AlertRecord::getAlertLevel, "info").eq(AlertRecord::getStatus, "pending")));
        stats.put("total", alertMapper.selectCount(wrapper));

        return Result.success(stats);
    }
}
