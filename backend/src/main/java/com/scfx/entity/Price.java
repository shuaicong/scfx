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

    /** 省份/大区分组（北港/黑龙江/美湾/东北等） */
    private String province;

    /** region 分类: port/enterprise/region/shipping/origin */
    private String areaType;

    private String contract;

    private BigDecimal price;

    /** 涨跌值 */
    private BigDecimal changeVal;

    /** 备注（粮质等级/蛋白含量/关税信息/品种说明） */
    private String remark;

    private String unit;

    private String source;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
