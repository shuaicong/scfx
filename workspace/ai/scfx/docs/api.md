# API 接口文档

## 基础信息

- 基础路径：`http://localhost:8080/api`
- 数据格式：JSON
- 认证方式：JWT Token（预留）

## 响应格式

```json
{
  "code": 200,
  "message": "操作成功",
  "data": { ... },
  "timestamp": 1714281600000
}
```

### 响应码说明

| code | 说明 |
|------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未认证 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

---

## 1. 仪表板

### 1.1 获取仪表板数据

```
GET /dashboard
```

**响应示例：**
```json
{
  "code": 200,
  "data": {
    "systemStatus": {
      "runningTasks": 0,
      "todaySuccess": 5,
      "todayFailed": 1,
      "todayReports": 10,
      "successRate": 83.33
    },
    "sourceStats": [
      {
        "sourceName": "liangxinwang",
        "displayName": "粮信网",
        "todaySuccess": 5,
        "todayFailed": 1,
        "todayReports": 10,
        "successRate": 83.33,
        "lastCollectTime": "2026-04-28T10:30:00"
      }
    ],
    "recentLogs": [...],
    "pendingAlerts": {...}
  }
}
```

### 1.2 获取统计信息

```
GET /dashboard/statistics?period=today
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| period | string | 否 | 统计周期：today/week/month |

---

## 2. 采集任务

### 2.1 获取任务列表

```
GET /tasks?page=1&size=20&status=&source=
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | int | 否 | 页码，默认1 |
| size | int | 否 | 每页数量，默认20 |
| status | string | 否 | 状态过滤 |
| source | string | 否 | 数据源过滤 |

**响应示例：**
```json
{
  "code": 200,
  "data": {
    "records": [
      {
        "id": 1,
        "taskName": "粮信网-玉米晨报采集",
        "taskType": "scheduled",
        "sourceName": "liangxinwang",
        "sourceUrl": "https://www.chinagrain.cn/report/",
        "status": "pending",
        "lastExecutionTime": "2026-04-28T10:30:00",
        "successCount": 100,
        "failedCount": 5
      }
    ],
    "total": 1,
    "size": 20,
    "current": 1
  }
}
```

### 2.2 获取任务详情

```
GET /tasks/{id}
```

### 2.3 创建任务

```
POST /tasks
Content-Type: application/json

{
  "taskName": "粮信网-玉米晨报采集",
  "taskType": "scheduled",
  "sourceName": "liangxinwang",
  "sourceUrl": "https://www.chinagrain.cn/report/"
}
```

### 2.4 更新任务状态

```
PUT /tasks/{id}/status?status=running
```

---

## 3. 采集器回调（Python 使用）

### 3.1 启动采集任务

```
POST /collector/exec/start
Content-Type: application/json

{
  "taskId": 1
}
```

**响应示例：**
```json
{
  "code": 200,
  "data": {
    "executionId": "550e8400-e29b-41d4-a716-446655440000",
    "taskId": 1,
    "startTime": "2026-04-28T10:30:00"
  }
}
```

### 3.2 上报日志

```
POST /collector/exec/{executionId}/log
Content-Type: application/json

{
  "level": "INFO",
  "message": "登录成功，开始采集报告列表"
}
```

| level 可选值 | 说明 |
|-------------|------|
| DEBUG | 调试信息 |
| INFO | 一般信息 |
| WARN | 警告信息 |
| ERROR | 错误信息 |

### 3.3 上报进度

```
POST /collector/exec/{executionId}/progress
Content-Type: application/json

{
  "collectedCount": 5
}
```

### 3.4 上报采集数据

```
POST /collector/exec/{executionId}/data
Content-Type: application/json

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

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| title | string | 是 | 报告标题 |
| source | string | 是 | 数据源标识 |
| url | string | 是 | 原始链接 |
| variety | string | 否 | 品种：玉米/小麦/稻米/大豆 |
| reportType | string | 否 | 报告类型：晨报/日报/周报/月报 |
| content | string | 否 | 正文内容 |
| publishTime | string | 否 | 发布时间 |

### 3.5 上报错误

```
POST /collector/exec/{executionId}/error
Content-Type: application/json

{
  "errorMessage": "登录失败：用户名或密码错误"
}
```

### 3.6 完成执行

```
POST /collector/exec/{executionId}/complete
Content-Type: application/json

{
  "status": "success",
  "collectedCount": 10
}
```

| status 可选值 | 说明 |
|--------------|------|
| success | 成功完成 |
| failed | 执行失败 |

### 3.7 获取执行状态

```
GET /collector/exec/{executionId}
```

---

## 4. 报告管理

### 4.1 获取报告列表

```
GET /reports?page=1&size=20&source=&variety=&reportType=
```

### 4.2 获取报告详情

```
GET /reports/{id}
```

### 4.3 获取最近报告

```
GET /reports/recent?limit=10
```

---

## 5. 日志查询

### 5.1 获取日志列表

```
GET /logs?page=1&size=50&level=&source=&startTime=&endTime=
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | int | 否 | 页码 |
| size | int | 否 | 每页数量 |
| level | string | 否 | 日志级别 |
| source | string | 否 | 数据源 |
| startTime | string | 否 | 开始时间 |
| endTime | string | 否 | 结束时间 |

### 5.2 获取日志统计

```
GET /logs/stats
```

---

## 6. 告警管理

### 6.1 获取告警列表

```
GET /alerts?page=1&size=20&level=&status=
```

### 6.2 获取告警统计

```
GET /alerts/stats
```

### 6.3 处理告警

```
PUT /alerts/{id}/resolve?resolvedBy=admin
```

---

## 7. 采集触发

### 7.1 触发粮信网采集

```
POST /collection/liangxinwang
```

### 7.2 获取采集状态

```
GET /collection/status
```

---

## 错误码详解

| 错误码 | HTTP状态码 | 说明 | 解决方案 |
|--------|-----------|------|----------|
| TASK_NOT_FOUND | 404 | 任务不存在 | 检查taskId是否正确 |
| TASK_ALREADY_RUNNING | 400 | 任务正在执行中 | 等待当前执行完成 |
| EXECUTION_NOT_FOUND | 404 | 执行记录不存在 | 检查executionId是否正确 |
| SOURCE_NOT_ENABLED | 400 | 数据源未启用 | 在配置中启用数据源 |
| COLLECTION_TIMEOUT | 408 | 采集超时 | 增加超时时间或检查网络 |

---

## Python 调用示例

```python
import requests

API_BASE = "http://localhost:8080/api"

# 1. 启动采集
resp = requests.post(f"{API_BASE}/collector/exec/start", json={"taskId": 1})
execution_id = resp.json()["data"]["executionId"]

# 2. 上报日志
requests.post(f"{API_BASE}/collector/exec/{execution_id}/log", json={
    "level": "INFO",
    "message": "登录成功"
})

# 3. 提交数据
requests.post(f"{API_BASE}/collector/exec/{execution_id}/data", json={
    "title": "玉米晨报",
    "source": "liangxinwang",
    "url": "https://example.com/123",
    "variety": "玉米",
    "reportType": "晨报"
})

# 4. 完成
requests.post(f"{API_BASE}/collector/exec/{execution_id}/complete", json={
    "status": "success",
    "collectedCount": 10
})
```
