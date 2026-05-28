package com.scfx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 告警规则实体
 */
@Data
@TableName("t_alert_rule")
public class AlertRule {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 规则名称 */
    private String ruleName;

    /** 规则类型：CONTINUOUS_FAIL/SERVICE_OFFLINE/ZERO_RESULT/TIMEOUT */
    private String ruleType;

    /** 条件配置（JSON）：{ threshold: 3 } */
    private String condition;

    /** 是否启用：1=启用, 0=禁用 */
    private Integer enabled;

    /** 通知渠道（JSON）：["dingtalk", "email"] */
    private String notifyChannels;

    /** 通知目标 */
    private String notifyTarget;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}