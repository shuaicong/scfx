package com.scfx.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_chat_session")
public class ChatSession {
    private String id;
    private String userId;
    private String title;
    private String titleSource;  // default | auto | manual
    private Integer messageCount;
    private String lastMessage;
    private Integer isDeleted;   // 0-正常 1-已删除
    private Integer isArchived;  // 0-未归档 1-已归档
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
