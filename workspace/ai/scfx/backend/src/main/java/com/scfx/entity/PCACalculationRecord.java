package com.scfx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * PCA 计算记录（版本快照）
 * 记录每次 PCA 重算的元数据，用于回溯排查聚类异常
 */
@Data
@TableName("t_pca_calculation_record")
public class PCACalculationRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long categoryId;

    /** 版本号（同一分类递增） */
    private Integer version;

    /** 触发类型：manual_full / incremental / manual_single / auto_incremental */
    private String triggerType;

    /** 参与本次计算的向量数 */
    private Integer pointCount;

    /** 计算前向量数 */
    private Integer beforeCount;

    /** 计算耗时(ms) */
    private Long computationCostMs;

    /** 备注 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
