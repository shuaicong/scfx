package com.scfx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 任务执行记录
 */
@Data
@TableName("t_task_execution")
public class TaskExecution {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 执行唯一ID */
    private String executionId;

    /** 脚本ID */
    private Long scriptId;

    /** 版本ID */
    private Long versionId;

    /** 触发方式：manual/scheduled/api */
    private String triggerType;

    /** 状态：pending/running/success/failed/cancelled */
    private String status;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 执行时长（毫秒） */
    private Long durationMs;

    /** 错误信息 */
    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 采集数量（兼容旧字段） */
    private Integer collectedCount;

    // ========== 执行统计 ==========

    /** 总处理数 */
    private Integer totalCount;

    /** 成功数 */
    private Integer successCount;

    /** 去重跳过数 */
    private Integer skipCount;

    /** 失败数 */
    private Integer errorCount;

    /** 数据量（MB） */
    private java.math.BigDecimal dataSizeMb;

    // ========== 阶段耗时（毫秒） ==========

    /** 登录阶段耗时 */
    private Long phaseLoginMs;

    /** 抓取阶段耗时 */
    private Long phaseCrawlMs;

    /** 解析阶段耗时 */
    private Long phaseParseMs;

    /** 上报阶段耗时 */
    private Long phaseReportMs;

    /** 执行详情 JSON */
    private String detailJson;

    /** 版本号（非数据库字段，由服务层查询时填充） */
    @TableField(exist = false)
    private Integer versionNum;
}
