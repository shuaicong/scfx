package com.scfx.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_vectorization_task")
public class VectorizationTask {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long categoryId;
    private Integer batchSize;
    private String status;            // pending/processing/completed
    private Integer totalCount;
    private Integer processedCount;
    private Integer failedCount;

    // 扩展字段
    private Integer priority;         // 优先级 0-10
    private String triggerType;        // auto/cron/manual
    private String triggerSource;      // 触发来源详情
    private String failedSample;      // 失败样本(JSON)

    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}