package com.scfx.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.scfx.handler.FloatArrayTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_pca_baseline")
public class PCABaseline {

    @TableId
    private Long categoryId;

    /** 已标记的最大 t_knowledge_viz.id，超过此值的视为新向量 */
    private Long lastVizId;

    /** 参与构建基线的向量数 */
    private Integer vectorCount;

    /** 均值向量（768维） */
    @TableField(typeHandler = FloatArrayTypeHandler.class)
    private float[] meanVector;

    /** 第一主成分（768维） */
    @TableField(typeHandler = FloatArrayTypeHandler.class)
    private float[] pc1;

    /** 第二主成分（768维） */
    @TableField(typeHandler = FloatArrayTypeHandler.class)
    private float[] pc2;

    /** 第三主成分（768维，3D预留） */
    @TableField(typeHandler = FloatArrayTypeHandler.class)
    private float[] pc3;

    /** 归一化边界 */
    private Double xMin;
    private Double xMax;
    private Double yMin;
    private Double yMax;
    private Double zMin;        // 3D 预留：Z 下界
    private Double zMax;        // 3D 预留：Z 上界

    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
