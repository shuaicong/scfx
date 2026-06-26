-- V11__add_collect_target.sql
-- 数据源表新增采集目标类型字段

ALTER TABLE `t_data_source`
ADD COLUMN `collect_target` varchar(20) DEFAULT 'knowledge_base'
COMMENT '采集目标: database=数据库采集, knowledge_base=知识库采集（默认）'
AFTER `config`;
