package com.scfx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 研报实体
 */
@Data
@TableName("t_report")
public class Report {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String title;

    private String variety;

    private String reportType;

    private String status;

    private Long templateId;

    private Integer currentVersion;

    private String generationStatus;

    private String richContent;

    private String author;

    private LocalDateTime publishTime;

    private String exportDocxPath;

    private String exportPdfPath;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
