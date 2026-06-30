-- V14: 重建报告相关表（因 V13 的 IF NOT EXISTS 未覆盖旧表结构）

DROP TABLE IF EXISTS t_report_version;
DROP TABLE IF EXISTS t_report_chart;
DROP TABLE IF EXISTS t_report_template_version;
DROP TABLE IF EXISTS t_report_generation_log;
DROP TABLE IF EXISTS t_report_template;
DROP TABLE IF EXISTS t_report;

CREATE TABLE t_report (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    title           VARCHAR(500) NOT NULL COMMENT '报告标题',
    variety         VARCHAR(50) COMMENT '品种',
    report_type     VARCHAR(20) DEFAULT 'weekly' COMMENT 'weekly/monthly/special',
    status          VARCHAR(20) DEFAULT 'draft' COMMENT 'draft/published',
    template_id     BIGINT COMMENT '关联模板ID',
    current_version INT DEFAULT 0,
    generation_status VARCHAR(20) DEFAULT 'none' COMMENT 'none/generating/completed/failed',
    rich_content    LONGTEXT COMMENT '当前HTML内容',
    author          VARCHAR(100),
    publish_time    DATETIME,
    export_docx_path VARCHAR(500) COMMENT 'MinIO docx路径',
    export_pdf_path  VARCHAR(500) COMMENT 'MinIO pdf路径',
    deleted         INT DEFAULT 0,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_variety (variety),
    INDEX idx_status (status),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE t_report_version (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_id       BIGINT NOT NULL,
    version_number  INT NOT NULL,
    title           VARCHAR(500),
    rich_content    LONGTEXT,
    editor_json     LONGTEXT COMMENT 'TipTap JSON快照',
    editor          VARCHAR(100),
    change_summary  VARCHAR(500),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_report_version (report_id, version_number),
    INDEX idx_report_version (report_id, version_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE t_report_chart (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_id   BIGINT NOT NULL,
    version_id  BIGINT,
    chart_type  VARCHAR(20) COMMENT 'line/table',
    variety     VARCHAR(50),
    region      VARCHAR(100),
    date_start  DATE,
    date_end    DATE,
    minio_path  VARCHAR(500),
    query_params JSON COMMENT '完整行情查询参数',
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_chart_report (report_id, version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE t_report_template (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    variety         VARCHAR(50),
    report_type     VARCHAR(20) DEFAULT 'weekly',
    current_version INT DEFAULT 0,
    description     TEXT,
    generation_config JSON COMMENT '生成规则配置',
    deleted         INT DEFAULT 0,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_variety (variety)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE t_report_template_version (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_id     BIGINT NOT NULL,
    version_number  INT NOT NULL,
    name            VARCHAR(200),
    rich_content    LONGTEXT COMMENT '含占位符的HTML',
    editor_json     LONGTEXT COMMENT 'TipTap JSON快照',
    editor          VARCHAR(100),
    change_summary  VARCHAR(500),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_template_version (template_id, version_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE t_report_generation_log (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_id    BIGINT NOT NULL,
    execution_id VARCHAR(50),
    status       VARCHAR(20),
    step         VARCHAR(50),
    message      TEXT,
    duration_ms  INT,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_report_log (report_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 重新插入模板数据
INSERT INTO t_report_template (name, variety, report_type, description, current_version) VALUES
('玉米分析周报', '玉米', 'weekly', '港口价格对比、产区价格、供需分析、海南市场行情', 0),
('稻米分析周报', '稻米', 'weekly', '国投收购、国际FOB报价、米厂开机率、价格走势', 0);
