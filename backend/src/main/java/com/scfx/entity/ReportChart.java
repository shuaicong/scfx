package com.scfx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 研报图表实体
 */
@Data
@TableName("t_report_chart")
public class ReportChart {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long reportId;

    private Long versionId;

    private String chartType;

    private String variety;

    private String region;

    private LocalDate dateStart;

    private LocalDate dateEnd;

    private String minioPath;

    private String queryParams;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
