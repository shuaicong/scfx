package com.scfx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

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
    private String contentHash;
    private String filePath;
    private String fileType;
    private Integer chunkCount;
    private String vectorStatus;
    private String vectorIds;
    private String executionId;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}