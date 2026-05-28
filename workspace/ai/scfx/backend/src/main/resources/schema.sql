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
    collected_count INT DEFAULT 0,
    total_count INT DEFAULT 0,
    success_count INT DEFAULT 0,
    skip_count INT DEFAULT 0,
    error_count INT DEFAULT 0,
    data_size_mb DECIMAL(10,2) DEFAULT 0,
    phase_login_ms BIGINT DEFAULT 0,
    phase_crawl_ms BIGINT DEFAULT 0,
    phase_parse_ms BIGINT DEFAULT 0,
    phase_report_ms BIGINT DEFAULT 0,
    detail_json TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 任务执行日志表
CREATE TABLE IF NOT EXISTS t_task_execution_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    execution_id VARCHAR(50),
    script_id BIGINT,
    level VARCHAR(20) NOT NULL,
    message VARCHAR(4000) NOT NULL,
    phase VARCHAR(20) DEFAULT NULL COMMENT '所属阶段：login/crawl/parse/report/system',
    category VARCHAR(20) DEFAULT NULL COMMENT '日志分类：progress/data/error/metric/checkpoint',
    elapsed_ms BIGINT DEFAULT NULL COMMENT '相对执行开始的毫秒偏移',
    data_json TEXT DEFAULT NULL COMMENT '可选的结构化数据 JSON',
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
    sync_to_knowledge_base TINYINT(1) DEFAULT 1 COMMENT '是否同步到知识库',
    category_id BIGINT DEFAULT NULL COMMENT '关联分类ID（采集结果同步到知识库时自动归类）',
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
    content_html TEXT COMMENT 'HTML格式内容（保留图片标签等）',
    content_hash VARCHAR(64),
    file_path VARCHAR(500),
    file_type VARCHAR(20),
    chunk_count INT DEFAULT 0,
    vector_status VARCHAR(20) DEFAULT 'pending' COMMENT 'pending/processing/completed/failed',
    vector_ids VARCHAR(500),
    execution_id VARCHAR(50),
    category_id BIGINT DEFAULT NULL COMMENT '所属分类ID',
    collection_source VARCHAR(50) DEFAULT NULL COMMENT '采集来源',
    collection_variety VARCHAR(50) DEFAULT NULL COMMENT '采集品种',
    collection_report_type VARCHAR(50) DEFAULT NULL COMMENT '报告类型',
    viz_x DOUBLE DEFAULT NULL COMMENT 'PCA降维X坐标（可视化用）',
    viz_y DOUBLE DEFAULT NULL COMMENT 'PCA降维Y坐标（可视化用）',
    viz_z DOUBLE DEFAULT NULL COMMENT 'PCA降维Z坐标（3D预留）',
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

-- 执行采集数据项表
CREATE TABLE IF NOT EXISTS t_execution_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    execution_id VARCHAR(50) NOT NULL COMMENT '执行ID',
    knowledge_id BIGINT COMMENT '知识库条目ID',
    title VARCHAR(500) COMMENT '标题',
    url VARCHAR(1000) COMMENT '来源URL',
    action VARCHAR(30) NOT NULL COMMENT '操作类型：created/skipped_duplicate/skipped_existing/error',
    error_message VARCHAR(1000) COMMENT '失败原因',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_execution_id (execution_id)
);

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

-- =============================================
-- 数据源管理相关表
-- =============================================

-- 数据源表
CREATE TABLE IF NOT EXISTS t_data_source (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE COMMENT '数据源编码',
    name VARCHAR(100) NOT NULL COMMENT '显示名称',
    description VARCHAR(500) COMMENT '描述信息',
    logo_url VARCHAR(255) COMMENT 'logo URL',
    enabled TINYINT(1) DEFAULT 1 COMMENT '启用状态',
    sort_order INT DEFAULT 0 COMMENT '排序',
    config JSON COMMENT '配置信息',
    last_heartbeat TIMESTAMP COMMENT '最后心跳时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据源表';

-- 采集器脚本版本表
CREATE TABLE IF NOT EXISTS t_collector_script_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    datasource_code VARCHAR(50) NOT NULL COMMENT '数据源编码',
    version INT NOT NULL COMMENT '版本号',
    file_path VARCHAR(255) NOT NULL COMMENT '文件路径',
    file_md5 VARCHAR(32) NOT NULL COMMENT '文件MD5',
    file_size INT COMMENT '文件大小',
    is_current TINYINT(1) DEFAULT 0 COMMENT '是否当前版本',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) COMMENT '创建人',
    UNIQUE INDEX idx_datasource_version (datasource_code, version),
    INDEX idx_datasource_current (datasource_code, is_current)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采集器脚本版本表';

-- 脚本操作日志表
CREATE TABLE IF NOT EXISTS t_script_operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    datasource_code VARCHAR(50) NOT NULL COMMENT '数据源编码',
    operation_type VARCHAR(20) NOT NULL COMMENT '操作类型(UPLOAD/UPDATE/DELETE/ROLLBACK)',
    operator VARCHAR(100) COMMENT '操作人',
    operate_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    file_md5 VARCHAR(32) NOT NULL COMMENT '文件MD5',
    file_size INT COMMENT '文件大小',
    backup_path VARCHAR(255) COMMENT '备份路径',
    remark VARCHAR(500) COMMENT '备注',
    INDEX idx_datasource_code (datasource_code),
    INDEX idx_operate_time (operate_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='脚本操作日志表';

-- =============================================
-- 操作日志和审计
-- =============================================

-- 操作日志表
CREATE TABLE IF NOT EXISTS t_operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    operator VARCHAR(100) NOT NULL COMMENT '操作人',
    operation_type VARCHAR(50) NOT NULL COMMENT '操作类型',
    target_type VARCHAR(50) NOT NULL COMMENT '目标类型',
    target_id BIGINT NOT NULL COMMENT '目标ID',
    detail JSON COMMENT '操作详情',
    ip VARCHAR(50) COMMENT 'IP地址',
    operate_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    INDEX idx_target (target_type, target_id),
    INDEX idx_operator_time (operator, operate_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

-- 告警规则表
CREATE TABLE IF NOT EXISTS t_alert_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_name VARCHAR(100) COMMENT '规则名称',
    rule_type VARCHAR(50) COMMENT '规则类型(CONTINUOUS_FAIL/SERVICE_OFFLINE/ZERO_RESULT/TIMEOUT)',
    `condition` JSON COMMENT '条件配置',
    enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    notify_channels JSON COMMENT '通知渠道',
    notify_target VARCHAR(500) COMMENT '通知目标',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警规则表';

-- =============================================
-- 初始化数据：数据源
-- =============================================

INSERT INTO t_data_source (code, name, description, config) VALUES
('liangxin', '粮信网', '中国粮食网玉米晨报', NULL),
('mysteel', '我的钢铁网', '我的钢铁网价格数据', NULL),
('chinagrain', '中华粮网', '中华粮网市场数据', NULL),
('usda', 'USDA', '美国农业部数据', NULL),
('market', '市场数据', '第三方市场数据', NULL);

-- 初始化数据：粮信网顶级分类
INSERT INTO t_category (name, icon, color, sort_order) VALUES
('粮信网', '🌐', '#58A6FF', 1),
('我的钢铁', '🏭', '#3FB950', 2),
('中华粮网', '🌾', '#F0883E', 3);

-- =============================================
-- 向量可视化相关（双向量方案）
-- =============================================

-- 可视化向量存储表（DashScope 768维向量）
CREATE TABLE IF NOT EXISTS t_knowledge_viz (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    knowledge_id BIGINT NOT NULL COMMENT '知识库条目ID',
    vector_768 VARBINARY(3072) DEFAULT NULL COMMENT 'DashScope 768维向量（3072字节，固定长度）',
    viz_status VARCHAR(20) DEFAULT 'pending' COMMENT '可视化状态：vectorized/failed/pending',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_viz_knowledge (knowledge_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='可视化向量存储表（DashScope 768维）';

-- PCA 基线缓存表（增量投影复用均值/主成分）
CREATE TABLE IF NOT EXISTS t_pca_baseline (
    category_id BIGINT NOT NULL PRIMARY KEY COMMENT '分类ID',
    last_viz_id BIGINT NOT NULL DEFAULT 0 COMMENT '已标记的最大 t_knowledge_viz.id',
    vector_count INT NOT NULL DEFAULT 0 COMMENT '参与基线的向量数',
    mean_vector VARBINARY(3072) DEFAULT NULL COMMENT '均值向量（768维）',
    pc1 VARBINARY(3072) DEFAULT NULL COMMENT '第一主成分（768维）',
    pc2 VARBINARY(3072) DEFAULT NULL COMMENT '第二主成分（768维）',
    pc3 VARBINARY(3072) DEFAULT NULL COMMENT '第三主成分（768维，3D预留）',
    x_min DOUBLE DEFAULT NULL COMMENT '归一化X下界',
    x_max DOUBLE DEFAULT NULL COMMENT '归一化X上界',
    y_min DOUBLE DEFAULT NULL COMMENT '归一化Y下界',
    y_max DOUBLE DEFAULT NULL COMMENT '归一化Y上界',
    z_min DOUBLE DEFAULT NULL COMMENT '归一化Z下界（3D预留）',
    z_max DOUBLE DEFAULT NULL COMMENT '归一化Z上界（3D预留）',
    version INT DEFAULT 1 COMMENT '版本号',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PCA基线缓存表';

-- PCA 计算记录表（版本快照，用于回溯排查聚类异常）
CREATE TABLE IF NOT EXISTS t_pca_calculation_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_id BIGINT NOT NULL COMMENT '分类ID',
    version INT NOT NULL COMMENT '版本号（同一分类递增）',
    trigger_type VARCHAR(20) NOT NULL COMMENT '触发类型: manual_full/incremental/manual_single/auto_incremental',
    point_count INT NOT NULL COMMENT '参与本次计算的向量数',
    before_count INT DEFAULT 0 COMMENT '计算前向量数',
    computation_cost_ms BIGINT DEFAULT NULL COMMENT '计算耗时(ms)',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注（如触发源、异常信息）',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_calc_version (category_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PCA计算记录表';

-- =============================================
-- 多算法降维坐标存储（PCA/MDS）
-- =============================================

-- 降维坐标存储表（支持多算法）
CREATE TABLE IF NOT EXISTS t_knowledge_dr_coords (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    knowledge_id BIGINT NOT NULL COMMENT '知识ID',
    category_id BIGINT NOT NULL COMMENT '分类ID',
    algorithm VARCHAR(20) NOT NULL COMMENT '降维算法: pca/mds',
    version INT NOT NULL COMMENT '版本号（按分类+算法递增）',
    x DOUBLE DEFAULT NULL COMMENT 'X坐标',
    y DOUBLE DEFAULT NULL COMMENT 'Y坐标',
    z DOUBLE DEFAULT NULL COMMENT 'Z坐标(3D预留)',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX uk_knowledge_algorithm (knowledge_id, algorithm),
    INDEX idx_cat_alg_ver (category_id, algorithm, version DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='降维坐标存储表（PCA/MDS多算法）';

-- 版本号原子递增表
CREATE TABLE IF NOT EXISTS t_dr_version (
    category_id BIGINT NOT NULL COMMENT '分类ID',
    algorithm VARCHAR(20) NOT NULL COMMENT '降维算法',
    current_version INT NOT NULL DEFAULT 1 COMMENT '当前版本号',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (category_id, algorithm)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='降维版本号表';

-- 切片表（文档解析后分片存储）
CREATE TABLE IF NOT EXISTS t_knowledge_chunk (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    knowledge_id BIGINT NOT NULL COMMENT '所属知识ID',
    category_id BIGINT COMMENT '分类ID',
    chunk_index INT NOT NULL COMMENT '切片序号（从0开始）',
    content TEXT NOT NULL COMMENT '切片文本内容',
    token_count INT DEFAULT NULL COMMENT 'token数',
    vector_status VARCHAR(20) DEFAULT 'pending' COMMENT '向量化状态: pending/processing/vectorized/failed',
    vector_id VARCHAR(100) DEFAULT NULL COMMENT '向量ID（DashScope返回）',
    error_message VARCHAR(500) DEFAULT NULL COMMENT '向量化失败信息',
    is_active INT DEFAULT 1 COMMENT '1=正常 0=已删除',
    content_terms VARCHAR(2000) DEFAULT NULL COMMENT '保留字段：全文检索用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_knowledge_id (knowledge_id),
    INDEX idx_knowledge_active (knowledge_id, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识切片表';
