package com.scfx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_batch_operation_log")
public class BatchOperationLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String operationType;      // move/delete
    private Long targetCategoryId;     // 目标分类
    private Long sourceCategoryId;     // 来源分类（移动时）
    private String knowledgeIds;        // JSON数组：[1,2,3]
    private String operator;            // 操作人
    private String undoneOperator;       // 撤销操作人
    private LocalDateTime undoneAt;    // 撤销时间

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}