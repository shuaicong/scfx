package com.scfx.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.scfx.handler.FloatArrayTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_knowledge_viz")
public class KnowledgeViz {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long knowledgeId;

    @TableField(typeHandler = FloatArrayTypeHandler.class)
    private float[] vector768;

    /** 可视化状态：normal / failed / pending */
    private String vizStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
