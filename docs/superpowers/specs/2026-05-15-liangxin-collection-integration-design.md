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
| `/collector/exec/start` | POST | `{scriptId, reportType}` | `{executionId, scriptId, startTime}` | Python 启动执行 |
| `/collector/exec/{id}/log` | POST | `{level, message}` | - | Python 上报日志 |
| `/collector/exec/{id}/data` | POST | `{title, content, source, ...}` | `{knowledgeId}` | Python 提交数据 |
| `/collector/exec/{id}/complete` | POST | `{status, collectedCount}` | - | Python 完成执行 |
| `/collector/exec/{id}/error` | POST | `{errorMessage}` | - | Python 上报错误 |
| `/task/execution/pending` | GET | - | `List<TaskExecution>` | Python 轮询待执行任务 (待实现) |
| `/knowledge/cleanup` | DELETE | `?days=30` | `{deletedCount}` | 清理历史数据 (待实现) |
| `/scripts/batch/delete` | POST | `{scriptIds: List<Long>}` | `{deletedCount}` | 批量删除脚本 (待实现) |
| `/scripts/batch/execute` | POST | `{scriptIds: List<Long>}` | `{results}` | 批量执行脚本 (待实现) |
| `/scripts/batch/execute-status` | GET | `?executionIds=id1,id2` | `List<ExecutionStatus>` | 批量查询执行状态 (待实现) |

---

### 3.3 待实现的接口详情

#### 3.3.1 批量删除脚本

**接口：** `POST /scripts/batch/delete`

**用途：** 批量删除选中的脚本

**请求：**
```json
{
  "scriptIds": [1, 2, 3]
}
```

**响应：**
```json
{
  "code": 200,
  "data": {
    "deletedCount": 3
  }
}
```

**逻辑：**
1. 校验脚本是否存在
2. 检查是否有正在执行的脚本（running 状态），如有则拒绝删除
3. 批量删除脚本记录
4. 返回删除数量

**边界情况：**
| 情况 | 处理 |
|------|------|
| 脚本正在执行中 | 返回错误"请先取消正在执行的脚本" |
| 脚本ID不存在 | 跳过，继续删除存在的 |
| 空数组 | 返回错误"请选择要删除的脚本" |

---

#### 3.3.2 批量执行脚本

**接口：** `POST /scripts/batch/execute`

**用途：** 批量执行选中的脚本（同时触发多个采集任务）

**请求：**
```json
{
  "scriptIds": [1, 2, 3]
}
```

**响应：**
```json
{
  "code": 200,
  "data": {
    "results": [
      {"scriptId": 1, "executionId": "abc123", "status": "pending"},
      {"scriptId": 2, "executionId": "def456", "status": "pending"},
      {"scriptId": 3, "error": "脚本正在执行中"}
    ]
  }
}
```

**逻辑：**
1. 对每个脚本校验状态（是否 enabled，是否已有 running 执行）
2. 为每个脚本创建 TaskExecution 记录
3. 并行/串行转发到 Python 服务
4. 返回每个脚本的执行结果

**边界情况：**
| 情况 | 处理 |
|------|------|
| 脚本正在执行中 | 该脚本跳过，返回 error 信息 |
| 脚本已禁用 | 该脚本跳过，返回 error 信息 |
| 部分失败 | 返回成功列表 + 失败列表 |

---

#### 3.3.3 批量查询执行状态

**接口：** `GET /scripts/batch/execute-status?executionIds=abc123,def456`

**用途：** 批量查询多个执行任务的状态（供轮询使用）

**参数：**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| executionIds | string | 是 | 逗号分隔的 executionId 列表 |

**响应：**
```json
{
  "code": 200,
  "data": [
    {"executionId": "abc123", "status": "running", "collectedCount": 5},
    {"executionId": "def456", "status": "success", "collectedCount": 10}
  ]
}
```

---

#### 3.3.4 Python 轮询待执行任务

**接口：** `GET /task/execution/pending`

**用途：** Python 服务每 10 秒轮询此接口，发现待执行的任务后开始采集

**响应：**
```json
{
  "code": 200,
  "data": [
    {
      "executionId": "abc123",
      "scriptId": 1,
      "scriptName": "粮信网玉米晨报",
      "triggerType": "scheduled",
      "reportType": "morning",
      "status": "pending",
      "startTime": "2026-05-15T08:30:00"
    }
  ]
}
```

**逻辑：**
1. Python 每 10 秒调用此接口
2. 返回所有 status=pending 的执行记录
3. Python 选择一个任务后，调用 `/collector/exec/start` 获取 executionId
4. 后端更新状态为 running，返回给 Python

---

#### 3.3.4 清理历史数据（暂不实现）

**接口：** `DELETE /knowledge/cleanup`

**用途：** 清理过期的知识库数据和执行记录

**状态：** 可选功能，等实际需要时再实现

**参数：**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| days | int | 否 | 保留最近 N 天，默认 30 |

**响应：**
```json
{
  "code": 200,
  "data": {
    "deletedKnowledgeCount": 1234,
    "deletedExecutionCount": 56,
    "deletedLogCount": 890
  }
}
```

**逻辑：**
1. 删除 `t_knowledge_base` 中 publish_time 早于 N 天前的记录
2. 删除 `t_task_execution` 中 created_at 早于 N 天前的记录
3. 删除对应的 `t_task_execution_log` 记录
4. 记录删除数量，返回给前端

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

#### 执行记录列表接口

**接口：** `GET /scripts/{id}/executions`

**用途：** 获取指定脚本的所有执行记录（分页）

**参数：**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | path | 是 | 脚本ID |
| page | int | 否 | 页码，默认 1 |
| size | int | 否 | 每页数量，默认 10 |

**响应：**
```json
{
  "code": 200,
  "data": {
    "records": [
      {
        "executionId": "abc123",
        "scriptId": 1,
        "status": "success",
        "startTime": "2026-05-15 08:00:00",
        "endTime": "2026-05-15 08:00:12",
        "durationMs": 12500,
        "collectedCount": 3,
        "errorMessage": null,
        "reportType": "morning"
      },
      {
        "executionId": "def456",
        "scriptId": 1,
        "status": "failed",
        "startTime": "2026-05-14 08:00:00",
        "endTime": "2026-05-14 08:00:03",
        "durationMs": 3200,
        "collectedCount": 0,
        "errorMessage": "登录失败，请检查账号密码",
        "reportType": "morning"
      }
    ],
    "total": 56,
    "page": 1,
    "size": 10
  }
}
```

**状态值：**
| 状态 | 说明 |
|------|------|
| pending | 等待执行 |
| running | 执行中 |
| success | 执行成功 |
| failed | 执行失败 |
| cancelled | 已取消 |

#### 执行详情接口

**接口：** `GET /scripts/executions/{executionId}`

**用途：** 获取单条执行记录的详细信息

**响应：**
```json
{
  "code": 200,
  "data": {
    "executionId": "abc123",
    "scriptId": 1,
    "scriptName": "粮信网玉米晨报",
    "status": "success",
    "startTime": "2026-05-15 08:00:00",
    "endTime": "2026-05-15 08:00:12",
    "durationMs": 12500,
    "collectedCount": 3,
    "reportType": "morning",
    "triggerType": "scheduled",
    "logs": [
      {"offset": 0, "level": "INFO", "message": "开始执行采集", "timestamp": "2026-05-15 08:00:01"},
      {"offset": 1, "level": "INFO", "message": "登录成功", "timestamp": "2026-05-15 08:00:03"},
      {"offset": 2, "level": "INFO", "message": "找到 3 篇报告", "timestamp": "2026-05-15 08:00:08"}
    ]
  }
}
```

### 5.4 任务列表按钮跳转

#### 原型按钮功能

| 按钮 | 函数 | 当前行为 | 问题 |
|------|------|---------|------|
| 创建任务 | `createTask()` | 跳转 `create-task-prototype.html` | ✓ 正常 |
| 任务名称/详情 | `viewDetail(id)` | 跳转 `task-detail-prototype.html?id=${id}` | ✓ 正常 |
| 执行 | `executeTask(id)` | 只显示 Toast，无跳转 | ❌ 应该跳转到详情页显示执行进度 |
| 版本 | `openVersionHistory(id)` | Dialog iframe 加载版本历史 | ✓ 正常 |
| 执行记录 | `openExecutionHistory(id)` | Dialog iframe 加载执行记录 | ✓ 正常 |
| 启用/禁用 | `toggleStatus(btn, id)` | 只修改 UI | ❌ 应该调用后端 API |
| 批量启用 | `batchEnable()` | 只修改 UI | ❌ 应该调用后端批量 API |
| 批量禁用 | `batchDisable()` | 只修改 UI | ❌ 应该调用后端批量 API |
| 批量删除 | `batchDelete()` | 只修改 UI | ❌ 应该调用后端批量 API |
| 刷新 | `refreshData()` | 只显示 Toast | ❌ 应该重新加载列表数据 |

#### 需要修改的按钮行为

**1. 执行按钮（executeTask）**

```javascript
// 当前（问题）
function executeTask(id) {
  showToast(`任务 #${id} 开始执行...`);
}

// 修改为：跳转到详情页显示执行进度
function executeTask(id) {
  // 先调用执行接口
  executeScriptApi.execute(id).then(res => {
    // 跳转到详情页
    router.push(`/scripts/${id}/detail?executionId=${res.data.executionId}`);
  });
}
```

**2. 刷新按钮（refreshData）**

```javascript
// 当前（问题）
function refreshData() {
  showToast('数据已刷新');
}

// 修改为：重新加载列表数据
async function refreshData() {
  const res = await scriptApi.list pagination.value.current, pagination.value.size);
  tableData.value = res.data.records;
  updateStats(res.data);
}
```

**3. 启用/禁用按钮（toggleStatus）**

```javascript
// 当前（问题）
function toggleStatus(btn, id) {
  // 只修改 UI
}

// 修改为：调用后端 API
async function toggleStatus(btn, id) {
  const newStatus = isEnabled ? 'disabled' : 'enabled';
  await scriptApi.updateStatus(id, newStatus);
  // 更新 UI
}
```

#### 文件变更

| 文件 | 修改内容 |
|------|---------|
| `frontend/TaskList.vue` | 修改 executeTask 跳转到详情页，refreshData 重新加载数据，toggleStatus 调用 API |
| `frontend/src/api/scripts.ts` | 添加批量操作接口调用方法 |

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

### 6.3 多数据源采集器架构

#### 设计背景

每个数据源（非标网站）需要独立的采集逻辑：
- 粮信网 → 登录 + 爬列表 + 爬详情
- 我的钢铁网 → 登录 + 爬列表 + 爬详情
- 巴西政府网 → 登录 + 爬列表 + 爬详情
- USDA → API 采集

无法用统一配置，必须每个数据源独立实现采集器。

#### 目录结构

```
python-collector-sdk/collectors/
├── __init__.py          # 采集器注册表
├── base.py              # 基类 BaseCollector
├── liangxin.py          # 粮信网采集器
├── mysteel.py           # 我的钢铁网采集器
├── gov_br.py           # 巴西政府网采集器
├── usda.py             # USDA 采集器
└── knowledge_api.py    # 知识库 API 客户端
```

#### 采集器注册机制

```python
# collectors/__init__.py
from .liangxin import LiangxinCollector
from .mysteel import MysteelCollector
from .gov_br import GovBrCollector
from .usda import UsdaCollector

# 采集器注册表
COLLECTORS = {
    "liangxin": {
        "name": "粮信网",
        "collector_class": LiangxinCollector,
        "required_params": ["username", "password", "login_url", "report_list_url"],
        "optional_params": ["content_selector", "timeout"]
    },
    "mysteel": {
        "name": "我的钢铁网",
        "collector_class": MysteelCollector,
        "required_params": ["username", "password", "login_url"],
    },
    # 新增采集器只需在这里注册
}

def get_collector(source: str):
    """根据数据源名称获取采集器"""
    return COLLECTORS.get(source)

def get_collector_info(source: str):
    """获取采集器配置信息"""
    info = COLLECTORS.get(source, {})
    return {
        "source": source,
        "name": info.get("name"),
        "required_params": info.get("required_params", []),
        "optional_params": info.get("optional_params", [])
    }

def list_available_collectors():
    """列出所有可用的采集器"""
    return [
        {"source": k, "name": v["name"]}
        for k, v in COLLECTORS.items()
    ]
```

#### 基类定义

```python
# collectors/base.py
class BaseCollector:
    """采集器基类"""

    def __init__(self, config: ReporterConfig, task_id: int,
                 username: str, password: str, **kwargs):
        self.config = config
        self.task_id = task_id
        self.username = username
        self.password = password
        self.params = kwargs  # 采集器特定参数

    def collect(self) -> int:
        """执行采集，返回采集数量"""
        raise NotImplementedError

    def run(self) -> dict:
        """执行入口，返回执行结果"""
        result = {"success": False, "collected_count": 0, "error": None}
        try:
            count = self.collect()
            result["success"] = True
            result["collected_count"] = count
        except Exception as e:
            result["error"] = str(e)
        return result
```

#### 采集器实现示例

```python
# collectors/liangxin.py
class LiangxinCollector(BaseCollector):
    """粮信网采集器"""

    def __init__(self, config: ReporterConfig, task_id: int,
                 username: str, password: str,
                 login_url: str, report_list_url: str,
                 content_selector: str = ".article-conte-infor",
                 **kwargs):
        super().__init__(config, task_id, username, password, **kwargs)
        self.login_url = login_url
        self.report_list_url = report_list_url
        self.content_selector = content_selector

    def collect(self) -> int:
        """执行粮信网采集逻辑"""
        # 1. 创建浏览器
        # 2. 登录
        # 3. 获取报告列表
        # 4. 遍历报告获取正文
        # 5. 上报到知识库
        # 6. 关闭浏览器
        pass
```

#### 调度器动态加载

```python
# scheduler/collector_server.py
from collectors import get_collector, list_available_collectors

@app.route('/collectors/list')
def list_collectors():
    """获取所有可用的采集器列表"""
    return jsonify(list_available_collectors())

@app.route('/execute', methods=['POST'])
def execute():
    """执行采集任务"""
    data = request.json
    source = data["source"]           # "liangxin"
    script_config = data["config"]     # 采集器配置
    execution_id = data["execution_id"]

    # 获取采集器类
    collector_info = get_collector(source)
    if not collector_info:
        return jsonify({"error": f"未知的采集器: {source}"}), 400

    collector_class = collector_info["collector_class"]

    # 创建采集器实例
    collector = collector_class(
        config=ReporterConfig(api_base=API_BASE),
        task_id=data.get("task_id", 1),
        **script_config
    )

    # 执行采集
    thread = threading.Thread(
        target=lambda: collector.run()
    )
    thread.start()

    return jsonify({"status": "started"})
```

### 6.6 后端存储

```json
// CollectionScript 表
{
  "scriptName": "粮信网玉米晨报",
  "source": "liangxin",
  "scriptConfig": {
    "username": "xxx",
    "password": "xxx",
    "login_url": "https://...",
    "report_list_url": "https://...",
    "content_selector": ".article-conte-infor"
  },
  "triggerType": "cron",
  "cronExpression": "0 30 9 * * ?"
}
```

#### 新增数据源流程

1. 实现新的采集器文件 `collectors/xxx.py`
2. 在 `collectors/__init__.py` 中注册
3. 重启 Python 服务
4. 后端自动识别新的采集器

---

### 6.4 采集器自注册机制

#### 设计目标

新增数据源只需：写 Python 文件 → 重启服务 → Python 自动发现并注册

#### 采集器 META（元数据）

每个采集器内置 META，用于 Python 内部 COLLECTORS 注册表：

```python
# collectors/mysteel.py
class MysteelCollector(BaseCollector):
    """我的钢铁网采集器"""

    # META 只用于 Python 内部，不同步到 Java
    META = {
        "code": "mysteel",
        "name": "我的钢铁网",
    }

    def __init__(self, config, task_id, username, password):
        ...
```

#### 自动发现采集器

```python
# collectors/__init__.py

def discover_collectors():
    """自动发现所有采集器，用于 Python 内部 COLLECTORS 注册"""
    collectors = {}

    import os
    for filename in os.listdir(os.path.dirname(__file__)):
        if filename.endswith('.py') and filename not in ('__init__.py', 'base.py', 'knowledge_api.py'):
            module_name = filename[:-3]
            module = __import__(f'collectors.{module_name}', fromlist=[module_name])

            for attr_name in dir(module):
                cls = getattr(module, attr_name)
                if isinstance(cls, type) and issubclass(cls, BaseCollector) and cls is not BaseCollector:
                    if hasattr(cls, 'META'):
                        code = cls.META['code']
                        collectors[code] = cls

    return collectors


COLLECTORS = discover_collectors()  # {"liangxin": LiangxinCollector, "mysteel": MysteelCollector, ...}
__all__ = ["BaseCollector", "COLLECTORS"]
```

#### 为什么不需要同步到 Java

| Java 需要知道 | 存储位置 |
|-------------|---------|
| 有哪些数据源 | t_data_source 表（手动创建） |
| 数据源配置 | t_data_source.config（手动配置） |
| 任务用哪个数据源 | t_collection_script.datasource_code（任务关联） |

**META 不是给 Java 用的**，Java 已经有完整的数据源管理，不需要从 Python 同步。

**执行时，Java 只需告诉 Python：**
```json
{
  "datasource_code": "liangxin",  // Java 知道这个 code
  "script_config": { "username": "xxx", "password": "xxx" }  // 任务级配置
}
```

**Python 根据 code 找到采集器：**
```python
collector_class = COLLECTORS["liangxin"]  # Java 说用哪个，Python 就用哪个
collector = collector_class(config, task_id, **script_config)
```

#### 启动时只检查不同步

```python
def check_collectors_health():
    """启动时检查采集器是否可用，不同步任何数据到 Java"""
    from collectorsdk.collectors import COLLECTORS

    logger.info(f"已加载 {len(COLLECTORS)} 个采集器: {list(COLLECTORS.keys())}")

    for code, collector_cls in COLLECTORS.items():
        # 验证采集器类可实例化（不实际执行）
        logger.info(f"  - {code}: {collector_cls.__name__}")

# 启动时检查
check_collectors_health()
```

#### 热更新（文件监听）

```python
# collectors/__init__.py

import watchdog.events
import threading

class CollectorFileHandler(watchdog.events.FileSystemEventHandler):
    """监听 collectors 目录变化"""

    def __init__(self, callback):
        self.callback = callback

    def on_created(self, event):
        if event.src_path.endswith('.py'):
            self.callback(event.src_path, "created")

    def on_modified(self, event):
        if event.src_path.endswith('.py'):
            self.callback(event.src_path, "modified")


def start_file_watcher():
    """启动文件监听"""
    collectors_dir = os.path.dirname(__file__)
    handler = CollectorFileHandler(on_collector_changed)
    observer = watchdog.observers.Observer()
    observer.schedule(handler, collectors_dir, recursive=False)
    observer.start()
    logger.info("已启动采集器文件监听")


def on_collector_changed(file_path, event_type):
    """文件变化时的处理 - 仅重新加载模块，不同步到 Java"""
    module_name = os.path.basename(file_path)[:-3]

    # 重新加载模块
    if module_name in sys.modules:
        importlib.reload(sys.modules[module_name])
    else:
        __import__(f'collectors.{module_name}')

    # 重新注册（COLLECTORS 是全局的，重新 discover 即可）
    global COLLECTORS
    COLLECTORS = discover_collectors()
    logger.info(f"采集器已重载: {module_name}, 当前共 {len(COLLECTORS)} 个")
```

#### 完整流程

```
Python 服务启动
    ↓
discover_collectors() 扫描 collectors/*.py
    ↓
建立 COLLECTORS 注册表
    ↓
check_collectors_health() 检查健康状态
    ↓
开始轮询后端 pending 任务
```

**Java 侧数据源管理：**
- 数据源在 Java 侧手动创建/管理（t_data_source 表）
- 任务通过 datasource_code 关联数据源
- 执行时 Java 把 datasource_code 传给 Python

#### 与前端上传脚本的配合

| 方式 | 触发时机 | 处理 |
|------|---------|------|
| Python 启动时 | 服务启动 | discover_collectors() 建立注册表 |
| 前端上传脚本 | 用户上传 .py | 覆盖文件 + watchdog 检测 + 重载模块 |
| 文件监听 | collectors/ 目录变化 | 自动重新加载 + 更新 COLLECTORS |

---

### 6.5 采集脚本管理

#### 两种采集脚本来源

| 来源 | 方式 | 适用场景 |
|------|------|---------|
| Python 工程文件 | IDE 编写 + Git 部署 | 日常开发维护 |
| 前端上传 | 后台上传 .py 文件 | 应急修改线上 bug |

#### 方案：文件存储 + 数据库元数据

```
Python 文件：/python-collector-sdk/collectorsdk/collectors/{code}.py
数据库记录：t_data_source 表（code, name, description, status）
```

#### 前端上传流程

```
前端上传 .py → Java 保存到文件 → watchdog 检测 → Python reload
```

#### Java 后端接口

```java
@PostMapping("/datasource/upload-collector")
public Result<DataSource> uploadCollector(
    @RequestParam("file") MultipartFile file,
    @RequestParam("code") String code,
    @RequestParam("name") String name,
    @RequestParam(value = "overwrite", defaultValue = "false") boolean overwrite
) {
    String path = pythonCollectorDir + "/" + code + ".py";

    // 检查是否已存在
    if (Files.exists(Path.of(path)) && !overwrite) {
        return Result.error("数据源已存在，请确认是否覆盖");
    }

    // 覆盖模式下先备份
    if (overwrite && Files.exists(Path.of(path))) {
        String backupPath = path + ".bak." + System.currentTimeMillis();
        Files.copy(Path.of(path), Path.of(backupPath));
    }

    // 保存新文件
    Files.write(Path.of(path), file.getBytes());

    // 通知 Python 重载
    notifyPythonReload(code);

    return Result.success(dataSourceService.createOrUpdate(code, name, ...));
}
```

#### 安全限制

| 限制项 | 说明 |
|-------|------|
| 文件类型 | 仅 .py 文件 |
| 文件大小 | ≤100KB |
| 安全扫描 | 禁止 `import os`、`exec(`、`eval(` 等危险操作 |
| 校验 | 必须继承 `BaseCollector` |

```java
private boolean validateScriptSafety(String source) {
    String[] dangerous = {"import os", "import subprocess", "import sys",
                           "eval(", "exec(", "open(", "os.system"};
    for (String pattern : dangerous) {
        if (source.contains(pattern)) return false;
    }
    return source.contains("BaseCollector");
}
```

#### 脚本查看（只读）

```java
@GetMapping("/datasource/{code}/script")
public Result<String> getScriptSource(@PathVariable String code) {
    String path = pythonCollectorDir + "/" + code + ".py";
    return Result.success(Files.readString(Path.of(path)));
}
```

前端显示只读代码内容，用于排查问题。

#### 与自注册机制的关系

| 功能 | 自注册机制 | 前端上传 |
|------|-----------|---------|
| 触发时机 | Python 服务启动 | 用户手动上传 |
| 脚本来源 | Git 部署到 Python 工程 | 后台直接上传 |
| 管理方式 | 开发者维护 | 管理员操作 |
| 热更新 | watchdog 监听文件变化 | 上传后通知重载 |

两种方式可以共存：开发者通过 Git 部署脚本，管理员通过后台上传覆盖。

---

### 6.6 后端存储

## 七、文件变更清单

| 文件路径 | 操作 | 说明 |
|---------|------|------|
| `python-collector-sdk/collectors/__init__.py` | 新增 | 采集器注册表 |
| `python-collector-sdk/collectors/base.py` | 新增 | 采集器基类 |
| `python-collector-sdk/collectors/liangxin.py` | 新增 | 粮信网采集器 |
| `python-collector-sdk/collectors/mysteel.py` | 新增 | 我的钢铁网采集器 (预留) |
| `python-collector-sdk/scheduler/collector_server.py` | 新增 | Flask HTTP API 服务 |
| `python-collector-sdk/scheduler/corn_scheduler.py` | 重构 | 改为被动触发，移除 APScheduler |
| `python-collector-sdk/scheduler/browser_pool.py` | 新增 | 浏览器连接池 + 冷却策略 |
| `backend/CollectorController.java` | 新增 | 转发请求到 Python 服务 |
| `backend/TaskExecution.java` | 修改 | 添加 reportType 字段 |
| `backend/TaskExecutionLog.java` | 新增 | 日志实体 |
| `backend/TaskExecutionMapper.java` | 修改 | 添加日志查询方法 |
| `backend/TaskExecutionService.java` | 修改 | 添加日志存储/查询 |
| `backend/TaskExecutionController.java` | 新增 | Python 轮询待执行任务接口 (待实现) |
| `backend/KnowledgeBaseController.java` | 修改 | 添加清理历史数据接口 (待实现) |
| `backend/ScriptController.java` | 新增 | 批量操作接口 (待实现) |
| `backend/TaskExecutionService.java` | 修改 | 执行完成时同步统计到 CollectionScript (待实现) |
| `backend/TriggerScheduleService.java` | 修改 | 添加 shouldExecute 判断，修复时区问题 (待实现) |
| `backend/CollectionScriptService.java` | 修改 | 删除废弃的 parseRepeatConfig 方法 (待实现) |
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

#### 补偿机制（后端补偿线程 + 前端提示）

**故障程度不同，处理方式不同：**

| 故障程度 | 场景 | 处理方式 |
|---------|------|---------|
| 轻度 | 网络瞬断 | 自动重试（5秒后） |
| 中度 | Python 服务需要几分钟恢复 | 指数退避重试 (5s, 10s, 20s, 40s, 80s) |
| 重度 | Python 服务长时间不可用 | 保持 pending，前端提示用户 |

**TaskExecution 新增字段：**
```java
Integer retryCount = 0;     // 当前重试次数
Long nextRetryTime;        // 下次重试时间戳
```

**补偿线程逻辑：**
```java
@Scheduled(fixedRate = 10000)  // 每10秒
public void retryPendingExecutions() {
    List<TaskExecution> pending = executionService.getPendingForRetry();
    for (TaskExecution exec : pending) {
        if (exec.getNextRetryTime() > now) continue;  // 未到重试时间
        if (exec.getRetryCount() >= MAX_RETRIES) continue;  // 超过最大次数
        forwardToPythonService(exec);  // 重新转发
        exec.setRetryCount(exec.getRetryCount() + 1);
        exec.setNextRetryTime(now + RETRY_INTERVALS[exec.getRetryCount()] * 1000);
    }
}
```

**服务重启恢复：**
```java
@PostConstruct
public void onStartup() {
    // 重启时重置 pending 任务的重试计数
    List<TaskExecution> pendingTasks = executionService.getAllPending();
    for (TaskExecution task : pendingTasks) {
        task.setRetryCount(0);
        task.setNextRetryTime(null);
    }
}
```

**前端提示（重度故障）：**
```
pending 状态超过 1 分钟
    ↓
显示警告图标 + "等待执行中..."
    ↓
用户可选择:
  - "重新触发" → 手动重试
  - "取消任务" → 设为 cancelled
```

---

### 9.2 用户可见性设计

**用户视角 vs 系统视角：**

| 用户可见 | 用户不可见 |
|---------|-----------|
| 状态：pending / running / success / failed | 重试次数 |
| 采集数量 | 下次重试时间 |
| 错误信息（失败时） | 指数退避算法 |
| 执行日志（简化版） | 补偿线程 |
| 可以取消 | |

**前端只显示关键信息：**
```javascript
const statusText = {
  'pending': '等待执行',
  'running': '执行中',
  'success': '执行成功',
  'failed': '执行失败',
  'cancelled': '已取消'
};
```

**用户可见日志示例：**
- "登录成功"
- "找到 3 篇报告"
- "正在采集第 2 篇..."
- "提交知识库成功"

**用户不可见的技术日志：**
- "INFO:root:Starting collection..."
- 重试相关日志

---

### 9.3 执行状态管理

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

### 9.4 Python 服务架构

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

### 9.5 日志系统

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

### 9.6 执行结果回传

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

### 9.7 定时调度

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

### 9.7.1 下次执行时间计算

#### 问题描述

当前 `calculateNextExecution` 方法对 cron 类型的处理是 stub 实现，只是简单的 `base.plusHours(1)`，无法正确计算真正的下次执行时间。

#### 正确实现

使用 Spring 的 `CronExpression.parse()` 计算真正下次执行时间：

```java
public LocalDateTime calculateNextExecution(CollectionScript script) {
    if ("cron".equals(script.getTriggerType()) && script.getCronExpression() != null) {
        // 使用 Spring 的 CronExpression 计算真正下次执行时间
        CronExpression cron = CronExpression.parse(script.getCronExpression());
        ZonedDateTime now = ZonedDateTime.now();
        TemporalAccessor next = cron.next(now);
        return LocalDateTime.ofInstant(
            Instant.from(next), ZoneId.systemDefault()
        );
    }
    // 其他重复类型使用现有逻辑...
}
```

#### 触发时机

| 时机 | 说明 |
|------|------|
| 脚本创建/编辑时 | 保存后立即计算并更新 `nextExecutionTime` |
| 执行完成时 | `TriggerScheduleService` 执行完成后调用，更新下次时间 |
| 定时调度检查前 | 每次触发前先验证/更新 `nextExecutionTime` |

#### 更新逻辑（TriggerScheduleService）

```java
// 执行完成后，计算并更新下次执行时间
@Scheduled(fixedRate = 60000)  // 每分钟
public void updateNextExecutionTimes() {
    List<CollectionScript> scripts = scriptService.getEnabledScripts();
    for (CollectionScript script : scripts) {
        LocalDateTime next = calculateNextExecution(script);
        script.setNextExecutionTime(next);
        scriptService.update(script);
    }
}
```

#### 前端显示

```
┌─────────────────────────────────────────────────────────┐
│ 脚本名称: 粮信网玉米晨报              状态: ● 启用      │
│ 数据源: 粮信网                         报告类型: 晨报    │
│ 执行周期: 每天 9:30                    下次执行: 09:30   │
│ 上次执行: 2026-05-14 09:30:00 (成功, 采集 3 条)         │
└─────────────────────────────────────────────────────────┘
```

#### 文件变更

| 文件 | 修改内容 |
|------|---------|
| `CollectionScriptService.java` | 修改 `calculateNextExecution` 方法，正确解析 cron |
| `TriggerScheduleService.java` | 添加执行后更新 `nextExecutionTime` 的逻辑 |

---

### 9.7.2 执行统计同步

#### 问题描述

`CollectionScript` 表有 `executionCount`、`successCount`、`failedCount` 字段，`CollectionScriptService` 中定义了 `recordExecutionResult()` 方法，但**从未被调用**。

导致执行统计永远不会更新，前端显示的统计数据始终为 0。

#### 同步时机

| 时机 | 调用方法 |
|------|---------|
| TaskExecution 状态变为 success | `recordExecutionResult(scriptId, true)` |
| TaskExecution 状态变为 failed | `recordExecutionResult(scriptId, false)` |
| TaskExecution 状态变为 cancelled | 无需同步 |

#### 修改代码（TaskExecutionService.java）

```java
public void completeExecution(String executionId, String status, int collectedCount) {
    TaskExecution execution = findByExecutionId(executionId);
    if (execution == null) return;

    execution.setStatus(status);
    execution.setEndTime(LocalDateTime.now());
    execution.setCollectedCount(collectedCount);

    // 同步统计到 CollectionScript
    boolean isSuccess = "success".equals(status);
    collectionScriptService.recordExecutionResult(execution.getScriptId(), isSuccess);

    taskExecutionMapper.updateById(execution);
}
```

#### 验证

```
执行前:
  CollectionScript.executionCount = 10
  CollectionScript.successCount = 8
  CollectionScript.failedCount = 2

执行完成后（success）:
  CollectionScript.executionCount = 11
  CollectionScript.successCount = 9
  CollectionScript.failedCount = 2
```

---

### 9.7.3 触发配置问题修复

#### 问题汇总

| 问题 | 严重程度 | 说明 |
|------|---------|------|
| `triggerConfig` 字段未使用 | 高 | 后端声明为 JSON String，但 `parseRepeatConfig` 从未被调用 |
| `parseRepeatConfig` 是废弃代码 | 高 | 存在但无调用，返回硬编码值 |
| 前后端类型不一致 | 中 | 前端 `triggerConfig` 声明为 `string`，实际传递对象 |
| 单次触发（once/single）无判断 | 中 | 前端支持，后端没有对应的执行判断逻辑 |
| 结束条件未判断 | 中 | 没有判断 `endType=endTime` 时是否过期 |
| 时区问题 | 低 | Cron 解析未指定时区 |

#### 解决方案

**1. 统一 triggerConfig 数据结构**

废弃 `triggerConfig` 字段，改用独立字段（现状）：

```
CollectionScript 表字段：
├── triggerType       -- manual/single/cron/repeat
├── cronExpression    -- cron 表达式
├── repeatType         -- daily/weekly/monthly
├── repeatTime         -- 时间点 "09:30"
├── weeklyDays         -- 周几 "1,2,3,4,5"
├── monthlyDay         -- 月第几天
├── monthlyLastDay     -- 是否月末
├── endType           -- once/endTime/repeatCount
├── endTime           -- 结束时间
└── repeatCount       -- 重复次数
```

**2. 修复 isCronMatch 时区问题**

```java
// 当前代码（有问题）
ZonedDateTime nowZoned = now.atZone(ZoneId.systemDefault());
java.time.temporal.TemporalAccessor next = cron.next(nowZoned);

// 修复：明确指定时区
ZonedDateTime nowZoned = now.atZone(ZoneId.of("Asia/Shanghai"));
```

**3. 添加单次触发判断**

```java
// 在 TriggerScheduleService 中添加
private boolean shouldExecute(CollectionScript script, LocalDateTime now) {
    String triggerType = script.getTriggerType();

    // 单次触发：检查是否已执行过
    if ("single".equals(triggerType) || "once".equals(triggerType)) {
        // 如果上次执行时间不为空，说明已执行过，跳过
        return script.getLastExecutionTime() == null;
    }

    // 重复触发：检查是否在结束条件内
    if ("repeat".equals(triggerType) || "cron".equals(triggerType)) {
        // 检查 endType
        if ("endTime".equals(script.getEndType())) {
            LocalDateTime endTime = script.getEndTime();
            if (endTime != null && now.isAfter(endTime)) {
                return false;  // 已过期，跳过
            }
        }
        if ("repeatCount".equals(script.getEndType())) {
            Integer count = script.getExecutionCount();
            if (count != null && count >= script.getRepeatCount()) {
                return false;  // 已达次数，跳过
            }
        }
    }

    return true;
}
```

**4. 统一触发判断逻辑**

将 `isCronMatch` 和 `shouldExecute` 合并为统一方法：

```java
@Scheduled(fixedRate = 60000)  // 每分钟
public void checkAndExecute() {
    List<CollectionScript> scripts = scriptService.getEnabledScripts();
    for (CollectionScript script : scripts) {
        if (!shouldExecute(script, LocalDateTime.now())) {
            continue;  // 不满足执行条件
        }
        if (isTimeMatch(script, LocalDateTime.now())) {
            // 触发执行
            scriptService.executeScript(script.getId());
        }
    }
}

private boolean isTimeMatch(CollectionScript script, LocalDateTime now) {
    String triggerType = script.getTriggerType();

    if ("cron".equals(triggerType)) {
        return isCronMatch(script, now);
    }

    if ("repeat".equals(triggerType)) {
        return isRepeatMatch(script, now);
    }

    // manual/single 类型不自动执行
    return false;
}
```

**5. 文件变更**

| 文件 | 修改内容 |
|------|---------|
| `TriggerScheduleService.java` | 添加 `shouldExecute` 判断，修复时区问题 |
| `CollectionScriptService.java` | 删除废弃的 `parseRepeatConfig` 方法 |
| `schema.sql` | 移除 `triggerConfig` 字段（如不使用） |

---

### 9.7.4 单次触发设计

#### 问题描述

1. **前端无单次触发 UI** - TriggerConfig.vue 只有 `simple`（周期）和 `cron` 两个 Tab，用户无法选择单次触发
2. **命名不一致** - 后端注释写 `single`，代码判断 `once`
3. **前端映射错误** - `simple` 被映射为 `repeat`，而不是 `once`

#### 单次触发流程

```
用户选择"单次触发"
    ↓
前端设置 triggerType = "once"
    ↓
TriggerScheduleService 检查：
    - lastExecutionTime == null? (从未执行)
    - 当前时间是否匹配 repeatTime + 日期
    ↓
匹配成功 → 执行一次
    ↓
lastExecutionTime 更新
    ↓
下次检查时 lastExecutionTime != null → 跳过
```

#### 前端 TriggerConfig.vue 修改

新增"单次触发" Tab：

```vue
<!-- Tab 选项 -->
<el-tabs v-model="triggerType">
  <el-tab-pane label="周期触发" name="repeat"></el-tab-pane>
  <el-tab-pane label="单次触发" name="once"></el-tab-pane>
  <el-tab-pane label="Cron表达式" name="cron"></el-tab-pane>
</el-tabs>

<!-- 单次触发配置 -->
<div v-if="triggerType === 'once'" class="once-config">
  <el-date-picker
    v-model="onceDate"
    type="date"
    placeholder="选择日期"
  />
  <el-time-picker
    v-model="onceTime"
    format="HH:mm"
    placeholder="选择时间"
  />
</div>
```

#### 后端判断逻辑（TriggerScheduleService）

```java
@Scheduled(fixedRate = 60000)
public void checkAndExecute() {
    List<CollectionScript> scripts = scriptService.getEnabledScripts();
    for (CollectionScript script : scripts) {
        if (!shouldExecute(script, LocalDateTime.now())) {
            continue;
        }
        if (isTimeMatch(script, LocalDateTime.now())) {
            scriptService.executeScript(script.getId());
        }
    }
}

private boolean shouldExecute(CollectionScript script, LocalDateTime now) {
    String triggerType = script.getTriggerType();

    // 单次触发
    if ("once".equals(triggerType)) {
        if (script.getLastExecutionTime() != null) {
            return false;  // 已执行过，不再触发
        }
        return true;  // 从未执行，需要判断时间
    }

    // 周期触发
    if ("repeat".equals(triggerType)) {
        if ("endTime".equals(script.getEndType())) {
            LocalDateTime endTime = script.getEndTime();
            if (endTime != null && now.isAfter(endTime)) {
                return false;
            }
        }
        if ("repeatCount".equals(script.getEndType())) {
            Integer count = script.getExecutionCount();
            if (count != null && count >= script.getRepeatCount()) {
                return false;
            }
        }
        return true;
    }

    // cron 类型由 isTimeMatch 判断
    return true;
}

private boolean isTimeMatch(CollectionScript script, LocalDateTime now) {
    String triggerType = script.getTriggerType();

    if ("once".equals(triggerType)) {
        // 单次触发：检查日期和时间是否匹配
        String repeatTime = script.getRepeatTime();  // 如 "09:30"
        LocalTime targetTime = LocalTime.parse(repeatTime);
        LocalDate targetDate = script.getLastExecutionTime() != null ?
            script.getLastExecutionTime().toLocalDate() : now.toLocalDate();

        return now.toLocalDate().equals(targetDate) &&
               now.toLocalTime().getHour() == targetTime.getHour() &&
               now.toLocalTime().getMinute() == targetTime.getMinute();
    }

    if ("cron".equals(triggerType)) {
        return isCronMatch(script, now);
    }

    if ("repeat".equals(triggerType)) {
        return isRepeatMatch(script, now);
    }

    return false;
}
```

#### 单次触发数据库表现

| 字段 | 值 | 说明 |
|------|-----|------|
| triggerType | once | 单次触发 |
| repeatTime | 09:30 | 执行时间点 |
| lastExecutionTime | null（执行前） | 从未执行 |
| lastExecutionTime | 2026-05-15 09:30:00（执行后） | 已执行，不再触发 |

#### 文件变更

| 文件 | 修改内容 |
|------|---------|
| `frontend/TriggerConfig.vue` | 新增"单次触发" Tab，once 选项 |
| `frontend/ScriptEditDrawer.vue` | 保存时拆分 startTime 为日期+时间字段 |
| `backend/TriggerScheduleService.java` | 修复 once 类型判断逻辑 |

---

### 9.7.5 单次触发配置字段不匹配 Bug 修复

#### 问题描述

前端 `ScriptEditDrawer.vue` 保存单次触发配置时，只设置了 `startTime`（完整日期时间），**没有设置 `repeatTime`**。

导致后端 `TriggerScheduleService.isTimeMatch()` 判断时 `repeatTime` 为 null，永远返回 false，任务永远不执行。

#### 问题代码

**前端（ScriptEditDrawer.vue）**：
```javascript
// 保存时只传了 startTime
const data = {
  triggerType: 'single',
  startTime: form.value.startTime,  // "2026-05-15 09:30:00"
  // repeatTime 没有设置！❌
}
```

**后端（TriggerScheduleService）**：
```java
if ("once".equals(triggerType)) {
    // isTimeMatch 检查 repeatTime，但为 null
    return isTimeMatch(script.getRepeatTime(), nowTime) && isDateMatch(script, now);
}

private boolean isTimeMatch(String repeatTime, LocalTime nowTime) {
    if (repeatTime == null) return false;  // null → 返回 false ❌
    LocalTime targetTime = LocalTime.parse(repeatTime);
    return Math.abs(nowTime.toSecondOfDay() - targetTime.toSecondOfDay()) < 60;
}
```

#### 修复方案

**前端保存时拆分为两个字段**：

```javascript
// ScriptEditDrawer.vue 保存时
const dateTime = form.value.startTime;  // "2026-05-15 09:30:00"
const [date, time] = dateTime.split(' ');

const data = {
  triggerType: 'single',
  startTime: date + ' 00:00:00',      // 日期部分，用于 isDateMatch
  repeatTime: time.substring(0, 5) + ':00'  // 时间部分 HH:mm:ss，用于 isTimeMatch
}
```

#### 字段对应关系

| 原型字段 | 数据库字段 | 用途 |
|---------|-----------|------|
| onceDate | start_time | 日期部分（用于 isDateMatch） |
| onceTime | repeat_time | 时间部分 HH:mm:ss（用于 isTimeMatch） |

#### 修复后数据库表现

| 字段 | 值 | 说明 |
|------|-----|------|
| triggerType | single | 单次触发 |
| startTime | 2026-05-15 00:00:00 | 日期部分 |
| repeatTime | 09:30:00 | 时间部分 |
| lastExecutionTime | null（执行前） | 从未执行 |

#### 文件变更

| 文件 | 修改内容 |
|------|---------|
| `frontend/ScriptEditDrawer.vue` | 保存时拆分 startTime 为日期+时间 |
| `backend/TriggerScheduleService.java` | 确保 isTimeMatch/isDateMatch 正确读取 |

---

### 9.7.6 周期触发问题修复

#### 问题汇总

| 问题 | 说明 |
|------|------|
| 原型用 "cycle"，实现用 "repeat" | 命名不一致 |
| 结束类型 "count" 后端未实现 | repeatCount 字段未使用 |

#### 问题1：命名统一

**原型 HTML**：使用 `value="cycle"`
**后端代码**：判断 `"repeat"`

统一方案：保持后端 `"repeat"`，更新原型文档。

#### 问题2：结束类型 "count" 未实现

**当前代码（缺失）**：
```java
// shouldExecute() 中只检查了 endType="date"
if ("date".equals(script.getEndType()) && script.getEndTime() != null && now.isAfter(script.getEndTime())) {
    return false;
}
// 缺少: endType="count" 的判断
```

**修复代码**：
```java
if ("repeat".equals(triggerType)) {
    // 检查结束条件
    if ("date".equals(script.getEndType())) {
        LocalDateTime endTime = script.getEndTime();
        if (endTime != null && now.isAfter(endTime)) {
            return false;  // 已过期
        }
    }
    if ("count".equals(script.getEndType())) {
        Integer count = script.getExecutionCount();
        Integer repeatCount = script.getRepeatCount();
        if (count != null && repeatCount != null && count >= repeatCount) {
            return false;  // 已达次数
        }
    }
}
```

#### 周期触发字段对应

| 字段 | 数据库列 | 说明 |
|------|---------|------|
| triggerType | trigger_type | 固定值 "repeat" |
| repeatType | repeat_type | daily/weekly/monthly |
| repeatTime | repeat_time | HH:mm:ss 格式 |
| weeklyDays | weekly_days | 逗号分隔 "1,3,5" |
| monthlyDay | monthly_day | 1-31 |
| monthlyLastDay | monthly_last_day | Boolean |
| endType | end_type | never/date/count |
| endTime | end_time | 日期时间 |
| repeatCount | repeat_count | 重复次数 |

#### 文件变更

| 文件 | 修改内容 |
|------|---------|
| `backend/TriggerScheduleService.java` | 添加 endType="count" 的判断逻辑 |
| `docs/superpowers/specs/task-detail-prototype.html` | 更新 cycle 为 repeat（文档统一） |

---

### 9.7.7 Python 调度模式问题

#### 问题描述

设计文档原则："采集脚本只负责'怎么采'，调度由系统决定'什么时候采'"

但实际实现中，Python corn_scheduler.py 是**主动调度模式**：
- 本地 config.yaml 配置了执行时间（`collect_times: ["09:30", "10:00"]`）
- APScheduler 主动按配置时间触发执行
- 后端配置的时间（`triggerType=repeat, repeatTime=09:30`）**与 Python 无关**

这**违反**了设计原则，且存在数据不一致风险。

#### 当前架构（错误）

```
前端配置"每天 9:30 执行"
    ↓ PUT /scripts/{id}
后端 CollectionScript 表 (triggerType=repeat, repeatTime="09:30:00")
    ↓
后端 TriggerScheduleService 每分钟检查
    ↓ 到了时间 → 创建 TaskExecution
    ↓
Python corn_scheduler.py
    ↓ config.yaml: collect_times: ["09:30"] ← 本地配置，不同步！
    ↓ APScheduler 主动触发
执行采集
```

#### 推荐架构（被动等待）

```
后端 TriggerScheduleService（每分钟）
    ↓ 检查 triggerType=repeat, repeatTime
    ↓ 时间到了 → 创建 TaskExecution (status=pending)

Python corn_scheduler.py（轮询 10 秒）
    ↓ GET /task/execution/pending
    ↓ 发现 pending 任务
    ↓ 执行采集
    ↓ POST /collector/exec/{id}/complete
```

**Python 不再需要知道"什么时候执行"，只响应后端的执行指令。**

#### 两种模式对比

| 维度 | 主动调度（当前，错误） | 被动等待（推荐，正确） |
|------|----------------------|---------------------|
| 时间配置 | Python config.yaml + 后端两套 | 后端一套 |
| 一致性 | ❌ 两边可能不同步 | ✓ 完全一致 |
| 解耦性 | ❌ Python 依赖本地时间 | ✓ Python 只管执行 |
| 后端挂了 | Python 仍能执行 | Python 无法执行 |
| 维护成本 | 高（改时间要改两处） | 低（只改后端） |
| 架构设计 | 违反"采集逻辑与调度分离"原则 | ✓ 符合设计原则 |

#### Python corn_scheduler.py 修改

```python
# 移除 APScheduler 主动调度
# 改为轮询后端 pending 任务

import schedule
import time

class CornScheduler:
    def __init__(self, api_base):
        self.api_base = api_base

    def poll_pending_tasks(self):
        """轮询后端获取待执行任务"""
        # GET /task/execution/pending
        response = requests.get(f"{self.api_base}/task/execution/pending")
        if response.status_code == 200:
            tasks = response.json().get("data", [])
            for task in tasks:
                self.execute_task(task)

    def execute_task(self, task):
        """执行单个任务"""
        script_id = task["scriptId"]
        execution_id = task["executionId"]
        report_type = task.get("reportType", "morning")

        # 执行采集
        collector = LiangxinCollector(config, script_id, username, password, report_type)
        result = collector.run()

        # 上报完成
        requests.post(
            f"{self.api_base}/collector/exec/{execution_id}/complete",
            json={"status": "success", "collectedCount": result.get("collected_count", 0)}
        )

    def run(self):
        """主循环：每 10 秒轮询一次"""
        while True:
            self.poll_pending_tasks()
            time.sleep(10)
```

#### 移除的配置

从 corn_scheduler.py 和 config.yaml 中移除：
```yaml
# 不再需要这些配置
corn:
  report_types:
    morning:
      collect_times: ["09:30", "10:00"]
    evening:
      collect_times: ["18:30"]
```

#### 文件变更

| 文件 | 修改内容 |
|------|---------|
| `python-collector-sdk/scheduler/corn_scheduler.py` | 移除 APScheduler，改为轮询后端 pending 任务 |
| `python-collector-sdk/config.yaml` | 移除 collect_times 配置 |
| `backend/TriggerScheduleService.java` | 确保时间到了创建 pending 任务 |

---

### 9.7.8 Cron 表达式问题修复

#### 问题汇总

| 功能 | 状态 | 说明 |
|------|------|------|
| 前端输入和校验 | ✓ | 有 Cron Tab 和常用模板 |
| 后端校验接口 | 部分 | 只返回 valid，不返回 description/nextExecutions |
| isCronMatch 执行判断 | ✓ | 使用 Spring CronExpression，正确 |
| 计算未来5次触发时间 | ❌ | 前端无法工作，后端未实现 |
| 返回描述信息 | ❌ | 后端只返回 valid，前端期望 description |

#### 问题1：后端校验接口返回不完整

**当前实现（错误）**：
```java
@PostMapping("/validate-cron")
public Result<Map<String, Boolean>> validateCron(@RequestBody Map<String, String> request) {
    String cron = request.get("cron");
    boolean valid = isValidCronExpression(cron);
    return Result.success(Map.of("valid", valid));  // 只返回 valid
}
```

**前端期望**：
```json
{
  "valid": true,
  "description": "每天 8:00 执行",
  "nextExecutions": [
    "2026-05-15 08:00",
    "2026-05-16 08:00",
    "2026-05-17 08:00",
    "2026-05-18 08:00",
    "2026-05-19 08:00"
  ]
}
```

#### 修复后端校验接口

```java
@PostMapping("/validate-cron")
public Result<Map<String, Object>> validateCron(@RequestBody Map<String, String> request) {
    String cron = request.get("cron");
    Map<String, Object> result = new HashMap<>();

    try {
        CronExpression.parse(cron);  // 验证格式
        result.put("valid", true);
        result.put("description", describeCron(cron));  // 描述信息

        // 计算未来5次触发时间
        List<String> nextExecutions = new ArrayList<>();
        ZonedDateTime now = ZonedDateTime.now();
        CronExpression parsed = CronExpression.parse(cron);

        TemporalAccessor next = parsed.next(now);
        for (int i = 0; i < 5 && next != null; i++) {
            nextExecutions.add(next.toString());
            next = parsed.next(next);
        }
        result.put("nextExecutions", nextExecutions);

    } catch (Exception e) {
        result.put("valid", false);
        result.put("error", e.getMessage());
    }

    return Result.success(result);
}

private String describeCron(String cron) {
    // 简单描述：解析 Cron 表达式返回人类可读描述
    // 例如: "0 8 * * *" -> "每天 8:00"
    // "0 9 * * 1" -> "每周一 9:00"
    // "0 8 1 * *" -> "每月1号 8:00"
    // ...
}
```

#### 问题2：计算未来5次触发时间

Spring 的 `CronExpression` 支持计算下次触发时间：

```java
CronExpression parsed = CronExpression.parse(cron);
TemporalAccessor next = parsed.next(now);

for (int i = 0; i < 5 && next != null; i++) {
    nextExecutions.add(format(next));  // 格式化输出
    next = parsed.next(next);  // 计算下下次
}
```

#### 前端如何使用

```typescript
async function validateCron() {
  const res = await cronApi.validate(cronExpression.value)
  if (res.data.valid) {
    cronDescription.value = res.data.description  // 显示描述
    nextExecutions.value = res.data.nextExecutions  // 显示未来5次
  }
}
```

#### 文件变更

| 文件 | 修改内容 |
|------|---------|
| `backend/CollectionScriptController.java` | `/validate-cron` 接口返回 description 和 nextExecutions |
| `backend/CronDescriptionUtil.java` | 新增 Cron 表达式描述工具类 |

---

### 9.8 取消执行

#### 现状

| 环节 | 状态 | 说明 |
|------|------|------|
| 原型设计 | ❌ 缺失 | task-list-prototype.html 和 task-detail-prototype.html 均无"取消执行"按钮 |
| 后端接口 | ✓ 已实现 | `POST /scripts/executions/{executionId}/cancel` |
| 前端 API | ✓ 已实现 | `executionApi.cancel(executionId)` |
| TaskDetail.vue | ✓ 已实现 | 执行对话框中有"取消执行"按钮 |
| CollectionProgress.vue | ✓ 已实现 | 进度抽屉中有"取消执行"按钮 |
| Python 取消机制 | ❌ 未实现 | Python 没有接收取消指令的接口 |

#### 后端接口

```java
@PostMapping("/executions/{executionId}/cancel")
public Result<Void> cancelExecution(@PathVariable String executionId) {
    executionService.updateStatus(executionId, "cancelled", "用户取消");
    return Result.success();
}
```

#### 前端实现

```javascript
async function handleCancelExecution() {
  await executionApi.cancel(currentExecutionId.value)
  finishExecution()
}
```

#### 问题：Python 无取消机制

当前设计说"发请求给 Python 取消"，但 Python 采集服务**没有实现取消接口**。

#### 流程（当前可实现的）

```
用户点击"取消"
    ↓
POST /api/scripts/executions/{executionId}/cancel
    ↓
后端更新 TaskExecution.status = "cancelled"
    ↓
前端刷新状态显示已取消
```

**注意**：只能取消 `pending` 状态的任务。`running` 状态的任务已经正在执行，Python 不会响应取消（因为没有实现）。

#### 待实现：Python 取消机制

```python
# corn_scheduler.py 添加取消接口
@app.route('/collector/exec/{execution_id}/cancel', methods=['POST'])
def cancel_execution(execution_id):
    if execution_id in executions:
        executions[execution_id]['cancelled'] = True
        return {"success": True}
    return {"error": "execution not found"}, 404

# 采集循环中检查
def collect_reports():
    for report in reports:
        if ctx.is_cancelled():
            return  # 提前退出
        process_report(report)
```

#### 文件变更

| 文件 | 修改内容 |
|------|---------|
| `task-detail-prototype.html` | 已有完整的取消执行功能 |
| `python-collector-sdk/corn_scheduler.py` | 实现取消接口和检查逻辑 |

---

### 9.9 错误处理

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

### 9.10 数据清理

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

### 9.11 监控告警

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

### 10.1 Python 服务启动

```bash
cd python-collector-sdk
python -m scheduler.collector_server --port 5001
```

### 10.2 配置后端地址

```yaml
# config.yaml
collector_service:
  url: "http://localhost:5001"
```

### 10.3 环境变量

| 变量 | 说明 | 默认值 |
|------|------|-------|
| `COLLECTOR_PORT` | Python 服务端口 | 5001 |
| `BROWSER_IDLE_TIMEOUT` | 浏览器空闲超时(秒) | 7200 |
