package com.scfx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 研报模板实体
 */
@Data
@TableName("t_report_template")
public class ReportTemplate {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String name;

    private String variety;

    private String reportType;

    private Integer currentVersion;

    private String description;

    private String generationConfig;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
