package com.scfx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 任务执行日志
 */
@Data
@TableName("t_task_execution_log")
public class TaskExecutionLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 执行ID */
    private String executionId;

    /** 脚本ID */
    private Long scriptId;

    /** 日志级别：DEBUG/INFO/WARN/ERROR */
    private String level;

    /** 日志消息 */
    private String message;

    /** 时间戳 */
    private LocalDateTime timestamp;
}