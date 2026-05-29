package com.scfx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_dr_version")
public class DrVersion {

    @TableId
    private Long categoryId;

    private String algorithm;

    private Integer currentVersion;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
