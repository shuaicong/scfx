-- ============================================================
-- V6: Rename `version` → `current_version` in t_dr_version
-- ============================================================
-- 原因：Java DrVersion 实体字段为 currentVersion，MyBatis-Plus
-- 映射为 current_version，但建表时列名为 version。导致
-- DrVersionMapper.selectCurrentVersion() 查询时报:
--   Unknown column 'current_version' in 'field list'
-- ============================================================

ALTER TABLE t_dr_version
  CHANGE COLUMN version current_version int NOT NULL COMMENT '当前降维版本号';
