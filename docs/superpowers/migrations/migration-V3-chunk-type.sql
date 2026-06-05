-- 知识切片表新增 chunk_type 字段（V3）
-- 执行方式：手动执行，幂等（IF NOT EXISTS 风格）
-- CREATE TABLE IF NOT EXISTS 迁移方案：ALTER TABLE 通过检查列是否存在来保证幂等

SET @db = DATABASE();

-- 检查列是否已存在，不存在则添加
SELECT COUNT(*) INTO @col_exists
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @db
  AND TABLE_NAME = 't_knowledge_chunk'
  AND COLUMN_NAME = 'chunk_type';

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE t_knowledge_chunk ADD COLUMN chunk_type VARCHAR(20) DEFAULT "text" COMMENT "切片类型: text/table" AFTER content_terms',
    'SELECT "chunk_type already exists" AS status');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
