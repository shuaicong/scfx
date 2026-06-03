package com.scfx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 知识库实体 */
@Data
@TableName("t_knowledge_base")
public class KnowledgeBase {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String sourceType;
    private String sourceName;
    private String originalUrl;
    private String author;
    private LocalDateTime publishTime;
    private String content;
    private String contentHtml;              // HTML格式内容（保留图片标签等）
    private String tableMeta;                // 结构化表格数据 JSON
    private String contentHash;
    private String filePath;
    private String fileType;
    private Integer chunkCount;
    private String vectorStatus;
    private String vectorIds;
    @TableField(typeHandler = com.scfx.handler.FloatArrayTypeHandler.class)
    private float[] retrievalVector;       // BGE-M3 检索向量（非切片文档用）
    private String executionId;
    private String createdBy;

    // 可视化坐标（PCA 降维后）
    private Double vizX;
    private Double vizY;
    private Double vizZ;        // 3D 预留：第三主成分 Z 坐标

    // 扩展字段
    private Long categoryId;                 // 所属分类ID
    private String collectionSource;         // 采集来源: liangxinwang
    private String collectionVariety;         // 采集品种: corn/wheat
    private String collectionReportType;      // 报告类型: 日报/周报

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}