package com.scfx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 报告实体
 */
@Data
@TableName("t_report")
public class Report {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String title;

    private LocalDateTime publishTime;

    private String source;

    private String author;

    private String editor;

    private String reportType;

    private String variety;

    private String originalUrl;

    private String contentHtmlPath;

    private String contentTextPath;

    private String content;

    private String vectorId;

    private String executionId;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
