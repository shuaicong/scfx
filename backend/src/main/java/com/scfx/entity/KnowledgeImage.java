package com.scfx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_knowledge_image")
public class KnowledgeImage {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long knowledgeId;

    private String sourceUrl;

    private String minioPath;

    private String minioBucket;

    private Integer fileSize;

    private String fileType;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
