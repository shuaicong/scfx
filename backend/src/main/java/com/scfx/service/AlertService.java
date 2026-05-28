package com.scfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scfx.common.Result;
import com.scfx.entity.AlertRecord;
import com.scfx.entity.AlertRule;
import com.scfx.mapper.AlertRecordMapper;
import com.scfx.mapper.AlertRuleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final AlertRuleMapper alertRuleMapper;
    private final StringRedisTemplate redisTemplate;

    private static final String HEARTBEAT_KEY = "heartbeat:python-collector";

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
    @Transactional
    public Result<Void> resolveAlert(Long id, String resolvedBy) {
        AlertRecord alert = alertMapper.selectById(id);
        if (alert != null) {
            alert.setStatus("resolved");
            alert.setResolvedBy(resolvedBy);
            alert.setResolvedAt(LocalDateTime.now());
            alertMapper.updateById(alert);
        }
        return Result.success();
    }

    /**
     * 获取告警统计
     */
    public Result<Map<String, Long>> getAlertStats() {
        Map<String, Long> stats = new HashMap<>();

        stats.put("critical", alertMapper.selectCount(new LambdaQueryWrapper<AlertRecord>()
                .eq(AlertRecord::getAlertLevel, "critical").eq(AlertRecord::getStatus, "pending")));
        stats.put("error", alertMapper.selectCount(new LambdaQueryWrapper<AlertRecord>()
                .eq(AlertRecord::getAlertLevel, "error").eq(AlertRecord::getStatus, "pending")));
        stats.put("warning", alertMapper.selectCount(new LambdaQueryWrapper<AlertRecord>()
                .eq(AlertRecord::getAlertLevel, "warning").eq(AlertRecord::getStatus, "pending")));
        stats.put("info", alertMapper.selectCount(new LambdaQueryWrapper<AlertRecord>()
                .eq(AlertRecord::getAlertLevel, "info").eq(AlertRecord::getStatus, "pending")));
        stats.put("total", alertMapper.selectCount(new LambdaQueryWrapper<AlertRecord>()
                .eq(AlertRecord::getStatus, "pending")));

        return Result.success(stats);
    }

    /**
     * 定时检查并发送告警
     */
    @Scheduled(fixedRate = 60000) // 每分钟检查
    public void checkAndAlert() {
        checkContinuousFailures();
        checkServiceOffline();
    }

    private void checkContinuousFailures() {
        AlertRule rule = alertRuleMapper.findByType("CONTINUOUS_FAIL");
        if (rule == null || rule.getEnabled() == 0) return;

        // TODO: 检查连续失败的脚本
        log.debug("检查连续失败告警规则");
    }

    private void checkServiceOffline() {
        AlertRule rule = alertRuleMapper.findByType("SERVICE_OFFLINE");
        if (rule == null || rule.getEnabled() == 0) return;

        String lastHeartbeat = redisTemplate.opsForValue().get(HEARTBEAT_KEY);
        if (lastHeartbeat == null) {
            sendAlert(rule, null, "SERVICE_OFFLINE", "采集服务离线", "采集服务心跳超时，请检查服务状态");
            return;
        }

        long diff = System.currentTimeMillis() - Long.parseLong(lastHeartbeat);
        if (diff > 120000) { // 2分钟
            sendAlert(rule, null, "SERVICE_OFFLINE",
                "采集服务离线", String.format("采集服务心跳超时（%d秒无响应）", diff / 1000));
        }
    }

    private void sendAlert(AlertRule rule, Long scriptId, String alertType, String title, String content) {
        if (hasRecentAlert(rule.getId(), scriptId)) return;

        AlertRecord record = new AlertRecord();
        record.setAlertType(alertType);
        record.setAlertLevel("error");
        record.setAlertTitle(title);
        record.setAlertContent(content);
        record.setTargetId(scriptId != null ? scriptId.toString() : null);
        record.setStatus("pending");
        record.setCreatedAt(LocalDateTime.now());

        try {
            // 根据渠道发送告警
            // for (String channel : parseChannels(rule.getNotifyChannels())) { ... }
            record.setStatus("sent");
            log.warn("发送告警 [{}]: {}", alertType, title);
        } catch (Exception e) {
            record.setStatus("failed");
            log.error("发送告警失败: {}", e.getMessage());
        }

        alertMapper.insert(record);
    }

    private boolean hasRecentAlert(Long ruleId, Long scriptId) {
        LocalDateTime thirtyMinutesAgo = LocalDateTime.now().minusMinutes(30);
        LambdaQueryWrapper<AlertRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertRecord::getTargetId, scriptId != null ? scriptId.toString() : null);
        wrapper.ge(AlertRecord::getCreatedAt, thirtyMinutesAgo);
        wrapper.last("LIMIT 1");
        return alertMapper.selectCount(wrapper) > 0;
    }

    // ========== 规则管理接口 ==========

    /**
     * 创建告警规则
     */
    @Transactional
    public Result<AlertRule> createRule(AlertRule rule) {
        rule.setCreatedAt(LocalDateTime.now());
        rule.setUpdatedAt(LocalDateTime.now());
        alertRuleMapper.insert(rule);
        return Result.success(rule);
    }

    /**
     * 更新告警规则
     */
    @Transactional
    public Result<Void> updateRule(AlertRule rule) {
        rule.setUpdatedAt(LocalDateTime.now());
        alertRuleMapper.updateById(rule);
        return Result.success();
    }

    /**
     * 删除告警规则
     */
    @Transactional
    public Result<Void> deleteRule(Long id) {
        alertRuleMapper.deleteById(id);
        return Result.success();
    }

    /**
     * 获取告警规则列表
     */
    public Result<Page<AlertRule>> getRules(int page, int size, String ruleType, Integer enabled) {
        Page<AlertRule> pageInfo = new Page<>(page, size);
        LambdaQueryWrapper<AlertRule> wrapper = new LambdaQueryWrapper<>();

        if (ruleType != null && !ruleType.isEmpty()) {
            wrapper.eq(AlertRule::getRuleType, ruleType);
        }
        if (enabled != null) {
            wrapper.eq(AlertRule::getEnabled, enabled);
        }

        wrapper.orderByDesc(AlertRule::getCreatedAt);
        Page<AlertRule> result = alertRuleMapper.selectPage(pageInfo, wrapper);

        return Result.success(result);
    }

    /**
     * 获取单个告警规则
     */
    public Result<AlertRule> getRule(Long id) {
        AlertRule rule = alertRuleMapper.selectById(id);
        return Result.success(rule);
    }
}