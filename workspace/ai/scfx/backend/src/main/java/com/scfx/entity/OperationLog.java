package com.scfx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Operation log entity for storing operation audit records
 */
@Data
@TableName("t_operation_log")
public class OperationLog {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String operator;         // Operator
    private String operationType;     // Operation type
    private String targetType;       // Target type
    private Long targetId;           // Target ID
    private String detail;          // Operation detail (JSON)
    private String ip;              // IP address

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime operateTime;
}