# 海南储备集团粮食市场智能分析平台 - 架构文档

## 1. 系统架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                           前端 (Vue 3)                               │
│                     http://localhost:3000                            │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      网关层 / Nginx (生产)                           │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      Java 后端 (Spring Boot)                         │
│                       http://localhost:8080/api                     │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌──────────┐ │
│  │  Dashboard  │  │   Tasks     │  │  Reports    │  │  Alerts  │ │
│  │ Controller  │  │ Controller  │  │ Controller  │  │Controller│ │
│  └─────────────┘  └─────────────┘  └─────────────┘  └──────────┘ │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌──────────┐ │
│  │   Logs      │  │  Collector  │  │  Stats      │  │   Auth   │ │
│  │ Controller  │  │ Controller  │  │ Controller  │  │Controller│ │
│  └─────────────┘  └─────────────┘  └─────────────┘  └──────────┘ │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                    ┌───────────────┼───────────────┐
                    ▼               ▼               ▼
          ┌─────────────┐   ┌─────────────┐   ┌─────────────┐
          │     H2      │   │   MySQL     │   │    Redis    │
          │  (开发模式)  │   │ (生产模式)  │   │  (可选缓存)  │
          └─────────────┘   └─────────────┘   └─────────────┘
```

## 2. Python 采集器架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Python 采集器 (独立进程)                           │
│                                                                      │
│  ┌───────────────┐    ┌───────────────┐    ┌───────────────────┐    │
│  │    Login      │───→│   Navigate    │───→│     Scrape       │    │
│  │   Module      │    │   Module     │    │     Module       │    │
│  └───────────────┘    └───────────────┘    └───────────────────┘    │
│          │                                          │               │
│          ▼                                          ▼               │
│  ┌───────────────┐                        ┌───────────────────┐    │
│  │    Cookie     │                        │     Parser       │    │
│  │  Persistence  │                        │     Module       │    │
│  └───────────────┘                        └───────────────────┘    │
│                                                    │               │
│                                                    ▼               │
│  ┌───────────────┐    ┌───────────────┐    ┌───────────────────┐    │
│  │  REST API     │←───│  REST API     │←───│   REST API        │    │
│  │  /start      │    │  /progress    │    │  /data           │    │
│  └───────────────┘    └───────────────┘    └───────────────────┘    │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
                          ┌───────────────────┐
                          │   Java Backend    │
                          │  Task Management  │
                          └───────────────────┘
```

## 3. 采集流程时序

```
Python Collector                    Java Backend                      Database
      │                                 │                                │
      │──── POST /collector/exec/start ──→                              │
      │     {taskId: 1}                                                │
      │← ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─│
      │     {executionId: "xxx"}                                       │
      │                                                                  │
      │──── POST /collector/exec/{id}/log ──→                         │
      │     {level: "INFO", message: "开始登录"}                       │
      │                                 │                                │
      │                                 │──── INSERT log ───────────────→│
      │                                                                  │
      │──── POST /collector/exec/{id}/log ──→                         │
      │     {level: "INFO", message: "登录成功"}                       │
      │                                                                  │
      │──── POST /collector/exec/{id}/progress ──→                     │
      │     {collectedCount: 5}                                         │
      │                                                                  │
      │──── POST /collector/exec/{id}/data ──→                         │
      │     {title, content, url, variety, ...} ──────────────────────→│
      │                                                                  │
      │──── POST /collector/exec/{id}/complete ──→                     │
      │     {status: "success", collectedCount: 10}                     │
      │                                 │                                │
      │                                 │──── UPDATE task ──────────────→│
      │                                 │──── INSERT execution ────────→│
```

## 4. Java 后端模块划分

### 4.1 Controller 层

| Controller | 路径 | 说明 |
|------------|------|------|
| DashboardController | /dashboard | 仪表板数据 |
| TaskController | /tasks | 任务 CRUD |
| CollectionController | /collection | 触发采集 |
| CollectorController | /collector/exec | 采集器回调 |
| ReportController | /reports | 报告管理 |
| LogController | /logs | 日志查询 |
| AlertController | /alerts | 告警管理 |
| StatsController | /stats | 统计查询 |

### 4.2 Service 层

| Service | 说明 |
|---------|------|
| CollectionTaskService | 任务管理 |
| TaskExecutionService | 执行生命周期管理 |
| LiangxinwangCollector | 粮信网采集器 |
| ReportService | 报告管理 |
| CollectionLogService | 日志管理 |
| AlertService | 告警管理 |

### 4.3 Entity 层

| Entity | 表名 | 说明 |
|--------|------|------|
| CollectionTask | t_collection_task | 采集任务 |
| TaskExecution | t_task_execution | 执行记录 |
| Report | t_report | 报告数据 |
| CollectionLog | t_collection_log | 日志记录 |
| AlertRecord | t_alert_record | 告警记录 |
| Price | t_price | 价格数据 |

## 5. 前端页面结构

```
/                         # 首页/重定向到 /dashboard
/dashboard               # 仪表板
/collection              # 采集任务管理
/reports                 # 报告列表
/logs                    # 日志查询
/alerts                  # 告警管理
/settings                # 系统设置
```

## 6. 部署架构（生产环境）

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Nginx (反向代理)                             │
│                     负载均衡 + SSL 终结                              │
└─────────────────────────────────────────────────────────────────────┘
                    │                    │
          ┌─────────┴──┐         ┌────────┴────────┐
          │  Frontend  │         │   Java Backend   │
          │   :3000    │         │     :8080        │
          └─────────────┘         └──────────────────┘
                                              │
                              ┌───────────────┼───────────────┐
                              ▼               ▼               ▼
                    ┌─────────────┐   ┌─────────────┐   ┌─────────────┐
                    │    MySQL    │   │    Redis    │   │  Vector DB  │
                    │   :3306     │   │   :6379     │   │  (Milvus)   │
                    └─────────────┘   └─────────────┘   └─────────────┘
```

## 7. 安全架构

### 7.1 认证
- JWT Token
- Token 有效期 24 小时
- 刷新机制

### 7.2 授权
- 基于角色的访问控制（RBAC）
- 管理员 / 操作员 / 查看者

### 7.3 接口安全
- 请求频率限制
- 参数校验
- SQL 注入防护

## 8. 配置外部化

```yaml
# application-prod.yml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/grain_platform
    username: grain_user
    password: ${DB_PASSWORD}

app:
  collection:
    sources:
      liangxinwang:
        enabled: true
      mysteel:
        enabled: false
```

## 9. 日志规范

### 9.1 日志级别
- ERROR: 采集失败、系统异常
- WARN: 重试、配置警告
- INFO: 任务开始/结束、步骤记录
- DEBUG: 详细调试信息

### 9.2 日志格式
```
[TIMESTAMP] [LEVEL] [SOURCE] [EXECUTION_ID] Message
2026-04-28 10:30:00 INFO liangxinwang abc123 开始登录粮信网
```

## 10. 技术选型理由

| 组件 | 选型 | 理由 |
|------|------|------|
| 爬虫语言 | Python | 生态丰富、Playwright 支持好、解析库强大 |
| 后端框架 | Spring Boot | 生态成熟、稳定、企业级 |
| 数据库 | H2/MySQL | 开发便捷、生产可靠 |
| 前端框架 | Vue 3 | 响应式、组件丰富 |
| 图表 | ECharts | 中文支持好、性能优秀 |
