package com.scfx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * AI 问答会话实体
 */
@Data
@TableName("t_chat_session")
public class ChatSession {
    @TableId
    private String id;

    private String userId;

    private String title;

    private String titleSource;  // default | auto | manual

    private Integer messageCount;

    private String lastMessage;

    private Integer isDeleted;   // 0-正常 1-已删除

    private Integer isArchived;  // 0-未归档 1-已归档

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
