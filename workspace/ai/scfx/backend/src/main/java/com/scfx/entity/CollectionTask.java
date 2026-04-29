package com.scfx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 采集任务实体
 */
@Data
@TableName("t_collection_task")
public class CollectionTask {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String taskName;

    private String taskType;

    private String sourceName;

    private String sourceUrl;

    private String collectConfig;

    private String scheduleConfig;

    private String status;

    private LocalDateTime lastExecutionTime;

    private LocalDateTime nextExecutionTime;

    private Integer successCount;

    private Integer failedCount;

    private Integer retryCount;

    private Integer maxRetryTimes;

    private Integer timeoutSeconds;

    private Integer priority;

    private String createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
