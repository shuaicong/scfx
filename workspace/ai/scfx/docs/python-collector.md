# Python 采集器开发指南

## 1. 概述

Python 采集器作为独立进程运行，负责：
- 模拟登录（Cookie/Session 持久化）
- 页面自动化导航
- 数据解析
- 通过 REST API 向 Java 后端上报数据

Java 后端负责：
- 任务调度管理
- 执行状态跟踪
- 数据接收入库
- 监控告警

## 2. 核心设计思想：完全解耦

### 架构原则

```
采集代码 = 干净纯粹（只做登录→爬取→解析→返回数据）
上报逻辑 = 独立模块（Observer / SDK）

上报开关可开可关
上报失败不影响采集
采集代码 100% 纯净
未来换上报方式不用改爬虫
```

### 5个核心维度

所有上报必须携带以下维度，Java 后端才能知道：谁采的、采的谁、采什么、从哪采、属于哪类任务

| 维度 | 字段 | 说明 | 示例 |
|------|------|------|------|
| 采集来源 | source | 数据来自哪个网站/系统 | liangxin, mysteel |
| 采集主体 | subject | 业务主体 | corn, wheat, rice |
| 采集类型 | type | 采集方式 | login_crawl, public_crawl |
| 采集对象 | object | 具体采集目标 | daily_report, price |
| 采集描述 | remark | 任务一句话说明 | "粮信网玉米晨报采集" |

## 3. SDK 架构

```
python-collector-sdk/
├── collectorsdk/
│   ├── __init__.py          # 包入口
│   ├── config.py           # 配置管理（开关、API地址、重试策略）
│   ├── dimensions.py       # 维度枚举定义
│   ├── reporter.py         # 核心上报类（异步、非阻塞）
│   ├── collectors.py       # 采集器基类（完全不侵入）
│   └── utils.py            # 工具函数
└── main.py                 # 入口示例
```

### 维度枚举

```python
from collectorsdk import Source, Subject, CollectType, CollectObject

# 采集来源
Source.LIANGXIN    # 粮信网
Source.MYSTEEL     # 我的钢铁网
Source.CHINAGRAIN  # 中华粮网

# 采集主体
Subject.CORN       # 玉米
Subject.WHEAT      # 小麦
Subject.RICE       # 稻米
Subject.SOYBEAN     # 大豆

# 采集类型
CollectType.LOGIN_CRAWL    # 模拟登录爬取
CollectType.PUBLIC_CRAWL   # 公开页面爬取
CollectType.API_COLLECT    # 接口采集
CollectType.FILE_DOWNLOAD # 文件下载

# 采集对象
CollectObject.DAILY_REPORT    # 日报
CollectObject.WEEKLY_REPORT   # 周报
CollectObject.MONTHLY_REPORT # 月报
CollectObject.PRICE          # 价格
CollectObject.NEWS           # 资讯
```

## 4. 使用方式

### 4.1 完全解耦模式（推荐）

继承 `BaseCollector`，采集代码完全不调用任何 HTTP/上报：

```python
from collectorsdk import (
    ReporterConfig, BaseCollector,
    Source, Subject, CollectType, CollectObject
)

class LiangxinwangCollector(BaseCollector):
    """粮信网采集器"""

    def __init__(self, config: ReporterConfig, task_id: int, username: str, password: str):
        super().__init__(
            config=config,
            task_id=task_id,
            source=Source.LIANGXIN.value,
            subject=Subject.CORN.value,
            coll_type=CollectType.LOGIN_CRAWL.value,
            obj=CollectObject.DAILY_REPORT.value,
            remark="粮信网玉米晨报采集"
        )
        self.username = username
        self.password = password

    def collect(self) -> int:
        """
        执行采集逻辑

        注意：此方法内完全不调用任何 HTTP/上报
        所有日志使用 self.log_* 方法
        所有数据提交使用 self.submit_report 方法
        """
        self.log_info("开始登录粮信网...")

        # 纯采集逻辑
        # browser = p.chromium.launch(headless=True)
        # ...

        # 提交数据
        self.submit_report(
            title="（2026年4月29日）玉米晨报",
            source=Source.LIANGXIN.value,
            url="https://www.chinagrain.cn/report/1",
            variety="玉米",
            report_type="晨报",
            content="今日国内玉米价格震荡运行为主...",
            publish_time="2026-04-29T08:00:00"
        )

        return 1

# 一行启动
config = ReporterConfig.from_env()
collector = LiangxinwangCollector(config, task_id=1, username="xxx", password="xxx")
result = collector.run()
```

### 4.2 手动模式

直接使用 `CollectorReporter`，灵活控制上报时机：

```python
from collectorsdk import CollectorReporter, ReporterConfig, Source

config = ReporterConfig.from_env()
reporter = CollectorReporter(config)

# 启动执行
result = reporter.report_start(task_id=1)
execution_id = result.get("executionId")

# 上报日志
reporter.log_info("开始采集...")

# 模拟采集
for i in range(5):
    reporter.report_progress(i + 1)

# 提交数据
reporter.submit_report(
    title="测试报告",
    source=Source.LIANGXIN.value,
    url="https://example.com/1",
    variety="玉米",
    report_type="晨报",
    content="测试内容",
    publish_time="2026-04-29T08:00:00"
)

# 完成
reporter.report_complete("success", 5)
```

### 4.3 关闭上报模式

设置 `enabled=False`，上报完全关闭，采集代码正常运行：

```python
config = ReporterConfig(enabled=False)
collector = LiangxinwangCollector(config, task_id=1, username="xxx", password="xxx")
result = collector.run()  # 采集正常完成，但不上报
```

## 5. REST API 端点

SDK 自动调用以下端点：

| 端点 | 方法 | 说明 |
|------|------|------|
| `/collector/exec/start` | POST | 启动采集任务 |
| `/collector/exec/{id}/progress` | POST | 上报进度 |
| `/collector/exec/{id}/log` | POST | 上报日志 |
| `/collector/exec/{id}/data` | POST | 上报数据 |
| `/collector/exec/{id}/error` | POST | 上报错误 |
| `/collector/exec/{id}/complete` | POST | 完成执行 |

## 6. SDK 自动管理

### 6.1 自动注册

Python SDK 启动时会自动向 Java 后端注册：

```python
# SDK 自动注册（无需手动调用）
config = ReporterConfig.from_env()
reporter = CollectorReporter(config)
# 自动注册到 /collector/register
# 自动开始心跳（每分钟）
```

### 6.2 心跳机制

SDK 每分钟自动发送心跳到 `/collector/heartbeat`，Java 后端据此判断采集器是否在线。

### 6.3 自动下线

Python 进程退出时，SDK 自动调用 `/collector/offline` 通知 Java 后端。

### 6.4 Java 端管理

Java 后端管理以下接口：

| 端点 | 方法 | 说明 |
|------|------|------|
| `/collector/register` | POST | 注册采集器 |
| `/collector/heartbeat` | POST | 心跳 |
| `/collector/offline` | POST | 下线 |
| `/collector/online` | GET | 获取在线采集器 |
| `/collector/list` | GET | 采集器列表（分页） |
| `/collector/version` | GET | SDK版本信息 |

Java 后端定时清理超过 5 分钟无心跳的采集器（标记为 offline）。

## 7. 配置

### 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| API_BASE | Java 后端地址 | http://localhost:8080/api |
| REPORTER_ENABLED | 上报开关 | true |
| REPORTER_RETRY_TIMES | 重试次数 | 3 |
| REPORTER_ASYNC_MODE | 异步模式 | true |
| TASK_ID | 任务ID | 1 |

### 代码配置

```python
config = ReporterConfig(
    enabled=True,
    api_base="http://localhost:8080/api",
    retry_times=3,
    retry_delay=1.0,
    timeout=5.0,
    cache_size=100,
    async_mode=True
)
```

## 8. 日志规范

采集器应记录以下关键日志：

| 阶段 | 日志级别 | 日志内容 |
|------|----------|----------|
| 开始登录 | INFO | 开始登录{网站名称} |
| 登录结果 | INFO/ERROR | 登录成功 / 登录失败: {原因} |
| 访问页面 | INFO | 访问页面: {URL} |
| 发现数据 | INFO | 发现 {N} 篇报告 |
| 采集进度 | INFO | 已采集 {M}/{N}: {标题} |
| 采集失败 | WARN | 采集报告失败: {原因} |
| 任务完成 | INFO | 采集完成，共 {N} 条数据 |

## 9. 错误处理

```python
try:
    # 采集逻辑
except LoginFailedException as e:
    self.log_error(f"登录失败: {e}")
    self.reporter.report_error(f"登录失败: {e}")
    self.reporter.report_complete("failed", 0)

except Exception as e:
    self.log_error(f"采集异常: {e}")
    raise
```

## 10. Java 对接

Java 后端接收上报时，会自动关联以下维度：

```sql
-- t_collection_log 表新增字段
ALTER TABLE t_collection_log ADD COLUMN subject VARCHAR(50);
ALTER TABLE t_collection_log ADD COLUMN coll_type VARCHAR(50);
ALTER TABLE t_collection_log ADD COLUMN coll_object VARCHAR(50);

-- 采集器信息表
CREATE TABLE t_collector_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    collector_name VARCHAR(100),
    sdk_version VARCHAR(20),
    source VARCHAR(50),
    subject VARCHAR(50),
    coll_type VARCHAR(50),
    coll_object VARCHAR(50),
    status VARCHAR(20),
    last_heartbeat TIMESTAMP,
    registered_at TIMESTAMP
);
```

Dashboard 可按 source、subject、coll_type 等维度进行统计。
