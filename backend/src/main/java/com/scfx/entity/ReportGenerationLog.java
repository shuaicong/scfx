package com.scfx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 研报生成日志实体
 */
@Data
@TableName("t_report_generation_log")
public class ReportGenerationLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long reportId;

    private String executionId;

    private String status;

    private String step;

    private String message;

    private Integer durationMs;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
