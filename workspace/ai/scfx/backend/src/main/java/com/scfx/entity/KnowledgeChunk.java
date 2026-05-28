package com.scfx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_knowledge_chunk")
public class KnowledgeChunk {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long knowledgeId;
    private Long categoryId;
    private Integer chunkIndex;
    private String content;
    private Integer tokenCount;
    private String vectorStatus;       // pending / processing / vectorized / failed
    private String vectorId;
    private String errorMessage;
    private Integer isActive;          // 1=正常, 0=已删除
    private String contentTerms;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
