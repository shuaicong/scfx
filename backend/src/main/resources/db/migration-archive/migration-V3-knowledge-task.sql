-- ============================================================
-- ARCHIVED: migration-V3-knowledge-task.sql
-- 归档原因: 被 Flyway V5 baseline 覆盖，建表已合并到 schema.sql
-- 归档日期: 2026-06-11
-- ============================================================

-- V3: 知识处理任务表
-- 用于跟踪每个知识的解析→切片→向量化流程

CREATE TABLE IF NOT EXISTS t_knowledge_task (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    knowledge_id      BIGINT NOT NULL COMMENT 'FK → t_knowledge_base.id',
    category_id       BIGINT COMMENT '分类ID',
    status            VARCHAR(20) NOT NULL DEFAULT 'pending'
                      COMMENT 'pending → parsing → chunking → vectorizing → completed / failed / cancelled',
    current_step      VARCHAR(20) COMMENT '当前步骤 parsing / chunking / vectorizing',
    progress          INT DEFAULT 0 COMMENT '整体进度 0-100',
    error_message     TEXT COMMENT '失败原因',
    error_category    VARCHAR(30) COMMENT '错误分类：FILE_CORRUPTED / PARSE_FAILED / API_TIMEOUT / QUOTA_EXCEEDED / CONTENT_INVALID / UNKNOWN',
    total_chunks      INT DEFAULT 0 COMMENT '切片总数（chunking后回填）',
    processed_chunks  INT DEFAULT 0 COMMENT '已向量化切片数',
    retry_count       INT DEFAULT 0 COMMENT '重试次数',
    file_size         BIGINT DEFAULT 0 COMMENT '源文件大小（字节）',
    file_type         VARCHAR(20) COMMENT 'pdf / docx / txt / md',
    idempotent_key    VARCHAR(64) COMMENT '幂等键',
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_knowledge_id (knowledge_id),
    INDEX idx_category_status (category_id, status, created_at),
    INDEX idx_idempotent_key (idempotent_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识处理任务表';
