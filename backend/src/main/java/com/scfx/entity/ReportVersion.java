package com.scfx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 研报版本实体
 */
@Data
@TableName("t_report_version")
public class ReportVersion {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long reportId;

    private Integer versionNumber;

    private String title;

    private String richContent;

    private String editorJson;

    private String editor;

    private String changeSummary;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
