# 粮信网采集集成到采集管理系统设计

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将粮信网玉米晨报/日报采集纳入采集管理系统，实现统一管理、实时日志、定时执行

**Architecture:** 前后端分离 + Python 常驻采集服务 + 轮询获取实时日志

**Tech Stack:** Spring Boot + Vue + Playwright + APScheduler + MySQL

---

## 一、整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端 (Vue + Element Plus)                 │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐           │
│  │  脚本管理    │ │  执行记录    │ │  实时日志    │           │
│  │  (触发执行)  │ │  (历史/状态) │ │  (轮询)     │           │
│  └──────────────┘ └──────────────┘ └──────────────┘           │
└────────────────────────────┬──────────────────────────────────┘
                             │ HTTP + JSON
┌────────────────────────────▼──────────────────────────────────┐
│                        后端 (Spring Boot)                       │
│  ┌────────────────────┐  ┌────────────────────┐               │
│  │ CollectionScript    │  │ TaskExecution       │               │
│  │ - 脚本元数据       │  │ - 执行记录         │               │
│  │ - 采集配置         │◄─┤ - 状态/结果       │               │
│  │ - 定时配置         │  │ - 采集数量         │               │
│  └─────────┬──────────┘  └────────────────────┘               │
│            │                                                    │
│            │ HTTP POST /collector/execute                       │
└────────────┼─────────────────────────────────────────────────┘
             │ HTTP
┌────────────▼─────────────────────────────────────────────────┐
│                  Python 采集调度服务 (常驻)                   │
│  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐ │
│  │ HTTP API       │  │ APScheduler    │  │ Collector      │ │
│  │ /execute      │  │ 晨报: 9:30   │  │ Playwright    │ │
│  │ /status       │  │ 日报: 18:30  │  │ 预热浏览器    │ │
│  │ /logs         │  │              │  │ 智能冷却     │ │
│  └────────────────┘  └────────────────┘  └────────────────┘ │
│                                                             │
│  冷却策略：执行完 2小时无任务 → 释放浏览器                 │
└─────────────────────────────────────────────────────────────┘
```

---

## 二、执行流程

### 2.1 手动执行

```
用户点击"执行"
  │
  ├─► 前端 POST /api/scripts/{id}/execute
  │     │
  │     ├─► 后端创建 TaskExecution (status=pending)
  │     ├─► 后端 POST http://py-service:5001/execute
  │     │     {scriptId, reportType, executionId}
  │     │
  │     └─► 前端开始轮询 GET /api/scripts/executions/{id}/logs?offset=0
  │
  ├─► Python 服务
  │     ├─► 更新状态为 running
  │     ├─► 预热/复用浏览器
  │     ├─► 执行 LiangxinCollector
  │     ├─► 每条日志 POST /logs/{executionId}
  │     └─► 完成返回 {success, collectedCount, error}
  │
  └─► 后端更新 TaskExecution
        {status: success/failed, collectedCount, durationMs}
```

### 2.2 定时执行

```
TriggerScheduleService (每分钟检查)
  │
  ├─► 时间匹配检查 (cron 或 repeat 配置)
  │
  └─► 触发 /collector/execute (同手动执行流程)
```

---

## 三、接口设计

### 3.1 Python 采集服务接口

| 接口 | 方法 | 参数 | 返回 | 说明 |
|------|------|------|------|------|
| `/execute` | POST | `{scriptId, reportType, executionId}` | `{executionId}` | 执行采集 |
| `/status/{executionId}` | GET | - | `{status, collectedCount, error}` | 获取状态 |
| `/logs/{executionId}` | GET | `?offset=0` | `[{offset, level, message, timestamp}]` | 获取日志 |
| `/health` | GET | - | `{ok: true}` | 健康检查 |

### 3.2 后端接口

| 接口 | 方法 | 参数 | 返回 | 说明 |
|------|------|------|------|------|
| `/scripts/{id}/execute` | POST | - | `{executionId, status}` | 触发执行 |
| `/scripts/{id}/execute-now` | POST | `{reportType}` | `{executionId}` | 立即执行 |
| `/scripts/executions/{id}` | GET | - | `TaskExecution` | 执行详情 |
| `/scripts/executions/{id}/logs` | GET | `?offset=0` | `{logs[], nextOffset}` | 执行日志 |
| `/scripts/executions/{id}/cancel` | POST | - | - | 取消执行 |

---

## 四、数据模型

### 4.1 TaskExecution 变更

```java
public class TaskExecution {
    String executionId;      // 执行唯一标识
    Long scriptId;           // 关联脚本ID
    String triggerType;       // manual/scheduled/cron
    String status;            // pending/running/success/failed/cancelled
    LocalDateTime startTime;
    LocalDateTime endTime;
    Long durationMs;          // 耗时毫秒
    Integer collectedCount;     // 采集数量
    String errorMessage;       // 错误信息
    String reportType;         // 晨报/日报
}
```

### 4.2 TaskExecutionLog 表

```sql
CREATE TABLE IF NOT EXISTS t_task_execution_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    execution_id VARCHAR(50) NOT NULL,
    offset INT NOT NULL,
    level VARCHAR(20),        -- INFO/WARN/ERROR
    message VARCHAR(4000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_execution_offset (execution_id, offset)
);
```

---

## 五、前端改动

### 5.1 脚本管理 Tab

- 点击"执行"按钮时，传入 `reportType` 参数（晨报/日报）
- 执行后弹出"执行详情"抽屉，显示实时日志

### 5.2 实时日志展示

```javascript
// 轮询获取日志
let logOffset = 0
const pollLogs = setInterval(async () => {
  const res = await GET(`/scripts/executions/${executionId}/logs?offset=${logOffset}`)
  if (res.code === 200) {
    appendLogs(res.data.logs)  // 追加日志到面板
    logOffset = res.data.nextOffset

    // 采集完成，停止轮询
    if (res.data.status === 'success' || res.data.status === 'failed') {
      clearInterval(pollLogs)
    }
  }
}, 2000)  // 2秒轮询
```

### 5.3 执行记录 Tab

- 显示每次执行的：开始时间、结束时间、耗时、状态、采集数量
- 支持查看执行详情（弹窗显示日志）

---

## 六、Python 服务改动

### 6.1 新增 collector_server.py

```python
from flask import Flask, request, jsonify
from collectorsdk.collectors.liangxin import LiangxinCollector
import threading

app = Flask(__name__)

# 执行状态存储
executions = {}  # executionId -> {status, logs, collectedCount, error}

@app.route('/execute', methods=['POST'])
def execute():
    data = request.json
    script_id = data['scriptId']
    report_type = data['reportType']  # morning/evening
    execution_id = data['executionId']

    # 后台执行采集
    thread = threading.Thread(target=run_collection, args=(execution_id, script_id, report_type))
    thread.start()

    return jsonify({'executionId': execution_id})

def run_collection(execution_id, script_id, report_type):
    # 执行采集，更新状态，存储日志
    collector = LiangxinCollector(...)
    result = collector.run()
    # 更新 executions[execution_id]

@app.route('/logs/<execution_id>')
def get_logs(execution_id):
    offset = int(request.args.get('offset', 0))
    logs = executions[execution_id]['logs'][offset:]
    return jsonify({
        'logs': logs,
        'nextOffset': offset + len(logs),
        'status': executions[execution_id]['status']
    })
```

### 6.2 冷却策略

```python
class BrowserPool:
    def __init__(self, idle_timeout=7200):  # 2小时
        self.browser = None
        self.last_used = 0
        self.idle_timeout = idle_timeout

    def get_browser(self):
        if self.browser and time.time() - self.last_used < self.idle_timeout:
            return self.browser
        # 创建新浏览器
        self.browser = ...
        return self.browser

    def release_if_idle(self):
        if time.time() - self.last_used > self.idle_timeout:
            self.browser.close()
            self.browser = None
```

---

## 七、文件变更清单

| 文件路径 | 操作 | 说明 |
|---------|------|------|
| `python-collector-sdk/scheduler/collector_server.py` | 新增 | Flask HTTP API 服务 |
| `python-collector-sdk/scheduler/corn_scheduler.py` | 重构 | 改为被动触发，移除 APScheduler |
| `python-collector-sdk/scheduler/browser_pool.py` | 新增 | 浏览器连接池 + 冷却策略 |
| `backend/CollectorController.java` | 新增 | 转发请求到 Python 服务 |
| `backend/TaskExecution.java` | 修改 | 添加 reportType 字段 |
| `backend/TaskExecutionLog.java` | 新增 | 日志实体 |
| `backend/TaskExecutionMapper.java` | 修改 | 添加日志查询方法 |
| `backend/TaskExecutionService.java` | 修改 | 添加日志存储/查询 |
| `schema.sql` | 修改 | 添加 report_type 列，创建 task_execution_log 表 |
| `frontend/src/views/collection/Collection.vue` | 修改 | 添加实时日志轮询、执行详情抽屉 |
| `frontend/src/api/index.ts` | 修改 | 添加日志查询接口 |

---

## 八、验证方案

1. **单元测试**
   - Python: 测试 collector_server 各接口
   - Java: 测试 Controller 转发逻辑

2. **集成测试**
   - 前端点击执行 → 后端转发 → Python 执行 → 日志返回 → 前端展示
   - 验证完整流程

3. **定时任务测试**
   - 配置 cron 表达式，验证定时触发

4. **冷却策略测试**
   - 空闲 2 小时后验证浏览器释放
   - 复用浏览器验证启动速度

---

## 九、详细逻辑设计

### 9.1 触发执行

#### 触发方式
- 脚本管理 tab → 点击"执行"按钮
- 需要选择报告类型：晨报 / 日报

#### 边界情况
| 情况 | 处理 |
|------|------|
| 脚本正在执行中 (status=running) | 按钮置灰，禁止重复点击 |
| 脚本已禁用 | 提示"请先启用脚本" |
| 网络超时 | 显示错误提示 |

#### 后端处理流程
```
1. 验证脚本存在且状态=enabled
2. 检查是否有正在执行的记录 (status=running)
   → 有：返回错误"脚本正在执行中"
3. 创建 TaskExecution (status=pending)
4. 异步转发请求到 Python 服务
5. 返回 {executionId, status: "pending"}
```

#### 补偿机制
Python 服务挂了时：
- 保存执行记录 status=pending
- 返回前端"任务已排队，等待调度服务响应"
- 后台每分钟重试 pending 状态的任务

---

### 9.2 执行状态管理

#### 状态定义
```
pending    → 等待执行
running    → 执行中
success    → 执行成功
failed     → 执行失败
cancelled  → 已取消
```

#### 状态流转
```
用户点击执行
    ↓
[pending] ──► Python服务接单 ──► [running]
    ↑                               │
    │                    执行失败    ↓
    │                    [failed] ◄──┘
    │
    │                    执行成功
    └────────────────► [success]

用户取消 ──► [cancelled] (仅 pending/running 可取消)
```

#### 状态持久化
- **目的：** 前端关闭后仍能查看状态，支持断线重连
- **存储：** TaskExecution 表（主状态）+ TaskExecutionLog 表（日志）

---

### 9.3 Python 服务架构

#### 核心模块
```
collector_server.py
├── Flask HTTP API          # 接收执行请求
├── BrowserPool             # 浏览器连接池 + 冷却
├── ExecutionManager         # 执行状态管理
└── LiangxinCollector       # 实际采集逻辑
```

#### ExecutionManager 设计
```python
class ExecutionManager:
    def __init__(self):
        self.executions = {}  # executionId -> ExecutionContext
        self.lock = threading.Lock()

    def start(self, execution_id, script_id, report_type):
        ctx = ExecutionContext(execution_id, script_id, report_type)
        with self.lock:
            self.executions[execution_id] = ctx
        thread = threading.Thread(target=self._run, args=(ctx,))
        thread.start()

    def get_logs(self, execution_id, offset):
        with self.lock:
            ctx = self.executions.get(execution_id)
            return ctx.logs[offset:] if ctx else []

    def get_status(self, execution_id):
        ctx = self.executions.get(execution_id)
        return ctx.status if ctx else None
```

#### 并发控制
- 同一 executionId 只执行一次（幂等）
- 同一脚本同时只能有一个 running 的执行

---

### 9.4 日志系统

#### 日志流向
```
LiangxinCollector
    ↓ (每条日志)
ExecutionContext.logs.append(log)
    ↓ (同时)
POST /backend/logs/{executionId}
    ↓
后端存储到 TaskExecutionLog 表
    ↓
前端轮询 GET /backend/logs/{executionId}?offset=X
```

#### 日志格式
```json
{
  "offset": 0,
  "level": "INFO",
  "message": "登录成功，检测到用户问候语",
  "timestamp": "2026-05-15T09:30:01"
}
```

#### 边界情况处理
| 情况 | 处理 |
|------|------|
| 前端中途关闭 | 日志继续写入 DB，后端标记执行完成 |
| 前端重连 | 从 offset=0 拉取，UI 追加显示 |
| 日志太多 (10000+条) | DB 建索引，定期清理（保留 30 天） |
| Python 服务崩溃 | 标记为 failed，错误信息记录 |
| 日志写入失败 (DB 挂了) | 先存内存，批写重试 |

#### 前端轮询策略
```javascript
let offset = 0
let polling = true

function pollLogs() {
  if (!polling) return

  fetch(`/api/scripts/executions/${execId}/logs?offset=${offset}`)
    .then(res => res.json())
    .then(data => {
      appendLogs(data.logs)  // UI 追加
      offset = data.nextOffset

      if (data.status === 'running') {
        setTimeout(pollLogs, 2000)  // 2秒后再拉
      } else {
        polling = false  // 执行结束
        showFinalStatus(data.status)
      }
    })
}

window.onbeforeunload = () => polling = false
```

---

### 9.5 执行结果回传

#### Python → 后端
采集完成后发送：
```json
{
  "executionId": "abc123",
  "status": "success",
  "collectedCount": 3,
  "error": null,
  "durationMs": 25000
}
```

#### 后端更新 TaskExecution
```java
execution.setStatus("success");
execution.setCollectedCount(3);
execution.setEndTime(LocalDateTime.now());
execution.setDurationMs(durationMs);
```

---

### 9.6 定时调度

#### 配置位置决策

**定时配置存储在 CollectionScript.triggerConfig（前端配置），不在 Python 代码中硬编码**

**理由：**
1. 定时是业务管理需求，不是采集逻辑
2. 晨报发布时间可能调整（如改成 10:00、10:15），应可由运营人员配置
3. 符合"采集管理系统"定位：采集脚本只负责"怎么采"，调度由系统决定"什么时候采"
4. 新增数据源时，直接在界面配置时间，不需要改 Python 代码

#### CollectionScript 数据模型

```java
public class CollectionScript {
    // ... existing fields

    // 定时配置（JSON格式）
    String triggerConfig;
    // 如: {"type": "fixed_times", "times": ["09:30", "10:00", "10:30"]}
    // 或: {"type": "cron", "expression": "0 30 9 * * ?"}

    // 报告类型（晨报/日报）
    String reportType;  // morning / evening
}
```

#### 前端配置 UI

```
脚本名称: 粮信网玉米晨报
报告类型: [晨报 ▼]
定时配置:
  ○ Cron表达式    ○ 固定时间点
  [09:30] [10:00] [10:30] [+ 添加]
```

#### 触发链路
```
TriggerScheduleService (Spring @Scheduled 每分钟)
    ↓
检查所有 enabled 脚本
    ↓
时间匹配判断 (cron / repeat 配置)
    ↓
调用 /scripts/{id}/execute (同手动)
```

#### cron 表达式示例
```
0 30 9 * * ?     # 每天 9:30
0 0,30 9 * * ?   # 每天 9:00, 9:30
```

#### repeat 配置示例
```json
{
  "type": "daily",
  "times": ["09:30", "10:00", "10:30"]
}
```

#### 边界情况
| 情况 | 处理 |
|------|------|
| 执行时间到了，但上次还没执行完 | 跳过本次，等待下次 |
| 服务重启丢失内存状态 | DB 中的 pending 任务由补偿线程处理 |
| 多个定时任务同时触发 | 并发执行（不同 executionId） |

---

### 9.7 取消执行

#### 流程
```
用户点击"取消"
    ↓
POST /api/scripts/executions/{id}/cancel
    ↓
后端查找执行记录
    ↓
status == running?
    ├─ 是 → 发请求给 Python 取消
    │         Python 设置 ctx.cancelled = true
    │         采集线程检查 cancelled 标志，提前退出
    │
    └─ 否 → status = cancelled (仅 pending 可)
```

#### 取消标志传播
```python
# 采集循环中检查
for report in reports:
    if self.ctx.is_cancelled():
        return  # 提前退出
    self.process_report(report)
```

---

### 9.8 错误处理

#### 错误分类
| 错误类型 | 示例 | 处理 |
|---------|------|------|
| 脚本配置错误 | 账号密码错 | failed + 错误信息 |
| 网络错误 | 登录超时 | failed + 重试3次 |
| 页面结构变化 | 选择器找不到 | failed + 告警需人工介入 |
| 浏览器崩溃 | browser.close() 异常 | 重启浏览器，重试 |
| 系统资源不足 | 内存不足 | failed + 告警 |

#### 重试机制
```python
MAX_RETRIES = 3
RETRY_DELAY = 30  # 秒

def execute_with_retry(ctx):
    for attempt in range(MAX_RETRIES):
        try:
            return do_collect(ctx)
        except RetryableError as e:
            if attempt < MAX_RETRIES - 1:
                time.sleep(RETRY_DELAY)
                continue
            else:
                raise  # 最终失败
```

---

### 9.9 数据清理

#### 日志保留策略
```sql
-- 保留最近 30 天的日志
DELETE FROM t_task_execution_log
WHERE created_at < DATE_SUB(NOW(), INTERVAL 30 DAY);

-- 保留最近 90 天的执行记录
DELETE FROM t_task_execution
WHERE created_at < DATE_SUB(NOW(), INTERVAL 90 DAY);
```

---

### 9.10 监控告警

#### 监控指标
| 指标 | 阈值 | 动作 |
|------|------|------|
| 连续失败次数 | 3 次 | 发送告警 |
| 执行耗时 | > 10 分钟 | 记录警告 |
| 采集数量 | 0 | 记录警告（可能页面结构变化） |

#### 告警渠道
- 内部工具暂时用日志记录
- 后续扩展：钉钉/企业微信/邮件

---

## 十、部署说明

### 9.1 Python 服务启动

```bash
cd python-collector-sdk
python -m scheduler.collector_server --port 5001
```

### 9.2 配置后端地址

```yaml
# config.yaml
collector_service:
  url: "http://localhost:5001"
```

### 9.3 环境变量

| 变量 | 说明 | 默认值 |
|------|------|-------|
| `COLLECTOR_PORT` | Python 服务端口 | 5001 |
| `BROWSER_IDLE_TIMEOUT` | 浏览器空闲超时(秒) | 7200 |
