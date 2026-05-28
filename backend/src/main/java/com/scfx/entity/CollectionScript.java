package com.scfx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 采集脚本实体
 */
@Data
@TableName("t_collection_script")
public class CollectionScript {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 脚本名称 */
    private String scriptName;

    /** 脚本描述 */
    private String description;

    /** 脚本文件路径 */
    private String scriptPath;

    /** 数据源标识 */
    private String source;

    /** 采集主体 */
    private String subject;

    /** 采集类型 */
    private String collType;

    /** 采集对象 */
    private String collObject;

    /** 脚本状态：enabled/disabled */
    private String status;

    /** 上报频率（秒） */
    private Integer reportIntervalSeconds;

    /** 触发类型：manual/single/cron/repeat */
    private String triggerType;

    /** 触发配置（JSON格式） */
    private String triggerConfig;

    /** Cron表达式 */
    private String cronExpression;

    /** 重复周期类型：daily/weekly/monthly */
    private String repeatType;

    /** 重复周期设置（JSON格式） */
    private String repeatConfig;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 最后执行时间 */
    private LocalDateTime lastExecutionTime;

    /** 下次执行时间 */
    private LocalDateTime nextExecutionTime;

    /** 执行次数 */
    private Integer executionCount;

    /** 成功次数 */
    private Integer successCount;

    /** 失败次数 */
    private Integer failedCount;

    /** 创建者 */
    private String createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 重复时间 HH:mm:ss */
    private String repeatTime;

    /** 每周执行日，多个用逗号分隔如 "1,3,5" */
    private String weeklyDays;

    /** 每月执行日 */
    private Integer monthlyDay;

    /** 是否每月最后一天 */
    private Boolean monthlyLastDay;

    /** 结束类型：never/date/count */
    private String endType;

    /** 重复次数（end_type=count时有效） */
    private Integer repeatCount;

    /** 版本号 */
    private Integer currentVersion;

    /** 是否同步到知识库 */
    private Boolean syncToKnowledgeBase;

    /** 关联分类ID（同步到知识库时使用） */
    private Long categoryId;

    /** 上次执行状态（非DB字段，查询时填充） */
    @TableField(exist = false)
    private String lastExecutionStatus;
}
