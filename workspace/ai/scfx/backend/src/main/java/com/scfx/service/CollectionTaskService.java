package com.scfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scfx.common.Result;
import com.scfx.entity.CollectionTask;
import com.scfx.mapper.CollectionTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 采集任务服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollectionTaskService {

    private final CollectionTaskMapper taskMapper;

    /**
     * 分页查询任务
     */
    public Result<Page<CollectionTask>> getTasks(int page, int size, String status, String source) {
        Page<CollectionTask> pageInfo = new Page<>(page, size);
        LambdaQueryWrapper<CollectionTask> wrapper = new LambdaQueryWrapper<>();

        if (status != null && !status.isEmpty()) {
            wrapper.eq(CollectionTask::getStatus, status);
        }
        if (source != null && !source.isEmpty()) {
            wrapper.eq(CollectionTask::getSourceName, source);
        }

        wrapper.orderByDesc(CollectionTask::getCreatedAt);
        Page<CollectionTask> result = taskMapper.selectPage(pageInfo, wrapper);

        return Result.success(result);
    }

    /**
     * 获取任务详情
     */
    public Result<CollectionTask> getTaskById(Long id) {
        CollectionTask task = taskMapper.selectById(id);
        if (task == null) {
            return Result.error("任务不存在");
        }
        return Result.success(task);
    }

    /**
     * 创建任务
     */
    public Result<CollectionTask> createTask(CollectionTask task) {
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        task.setStatus("pending");
        task.setSuccessCount(0);
        task.setFailedCount(0);
        task.setRetryCount(0);
        taskMapper.insert(task);
        log.info("创建采集任务: {}", task.getTaskName());
        return Result.success(task);
    }

    /**
     * 更新任务状态
     */
    public Result<Void> updateTaskStatus(Long id, String status) {
        CollectionTask task = new CollectionTask();
        task.setId(id);
        task.setStatus(status);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        return Result.success();
    }

    /**
     * 删除任务
     */
    public Result<Void> deleteTask(Long id) {
        taskMapper.deleteById(id);
        return Result.success();
    }

    /**
     * 获取所有任务
     */
    public List<CollectionTask> getAllTasks() {
        return taskMapper.selectList(null);
    }

    /**
     * 获取运行中的任务数
     */
    public long getRunningTaskCount() {
        LambdaQueryWrapper<CollectionTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CollectionTask::getStatus, "running");
        return taskMapper.selectCount(wrapper);
    }

    /**
     * 获取今日成功数
     */
    public long getTodaySuccessCount() {
        LambdaQueryWrapper<CollectionTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CollectionTask::getStatus, "success");
        wrapper.ge(CollectionTask::getLastExecutionTime, LocalDateTime.now().withHour(0).withMinute(0).withSecond(0));
        return taskMapper.selectCount(wrapper);
    }

    /**
     * 获取今日失败数
     */
    public long getTodayFailedCount() {
        LambdaQueryWrapper<CollectionTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CollectionTask::getStatus, "failed");
        wrapper.ge(CollectionTask::getLastExecutionTime, LocalDateTime.now().withHour(0).withMinute(0).withSecond(0));
        return taskMapper.selectCount(wrapper);
    }

    /**
     * 获取各数据源统计信息
     */
    public List<Map<String, Object>> getSourceStats() {
        List<CollectionTask> tasks = taskMapper.selectList(
            new LambdaQueryWrapper<CollectionTask>().orderByDesc(CollectionTask::getSourceName)
        );

        // 按数据源分组统计
        java.util.Map<String, Map<String, Object>> sourceMap = new java.util.LinkedHashMap<>();

        for (CollectionTask task : tasks) {
            String sourceName = task.getSourceName();
            if (!sourceMap.containsKey(sourceName)) {
                Map<String, Object> stats = new java.util.HashMap<>();
                stats.put("sourceName", sourceName);
                stats.put("displayName", getDisplayName(sourceName));
                stats.put("todaySuccess", 0L);
                stats.put("todayFailed", 0L);
                stats.put("lastCollectTime", null);
                sourceMap.put(sourceName, stats);
            }

            Map<String, Object> stats = sourceMap.get(sourceName);

            if ("success".equals(task.getStatus()) && task.getLastExecutionTime() != null
                && task.getLastExecutionTime().isAfter(LocalDateTime.now().withHour(0).withMinute(0).withSecond(0))) {
                stats.put("todaySuccess", ((Long) stats.get("todaySuccess")) + 1);
            }
            if ("failed".equals(task.getStatus()) && task.getLastExecutionTime() != null
                && task.getLastExecutionTime().isAfter(LocalDateTime.now().withHour(0).withMinute(0).withSecond(0))) {
                stats.put("todayFailed", ((Long) stats.get("todayFailed")) + 1);
            }
            if (task.getLastExecutionTime() != null) {
                Object lastTime = stats.get("lastCollectTime");
                if (lastTime == null || task.getLastExecutionTime().isAfter((LocalDateTime) lastTime)) {
                    stats.put("lastCollectTime", task.getLastExecutionTime());
                }
            }
        }

        // 计算成功率
        for (Map<String, Object> stats : sourceMap.values()) {
            long success = (Long) stats.get("todaySuccess");
            long failed = (Long) stats.get("todayFailed");
            long total = success + failed;
            double rate = total > 0 ? (double) success / total * 100 : 100.0;
            stats.put("successRate", Math.round(rate * 100.0) / 100.0);
        }

        return new java.util.ArrayList<>(sourceMap.values());
    }

    private String getDisplayName(String sourceName) {
        return switch (sourceName) {
            case "liangxinwang" -> "粮信网";
            case "mysteel" -> "我的钢铁网";
            case "china_grain" -> "中华粮网";
            default -> sourceName;
        };
    }
}
