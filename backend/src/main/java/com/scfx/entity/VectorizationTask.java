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
    private String status;            // pending/processing/completed
    private Integer totalCount;
    private Integer processedCount;
    private Integer failedCount;
    private String triggerType;        // auto/cron/manual
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}