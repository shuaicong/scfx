package com.scfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scfx.common.Result;
import com.scfx.entity.CollectionTask;
import com.scfx.entity.TaskExecution;
import com.scfx.mapper.CollectionTaskMapper;
import com.scfx.mapper.TaskExecutionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 任务执行服务 - 管理采集任务的执行生命周期
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskExecutionService {

    private final TaskExecutionMapper executionMapper;
    private final CollectionTaskMapper taskMapper;
    private final CollectionLogService logService;

    /**
     * 开始执行任务
     * 检查任务是否已在运行，避免并发冲突
     */
    @Transactional
    public Result<TaskExecution> startExecution(Long taskId) {
        // 检查任务是否存在
        CollectionTask task = taskMapper.selectById(taskId);
        if (task == null) {
            return Result.error("任务不存在");
        }

        // 检查是否已有运行中的执行
        LambdaQueryWrapper<TaskExecution> runningWrapper = new LambdaQueryWrapper<>();
        runningWrapper.eq(TaskExecution::getTaskId, taskId)
                      .eq(TaskExecution::getStatus, "running");
        long runningCount = executionMapper.selectCount(runningWrapper);
        if (runningCount > 0) {
            return Result.error("任务正在执行中，请稍后再试");
        }

        // 创建新的执行记录
        TaskExecution execution = new TaskExecution();
        execution.setTaskId(taskId);
        execution.setExecutionId(UUID.randomUUID().toString());
        execution.setStatus("running");
        execution.setStartTime(LocalDateTime.now());
        execution.setCollectedCount(0);
        executionMapper.insert(execution);

        // 更新任务状态为 running
        task.setStatus("running");
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);

        // 记录日志
        logService.addLog(taskId, task.getTaskName(), "INFO",
            "开始执行采集任务", task.getSourceName(), execution.getExecutionId(),
            null, null, null);

        return Result.success(execution);
    }

    /**
     * 上报进度
     */
    public void reportProgress(String executionId, int collectedCount) {
        TaskExecution execution = findByExecutionId(executionId);
        if (execution != null) {
            execution.setCollectedCount(collectedCount);
            executionMapper.updateById(execution);

            logService.addLog(execution.getTaskId(), null, "INFO",
                "采集进度: 已采集 " + collectedCount + " 条数据",
                getSourceName(execution.getTaskId()),
                executionId, null, null, null);
        }
    }

    /**
     * 上报日志
     */
    public void addLog(String executionId, String level, String message) {
        TaskExecution execution = findByExecutionId(executionId);
        if (execution != null) {
            logService.addLog(execution.getTaskId(), null, level,
                message, getSourceName(execution.getTaskId()), executionId, null, null, null);
        }
    }

    /**
     * 上报错误
     */
    @Transactional
    public void reportError(String executionId, String errorMessage) {
        TaskExecution execution = findByExecutionId(executionId);
        if (execution != null) {
            execution.setStatus("failed");
            execution.setErrorMessage(errorMessage);
            execution.setEndTime(LocalDateTime.now());
            calculateDuration(execution);
            executionMapper.updateById(execution);

            // 更新任务状态
            updateTaskFinalStatus(execution.getTaskId(), "failed");

            // 记录错误日志
            logService.addLog(execution.getTaskId(), null, "ERROR",
                "采集失败: " + errorMessage, getSourceName(execution.getTaskId()), executionId, null, null, null);
        }
    }

    /**
     * 完成执行
     */
    @Transactional
    public void completeExecution(String executionId, String status, int collectedCount) {
        TaskExecution execution = findByExecutionId(executionId);
        if (execution != null) {
            execution.setStatus(status);
            execution.setCollectedCount(collectedCount);
            execution.setEndTime(LocalDateTime.now());
            calculateDuration(execution);
            executionMapper.updateById(execution);

            // 更新任务状态
            updateTaskFinalStatus(execution.getTaskId(), status);

            // 记录完成日志
            String message = "success".equals(status)
                ? "采集完成，成功 " + collectedCount + " 条数据"
                : "采集结束，状态: " + status;
            logService.addLog(execution.getTaskId(), null, "INFO",
                message, getSourceName(execution.getTaskId()), executionId, null, null, null);
        }
    }

    /**
     * 根据 executionId 查询执行记录
     */
    public TaskExecution findByExecutionId(String executionId) {
        LambdaQueryWrapper<TaskExecution> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskExecution::getExecutionId, executionId);
        return executionMapper.selectOne(wrapper);
    }

    /**
     * 获取任务最近一次执行
     */
    public TaskExecution getLastExecution(Long taskId) {
        LambdaQueryWrapper<TaskExecution> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskExecution::getTaskId, taskId)
               .orderByDesc(TaskExecution::getStartTime)
               .last("LIMIT 1");
        return executionMapper.selectOne(wrapper);
    }

    private void updateTaskFinalStatus(Long taskId, String status) {
        CollectionTask task = taskMapper.selectById(taskId);
        if (task != null) {
            task.setStatus(status);
            task.setLastExecutionTime(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());

            if ("success".equals(status)) {
                task.setSuccessCount(task.getSuccessCount() == null ? 1 : task.getSuccessCount() + 1);
            } else if ("failed".equals(status)) {
                task.setFailedCount(task.getFailedCount() == null ? 1 : task.getFailedCount() + 1);
            }

            taskMapper.updateById(task);
        }
    }

    private void calculateDuration(TaskExecution execution) {
        if (execution.getStartTime() != null && execution.getEndTime() != null) {
            long seconds = java.time.Duration.between(
                execution.getStartTime(), execution.getEndTime()).getSeconds();
            execution.setDurationSeconds((int) seconds);
        }
    }

    private String getSourceName(Long taskId) {
        CollectionTask task = taskMapper.selectById(taskId);
        return task != null ? task.getSourceName() : null;
    }
}
