package com.scfx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 采集器注册信息
 * 用于管理 Python SDK 的注册和状态
 */
@Data
@TableName("t_collector_info")
public class CollectorInfo {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 采集器名称 */
    private String collectorName;

    /** SDK版本 */
    private String sdkVersion;

    /** 采集来源（如 liangxin, mysteel） */
    private String source;

    /** 采集主体（如 corn, wheat） */
    private String subject;

    /** 采集类型（如 login_crawl, public_crawl） */
    private String collType;

    /** 采集对象（如 daily_report, price） */
    private String collObject;

    /** 采集器描述 */
    private String description;

    /** 状态（online/offline） */
    private String status;

    /** 最后心跳时间 */
    private LocalDateTime lastHeartbeat;

    /** 注册时间 */
    private LocalDateTime registeredAt;

    /** 实例数量 */
    private Integer instanceCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
