-- ============================================================
-- ARCHIVED: migration-V2-chunk-fields.sql
-- 归档原因: 被 Flyway V5 baseline 覆盖，列变更已合并到 schema.sql
-- 归档日期: 2026-06-11
-- ============================================================

-- 知识切片表新增字段（V2）
-- 执行方式：后端 @PostConstruct 自动执行，幂等（IF NOT EXISTS 风格）

ALTER TABLE t_knowledge_chunk
  ADD COLUMN chunk_total   INT DEFAULT 0     COMMENT '所属文档总切片数' AFTER chunk_index,
  ADD COLUMN start_offset  INT DEFAULT 0     COMMENT '在原文中的起始字符偏移' AFTER content,
  ADD COLUMN end_offset    INT DEFAULT 0     COMMENT '在原文中的结束字符偏移' AFTER start_offset,
  ADD COLUMN is_summary    TINYINT DEFAULT 0 COMMENT '1=首切片（代表全文语义）, 0=普通切片' AFTER end_offset;
