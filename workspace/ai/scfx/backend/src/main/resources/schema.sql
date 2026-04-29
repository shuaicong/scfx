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
    execution_id VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    duration_seconds INT,
    error_message VARCHAR(4000),
    collected_count INT DEFAULT 0,
    data_size_mb DECIMAL(10,2) DEFAULT 0,
    cpu_usage DECIMAL(5,2),
    memory_usage DECIMAL(5,2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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

-- 采集器信息表
CREATE TABLE IF NOT EXISTS t_collector_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    collector_name VARCHAR(100) NOT NULL,
    sdk_version VARCHAR(20),
    source VARCHAR(50),
    subject VARCHAR(50),
    coll_type VARCHAR(50),
    coll_object VARCHAR(50),
    description VARCHAR(500),
    status VARCHAR(20) DEFAULT 'offline',
    last_heartbeat TIMESTAMP,
    registered_at TIMESTAMP,
    instance_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 初始化粮信网采集任务
INSERT INTO t_collection_task (task_name, task_type, source_name, source_url, status)
SELECT '粮信网-玉米晨报采集', 'scheduled', 'liangxinwang', 'https://www.chinagrain.cn/report/', 'pending'
WHERE NOT EXISTS (SELECT 1 FROM t_collection_task WHERE source_name = 'liangxinwang');
