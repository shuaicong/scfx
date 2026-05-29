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

    /** 所属阶段：login/crawl/parse/report/system */
    private String phase;

    /** 日志分类：progress/data/error/metric/checkpoint */
    private String category;

    /** 相对执行开始的毫秒偏移 */
    private Long elapsedMs;

    /** 可选的结构化数据 JSON */
    private String dataJson;

    /** 时间戳 */
    private LocalDateTime timestamp;
}
