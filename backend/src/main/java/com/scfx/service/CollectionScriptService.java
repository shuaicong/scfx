package com.scfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scfx.common.Result;
import com.scfx.dto.ScriptExecuteReq;
import com.scfx.entity.CollectionScript;
import com.scfx.entity.TaskExecution;
import com.scfx.mapper.CollectionLogMapper;
import com.scfx.mapper.CollectionScriptMapper;
import com.scfx.mapper.ExecutionItemMapper;
import com.scfx.mapper.TaskExecutionLogMapper;
import com.scfx.mapper.TaskExecutionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * 采集脚本管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollectionScriptService {

    private final CollectionScriptMapper scriptMapper;

    @Autowired
    private TaskExecutionService executionService;

    @Autowired
    private TaskExecutionMapper taskExecutionMapper;

    @Autowired
    private TaskExecutionLogMapper taskExecutionLogMapper;

    @Autowired
    private ExecutionItemMapper executionItemMapper;

    @Autowired
    private CollectionLogMapper collectionLogMapper;

    @Value("${collector.min-execute-date:2020-01-01}")
    private String minExecuteDate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 立即执行脚本
     * 创建待执行记录，由 CollectorAgentService 异步执行 Python 脚本
     */
    @Transactional
    public Result<Map<String, Object>> executeScriptNow(Long id, ScriptExecuteReq req) {
        CollectionScript script = scriptMapper.selectById(id);
        if (script == null) {
            return Result.error("脚本不存在");
        }
        if ("disabled".equals(script.getStatus())) {
            return Result.error("脚本已禁用，请先启用");
        }

        // 1. 分布式锁拦截（基于 scriptId）
        String lockKey = "script_execute_lock_" + id;
        if (!acquireLock(lockKey, 30)) {
            return Result.error("操作频繁，请稍后重试");
        }
        try {
            // 2. 全局并发拦截：锁内二次校验（pending 或 running 均拦截）
            long concurrentCount = taskExecutionMapper.selectCount(
                new LambdaQueryWrapper<TaskExecution>()
                    .eq(TaskExecution::getScriptId, id)
                    .in(TaskExecution::getStatus, "pending", "running"));
            if (concurrentCount > 0) {
                return Result.error("该任务正在执行中，请等待完成");
            }

            // 3. 白名单检查
            String source = script.getSource();
            boolean isWhiteListed = "liangxin".equals(source) || "liangxin-daily".equals(source) || "liangxin-weekly".equals(source);

            // 4. 处理 date 参数
            String date = (req != null && isWhiteListed) ? req.getDate() : null;
            if (date != null) {
                date = date.trim();
                if (date.length() > 10) {
                    return Result.error("日期格式错误：长度不能超过 10 位");
                }
                try {
                    LocalDate parsed = LocalDate.parse(date, DATE_FORMATTER);
                    if (parsed.isAfter(LocalDate.now())) {
                        return Result.error("不能采集未来日期的数据");
                    }
                    LocalDate minDate = LocalDate.parse(minExecuteDate, DATE_FORMATTER);
                    if (parsed.isBefore(minDate)) {
                        return Result.error("日期早于最小可采集日期 " + minExecuteDate);
                    }
                } catch (DateTimeParseException e) {
                    return Result.error("日期格式错误，必须为 yyyy-MM-dd");
                }
            }

            // 5. 创建执行记录
            TaskExecution execution = executionService.createExecution(id, "manual");

            // 6. 写入 detail_json（只新增 date 键，不覆盖原有 JSON）
            if (date != null) {
                try {
                    JsonNode root;
                    if (execution.getDetailJson() != null && !execution.getDetailJson().isEmpty()) {
                        root = objectMapper.readTree(execution.getDetailJson());
                    } else {
                        root = objectMapper.createObjectNode();
                    }
                    ((ObjectNode) root).put("date", date);
                    execution.setDetailJson(objectMapper.writeValueAsString(root));
                } catch (Exception e) {
                    // JSON 解析失败时初始化为空 JSON 对象
                    try {
                        ObjectNode root = objectMapper.createObjectNode();
                        root.put("date", date);
                        execution.setDetailJson(objectMapper.writeValueAsString(root));
                    } catch (Exception ignored) {
                        // 极端情况写入失败，忽略该日志
                        log.warn("写入 detail_json 失败: executionId={}", execution.getExecutionId());
                    }
                }
                taskExecutionMapper.updateById(execution);
            }

            // 8. 更新脚本统计
            script.setLastExecutionTime(LocalDateTime.now());
            script.setNextExecutionTime(calculateNextExecution(script));
            script.setExecutionCount(script.getExecutionCount() == null ? 1 : script.getExecutionCount() + 1);
            scriptMapper.updateById(script);

            // 单次触发手动执行后自动禁用
            String tt = script.getTriggerType();
            if ("once".equals(tt) || "single".equals(tt)) {
                script.setStatus("disabled");
                scriptMapper.updateById(script);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("scriptId", id);
            result.put("executionId", execution.getExecutionId());
            result.put("scriptName", script.getScriptName());
            result.put("source", script.getSource());

            log.info("manual_execute_collect_date:{}, 任务ID:{}", date, id);
            return Result.success(result);
        } finally {
            releaseLock(lockKey);
        }
    }

    private final ConcurrentMap<String, ReentrantLock> lockMap = new ConcurrentHashMap<>();

    private boolean acquireLock(String key, int seconds) {
        ReentrantLock lock = lockMap.computeIfAbsent(key,
            k -> new ReentrantLock());
        try {
            return lock.tryLock(seconds, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void releaseLock(String key) {
        ReentrantLock lock = lockMap.remove(key);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    /**
     * 新增脚本（支持全字段创建）
     */
    public Result<CollectionScript> createScript(CollectionScript script) {
        script.setCreatedAt(LocalDateTime.now());
        script.setUpdatedAt(LocalDateTime.now());
        script.setStatus("enabled");
        script.setExecutionCount(0);
        script.setSuccessCount(0);
        script.setFailedCount(0);
        // script_path 为 NOT NULL，使用空字符串占位（文件上传创建时会被覆盖）
        if (script.getScriptPath() == null || script.getScriptPath().isBlank()) {
            script.setScriptPath("");
        }

        // 周期触发 → 转换为 Cron 表达式，统一调度（同 updateScript 逻辑）
        String tt = script.getTriggerType();
        if ("repeat".equals(tt)) {
            String cronExpr = convertCycleToCron(script);
            if (cronExpr != null) {
                script.setCronExpression(cronExpr);
            }
            script.setTriggerType("cron");
            script.setRepeatType(null);
            script.setRepeatTime(null);
            script.setRepeatConfig(null);
            script.setWeeklyDays(null);
            script.setMonthlyDay(null);
            script.setMonthlyLastDay(null);
            tt = "cron";
        }

        // 根据触发类型清除无关字段
        if (tt != null) {
            if ("cron".equals(tt)) {
                script.setRepeatType(null);
                script.setRepeatTime(null);
                script.setRepeatConfig(null);
                script.setStartTime(null);
                script.setWeeklyDays(null);
                script.setMonthlyDay(null);
                script.setMonthlyLastDay(null);
            } else if ("once".equals(tt) || "single".equals(tt)) {
                script.setCronExpression(null);
                script.setRepeatType(null);
                script.setRepeatTime(null);
                script.setRepeatConfig(null);
                script.setEndTime(null);
                script.setEndType(null);
                script.setRepeatCount(null);
                script.setWeeklyDays(null);
                script.setMonthlyDay(null);
                script.setMonthlyLastDay(null);
            }
        }

        scriptMapper.insert(script);
        log.info("创建采集脚本: {}", script.getScriptName());
        return Result.success(script);
    }

    /**
     * 将周期触发配置转换为 Cron 表达式
     */
    private String convertCycleToCron(CollectionScript script) {
        String repeatType = script.getRepeatType();
        String repeatTime = script.getRepeatTime(); // HH:mm:ss or HH:mm
        if (repeatTime == null || repeatTime.isBlank()) repeatTime = "08:00:00";

        String[] timeParts = repeatTime.split(":");
        String second = timeParts.length > 2 ? timeParts[2] : "00";
        String minute = timeParts.length > 1 ? timeParts[1] : "00";
        String hour = timeParts[0];

        if ("daily".equals(repeatType)) {
            return String.format("%s %s %s * * ?", second, minute, hour);
        } else if ("weekly".equals(repeatType)) {
            String days = script.getWeeklyDays();
            if (days == null || days.isBlank()) days = "1";
            return String.format("%s %s %s * * %s", second, minute, hour, days);
        } else if ("monthly".equals(repeatType)) {
            if (Boolean.TRUE.equals(script.getMonthlyLastDay())) {
                return String.format("%s %s %s L * ?", second, minute, hour);
            }
            int day = script.getMonthlyDay() != null ? script.getMonthlyDay() : 1;
            return String.format("%s %s %s %d * ?", second, minute, hour, day);
        }
        return null;
    }

    /**
     * 更新脚本
     */
    @Transactional
    public Result<CollectionScript> updateScript(CollectionScript script) {
        script.setUpdatedAt(LocalDateTime.now());
        String tt = script.getTriggerType();

        // 周期触发 → 转换为 Cron 表达式，统一调度
        if ("repeat".equals(tt)) {
            String cronExpr = convertCycleToCron(script);
            if (cronExpr != null) {
                script.setCronExpression(cronExpr);
            }
            script.setTriggerType("cron");
            script.setRepeatType(null);
            script.setRepeatTime(null);
            script.setRepeatConfig(null);
            script.setWeeklyDays(null);
            script.setMonthlyDay(null);
            script.setMonthlyLastDay(null);
            tt = "cron"; // 后续清理逻辑走 cron 分支
        }

        // 根据触发类型清除无关字段，避免脏数据写入数据库
        if (tt != null) {
            if ("cron".equals(tt)) {
                script.setRepeatType(null);
                script.setRepeatTime(null);
                script.setRepeatConfig(null);
                script.setStartTime(null);
                script.setWeeklyDays(null);
                script.setMonthlyDay(null);
                script.setMonthlyLastDay(null);
            } else if ("once".equals(tt) || "single".equals(tt)) {
                script.setCronExpression(null);
                script.setRepeatType(null);
                script.setRepeatTime(null);
                script.setRepeatConfig(null);
                script.setEndTime(null);
                script.setEndType(null);
                script.setRepeatCount(null);
                script.setWeeklyDays(null);
                script.setMonthlyDay(null);
                script.setMonthlyLastDay(null);
            }
        }
        scriptMapper.updateById(script);

        // 清除无关字段：updateById 按 NOT_NULL 策略执行，需显式将多余字段置 NULL
        LambdaUpdateWrapper<CollectionScript> cleanup = new LambdaUpdateWrapper<>();
        cleanup.eq(CollectionScript::getId, script.getId());
        if ("cron".equals(tt)) {
            cleanup.set(CollectionScript::getRepeatType, null)
                   .set(CollectionScript::getRepeatTime, null)
                   .set(CollectionScript::getRepeatConfig, null)
                   .set(CollectionScript::getStartTime, null)
                   .set(CollectionScript::getWeeklyDays, null)
                   .set(CollectionScript::getMonthlyDay, null)
                   .set(CollectionScript::getMonthlyLastDay, null)
                   .set(CollectionScript::getNextExecutionTime, null);
            // 结束条件：仅当请求未携带时才清除，避免覆盖用户已设置的值
            if (script.getEndType() == null) cleanup.set(CollectionScript::getEndType, null);
            if (script.getEndTime() == null) cleanup.set(CollectionScript::getEndTime, null);
            if (script.getRepeatCount() == null) cleanup.set(CollectionScript::getRepeatCount, null);
            scriptMapper.update(null, cleanup);
        } else if ("once".equals(tt) || "single".equals(tt)) {
            cleanup.set(CollectionScript::getCronExpression, null)
                   .set(CollectionScript::getRepeatType, null)
                   .set(CollectionScript::getRepeatTime, null)
                   .set(CollectionScript::getRepeatConfig, null)
                   .set(CollectionScript::getEndTime, null)
                   .set(CollectionScript::getEndType, null)
                   .set(CollectionScript::getRepeatCount, null)
                   .set(CollectionScript::getWeeklyDays, null)
                   .set(CollectionScript::getMonthlyDay, null)
                   .set(CollectionScript::getMonthlyLastDay, null);
            scriptMapper.update(null, cleanup);
        }

        // 单次触发重新配置后：启用脚本并清除上次执行时间，允许重新执行
        if ("once".equals(tt) || "single".equals(tt)) {
            log.info("单次任务重新配置: id={}, startTime={}, triggerType={}",
                script.getId(), script.getStartTime(), tt);

            // updateById skips null fields, so use UpdateWrapper to explicitly set all fields
            LambdaUpdateWrapper<CollectionScript> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(CollectionScript::getId, script.getId())
                   .set(CollectionScript::getStatus, "enabled")
                   .set(CollectionScript::getUpdatedAt, LocalDateTime.now())
                   .setSql("last_execution_time = NULL");
            if (script.getStartTime() != null) {
                wrapper.set(CollectionScript::getNextExecutionTime, script.getStartTime());
            }
            scriptMapper.update(null, wrapper);
            log.info("单次任务已重新启用: id={}, nextExecutionTime={}", script.getId(), script.getStartTime());
        }

        log.info("更新采集脚本: {}", script.getScriptName());
        CollectionScript saved = scriptMapper.selectById(script.getId());
        log.info("保存后脚本状态: id={}, status={}, lastExec={}, nextExec={}, startTime={}",
            saved.getId(), saved.getStatus(), saved.getLastExecutionTime(),
            saved.getNextExecutionTime(), saved.getStartTime());
        return Result.success(saved);
    }

    /**
     * 删除脚本及其关联数据
     */
    @Transactional
    public Result<Void> deleteScript(Long id) {
        // 1. 查询该脚本的所有执行记录
        List<TaskExecution> executions = taskExecutionMapper.selectList(
            new LambdaQueryWrapper<TaskExecution>()
                .eq(TaskExecution::getScriptId, id));
        List<String> executionIds = executions.stream()
            .map(TaskExecution::getExecutionId)
            .filter(e -> e != null)
            .collect(Collectors.toList());

        if (!executionIds.isEmpty()) {
            // 2. 删除执行日志
            taskExecutionLogMapper.delete(
                new LambdaQueryWrapper<com.scfx.entity.TaskExecutionLog>()
                    .in(com.scfx.entity.TaskExecutionLog::getExecutionId, executionIds));

            // 3. 删除执行数据项
            executionItemMapper.delete(
                new LambdaQueryWrapper<com.scfx.entity.ExecutionItem>()
                    .in(com.scfx.entity.ExecutionItem::getExecutionId, executionIds));
        }

        // 4. 删除执行记录
        taskExecutionMapper.delete(
            new LambdaQueryWrapper<TaskExecution>()
                .eq(TaskExecution::getScriptId, id));

        // 5. 删除采集日志
        collectionLogMapper.delete(
            new LambdaQueryWrapper<com.scfx.entity.CollectionLog>()
                .eq(com.scfx.entity.CollectionLog::getTaskId, id));

        // 6. 删除脚本本身
        scriptMapper.deleteById(id);
        log.info("删除采集脚本及关联数据: id={}, 关联执行记录={}条", id, executions.size());
        return Result.success();
    }

    /**
     * 根据名称删除脚本
     */
    @Transactional
    public Result<Void> deleteScriptByName(String scriptName) {
        LambdaQueryWrapper<CollectionScript> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CollectionScript::getScriptName, scriptName);
        scriptMapper.delete(wrapper);
        log.info("删除采集脚本: name={}", scriptName);
        return Result.success();
    }

    /**
     * 执行脚本
     */
    @Transactional
    public Result<Map<String, Object>> executeScriptByPath(Long id) {
        CollectionScript script = scriptMapper.selectById(id);
        if (script == null) {
            return Result.error("脚本不存在");
        }
        if ("disabled".equals(script.getStatus())) {
            return Result.error("脚本已禁用，请先启用");
        }

        // 更新执行状态
        script.setLastExecutionTime(LocalDateTime.now());
        script.setNextExecutionTime(calculateNextExecution(script));
        script.setExecutionCount(script.getExecutionCount() == null ? 1 : script.getExecutionCount() + 1);
        scriptMapper.updateById(script);

        Map<String, Object> result = new HashMap<>();
        result.put("scriptId", id);
        result.put("scriptName", script.getScriptName());
        result.put("startTime", script.getLastExecutionTime());
        result.put("source", script.getSource());
        result.put("subject", script.getSubject());

        log.info("执行采集脚本: {}", script.getScriptName());
        return Result.success(result);
    }

    /**
     * 根据 ID 获取脚本（返回实体，供内部调用）
     */
    public CollectionScript getById(Long id) {
        return scriptMapper.selectById(id);
    }

    /**
     * 获取脚本详情
     */
    public Result<CollectionScript> getScriptById(Long id) {
        CollectionScript script = scriptMapper.selectById(id);
        if (script == null) {
            return Result.error("脚本不存在");
        }
        return Result.success(script);
    }

    /**
     * 分页查询脚本
     */
    public Result<Page<CollectionScript>> getScripts(int page, int size, String status, String source) {
        Page<CollectionScript> pageInfo = new Page<>(page, size);
        LambdaQueryWrapper<CollectionScript> wrapper = new LambdaQueryWrapper<>();

        if (status != null && !status.isEmpty()) {
            wrapper.eq(CollectionScript::getStatus, status);
        }
        if (source != null && !source.isEmpty()) {
            wrapper.eq(CollectionScript::getSource, source);
        }

        wrapper.orderByDesc(CollectionScript::getUpdatedAt);
        Page<CollectionScript> result = scriptMapper.selectPage(pageInfo, wrapper);

        // 批量查询每个脚本的最新执行状态
        if (result.getRecords() != null && !result.getRecords().isEmpty()) {
            List<Long> scriptIds = result.getRecords().stream()
                .map(CollectionScript::getId)
                .collect(Collectors.toList());
            // 查询每个脚本的最新执行记录
            List<TaskExecution> latestExecs = taskExecutionMapper.getLatestByScriptIds(scriptIds);
            Map<Long, String> statusMap = latestExecs.stream()
                .collect(Collectors.toMap(
                    TaskExecution::getScriptId,
                    TaskExecution::getStatus,
                    (a, b) -> a
                ));
            for (CollectionScript s : result.getRecords()) {
                s.setLastExecutionStatus(statusMap.get(s.getId()));
            }
        }

        return Result.success(result);
    }

    /**
     * 获取所有启用的脚本
     */
    public List<CollectionScript> getEnabledScripts() {
        LambdaQueryWrapper<CollectionScript> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CollectionScript::getStatus, "enabled");
        return scriptMapper.selectList(wrapper);
    }

    /**
     * 启用脚本
     */
    public Result<Void> enableScript(Long id) {
        CollectionScript script = scriptMapper.selectById(id);
        if (script == null) {
            return Result.error("脚本不存在");
        }
        script.setStatus("enabled");
        script.setUpdatedAt(LocalDateTime.now());
        scriptMapper.updateById(script);
        log.info("启用采集脚本: {}", script.getScriptName());
        return Result.success();
    }

    /**
     * 禁用脚本
     */
    public Result<Void> disableScript(Long id) {
        CollectionScript script = scriptMapper.selectById(id);
        if (script == null) {
            return Result.error("脚本不存在");
        }
        script.setStatus("disabled");
        script.setUpdatedAt(LocalDateTime.now());
        scriptMapper.updateById(script);
        log.info("禁用采集脚本: {}", script.getScriptName());
        return Result.success();
    }

    /**
     * 手动执行脚本
     */
    @Transactional
    public Result<Map<String, Object>> executeScript(Long id) {
        CollectionScript script = scriptMapper.selectById(id);
        if (script == null) {
            return Result.error("脚本不存在");
        }
        if ("disabled".equals(script.getStatus())) {
            return Result.error("脚本已禁用，请先启用");
        }

        // 更新执行状态
        script.setLastExecutionTime(LocalDateTime.now());
        script.setNextExecutionTime(calculateNextExecution(script));
        script.setExecutionCount(script.getExecutionCount() == null ? 1 : script.getExecutionCount() + 1);
        scriptMapper.updateById(script);

        Map<String, Object> result = new HashMap<>();
        result.put("scriptId", id);
        result.put("scriptName", script.getScriptName());
        result.put("startTime", script.getLastExecutionTime());
        result.put("source", script.getSource());
        result.put("subject", script.getSubject());

        log.info("手动执行采集脚本: {}", script.getScriptName());
        return Result.success(result);
    }

    /**
     * 记录执行结果
     */
    public void recordExecutionResult(Long id, boolean success) {
        CollectionScript script = scriptMapper.selectById(id);
        if (script != null) {
            if (success) {
                script.setSuccessCount(script.getSuccessCount() == null ? 1 : script.getSuccessCount() + 1);
            } else {
                script.setFailedCount(script.getFailedCount() == null ? 1 : script.getFailedCount() + 1);
            }
            scriptMapper.updateById(script);
        }
    }

    /**
     * 定时任务：检查单次触发脚本
     */
    @Transactional
    @Scheduled(fixedRate = 60000)
    public void checkSingleTriggerScripts() {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<CollectionScript> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CollectionScript::getStatus, "enabled")
               .eq(CollectionScript::getTriggerType, "single")
               .le(CollectionScript::getStartTime, now)
               .and(w -> w.isNull(CollectionScript::getEndTime).or().gt(CollectionScript::getEndTime, now));

        List<CollectionScript> scripts = scriptMapper.selectList(wrapper);
        for (CollectionScript script : scripts) {
            if (shouldExecuteSingle(script, now)) {
                log.info("触发单次脚本: {}", script.getScriptName());
                // 创建执行记录，由 CollectorAgentService 轮询执行
                if (!hasPendingExecution(script.getId())) {
                    executionService.createExecution(script.getId(), "schedule");
                }
                script.setLastExecutionTime(now);
                script.setExecutionCount(script.getExecutionCount() == null ? 1 : script.getExecutionCount() + 1);
                scriptMapper.updateById(script);
            }
        }
    }

    /**
     * 定时任务：检查周期触发脚本
     */
    @Transactional
    @Scheduled(fixedRate = 60000)
    public void checkRepeatTriggerScripts() {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<CollectionScript> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CollectionScript::getStatus, "enabled")
               .in(CollectionScript::getTriggerType, "repeat", "cron")
               .and(w -> w.isNull(CollectionScript::getStartTime)
                         .or().le(CollectionScript::getStartTime, now));
        // 排除已过期的
        wrapper.and(w -> w.isNull(CollectionScript::getEndTime).or().gt(CollectionScript::getEndTime, now));

        List<CollectionScript> scripts = scriptMapper.selectList(wrapper);
        for (CollectionScript script : scripts) {
            if (shouldExecuteRepeat(script, now)) {
                log.info("触发周期脚本: {}", script.getScriptName());
                // 创建执行记录，由 CollectorAgentService 轮询执行
                if (!hasPendingExecution(script.getId())) {
                    executionService.createExecution(script.getId(), "schedule");
                }
                script.setLastExecutionTime(now);
                script.setExecutionCount(script.getExecutionCount() == null ? 1 : script.getExecutionCount() + 1);
                scriptMapper.updateById(script);
            }
        }
    }

    private boolean shouldExecuteSingle(CollectionScript script, LocalDateTime now) {
        if (script.getStartTime() == null) return false;
        // 如果没有配置end_time，或者当前时间在范围内
        if (script.getEndTime() == null || script.getEndTime().isAfter(now)) {
            return now.isAfter(script.getStartTime()) &&
                   (script.getLastExecutionTime() == null ||
                    script.getLastExecutionTime().isBefore(script.getStartTime()));
        }
        return false;
    }

    private boolean shouldExecuteRepeat(CollectionScript script, LocalDateTime now) {
        // 结束条件检查
        if ("date".equals(script.getEndType()) && script.getEndTime() != null) {
            if (now.isAfter(script.getEndTime())) {
                log.info("脚本已达到结束日期，自动禁用: id={}, endTime={}", script.getId(), script.getEndTime());
                script.setStatus("disabled");
                scriptMapper.updateById(script);
                return false;
            }
        }
        if ("count".equals(script.getEndType()) && script.getRepeatCount() != null) {
            int execCount = script.getExecutionCount() != null ? script.getExecutionCount() : 0;
            if (execCount >= script.getRepeatCount()) {
                log.info("脚本已达到执行次数，自动禁用: id={}, count={}/{}", script.getId(), execCount, script.getRepeatCount());
                script.setStatus("disabled");
                scriptMapper.updateById(script);
                return false;
            }
        }

        LocalDateTime nextExec = calculateNextExecution(script);
        if (nextExec == null) return false;

        // 首次执行：如果开始时间已到（或没有限制），立即触发
        if (script.getLastExecutionTime() == null) {
            return script.getStartTime() == null || !now.isBefore(script.getStartTime());
        }

        // 跳过已触发过的时间点：cronExpr.next(now.minusMinutes(3)) 的 3 分钟回溯
        // 可能导致同一 cron 时间点被重复返回，使用 nextExecutionTime 判断
        //（lastExecutionTime 是调度器创建执行的时间，可能早于 cron 目标时间）
        if (script.getNextExecutionTime() != null && !nextExec.isAfter(script.getNextExecutionTime())) {
            return false;
        }

        if (now.isAfter(nextExec.minusMinutes(1))) {
            script.setNextExecutionTime(nextExec);
            return true;
        }
        return false;
    }

    /**
     * 检查脚本是否已有待执行或正在执行的记录，避免重复触发
     */
    private boolean hasPendingExecution(Long scriptId) {
        LambdaQueryWrapper<TaskExecution> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskExecution::getScriptId, scriptId)
               .in(TaskExecution::getStatus, "pending", "running");
        return taskExecutionMapper.selectCount(wrapper) > 0;
    }

    /**
     * 计算下次执行时间（统一使用 CronExpression，周期触发已被转换为 cron）
     */
    public LocalDateTime calculateNextExecution(CollectionScript script) {
        String tt = script.getTriggerType();
        // 单次触发没有下次执行时间
        if ("once".equals(tt) || "single".equals(tt)) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();
        int interval = script.getReportIntervalSeconds() != null ? script.getReportIntervalSeconds() : 60;

        // Cron 表达式调度（周期触发已在保存时转换为 cron）
        if (("cron".equals(tt) || "repeat".equals(tt)) && script.getCronExpression() != null) {
            try {
                String normalizedCron = com.scfx.util.CronDescriptionUtil.normalizeToSixFields(script.getCronExpression());
                org.springframework.scheduling.support.CronExpression cronExpr =
                    org.springframework.scheduling.support.CronExpression.parse(normalizedCron);
                java.time.ZonedDateTime nowZoned = java.time.ZonedDateTime.now();
                java.time.temporal.TemporalAccessor next = cronExpr.next(nowZoned.minusMinutes(3));
                if (next != null) {
                    return LocalDateTime.ofInstant(java.time.Instant.from(next), java.time.ZoneId.systemDefault());
                }
            } catch (Exception e) {
                log.warn("Failed to parse cron expression: {}", script.getCronExpression(), e);
            }
            // Fallback
            return now.plusHours(1).withMinute(0).withSecond(0).withNano(0);
        }

        // 通用 repeat：基于 lastExecutionTime 计算下次执行，确保间隔正确
        LocalDateTime base = script.getLastExecutionTime() != null ? script.getLastExecutionTime() : now;
        return base.plusSeconds(interval);
    }

    private Map<String, Object> parseRepeatConfig(String config) {
        Map<String, Object> result = new HashMap<>();
        result.put("hour", 8);
        result.put("minute", 0);
        // 实际应JSON解析
        return result;
    }

    /**
     * 更新最后执行时间（调度器执行时调用）
     */
    public void updateLastExecutionTime(Long scriptId) {
        CollectionScript script = scriptMapper.selectById(scriptId);
        if (script != null) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime nextExec = calculateNextExecution(script);
            LambdaUpdateWrapper<CollectionScript> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(CollectionScript::getId, scriptId)
                   .set(CollectionScript::getLastExecutionTime, now)
                   .set(CollectionScript::getUpdatedAt, now)
                   .setSql("execution_count = COALESCE(execution_count, 0) + 1");
            if (nextExec != null) {
                wrapper.set(CollectionScript::getNextExecutionTime, nextExec);
            } else {
                wrapper.setSql("next_execution_time = NULL");
            }
            scriptMapper.update(null, wrapper);
        }
    }

    /**
     * 获取脚本统计
     */
    public Result<Map<String, Object>> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", scriptMapper.selectCount(null));
        stats.put("enabled", scriptMapper.selectCount(
            new LambdaQueryWrapper<CollectionScript>().eq(CollectionScript::getStatus, "enabled")));
        stats.put("disabled", scriptMapper.selectCount(
            new LambdaQueryWrapper<CollectionScript>().eq(CollectionScript::getStatus, "disabled")));
        return Result.success(stats);
    }

    /**
     * 获取所有脚本
     */
    public List<CollectionScript> getAllScripts() {
        return scriptMapper.selectList(null);
    }

    /**
     * 获取今日成功执行数
     */
    public long getTodaySuccessCount() {
        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        return scriptMapper.selectCount(
            new LambdaQueryWrapper<CollectionScript>()
                .eq(CollectionScript::getStatus, "enabled")
                .ge(CollectionScript::getLastExecutionTime, todayStart));
    }

    /**
     * 获取今日失败执行数
     */
    public long getTodayFailedCount() {
        // 由于 CollectionScript 没有失败追踪，这里返回 0
        // 实际失败数应该从 TaskExecution 获取
        return 0;
    }

    /**
     * 获取数据源统计
     */
    public List<Map<String, Object>> getSourceStats() {
        List<CollectionScript> scripts = scriptMapper.selectList(
            new LambdaQueryWrapper<CollectionScript>().orderByDesc(CollectionScript::getSource));

        Map<String, Map<String, Object>> sourceMap = new LinkedHashMap<>();

        for (CollectionScript script : scripts) {
            String source = script.getSource() != null ? script.getSource() : "unknown";
            if (!sourceMap.containsKey(source)) {
                Map<String, Object> stat = new HashMap<>();
                stat.put("source", source);
                stat.put("displayName", getDisplayName(source));
                stat.put("total", 0);
                stat.put("enabled", 0);
                sourceMap.put(source, stat);
            }
            Map<String, Object> stat = sourceMap.get(source);
            stat.put("total", ((Long) stat.get("total")) + 1);
            if ("enabled".equals(script.getStatus())) {
                stat.put("enabled", ((Long) stat.get("enabled")) + 1);
            }
        }

        return new ArrayList<>(sourceMap.values());
    }

    private String getDisplayName(String source) {
        return switch (source) {
            case "liangxinwang" -> "粮信网";
            case "mysteel" -> "我的钢铁网";
            case "china_grain" -> "中华粮网";
            default -> source;
        };
    }
}
