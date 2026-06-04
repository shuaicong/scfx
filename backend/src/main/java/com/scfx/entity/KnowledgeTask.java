package com.scfx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识处理任务 — 跟踪每条知识的解析→切片→向量化流程
 */
@Data
@TableName("t_knowledge_task")
public class KnowledgeTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long knowledgeId;

    private Long categoryId;

    /** pending → parsing → chunking → vectorizing → completed / failed / cancelled */
    private String status;

    /** 当前步骤 parsing / chunking / vectorizing */
    private String currentStep;

    /** 整体进度 0-100 */
    private Integer progress;

    /** 失败原因 */
    private String errorMessage;

    /** 错误分类 */
    private String errorCategory;

    /** 切片总数（chunking 后回填） */
    private Integer totalChunks;

    /** 已向量化切片数 */
    private Integer processedChunks;

    /** 重试次数 */
    private Integer retryCount;

    /** 源文件大小（字节） */
    private Long fileSize;

    /** pdf / docx / txt / md */
    private String fileType;

    /** 幂等键 */
    private String idempotentKey;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
