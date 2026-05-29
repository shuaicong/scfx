package com.scfx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 告警记录实体
 */
@Data
@TableName("t_alert_record")
public class AlertRecord {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String alertType;

    private String alertLevel;

    private String alertTitle;

    private String alertContent;

    private String targetId;

    private String status;

    private String notifiedChannels;

    private String resolvedBy;

    private LocalDateTime resolvedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
