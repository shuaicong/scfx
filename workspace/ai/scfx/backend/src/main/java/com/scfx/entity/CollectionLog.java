package com.scfx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 采集日志实体
 */
@Data
@TableName("t_collection_log")
public class CollectionLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private String taskName;

    private String level;

    private String message;

    private String source;

    private String subject;

    private String collType;

    private String collObject;

    private String executionId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
