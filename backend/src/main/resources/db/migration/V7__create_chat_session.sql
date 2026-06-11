-- V7__create_chat_session.sql
-- AI 问答会话管理表
CREATE TABLE IF NOT EXISTS t_chat_session (
  id VARCHAR(36) NOT NULL PRIMARY KEY COMMENT '会话ID (UUID)',
  user_id VARCHAR(64) NOT NULL COMMENT '用户ID，关联系统用户',
  title VARCHAR(255) NOT NULL DEFAULT '' COMMENT '会话标题',
  title_source ENUM('default','auto','manual') NOT NULL DEFAULT 'default' COMMENT '标题来源：默认/智能生成/手动编辑',
  message_count INT NOT NULL DEFAULT 0 COMMENT '会话消息总数',
  last_message TEXT COMMENT '最后一条消息摘要',
  is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除标记：0-正常 1-已删除',
  is_archived TINYINT(1) NOT NULL DEFAULT 0 COMMENT '归档标记：0-未归档 1-已归档（Phase2 启用）',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  INDEX idx_user_deleted_time (user_id, is_deleted, updated_at DESC),
  INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI问答会话管理表';
