package com.scfx.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_vectorization_log")
public class VectorizationLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long knowledgeId;
    private Long categoryId;
    private String status;            // pending/processing/success/failed
    private Integer retryCount;
    private String errorMessage;
    private String vectorId;

    // 扩展字段
    private Integer processTimeMs;    // 处理耗时(毫秒)
    private String modelVersion;      // 向量化模型版本
    private String contentHash;       // 内容MD5
    private Integer contentLength;    // 内容长度

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}