-- ============================================================
-- ARCHIVED: migration_002_table_meta.sql
-- 归档原因: 被 Flyway V5 baseline 覆盖，纳入基线管理
-- 归档日期: 2026-06-11
-- ============================================================

-- 知识库表新增 table_meta 字段，存储结构化表格数据 JSON 数组
-- 格式: [{"headers": ["省区","价格"], "rows": [["黑龙江","2150"],...], "caption": "..."}]
ALTER TABLE t_knowledge_base
    ADD COLUMN table_meta TEXT COMMENT '结构化表格数据 JSON 数组 [{headers, rows, caption}]'
    AFTER content_html;
