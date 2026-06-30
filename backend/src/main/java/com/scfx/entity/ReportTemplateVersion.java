package com.scfx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 研报模板版本实体
 */
@Data
@TableName("t_report_template_version")
public class ReportTemplateVersion {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long templateId;

    private Integer versionNumber;

    private String name;

    private String richContent;

    private String editorJson;

    private String editor;

    private String changeSummary;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
