-- V8__add_reasoning_content.sql
-- AI 问答深度思考（CoT）推理过程存储
ALTER TABLE t_chat_history
ADD COLUMN reasoning_content LONGTEXT DEFAULT NULL COMMENT '深度思考推理过程（CoT）'
AFTER content;
