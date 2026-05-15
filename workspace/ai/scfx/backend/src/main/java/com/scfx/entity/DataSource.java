package com.scfx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_data_source")
public class DataSource {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String code;                    // 数据源编码: liangxin, mysteel, chinagrain

    private String name;                    // 显示名称: 粮信网, 我的钢铁网

    private String description;            // 描述信息

    private String logoUrl;                // logo URL

    private Integer enabled;               // 启用状态: 1=启用, 0=禁用

    private Integer sortOrder;             // 排序

    private String config;                 // 配置信息（JSON格式）

    private LocalDateTime lastHeartbeat;   // 最后心跳时间

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}