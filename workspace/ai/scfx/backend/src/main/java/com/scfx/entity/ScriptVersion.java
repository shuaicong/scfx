package com.scfx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 脚本版本历史
 */
@Data
@TableName("t_script_version")
public class ScriptVersion {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 脚本ID */
    private Long scriptId;

    /** 版本号 */
    private Integer versionNum;

    /** 脚本名称 */
    private String scriptName;

    /** 脚本内容快照 */
    private String scriptContent;

    /** 触发类型：once/repeat/cron */
    private String triggerType;

    /** 重复类型：daily/weekly/monthly */
    private String repeatType;

    /** 重复时间 */
    private String repeatTime;

    /** 每周执行日 */
    private String weeklyDays;

    /** 每月执行日 */
    private Integer monthlyDay;

    /** 是否每月最后一天 */
    private Boolean monthlyLastDay;

    /** Cron表达式 */
    private String cronExpression;

    /** 结束类型 */
    private String endType;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 重复次数 */
    private Integer repeatCount;

    /** 修改说明 */
    private String changeDescription;

    /** 操作人 */
    private String createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 是否当前版本 */
    private Boolean isCurrent;
}