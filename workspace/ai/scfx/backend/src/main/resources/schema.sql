-- 粮食市场智能分析平台 - 数据库初始化脚本 (H2兼容)

-- 采集任务表
CREATE TABLE IF NOT EXISTS t_collection_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_name VARCHAR(100) NOT NULL,
    task_type VARCHAR(20) NOT NULL,
    source_name VARCHAR(50) NOT NULL,
    source_url VARCHAR(500),
    collect_config VARCHAR(2000),
    schedule_config VARCHAR(2000),
    status VARCHAR(20) DEFAULT 'pending',
    last_execution_time TIMESTAMP,
    next_execution_time TIMESTAMP,
    success_count INT DEFAULT 0,
    failed_count INT DEFAULT 0,
    retry_count INT DEFAULT 0,
    max_retry_times INT DEFAULT 3,
    timeout_seconds INT DEFAULT 300,
    priority INT DEFAULT 1,
    created_by VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 任务执行记录表
CREATE TABLE IF NOT EXISTS t_task_execution (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT,
    script_id BIGINT,
    version_id BIGINT,
    execution_id VARCHAR(50) NOT NULL,
    trigger_type VARCHAR(20),
    status VARCHAR(20) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    duration_ms BIGINT,
    error_message VARCHAR(4000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 任务执行日志表
CREATE TABLE IF NOT EXISTS t_task_execution_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    execution_id VARCHAR(50),
    script_id BIGINT,
    level VARCHAR(20) NOT NULL,
    message VARCHAR(4000) NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 报告表
CREATE TABLE IF NOT EXISTS t_report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    publish_time TIMESTAMP,
    source VARCHAR(100) DEFAULT '粮信网',
    author VARCHAR(100),
    editor VARCHAR(100),
    report_type VARCHAR(50),
    variety VARCHAR(50),
    original_url VARCHAR(1000),
    content_html_path VARCHAR(500),
    content_text_path VARCHAR(500),
    content TEXT,
    vector_id VARCHAR(100),
    execution_id VARCHAR(50),
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 价格数据表
CREATE TABLE IF NOT EXISTS t_price (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_id BIGINT,
    date DATE NOT NULL,
    variety VARCHAR(50) NOT NULL,
    region VARCHAR(50),
    contract VARCHAR(50),
    price DECIMAL(12,2),
    change_val DECIMAL(12,2),
    unit VARCHAR(50),
    source VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 采集日志表
CREATE TABLE IF NOT EXISTS t_collection_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT,
    task_name VARCHAR(100),
    level VARCHAR(20) NOT NULL,
    message VARCHAR(4000) NOT NULL,
    source VARCHAR(50),
    subject VARCHAR(50),
    coll_type VARCHAR(50),
    coll_object VARCHAR(50),
    execution_id VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 告警记录表
CREATE TABLE IF NOT EXISTS t_alert_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    alert_type VARCHAR(50) NOT NULL,
    alert_level VARCHAR(20) NOT NULL,
    alert_title VARCHAR(200) NOT NULL,
    alert_content VARCHAR(4000) NOT NULL,
    target_id VARCHAR(50),
    status VARCHAR(20) DEFAULT 'pending',
    notified_channels VARCHAR(500),
    resolved_by VARCHAR(50),
    resolved_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 采集脚本表
CREATE TABLE IF NOT EXISTS t_collection_script (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    script_name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    script_path VARCHAR(500) NOT NULL,
    script_content TEXT,
    source VARCHAR(50),
    subject VARCHAR(50),
    coll_type VARCHAR(50),
    coll_object VARCHAR(50),
    status VARCHAR(20) DEFAULT 'enabled',
    report_interval_seconds INT DEFAULT 60,
    trigger_type VARCHAR(20) DEFAULT 'manual',
    trigger_config VARCHAR(2000),
    cron_expression VARCHAR(100),
    repeat_type VARCHAR(20),
    repeat_config VARCHAR(500),
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    last_execution_time TIMESTAMP,
    next_execution_time TIMESTAMP,
    execution_count INT DEFAULT 0,
    success_count INT DEFAULT 0,
    failed_count INT DEFAULT 0,
    weekly_days VARCHAR(100),
    monthly_day INT,
    monthly_last_day BOOLEAN,
    end_type VARCHAR(20),
    repeat_count INT,
    repeat_time VARCHAR(20),
    current_version INT,
    created_by VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 采集脚本版本表
CREATE TABLE IF NOT EXISTS t_script_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    script_id BIGINT NOT NULL,
    version_num INT NOT NULL,
    script_name VARCHAR(100),
    script_content TEXT,
    trigger_type VARCHAR(20),
    repeat_type VARCHAR(20),
    repeat_time VARCHAR(20),
    weekly_days VARCHAR(100),
    monthly_day INT,
    monthly_last_day BOOLEAN,
    cron_expression VARCHAR(100),
    end_type VARCHAR(20),
    end_time TIMESTAMP,
    repeat_count INT,
    change_description VARCHAR(500),
    created_by VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_current BOOLEAN DEFAULT FALSE
);

-- 知识库表
CREATE TABLE IF NOT EXISTS t_knowledge_base (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    source_type VARCHAR(20) NOT NULL COMMENT 'collection/upload/manual',
    source_name VARCHAR(100),
    original_url VARCHAR(1000),
    author VARCHAR(100),
    publish_time DATETIME,
    content TEXT NOT NULL,
    content_hash VARCHAR(64),
    file_path VARCHAR(500),
    file_type VARCHAR(20),
    chunk_count INT DEFAULT 0,
    vector_status VARCHAR(20) DEFAULT 'pending' COMMENT 'pending/processing/completed/failed',
    vector_ids VARCHAR(500),
    execution_id VARCHAR(50),
    created_by VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    INDEX idx_source_type (source_type),
    INDEX idx_source_name (source_name),
    INDEX idx_vector_status (vector_status),
    INDEX idx_content_hash (content_hash),
    INDEX idx_publish_time (publish_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库表';

-- 初始化粮信网采集任务
INSERT INTO t_collection_task (task_name, task_type, source_name, source_url, status)
SELECT '粮信网-玉米晨报采集', 'scheduled', 'liangxinwang', 'https://www.chinagrain.cn/report/', 'pending'
WHERE NOT EXISTS (SELECT 1 FROM t_collection_task WHERE source_name = 'liangxinwang');

-- =============================================
-- 分类管理相关表
-- =============================================

-- 分类表
CREATE TABLE IF NOT EXISTS t_category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    icon VARCHAR(50) DEFAULT '📁',
    color VARCHAR(20) DEFAULT NULL COMMENT '分类主题颜色',
    description VARCHAR(500) DEFAULT NULL COMMENT '分类描述/备注',
    parent_id BIGINT DEFAULT NULL COMMENT '父分类ID，NULL表示顶级',
    sort_order INT DEFAULT 0 COMMENT '排序序号，数字越小越靠前',
    pinned TINYINT DEFAULT 0 COMMENT '是否置顶，1=置顶',
    last_operated_by VARCHAR(100) DEFAULT NULL COMMENT '最后操作人',
    last_operated_at DATETIME DEFAULT NULL COMMENT '最后操作时间',
    permission_level VARCHAR(20) DEFAULT 'public' COMMENT '权限级别：public/team/private',
    allowed_users VARCHAR(500) DEFAULT NULL COMMENT '团队模式下允许的用户列表，逗号分隔',
    active_season_start VARCHAR(10) DEFAULT NULL COMMENT '活跃季节开始月份，如 "09"',
    active_season_end VARCHAR(10) DEFAULT NULL COMMENT '活跃季节结束月份，如 "11"',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME DEFAULT NULL COMMENT '软删除时间，NULL表示未删除',
    version BIGINT DEFAULT 0 COMMENT '版本号，用于实时同步检测',
    FOREIGN KEY (parent_id) REFERENCES t_category(id) ON DELETE SET NULL,
    INDEX idx_category_parent (parent_id),
    INDEX idx_category_deleted (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库分类表';

-- 知识-分类关联表
CREATE TABLE IF NOT EXISTS t_knowledge_category (
    knowledge_id BIGINT NOT NULL COMMENT '知识条目ID',
    category_id BIGINT NOT NULL COMMENT '分类ID',
    PRIMARY KEY (knowledge_id, category_id),
    INDEX idx_knowledge_category_kid (knowledge_id),
    INDEX idx_knowledge_category_cid (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识-分类关联表';

-- 分类操作日志表
CREATE TABLE IF NOT EXISTS t_category_operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_id BIGINT NOT NULL COMMENT '分类ID',
    operator VARCHAR(100) DEFAULT NULL COMMENT '操作人',
    operation_type VARCHAR(50) DEFAULT NULL COMMENT '操作类型（CREATE/UPDATE/DELETE/RESTORE/MOVE）',
    operation_detail VARCHAR(500) DEFAULT NULL COMMENT '操作详情（JSON 格式保存操作前后状态）',
    operated_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    INDEX idx_log_category (category_id),
    INDEX idx_log_operated (operated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分类操作日志表';

-- 分类订阅表
CREATE TABLE IF NOT EXISTS t_category_subscription (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_id BIGINT NOT NULL COMMENT '分类ID',
    user_id VARCHAR(100) DEFAULT NULL COMMENT '订阅用户ID',
    subscribed_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '订阅时间',
    notify_count INT DEFAULT 0 COMMENT '未读通知数量',
    last_notified_at DATETIME DEFAULT NULL COMMENT '最后通知时间',
    UNIQUE KEY uk_category_user (category_id, user_id),
    INDEX idx_subscription_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分类订阅表';

-- 知识移动历史表
CREATE TABLE IF NOT EXISTS t_knowledge_move_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    knowledge_id BIGINT NOT NULL COMMENT '知识ID',
    from_category_id BIGINT DEFAULT NULL COMMENT '原分类ID',
    to_category_id BIGINT DEFAULT NULL COMMENT '目标分类ID',
    moved_by VARCHAR(100) DEFAULT NULL COMMENT '操作人',
    moved_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '移动时间',
    INDEX idx_move_knowledge (knowledge_id),
    INDEX idx_move_to (to_category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识移动历史表';

-- 用户分类收藏夹表
CREATE TABLE IF NOT EXISTS t_category_favorite (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL COMMENT '用户ID',
    name VARCHAR(100) NOT NULL COMMENT '收藏夹名称，如"我的常用"',
    category_ids VARCHAR(500) DEFAULT NULL COMMENT '包含的分类ID，逗号分隔',
    sort_order INT DEFAULT 0 COMMENT '排序',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_favorite_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户分类收藏夹表';

-- 初始化数据：粮信网顶级分类
INSERT INTO t_category (name, icon, color, sort_order) VALUES
('粮信网', '🌐', '#58A6FF', 1),
('我的钢铁', '🏭', '#3FB950', 2),
('中华粮网', '🌾', '#F0883E', 3);
