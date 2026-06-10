-- V5__create_chat_history.sql
-- AI 问答对话历史表
CREATE TABLE IF NOT EXISTS t_chat_history (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  user_id VARCHAR(64) NOT NULL COMMENT '用户ID',
  request_id VARCHAR(36) NOT NULL COMMENT '请求链路追踪ID',
  session_id VARCHAR(36) NOT NULL COMMENT '会话ID',
  client_msg_id VARCHAR(36) NOT NULL COMMENT '前端消息唯一ID（幂等键）',
  role VARCHAR(10) NOT NULL COMMENT '角色：user/assistant',
  content TEXT NOT NULL COMMENT '对话内容',
  knowledge_ids JSON COMMENT '关联检索知识库ID',
  message_id INT NOT NULL COMMENT '消息全局序号',
  group_id INT NOT NULL COMMENT '问答组ID',
  seq TINYINT NOT NULL DEFAULT 0 COMMENT '组内序号：0-user 1-assistant',
  session_status TINYINT DEFAULT 1 COMMENT '会话状态: 1-正常 0-已结束',
  is_deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX idx_user_session (user_id, session_id),
  INDEX idx_session_time (session_id, created_at),
  INDEX idx_session_msg_id (session_id, message_id) COMMENT '重建历史专用',
  UNIQUE KEY uk_session_msg (session_id, client_msg_id) COMMENT '幂等约束'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话历史';
