-- 知识库增强迁移脚本
USE grain_platform;

-- 分类映射规则表
CREATE TABLE IF NOT EXISTS t_category_mapping (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    source_type VARCHAR(50) NOT NULL COMMENT '来源类型: liangxinwang/mysteel',
    variety VARCHAR(50) COMMENT '品种: corn/wheat/rice',
    report_type VARCHAR(50) COMMENT '报告类型: 日报/周报/月报',
    category_id BIGINT NOT NULL COMMENT '目标分类ID',
    priority TINYINT DEFAULT 5 COMMENT '优先级 0-10',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用: 0-禁用 1-启用',
    description VARCHAR(500) COMMENT '规则描述',
    start_time DATETIME COMMENT '生效开始时间',
    end_time DATETIME COMMENT '生效结束时间',
    created_by VARCHAR(50) COMMENT '创建人',
    updated_by VARCHAR(50) COMMENT '修改人',
    match_count INT DEFAULT 0 COMMENT '匹配次数',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_source_type (source_type),
    INDEX idx_category_id (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 批量操作日志表
CREATE TABLE IF NOT EXISTS t_batch_operation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    operation_type VARCHAR(20) NOT NULL COMMENT '操作类型: move/delete',
    target_category_id BIGINT COMMENT '目标分类',
    source_category_id BIGINT COMMENT '来源分类（移动时）',
    knowledge_ids JSON COMMENT '知识ID列表: [1,2,3]',
    operator VARCHAR(50) NOT NULL COMMENT '操作人',
    undone_operator VARCHAR(50) COMMENT '撤销操作人',
    undone_at DATETIME COMMENT '撤销时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_operation_type (operation_type),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 知识库表（增强版）
CREATE TABLE IF NOT EXISTS t_knowledge_base (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(500) NOT NULL COMMENT '标题',
    source_type VARCHAR(50) COMMENT '来源类型',
    source_name VARCHAR(100) COMMENT '来源名称',
    original_url VARCHAR(1000) COMMENT '原始URL',
    author VARCHAR(100) COMMENT '作者',
    publish_time DATETIME COMMENT '发布时间',
    content LONGTEXT COMMENT '内容',
    content_hash VARCHAR(64) COMMENT '内容MD5',
    file_path VARCHAR(500) COMMENT '文件路径',
    file_type VARCHAR(50) COMMENT '文件类型',
    chunk_count INT DEFAULT 0 COMMENT '分块数量',
    vector_status VARCHAR(20) DEFAULT 'pending' COMMENT '向量化状态',
    vector_ids TEXT COMMENT '向量ID列表',
    execution_id VARCHAR(50) COMMENT '执行ID',
    created_by VARCHAR(50) COMMENT '创建人',
    category_id BIGINT COMMENT '所属分类ID',
    collection_source VARCHAR(50) COMMENT '采集来源',
    collection_variety VARCHAR(50) COMMENT '采集品种',
    collection_report_type VARCHAR(50) COMMENT '报告类型',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_source_type (source_type),
    INDEX idx_vector_status (vector_status),
    INDEX idx_category_id (category_id),
    INDEX idx_content_hash (content_hash),
    UNIQUE KEY uk_content_hash (content_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 向量化任务表
CREATE TABLE IF NOT EXISTS t_vectorization_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    category_id BIGINT COMMENT '分类ID',
    total_count INT DEFAULT 0 COMMENT '总数',
    processed_count INT DEFAULT 0 COMMENT '已处理',
    failed_count INT DEFAULT 0 COMMENT '失败数',
    status VARCHAR(20) DEFAULT 'pending' COMMENT '状态',
    trigger_type VARCHAR(20) COMMENT '触发类型: manual/auto/scheduled',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME,
    INDEX idx_category_id (category_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 向量化日志表
CREATE TABLE IF NOT EXISTS t_vectorization_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT COMMENT '任务ID',
    knowledge_id BIGINT NOT NULL COMMENT '知识ID',
    category_id BIGINT COMMENT '分类ID',
    status VARCHAR(20) DEFAULT 'pending' COMMENT '状态',
    vector_id VARCHAR(100) COMMENT '向量ID',
    error_message TEXT COMMENT '错误信息',
    retry_count INT DEFAULT 0 COMMENT '重试次数',
    process_time_ms INT COMMENT '处理时间ms',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_knowledge_id (knowledge_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;