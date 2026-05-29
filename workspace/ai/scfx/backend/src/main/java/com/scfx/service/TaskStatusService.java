package com.scfx.service;

import lombok.Data;
import java.time.LocalDateTime;

public interface TaskStatusService {
    /**
     * 批量查询任务状态
     * @param scriptIds 脚本ID列表
     * @return Map<scriptId, TaskStatus>
     */
    java.util.Map<Long, TaskStatus> getTasksStatus(java.util.List<Long> scriptIds);

    /**
     * 获取单个任务状态
     * @param scriptId 脚本ID
     * @return TaskStatus
     */
    TaskStatus getTaskStatus(Long scriptId);

    /**
     * 停止执行中的任务
     * @param executionId 执行ID
     */
    void stopExecution(String executionId);

    @Data
    class TaskStatus {
        private Long scriptId;
        private String status;           // pending/running/success/failed/cancelled
        private Integer collectedCount;   // 实时采集数量
        private String executionId;       // 当前执行ID
        private LocalDateTime startTime;  // 开始时间
        private LocalDateTime lastExecuted; // 最后执行时间
        private Integer lastCollectedCount; // 上次采集数量
    }
}