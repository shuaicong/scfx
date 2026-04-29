# 更新日志

所有重要的版本更新都会记录在此文件中。

格式规范：
- **新增**: 新功能
- **优化**: 功能改进
- **修复**: Bug 修复
- **重构**: 代码重构
- **文档**: 文档更新

---

## [1.1.0] - 2026-04-28

### 新增

- **仪表板多数据源统计**
  - 按数据源分组展示统计信息
  - 支持按源统计成功/失败/报告数
  - 显示最后采集时间

- **采集任务执行管理**
  - 新增 `TaskExecutionService` 管理执行生命周期
  - 新增 `CollectorController` 提供 REST API
  - 支持执行互斥（同一任务同时只能有一个执行）

- **Python 采集器集成**
  - `/collector/exec/start` 启动采集
  - `/collector/exec/{id}/log` 上报日志
  - `/collector/exec/{id}/progress` 上报进度
  - `/collector/exec/{id}/data` 上报数据
  - `/collector/exec/{id}/error` 上报错误
  - `/collector/exec/{id}/complete` 完成执行

### 优化

- **日志系统改进**
  - 增加 `executionId` 字段关联日志与执行记录
  - 详细步骤日志记录

- **数据库优化**
  - H2 改为文件存储模式
  - 修复中文乱码问题
  - 添加必要索引

- **前端 UI 改进**
  - Dashboard.vue 重构为按数据源展示
  - 优化卡片布局和样式

### 修复

- 修复 H2 Console 无法连接问题（内存数据库改为文件数据库）
- 修复任务名称中文乱码问题
- 修复多个 Vue 文件语法错误

### 文档

- 新增 `docs/requirements.md` 需求文档
- 新增 `docs/architecture.md` 架构文档
- 新增 `docs/db-design.md` 数据库设计文档
- 新增 `docs/python-collector.md` Python 采集器开发指南
- 更新 `README.md`

---

## [1.0.0] - 2026-04-28

### 新增

- **基础框架**
  - Spring Boot 3.2 后端项目
  - Vue 3 + TypeScript 前端项目
  - H2 开发数据库

- **数据库表**
  - `t_collection_task` 采集任务表
  - `t_task_execution` 任务执行记录表
  - `t_report` 报告表
  - `t_collection_log` 采集日志表
  - `t_alert_record` 告警记录表
  - `t_price` 价格数据表

- **核心功能**
  - 仪表板展示（统计卡片、日志时间线、告警统计）
  - 任务管理（CRUD、分页查询）
  - 采集日志查询
  - 告警管理

- **采集器**
  - `LiangxinwangCollector` 粮信网采集器（模拟模式）
  - Cookie/Session 持久化支持
  - 报告数据保存

- **API 端点**
  - `GET /dashboard` 仪表板数据
  - `GET /tasks` 任务列表
  - `POST /tasks` 创建任务
  - `GET /logs` 日志查询
  - `GET /alerts` 告警查询
  - `POST /collection/liangxinwang` 触发采集

---

## 待规划

### v1.2.0

- [ ] 定时任务调度（基于 Quartz 或 Spring Schedule）
- [ ] 真正的 Playwright 集成采集
- [ ] 多数据源支持（我的钢铁网、中华粮网）
- [ ] 报告详情页

### v1.3.0

- [ ] 用户认证与权限管理
- [ ] JWT Token 认证
- [ ] RBAC 权限控制

### v2.0.0

- [ ] AI 智能问答
- [ ] 向量数据库集成（Milvus/Pinecone）
- [ ] RAG 检索增强生成
- [ ] 自动报告生成（周报/月报）

---

## 版本说明

- **开发版**: x.x.x-dev
- **正式版**: x.x.x
- **预发布**: x.x.x-rc.1

## 升级指南

### 从 1.0.0 升级到 1.1.0

1. 更新数据库 `t_collection_log` 表添加 `execution_id` 字段：
```sql
ALTER TABLE t_collection_log ADD COLUMN execution_id VARCHAR(50);
```

2. 重启后端服务

3. 前端重新构建：
```bash
cd frontend && npm run build
```
