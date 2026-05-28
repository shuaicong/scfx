package com.scfx.service.impl;

import com.scfx.entity.TaskExecution;
import com.scfx.mapper.TaskExecutionMapper;
import com.scfx.service.TaskExecutionService;
import com.scfx.service.TaskStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskStatusServiceImpl implements TaskStatusService {
    private final TaskExecutionMapper executionMapper;
    private final TaskExecutionService executionService;

    @Override
    public Map<Long, TaskStatus> getTasksStatus(List<Long> scriptIds) {
        if (scriptIds == null || scriptIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, TaskStatus> result = new HashMap<>();

        for (Long scriptId : scriptIds) {
            TaskStatus status = getTaskStatus(scriptId);
            result.put(scriptId, status);
        }

        return result;
    }

    @Override
    public TaskStatus getTaskStatus(Long scriptId) {
        TaskStatus status = new TaskStatus();
        status.setScriptId(scriptId);

        // 查询正在执行的记录
        TaskExecution running = executionMapper.findRunningByScriptId(scriptId);
        if (running != null) {
            status.setStatus("running");
            status.setExecutionId(running.getExecutionId());
            status.setStartTime(running.getStartTime());
            status.setCollectedCount(running.getCollectedCount() != null ? running.getCollectedCount() : 0);
        } else {
            // 查询最近一次执行记录
            TaskExecution last = executionMapper.findLastByScriptId(scriptId);
            if (last != null) {
                status.setStatus(last.getStatus());
                status.setLastExecuted(last.getEndTime());
                status.setLastCollectedCount(last.getCollectedCount() != null ? last.getCollectedCount() : 0);
            } else {
                status.setStatus("pending");
            }
        }

        return status;
    }

    @Override
    public void stopExecution(String executionId) {
        executionService.updateStatus(executionId, "cancelled", null);

        // 通知 Python 停止（写标记文件）
        String stopFile = "/tmp/stop_" + executionId;
        try {
            java.nio.file.Files.write(
                java.nio.file.Paths.get(stopFile),
                "stop".getBytes()
            );
        } catch (Exception e) {
            // 忽略，Python 会定期检查
        }
    }
}