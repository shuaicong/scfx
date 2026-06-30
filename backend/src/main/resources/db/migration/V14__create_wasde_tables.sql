-- V14: WASDE 报告采集与解析数据表

-- 1. WASDE 结构化供需数据表
CREATE TABLE IF NOT EXISTS t_wasde_data (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    report_key      VARCHAR(32)     NOT NULL COMMENT '报告唯一标识, wasde_202606',
    source_type     VARCHAR(16)     NOT NULL COMMENT '数据源, wasde/conab',
    commodity       VARCHAR(64)     NOT NULL COMMENT '品种, CORN/WHEAT/SOYBEANS',
    country         VARCHAR(64)              COMMENT '国家/地区',
    attribute       VARCHAR(32)              COMMENT '指标, PRODUCTION/IMPORTS/EXPORTS/ENDING_STOCK',
    year_marketing  VARCHAR(16)              COMMENT '市场年度, 如 2025/26',
    value           DECIMAL(20, 2)           COMMENT '数值',
    unit            VARCHAR(16)              COMMENT '单位, 百万蒲式耳/百万吨',
    report_date     DATE            NOT NULL COMMENT '报告发布日期',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_report_com_country_attr_my (report_key, commodity, country, attribute, year_marketing),
    KEY idx_report_key (report_key),
    KEY idx_commodity (commodity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='WASDE 供需数据';

-- 2. 解析记录表（幂等 + 失败追踪）
CREATE TABLE IF NOT EXISTS t_parse_record (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    source_type     VARCHAR(16)     NOT NULL COMMENT '数据源, wasde/conab',
    report_key      VARCHAR(32)     NOT NULL COMMENT '报告唯一标识',
    status          VARCHAR(16)     NOT NULL DEFAULT 'pending' COMMENT '解析状态, pending/success/failed',
    minio_path      VARCHAR(512)             COMMENT 'XML 文件 MinIO 路径',
    error_message   TEXT                     COMMENT '失败异常堆栈',
    retry_count     INT             NOT NULL DEFAULT 0 COMMENT '已重试次数',
    report_date     DATE                     COMMENT '报告发布日期',
    parse_at        DATETIME                 COMMENT '最后解析时间',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    UNIQUE KEY uk_source_report (source_type, report_key),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报告解析记录';

-- 3. 解析业务日志表
-- 注意：V13 已有 t_report_generation_log 用于报告生成日志（不同用途），
-- 本表 t_parse_log 专用于解析日志，使用独立表名避免冲突
CREATE TABLE IF NOT EXISTS t_parse_log (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    source_type     VARCHAR(32)              COMMENT '数据源',
    report_key      VARCHAR(32)              COMMENT '报告标识',
    level           VARCHAR(8)      NOT NULL DEFAULT 'INFO' COMMENT '日志级别, INFO/WARN/ERROR',
    message         TEXT                     COMMENT '日志消息',
    stack_trace     TEXT                     COMMENT '异常堆栈（ERROR 时填写）',
    minio_path      VARCHAR(512)             COMMENT '关联文件路径',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    KEY idx_source_report (source_type, report_key),
    KEY idx_level (level),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='解析业务日志';
