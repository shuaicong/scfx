package com.scfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scfx.common.Result;
import com.scfx.entity.CollectionLog;
import com.scfx.mapper.CollectionLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 采集日志服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollectionLogService {

    private final CollectionLogMapper logMapper;

    /**
     * 添加日志
     */
    public void addLog(Long taskId, String taskName, String level, String message, String source, String executionId,
                       String subject, String collType, String collObject) {
        CollectionLog collectionLog = new CollectionLog();
        collectionLog.setTaskId(taskId);
        collectionLog.setTaskName(taskName);
        collectionLog.setLevel(level);
        collectionLog.setMessage(message);
        collectionLog.setSource(source);
        collectionLog.setExecutionId(executionId);
        collectionLog.setSubject(subject);
        collectionLog.setCollType(collType);
        collectionLog.setCollObject(collObject);
        collectionLog.setCreatedAt(LocalDateTime.now());
        logMapper.insert(collectionLog);

        // 同时输出到控制台
        switch (level) {
            case "ERROR" -> log.error("[{}] {}", taskName, message);
            case "WARN" -> log.warn("[{}] {}", taskName, message);
            default -> log.info("[{}] {}", taskName, message);
        }
    }

    /**
     * 分页查询日志
     */
    public Result<Page<CollectionLog>> getLogs(int page, int size, String level, String source, String startTime, String endTime) {
        Page<CollectionLog> pageInfo = new Page<>(page, size);
        LambdaQueryWrapper<CollectionLog> wrapper = new LambdaQueryWrapper<>();

        if (level != null && !level.isEmpty() && !"all".equals(level)) {
            wrapper.eq(CollectionLog::getLevel, level);
        }
        if (source != null && !source.isEmpty()) {
            wrapper.eq(CollectionLog::getSource, source);
        }
        if (startTime != null && !startTime.isEmpty()) {
            wrapper.ge(CollectionLog::getCreatedAt, LocalDateTime.parse(startTime));
        }
        if (endTime != null && !endTime.isEmpty()) {
            wrapper.le(CollectionLog::getCreatedAt, LocalDateTime.parse(endTime));
        }

        wrapper.orderByDesc(CollectionLog::getCreatedAt);
        Page<CollectionLog> result = logMapper.selectPage(pageInfo, wrapper);

        return Result.success(result);
    }

    /**
     * 获取最近的日志
     */
    public List<CollectionLog> getRecentLogs(int limit) {
        LambdaQueryWrapper<CollectionLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(CollectionLog::getCreatedAt);
        wrapper.last("LIMIT " + limit);
        return logMapper.selectList(wrapper);
    }

    /**
     * 获取日志统计
     */
    public Result<java.util.Map<String, Long>> getLogStats() {
        java.util.Map<String, Long> stats = new java.util.HashMap<>();

        LambdaQueryWrapper<CollectionLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(CollectionLog::getCreatedAt, LocalDateTime.now().withHour(0).withMinute(0).withSecond(0));

        stats.put("total", logMapper.selectCount(wrapper));
        stats.put("error", logMapper.selectCount(new LambdaQueryWrapper<CollectionLog>()
                .eq(CollectionLog::getLevel, "ERROR")
                .ge(CollectionLog::getCreatedAt, LocalDateTime.now().withHour(0).withMinute(0).withSecond(0))));
        stats.put("warn", logMapper.selectCount(new LambdaQueryWrapper<CollectionLog>()
                .eq(CollectionLog::getLevel, "WARN")
                .ge(CollectionLog::getCreatedAt, LocalDateTime.now().withHour(0).withMinute(0).withSecond(0))));
        stats.put("info", logMapper.selectCount(new LambdaQueryWrapper<CollectionLog>()
                .eq(CollectionLog::getLevel, "INFO")
                .ge(CollectionLog::getCreatedAt, LocalDateTime.now().withHour(0).withMinute(0).withSecond(0))));

        return Result.success(stats);
    }
}
