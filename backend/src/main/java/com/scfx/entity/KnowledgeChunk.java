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
    private Integer chunkTotal;        // 所属文档总切片数
    private String content;
    private Integer startOffset;       // 在原文中的起始字符偏移
    private Integer endOffset;         // 在原文中的结束字符偏移
    private Integer isSummary;         // 1=首切片（代表全文语义）, 0=普通切片
    private Integer tokenCount;
    @TableField(typeHandler = com.scfx.handler.FloatArrayTypeHandler.class)
    private float[] vectorBgeM3;       // BGE-M3 768维检索向量
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
