package com.scfx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_knowledge_dr_coords")
public class DrCoord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long knowledgeId;

    private Long categoryId;

    private String algorithm;

    private Integer version;

    private Double x;

    private Double y;

    private Double z;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
