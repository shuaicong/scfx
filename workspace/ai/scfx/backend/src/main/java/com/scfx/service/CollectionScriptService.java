package com.scfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scfx.common.Result;
import com.scfx.entity.CollectionScript;
import com.scfx.entity.TaskExecution;
import com.scfx.mapper.CollectionScriptMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 采集脚本管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollectionScriptService {

    private final CollectionScriptMapper scriptMapper;

    @Autowired
    private ScriptFileService scriptFileService;

    @Autowired
    private TaskExecutionService executionService;

    /**
     * 立即执行脚本
     */
    @Transactional
    public Result<Map<String, Object>> executeScriptNow(Long id) {
        CollectionScript script = scriptMapper.selectById(id);
        if (script == null) {
            return Result.error("脚本不存在");
        }
        if ("disabled".equals(script.getStatus())) {
            return Result.error("脚本已禁用，请先启用");
        }

        // 创建执行记录
        TaskExecution execution = executionService.createExecution(id, "manual");

        // 更新执行状态
        script.setLastExecutionTime(LocalDateTime.now());
        script.setExecutionCount(script.getExecutionCount() == null ? 1 : script.getExecutionCount() + 1);
        scriptMapper.updateById(script);

        Map<String, Object> result = new HashMap<>();
        result.put("executionId", execution.getExecutionId());
        result.put("scriptId", id);
        result.put("scriptName", script.getScriptName());
        result.put("scriptPath", script.getScriptPath());
        result.put("scriptContent", script.getScriptContent());
        result.put("startTime", script.getLastExecutionTime());
        result.put("source", script.getSource());
        result.put("subject", script.getSubject());

        log.info("立即执行采集脚本: {}", script.getScriptName());
        return Result.success(result);
    }

    /**
     * 新增脚本（简化为只需要名称和描述）
     */
    public Result<CollectionScript> createScript(String scriptName, String description, String scriptContent) {
        // 1. 保存脚本文件
        String scriptPath = scriptFileService.saveScript(scriptName, scriptContent);

        // 2. 创建数据库记录
        CollectionScript script = new CollectionScript();
        script.setScriptName(scriptName);
        script.setDescription(description);
        script.setScriptPath(scriptPath);
        script.setScriptContent(scriptContent);
        script.setCreatedAt(LocalDateTime.now());
        script.setUpdatedAt(LocalDateTime.now());
        script.setStatus("enabled");
        script.setExecutionCount(0);
        script.setSuccessCount(0);
        script.setFailedCount(0);
        scriptMapper.insert(script);

        log.info("创建采集脚本: {} -> {}", scriptName, scriptPath);
        return Result.success(script);
    }

    /**
     * 更新脚本内容
     */
    public Result<CollectionScript> updateScriptContent(Long id, String scriptContent) {
        CollectionScript script = scriptMapper.selectById(id);
        if (script == null) {
            return Result.error("脚本不存在");
        }

        // 更新文件
        String scriptPath = scriptFileService.updateScript(script.getScriptPath(), scriptContent);

        // 更新数据库
        script.setScriptContent(scriptContent);
        script.setUpdatedAt(LocalDateTime.now());
        scriptMapper.updateById(script);

        log.info("更新脚本内容: {}", script.getScriptName());
        return Result.success(script);
    }

    /**
     * 上传脚本文件
     */
    public Result<CollectionScript> uploadScript(String scriptName, String description, MultipartFile file) {
        try {
            // 保存上传的文件
            String scriptPath = scriptFileService.uploadScript(file, scriptName);

            // 读取文件内容
            String scriptContent = scriptFileService.readScript(scriptPath);

            // 创建数据库记录
            CollectionScript script = new CollectionScript();
            script.setScriptName(scriptName);
            script.setDescription(description);
            script.setScriptPath(scriptPath);
            script.setScriptContent(scriptContent);
            script.setCreatedAt(LocalDateTime.now());
            script.setUpdatedAt(LocalDateTime.now());
            script.setStatus("enabled");
            script.setExecutionCount(0);
            script.setSuccessCount(0);
            script.setFailedCount(0);
            scriptMapper.insert(script);

            log.info("上传脚本: {} -> {}", scriptName, scriptPath);
            return Result.success(script);
        } catch (Exception e) {
            log.error("上传脚本失败", e);
            return Result.error("上传脚本失败: " + e.getMessage());
        }
    }

    /**
     * 读取脚本文件内容
     */
    public Result<String> getScriptContent(Long id) {
        CollectionScript script = scriptMapper.selectById(id);
        if (script == null) {
            return Result.error("脚本不存在");
        }

        try {
            String content = scriptFileService.readScript(script.getScriptPath());
            return Result.success(content);
        } catch (Exception e) {
            log.error("读取脚本内容失败", e);
            return Result.error("读取脚本内容失败: " + e.getMessage());
        }
    }

    /**
     * 更新脚本（元数据，不更新文件内容）
     */
    public Result<CollectionScript> updateScript(CollectionScript script) {
        script.setUpdatedAt(LocalDateTime.now());
        scriptMapper.updateById(script);
        log.info("更新采集脚本: {}", script.getScriptName());
        return Result.success(scriptMapper.selectById(script.getId()));
    }

/**
     * 删除脚本（同时删除文件）
     */
    @Transactional
    public Result<Void> deleteScript(Long id) {
        CollectionScript script = scriptMapper.selectById(id);
        if (script != null && script.getScriptPath() != null) {
            scriptFileService.deleteScript(script.getScriptPath());
        }
        scriptMapper.deleteById(id);
        log.info("删除采集脚本: id={}", id);
        return Result.success();
    }

    /**
     * 根据名称删除脚本
     */
    @Transactional
    public Result<Void> deleteScriptByName(String scriptName) {
        LambdaQueryWrapper<CollectionScript> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CollectionScript::getScriptName, scriptName);
        List<CollectionScript> scripts = scriptMapper.selectList(wrapper);

        for (CollectionScript script : scripts) {
            if (script.getScriptPath() != null) {
                scriptFileService.deleteScript(script.getScriptPath());
            }
            scriptMapper.deleteById(script.getId());
        }
        log.info("删除采集脚本: name={}", scriptName);
        return Result.success();
    }

    /**
     * 执行脚本（通过文件路径）
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

        // 读取脚本内容
        String scriptContent;
        try {
            scriptContent = scriptFileService.readScript(script.getScriptPath());
        } catch (Exception e) {
            return Result.error("读取脚本文件失败: " + e.getMessage());
        }

        // 更新执行状态
        script.setLastExecutionTime(LocalDateTime.now());
        script.setExecutionCount(script.getExecutionCount() == null ? 1 : script.getExecutionCount() + 1);
        scriptMapper.updateById(script);

        Map<String, Object> result = new HashMap<>();
        result.put("scriptId", id);
        result.put("scriptName", script.getScriptName());
        result.put("scriptPath", script.getScriptPath());
        result.put("scriptContent", scriptContent);
        result.put("startTime", script.getLastExecutionTime());
        result.put("source", script.getSource());
        result.put("subject", script.getSubject());

        log.info("执行采集脚本: {}", script.getScriptName());
        return Result.success(result);
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
    @Scheduled(fixedRate = 60000)
    public void checkSingleTriggerScripts() {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<CollectionScript> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CollectionScript::getStatus, "enabled")
               .eq(CollectionScript::getTriggerType, "single")
               .le(CollectionScript::getStartTime, now)
               .or()
               .isNull(CollectionScript::getEndTime)
               .gt(CollectionScript::getEndTime, now);

        List<CollectionScript> scripts = scriptMapper.selectList(wrapper);
        for (CollectionScript script : scripts) {
            if (shouldExecuteSingle(script, now)) {
                log.info("触发单次脚本: {}", script.getScriptName());
                // 这里会触发脚本执行，实际由调度器调用
                script.setLastExecutionTime(now);
                scriptMapper.updateById(script);
            }
        }
    }

    /**
     * 定时任务：检查周期触发脚本
     */
    @Scheduled(fixedRate = 60000)
    public void checkRepeatTriggerScripts() {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<CollectionScript> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CollectionScript::getStatus, "enabled")
               .in(CollectionScript::getTriggerType, "repeat", "cron")
               .le(CollectionScript::getStartTime, now);
        // 排除已过期的
        wrapper.and(w -> w.isNull(CollectionScript::getEndTime).or().gt(CollectionScript::getEndTime, now));

        List<CollectionScript> scripts = scriptMapper.selectList(wrapper);
        for (CollectionScript script : scripts) {
            if (shouldExecuteRepeat(script, now)) {
                log.info("触发周期脚本: {}", script.getScriptName());
                script.setLastExecutionTime(now);
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
        if (script.getLastExecutionTime() == null) {
            return true;
        }

        LocalDateTime nextExec = calculateNextExecution(script);
        if (nextExec != null && now.isAfter(nextExec.minusMinutes(1))) {
            script.setNextExecutionTime(nextExec);
            return true;
        }
        return false;
    }

    /**
     * 计算下次执行时间
     */
    public LocalDateTime calculateNextExecution(CollectionScript script) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime base = script.getLastExecutionTime() != null ? script.getLastExecutionTime() : now;
        int interval = script.getReportIntervalSeconds() != null ? script.getReportIntervalSeconds() : 60;

        if ("daily".equals(script.getRepeatType())) {
            return base.toLocalDate().plusDays(1).atTime(8, 0);
        } else if ("weekly".equals(script.getRepeatType())) {
            return base.plusDays(7).withHour(8).withMinute(0).withSecond(0);
        } else if ("monthly".equals(script.getRepeatType())) {
            return base.plusMonths(1).withDayOfMonth(1).withHour(8).withMinute(0).withSecond(0);
        } else if ("cron".equals(script.getTriggerType()) && script.getCronExpression() != null) {
            return base.plusHours(1);
        }

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
     * 更新最后执行时间
     */
    public void updateLastExecutionTime(Long scriptId) {
        CollectionScript script = scriptMapper.selectById(scriptId);
        if (script != null) {
            script.setLastExecutionTime(LocalDateTime.now());
            scriptMapper.updateById(script);
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
}
