package com.scfx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 价格数据实体
 */
@Data
@TableName("t_price")
public class Price {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long reportId;

    private LocalDate date;

    private String variety;

    private String region;

    private String contract;

    private BigDecimal price;

    private BigDecimal change;

    private String unit;

    private String source;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
