package com.scfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scfx.entity.TaskExecution;
import com.scfx.entity.TaskExecutionLog;
import com.scfx.mapper.TaskExecutionLogMapper;
import com.scfx.mapper.TaskExecutionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskExecutionService {

    private final TaskExecutionMapper executionMapper;
    private final TaskExecutionLogMapper logMapper;

    /**
     * 创建执行记录
     */
    public TaskExecution createExecution(Long scriptId, String triggerType) {
        TaskExecution execution = new TaskExecution();
        execution.setExecutionId(UUID.randomUUID().toString().replace("-", ""));
        execution.setScriptId(scriptId);
        execution.setTriggerType(triggerType);
        execution.setStatus("pending");
        execution.setStartTime(LocalDateTime.now());
        executionMapper.insert(execution);
        return execution;
    }

    /**
     * 更新执行状态
     */
    public void updateStatus(String executionId, String status, String errorMessage) {
        TaskExecution execution = findByExecutionId(executionId);
        if (execution != null) {
            execution.setStatus(status);
            if (errorMessage != null) {
                execution.setErrorMessage(errorMessage);
            }
            if ("success".equals(status) || "failed".equals(status) || "cancelled".equals(status)) {
                execution.setEndTime(LocalDateTime.now());
                execution.setDurationMs(
                    java.time.Duration.between(execution.getStartTime(), execution.getEndTime()).toMillis()
                );
            }
            executionMapper.updateById(execution);
        }
    }

    /**
     * 分页查询执行记录
     */
    public Page<TaskExecution> getExecutions(Long scriptId, int page, int size) {
        Page<TaskExecution> pageInfo = new Page<>(page, size);
        LambdaQueryWrapper<TaskExecution> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(scriptId != null, TaskExecution::getScriptId, scriptId)
               .orderByDesc(TaskExecution::getStartTime);
        return executionMapper.selectPage(pageInfo, wrapper);
    }

    /**
     * 获取执行详情
     */
    public TaskExecution getExecution(String executionId) {
        return findByExecutionId(executionId);
    }

    /**
     * 添加日志
     */
    public void addLog(String executionId, Long scriptId, String level, String message) {
        TaskExecutionLog logEntry = new TaskExecutionLog();
        logEntry.setExecutionId(executionId);
        logEntry.setScriptId(scriptId);
        logEntry.setLevel(level);
        logEntry.setMessage(message);
        logEntry.setTimestamp(LocalDateTime.now());
        logMapper.insert(logEntry);
    }

    /**
     * 上报错误（被采集器调用）
     */
    public void reportError(String executionId, String errorMessage) {
        updateStatus(executionId, "failed", errorMessage);
        addLog(executionId, null, "ERROR", errorMessage);
    }

    /**
     * 完成执行（被采集器调用）
     */
    public void completeExecution(String executionId, String status, int collectedCount) {
        updateStatus(executionId, status, null);
        String logMsg = "Execution completed with status: " + status + ", collected: " + collectedCount;
        addLog(executionId, null, "INFO", logMsg);
    }

    /**
     * 获取执行日志
     */
    public List<TaskExecutionLog> getLogs(String executionId) {
        return logMapper.selectList(
            new LambdaQueryWrapper<TaskExecutionLog>()
                .eq(TaskExecutionLog::getExecutionId, executionId)
                .orderByAsc(TaskExecutionLog::getTimestamp)
        );
    }

    public TaskExecution findByExecutionId(String executionId) {
        return executionMapper.selectOne(
            new LambdaQueryWrapper<TaskExecution>()
                .eq(TaskExecution::getExecutionId, executionId)
        );
    }
}