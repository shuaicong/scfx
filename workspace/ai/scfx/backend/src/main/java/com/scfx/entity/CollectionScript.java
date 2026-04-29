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

    /** 脚本内容（Python代码） */
    private String scriptContent;

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
}
