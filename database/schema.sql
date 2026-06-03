-- 海南储备集团粮食市场智能分析平台 - 数据库初始化脚本

CREATE DATABASE IF NOT EXISTS grain_platform DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE grain_platform;

-- 采集任务表
CREATE TABLE IF NOT EXISTS t_collection_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_name VARCHAR(100) NOT NULL COMMENT '任务名称',
    task_type VARCHAR(20) NOT NULL COMMENT '任务类型',
    source_name VARCHAR(50) NOT NULL COMMENT '数据源名称',
    source_url VARCHAR(500),
    collect_config JSON COMMENT '采集配置',
    schedule_config JSON COMMENT '调度配置',
    status VARCHAR(20) DEFAULT 'pending' COMMENT '状态',
    last_execution_time DATETIME,
    next_execution_time DATETIME,
    success_count INT DEFAULT 0,
    failed_count INT DEFAULT 0,
    retry_count INT DEFAULT 0,
    max_retry_times INT DEFAULT 3,
    timeout_seconds INT DEFAULT 300,
    priority TINYINT DEFAULT 1,
    created_by VARCHAR(50),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_next_time (next_execution_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 任务执行记录表
CREATE TABLE IF NOT EXISTS t_task_execution (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    execution_id VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME,
    duration_seconds INT,
    error_message TEXT,
    collected_count INT DEFAULT 0,
    data_size_mb DECIMAL(10,2) DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_task_id (task_id),
    INDEX idx_start_time (start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 报告表
CREATE TABLE IF NOT EXISTS t_report (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(500) NOT NULL,
    publish_time DATETIME,
    source VARCHAR(100) DEFAULT '粮信网',
    author VARCHAR(100),
    editor VARCHAR(100),
    report_type VARCHAR(50),
    variety VARCHAR(50),
    original_url VARCHAR(1000),
    content_html_path VARCHAR(500),
    content_text_path VARCHAR(500),
    content LONGTEXT,
    vector_id VARCHAR(100),
    deleted TINYINT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_publish_time (publish_time),
    INDEX idx_variety (variety),
    UNIQUE KEY uk_url (original_url(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 价格数据表
CREATE TABLE IF NOT EXISTS t_price (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    report_id BIGINT,
    date DATE NOT NULL,
    variety VARCHAR(50) NOT NULL,
    region VARCHAR(50),
    contract VARCHAR(50),
    price DECIMAL(12,2),
    `change` DECIMAL(12,2),
    unit VARCHAR(50),
    source VARCHAR(100),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_date (date),
    INDEX idx_variety (variety)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 采集日志表
CREATE TABLE IF NOT EXISTS t_collection_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT,
    task_name VARCHAR(100),
    level VARCHAR(20) NOT NULL,
    message TEXT NOT NULL,
    source VARCHAR(50),
    execution_id VARCHAR(50),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_level (level),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 告警记录表
CREATE TABLE IF NOT EXISTS t_alert_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    alert_type VARCHAR(50) NOT NULL,
    alert_level VARCHAR(20) NOT NULL,
    alert_title VARCHAR(200) NOT NULL,
    alert_content MEDIUMTEXT NOT NULL,
    target_id VARCHAR(50),
    status VARCHAR(20) DEFAULT 'pending',
    notified_channels JSON,
    resolved_by VARCHAR(50),
    resolved_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_created_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 知识库表
CREATE TABLE IF NOT EXISTS t_knowledge_base (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(500) NOT NULL,
    source_type VARCHAR(20) NOT NULL COMMENT 'collection/upload/manual',
    source_name VARCHAR(100),
    original_url VARCHAR(1000),
    author VARCHAR(100),
    publish_time DATETIME,
    content MEDIUMTEXT NOT NULL,
    content_html MEDIUMTEXT COMMENT 'HTML格式内容（保留图片标签等）',
    table_meta TEXT COMMENT '结构化表格数据 JSON 数组',
    content_hash VARCHAR(64),
    file_path VARCHAR(500),
    file_type VARCHAR(20),
    chunk_count INT DEFAULT 0,
    vector_status VARCHAR(20) DEFAULT 'pending' COMMENT 'pending/processing/completed/failed',
    vector_ids VARCHAR(500),
    retrieval_vector BLOB DEFAULT NULL COMMENT 'BGE-M3 检索向量（serialized float[]，非切片文档用）',
    execution_id VARCHAR(50),
    category_id BIGINT DEFAULT NULL COMMENT '所属分类ID',
    collection_source VARCHAR(50) DEFAULT NULL COMMENT '采集来源',
    collection_variety VARCHAR(50) DEFAULT NULL COMMENT '采集品种',
    collection_report_type VARCHAR(50) DEFAULT NULL COMMENT '报告类型',
    viz_x DOUBLE DEFAULT NULL COMMENT 'PCA降维X坐标（可视化用）',
    viz_y DOUBLE DEFAULT NULL COMMENT 'PCA降维Y坐标（可视化用）',
    viz_z DOUBLE DEFAULT NULL COMMENT 'PCA降维Z坐标（3D预留）',
    created_by VARCHAR(50),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    INDEX idx_source_type (source_type),
    INDEX idx_source_name (source_name),
    INDEX idx_vector_status (vector_status),
    INDEX idx_content_hash (content_hash),
    INDEX idx_publish_time (publish_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库表';

-- 初始化粮信网采集任务
INSERT INTO t_collection_task (task_name, task_type, source_name, source_url, status) VALUES
('粮信网-玉米晨报采集', 'scheduled', 'liangxinwang', 'https://www.chinagrain.cn/report/', 'pending');
