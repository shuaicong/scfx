package com.scfx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 任务执行记录
 */
@Data
@TableName("t_task_execution")
public class TaskExecution {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 执行唯一ID */
    private String executionId;

    /** 脚本ID */
    private Long scriptId;

    /** 版本ID */
    private Long versionId;

    /** 触发方式：manual/scheduled/api */
    private String triggerType;

    /** 状态：pending/running/success/failed/cancelled */
    private String status;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 执行时长（毫秒） */
    private Long durationMs;

    /** 错误信息 */
    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 采集数量 */
    private Integer collectedCount;
}