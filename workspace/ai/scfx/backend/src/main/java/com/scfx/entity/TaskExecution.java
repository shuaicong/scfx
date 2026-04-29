package com.scfx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 任务执行记录实体
 */
@Data
@TableName("t_task_execution")
public class TaskExecution {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private String executionId;

    private String status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer durationSeconds;

    private String errorMessage;

    private Integer collectedCount;

    private String dataSizeMb;

    private String cpuUsage;

    private String memoryUsage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
