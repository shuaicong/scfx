# 海南储备集团粮食市场智能分析平台 - 数据库设计

## 1. ER 图概述

```
┌─────────────────┐       ┌─────────────────┐
│t_collection_task│       │t_task_execution│
│                 │       │                 │
│ id (PK)         │──1:N──│ id (PK)         │
│ task_name       │       │ task_id (FK)   │
│ source_name     │       │ execution_id   │
│ status          │       │ status         │
│ ...             │       │ start_time     │
└─────────────────┘       │ end_time       │
        │                 └─────────────────┘
        │ 1:N
        ▼
┌─────────────────┐       ┌─────────────────┐
│   t_report     │       │ t_collection_log│
│                 │       │                 │
│ id (PK)         │       │ id (PK)         │
│ title           │       │ task_id (FK)    │
│ source (FK)     │       │ execution_id    │
│ variety         │       │ level           │
│ report_type     │       │ message         │
│ publish_time    │       │ source          │
│ content         │       │ created_at      │
│ ...             │       └─────────────────┘
└─────────────────┘
        │
        │ 1:N
        ▼
┌─────────────────┐
│   t_price      │
│                 │
│ id (PK)         │
│ report_id (FK)  │
│ date            │
│ variety         │
│ price           │
│ ...             │
└─────────────────┘

┌─────────────────┐
│ t_alert_record  │
│                 │
│ id (PK)         │
│ alert_type      │
│ alert_level     │
│ alert_title     │
│ status          │
│ ...             │
└─────────────────┘
```

## 2. 表结构详细设计

### 2.1 t_collection_task - 采集任务表

```sql
CREATE TABLE t_collection_task (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '任务ID',
    task_name       VARCHAR(100) NOT NULL COMMENT '任务名称',
    task_type       VARCHAR(20) NOT NULL COMMENT '任务类型: scheduled(定时), manual(手动)',
    source_name     VARCHAR(50) NOT NULL COMMENT '数据源标识: liangxinwang, mysteel, etc.',
    source_url      VARCHAR(500) COMMENT '采集URL',
    collect_config  VARCHAR(2000) COMMENT '采集配置(JSON)',
    schedule_config VARCHAR(2000) COMMENT '调度配置(JSON/Cron)',
    status          VARCHAR(20) DEFAULT 'pending' COMMENT '状态: pending/running/success/failed',
    last_execution_time TIMESTAMP COMMENT '最后执行时间',
    next_execution_time TIMESTAMP COMMENT '下次执行时间',
    success_count   INT DEFAULT 0 COMMENT '累计成功次数',
    failed_count    INT DEFAULT 0 COMMENT '累计失败次数',
    retry_count     INT DEFAULT 0 COMMENT '当前重试次数',
    max_retry_times INT DEFAULT 3 COMMENT '最大重试次数',
    timeout_seconds INT DEFAULT 300 COMMENT '超时时间(秒)',
    priority        INT DEFAULT 1 COMMENT '优先级: 1-10',
    created_by      VARCHAR(50) COMMENT '创建人',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_source_name (source_name),
    INDEX idx_status (status),
    INDEX idx_last_execution (last_execution_time)
) COMMENT '采集任务表';
```

### 2.2 t_task_execution - 任务执行记录表

```sql
CREATE TABLE t_task_execution (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id         BIGINT COMMENT '任务ID',
    execution_id    VARCHAR(50) NOT NULL COMMENT '执行唯一标识(UUID)',
    status          VARCHAR(20) NOT NULL COMMENT '执行状态: running/success/failed',
    start_time      TIMESTAMP NOT NULL COMMENT '开始时间',
    end_time        TIMESTAMP COMMENT '结束时间',
    duration_seconds INT COMMENT '执行时长(秒)',
    error_message   VARCHAR(4000) COMMENT '错误信息',
    collected_count INT DEFAULT 0 COMMENT '采集数量',
    data_size_mb    DECIMAL(10,2) DEFAULT 0 COMMENT '数据大小(MB)',
    cpu_usage       DECIMAL(5,2) COMMENT 'CPU使用率',
    memory_usage    DECIMAL(5,2) COMMENT '内存使用率',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_task_id (task_id),
    INDEX idx_execution_id (execution_id),
    INDEX idx_status (status),
    INDEX idx_start_time (start_time)
) COMMENT '任务执行记录表';
```

### 2.3 t_report - 报告表

```sql
CREATE TABLE t_report (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    title           VARCHAR(500) NOT NULL COMMENT '报告标题',
    publish_time    TIMESTAMP COMMENT '发布时间',
    source          VARCHAR(100) DEFAULT '粮信网' COMMENT '数据来源',
    author          VARCHAR(100) COMMENT '作者',
    editor          VARCHAR(100) COMMENT '编辑',
    report_type     VARCHAR(50) COMMENT '报告类型: 晨报/日报/周报/月报',
    variety         VARCHAR(50) COMMENT '品种: 玉米/小麦/稻米/大豆',
    original_url    VARCHAR(1000) COMMENT '原始URL',
    content_html_path VARCHAR(500) COMMENT 'HTML内容文件路径',
    content_text_path VARCHAR(500) COMMENT '文本内容文件路径',
    content         TEXT COMMENT '正文内容(摘要)',
    vector_id       VARCHAR(100) COMMENT '向量ID(知识库)',
    deleted         INT DEFAULT 0 COMMENT '软删除: 0未删除, 1已删除',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_source (source),
    INDEX idx_variety (variety),
    INDEX idx_report_type (report_type),
    INDEX idx_publish_time (publish_time),
    INDEX idx_deleted (deleted)
) COMMENT '报告表';
```

### 2.4 t_collection_log - 采集日志表

```sql
CREATE TABLE t_collection_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id         BIGINT COMMENT '任务ID',
    task_name       VARCHAR(100) COMMENT '任务名称',
    level           VARCHAR(20) NOT NULL COMMENT '日志级别: ERROR/WARN/INFO/DEBUG',
    message         VARCHAR(4000) NOT NULL COMMENT '日志内容',
    source          VARCHAR(50) COMMENT '数据源',
    execution_id    VARCHAR(50) COMMENT '执行ID',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_task_id (task_id),
    INDEX idx_level (level),
    INDEX idx_source (source),
    INDEX idx_execution_id (execution_id),
    INDEX idx_created_at (created_at)
) COMMENT '采集日志表';
```

### 2.5 t_alert_record - 告警记录表

```sql
CREATE TABLE t_alert_record (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    alert_type      VARCHAR(50) NOT NULL COMMENT '告警类型: task_error/system_error/data_anomaly',
    alert_level     VARCHAR(20) NOT NULL COMMENT '告警级别: critical/error/warning/info',
    alert_title     VARCHAR(200) NOT NULL COMMENT '告警标题',
    alert_content   VARCHAR(4000) NOT NULL COMMENT '告警内容',
    target_id       VARCHAR(50) COMMENT '关联目标ID(任务ID/执行ID)',
    status          VARCHAR(20) DEFAULT 'pending' COMMENT '状态: pending/resolved/ignored',
    notified_channels VARCHAR(500) COMMENT '通知渠道: email/dingtalk/wechat',
    resolved_by     VARCHAR(50) COMMENT '处理人',
    resolved_at     TIMESTAMP COMMENT '处理时间',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_alert_type (alert_type),
    INDEX idx_alert_level (alert_level),
    INDEX idx_status (status),
    INDEX idx_target_id (target_id),
    INDEX idx_created_at (created_at)
) COMMENT '告警记录表';
```

### 2.6 t_price - 价格数据表

```sql
CREATE TABLE t_price (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_id       BIGINT COMMENT '关联报告ID',
    date            DATE NOT NULL COMMENT '价格日期',
    variety         VARCHAR(50) NOT NULL COMMENT '品种',
    region          VARCHAR(50) COMMENT '地区',
    contract        VARCHAR(50) COMMENT '合约',
    price           DECIMAL(12,2) COMMENT '价格',
    change_val      DECIMAL(12,2) COMMENT '涨跌值',
    unit            VARCHAR(50) COMMENT '单位',
    source          VARCHAR(100) COMMENT '数据来源',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_date (date),
    INDEX idx_variety (variety),
    INDEX idx_region (region),
    INDEX idx_report_id (report_id)
) COMMENT '价格数据表';
```

## 3. 字典值说明

### 3.1 source_name - 数据源标识

| 代码 | 名称 | 网站 |
|------|------|------|
| liangxinwang | 粮信网 | chinagrain.cn |
| mysteel | 我的钢铁网 | mysteel.com |
| china_grain | 中华粮网 | cngrain.com |

### 3.2 task_type - 任务类型

| 代码 | 名称 | 说明 |
|------|------|------|
| scheduled | 定时任务 | 按Cron表达式执行 |
| manual | 手动任务 | 手动触发执行 |
| api | API任务 | 通过API触发 |

### 3.3 task_status - 任务状态

| 代码 | 名称 | 说明 |
|------|------|------|
| pending | 等待中 | 等待调度或手动触发 |
| running | 运行中 | 正在执行采集 |
| success | 成功 | 最近一次执行成功 |
| failed | 失败 | 最近一次执行失败 |

### 3.4 execution_status - 执行状态

| 代码 | 名称 | 说明 |
|------|------|------|
| running | 运行中 | 执行进行中 |
| success | 成功 | 执行成功完成 |
| failed | 失败 | 执行失败 |

### 3.5 alert_level - 告警级别

| 代码 | 名称 | 说明 |
|------|------|------|
| critical | 严重 | 系统不可用 |
| error | 错误 | 功能异常 |
| warning | 警告 | 需要关注 |
| info | 信息 | 通知类 |

### 3.6 variety - 品种

| 代码 | 名称 |
|------|------|
| 玉米 | 玉米 |
| 小麦 | 小麦 |
| 稻米 | 稻米/大米 |
| 大豆 | 大豆 |

### 3.7 report_type - 报告类型

| 代码 | 名称 |
|------|------|
| 晨报 | 晨报 |
| 日报 | 日报 |
| 周报 | 周报 |
| 月报 | 月报 |
| 专题 | 专题报告 |
| 资讯 | 资讯 |

## 4. API 数据模型

### 4.1 启动采集请求/响应

```json
// POST /collector/exec/start
// Request
{
  "taskId": 1
}

// Response
{
  "code": 200,
  "data": {
    "executionId": "550e8400-e29b-41d4-a716-446655440000",
    "taskId": 1,
    "startTime": "2026-04-28T10:30:00"
  }
}
```

### 4.2 上报日志

```json
// POST /collector/exec/{executionId}/log
{
  "level": "INFO",
  "message": "登录成功，开始采集报告列表"
}
```

### 4.3 上报采集数据

```json
// POST /collector/exec/{executionId}/data
{
  "title": "（2026年4月28日）玉米晨报",
  "source": "liangxinwang",
  "url": "https://www.chinagrain.cn/report/12345",
  "variety": "玉米",
  "reportType": "晨报",
  "content": "今日国内玉米价格震荡运行为主...",
  "publishTime": "2026-04-28T08:00:00"
}
```

### 4.4 完成任务

```json
// POST /collector/exec/{executionId}/complete
{
  "status": "success",
  "collectedCount": 10
}
```

### 4.5 上报错误

```json
// POST /collector/exec/{executionId}/error
{
  "errorMessage": "登录失败：用户名或密码错误"
}
```

## 5. SQL 初始化数据

```sql
-- 初始化粮信网采集任务
INSERT INTO t_collection_task (task_name, task_type, source_name, source_url, status)
SELECT '粮信网-玉米晨报采集', 'scheduled', 'liangxinwang', 'https://www.chinagrain.cn/report/', 'pending'
WHERE NOT EXISTS (SELECT 1 FROM t_collection_task WHERE source_name = 'liangxinwang');

-- 初始化我的钢铁网任务（待接入）
INSERT INTO t_collection_task (task_name, task_type, source_name, source_url, status)
SELECT '我的钢铁网-玉米市场采集', 'scheduled', 'mysteel', 'https://news.mysteel.com/corn/', 'pending'
WHERE NOT EXISTS (SELECT 1 FROM t_collection_task WHERE source_name = 'mysteel');
```
