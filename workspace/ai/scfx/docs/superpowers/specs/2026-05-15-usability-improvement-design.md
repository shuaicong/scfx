# 采集管理系统使用体验优化设计

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 作为高频重度使用者，解决当前系统最影响体验的问题

**背景:** 通过用户调研和实际使用分析，识别出以下核心痛点和改进方案

---

## 一、核心痛点分析

### 1.1 当前最影响体验的问题

| 痛点 | 严重程度 | 影响 |
|------|---------|------|
| 任务列表看不到实时状态 | 高 | 无法快速定位问题任务 |
| Python 服务挂了不知道 | 高 | 任务失败但不知道原因 |
| 失败排查困难 | 高 | 问题定位耗时太长 |
| 脚本版本不关联 | 中 | 出问题无法追溯 |
| 定时配置不知道是否正确 | 中 | 配置错误直到不执行才知道 |
| 操作无记录可追溯 | 中 | 问题排查缺少审计线索 |
| 新增数据源步骤多 | 低 | 流程繁琐 |

---

## 二、改进方案

### 2.1 任务列表实时状态监控

#### 问题

当前任务列表只显示"已配置/已启用"，执行中的状态不更新，需要点进详情页才能看到。

#### 改进

**任务列表增加实时状态列：**

```
┌────────────────────────────────────────────────────────────────────┐
│ 任务名称        │ 数据源 │ 定时       │ 状态        │ 操作          │
├────────────────────────────────────────────────────────────────────┤
│ 粮信网玉米晨报   │ 粮信网 │ 9:30      │ ⚡ 执行中 3条│ [停止]        │
│ 我的钢铁网价格   │ 我的钢铁│ Cron     │ ⚠ 失败      │ [重试] [日志] │
│ 中华粮网日报     │ 中华粮网│ 18:30    │ ✓ 成功 10条 │ [查看] [日志] │
│ USDA数据        │ USDA   │ 每天8点   │ ○ 待执行    │ [执行]        │
└────────────────────────────────────────────────────────────────────┘
```

**状态显示规则：**

| 状态 | 显示 | 说明 |
|------|------|------|
| pending | ○ 待执行 | 等待调度 |
| running | ⚡ 执行中 N条 | 正在执行，显示实时数量 |
| success | ✓ 成功 N条 | 执行成功，显示采集数量 |
| failed | ⚠ 失败 | 执行失败，需要处理 |
| cancelled | ⊘ 已取消 | 用户取消（区别于失败） |

**停止功能：**

| 方式 | 说明 |
|------|------|
| 停止方式 | 优雅退出（采集完当前项再停止） |
| 状态记录 | cancelled（已取消），区别于 failed（失败） |

**实现方式：**

```javascript
// 前端轮询任务状态（每5秒）
async function pollTaskStatuses() {
  const res = await fetch('/api/scripts/status?ids=1,2,3,4')
  const statuses = res.data  // { id: status, collectedCount, ... }

  for (const [id, status] of Object.entries(statuses)) {
    const row = document.getElementById(`task-row-${id}`)
    updateTaskRow(row, status)
  }
}

setInterval(pollTaskStatuses, 5000)
```

**后端接口：**

```java
@GetMapping("/scripts/status")
public Result<Map<Long, TaskStatus>> getTasksStatus(
    @RequestParam List<Long> ids
) {
    // 批量查询任务当前状态
    Map<Long, TaskStatus> statuses = scriptService.getTasksStatus(ids)
    return Result.success(statuses)
}
```

**停止执行接口：**

```java
@PostMapping("/scripts/{scriptId}/executions/{executionId}/stop")
public Result<Void> stopExecution(...) {
    // 1. 更新执行状态为 cancelled
    executionService.updateStatus(executionId, "cancelled");
    // 2. 通知 Python 停止（写标记文件）
    stopMarkerService.markStop(executionId);
    return Result.success();
}
```

```python
# Python 采集器检查停止标记
def check_should_stop():
    stop_file = f"/tmp/stop_{execution_id}"
    if os.path.exists(stop_file):
        os.remove(stop_file)
        raise StopException("用户停止")

# 在采集循环中定期检查
for item in items:
    check_should_stop()
    collect(item)
```

---

### 2.2 Python 服务心跳监控

#### 问题

Python 服务挂了，前端不知道，任务失败不知道为什么。

#### 当前状态

| 端 | 状态 | 说明 |
|---|---|---|
| Python | ✅ 已有 | `reporter.py` 每60秒发送心跳到 `/collector/heartbeat` |
| Java后端 | ✅ 已有 | `CollectorManageController.java` 接收心跳并更新 `lastHeartbeat` 字段 |

#### 改进

**前端显示采集服务状态：**

```
┌─────────────────────────────┐
│  采集服务: ● 正常            │
└─────────────────────────────┘

┌─────────────────────────────┐
│  采集服务: ⚠ 异常 (离线)    │
│  [重新连接] [查看详情]       │
└─────────────────────────────┘
```

**配置参数：**

| 参数 | 值 | 说明 |
|------|---|------|
| 心跳超时 | 2分钟 | 超过2分钟无心跳则告警 |
| 前端轮询间隔 | 10秒 | 每10秒检查一次心跳状态 |

**前端检查心跳：**

```javascript
// 每10秒检查心跳是否超时（超过120秒没有心跳则显示异常）
async function checkServiceHealth() {
  const res = await fetch('/api/collector/heartbeat/status')
  const lastHeartbeat = res.data.lastHeartbeat

  const now = Date.now()
  const timeout = 120000  // 2分钟超时

  if (now - lastHeartbeat > timeout) {
    showServiceStatus('offline')
  } else {
    showServiceStatus('online')
  }
}

setInterval(checkServiceHealth, 10000)
```

**"重新连接"按钮：** 强制刷新心跳状态，立即重新检测服务健康状态。

**告警触发：**

```java
// 定时检查心跳，超时发送告警
@Scheduled(fixedRate = 60000)
public void checkHeartbeatTimeout() {
    Long lastHeartbeat = cache.get("heartbeat:python-collector", Long.class);

    if (lastHeartbeat == null || System.currentTimeMillis() - lastHeartbeat > 120000) {
        // 超过2分钟没有心跳，发送告警
        alertService.send("采集服务离线", AlertLevel.WARN);
    }
}
```

---

### 2.3 执行详情独立页面

#### 问题

执行详情在弹窗里看，日志内容受限，无法完整分析。

#### 改进

**执行详情改为独立页面：**

```
URL: /scripts/{scriptId}/executions/{executionId}
```

**页面布局：**

```
┌─────────────────────────────────────────────────────────────────┐
│ ← 返回任务列表     执行详情                    2026-05-15 08:00 │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  基本信息                                                        │
│  ├─ 任务：粮信网玉米晨报                                         │
│  ├─ 数据源：粮信网                                               │
│  ├─ 触发方式：定时（9:30）                                       │
│  ├─ 脚本版本：v3 (MD5: a1b2c3d4)       [查看此版本] [对比版本]   │
│  ├─ 执行状态：✓ 成功                                            │
│  └─ 采集数量：3 条                                              │
│                                                                 │
│  执行日志                              [下载日志] [清屏]  ⚡ 实时 │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ [08:00:01] INFO  开始执行采集                               ││
│  │ [08:00:02] INFO  登录成功                                   ││
│  │ ...                                                         ││
│  └─────────────────────────────────────────────────────────────┘│
│                                                                 │
│  操作                                                            │
│  [重新执行]  [对比执行记录]  [对比脚本版本]  [对比执行日志]      │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**功能说明：**

| 功能 | 说明 |
|------|------|
| 日志实时刷新 | 执行中日志动态更新，无需刷新页面 |
| 查看此版本 | 显示版本快照（版本号、MD5、上传时间、上传人），点击跳转版本历史入口 |
| 对比执行记录 | 从历史执行列表选两条，对比采集数量、耗时、状态变化 |
| 对比脚本版本 | 从版本历史选两个版本，对比代码内容差异 |
| 对比执行日志 | 本次 vs 上次日志，按时间线对齐，差异标记（✓/⚠/✗） |

**与任务详情页的关系：**

| 页面 | URL | 用途 |
|------|-----|------|
| 任务详情页 | `/scripts/{id}` | 编辑任务配置、查看定时设置 |
| 执行详情页 | `/scripts/{id}/executions/{execId}` | 查看某次执行的具体情况、日志 |

**详细设计见：** [2026-05-15-execution-compare-design.md](2026-05-15-execution-compare-design.md)

---

### 2.4 Cron 表达式校验增强

#### 问题

配置 Cron 表达式后不知道是否正确，不知道什么时候会执行。

#### 改进

**Cron 输入 + 校验界面：**

```
┌─────────────────────────────────────────────────────────────┐
│ Cron 表达式                                                   │
│                                                             │
│  快速选择：[每天9:30 ▼]                                      │
│                                                             │
│  或手动输入：                                                 │
│  ┌─────────────────────────────────────────────────────────┐│
│  │ 0 30 9 * * ?________________________________    [校验] ││
│  └─────────────────────────────────────────────────────────┘│
│                                                             │
│  ✓ 格式正确                                                   │
│  描述：每天 9:30 执行                                          │
│                                                             │
│  未来5次触发时间：                                             │
│    1. 2026-05-16 09:30:00 (明天)                              │
│    2. 2026-05-17 09:30:00                                    │
│    3. 2026-05-18 09:30:00                                    │
│    4. 2026-05-19 09:30:00                                    │
│    5. 2026-05-20 09:30:00                                    │
└─────────────────────────────────────────────────────────────┘
```

**交互方式：**

| 功能 | 实现 |
|------|------|
| 输入方式 | 预设选项 + 允许手动输入 |
| 校验触发 | 实时校验（输入时，带防抖300ms） |
| 快速选择 | 预设模板（"每天9:30"、"每周一9:00"等） |

**预设模板：**

| 模板 | Cron 表达式 | 描述 |
|------|-------------|------|
| 每天9:30 | `0 30 9 * * ?` | 每天上午 9:30 |
| 每天18:00 | `0 0 18 * * ?` | 每天下午 6:00 |
| 每周一9:00 | `0 0 9 ? * MON` | 每周一上午 9:00 |
| 每月1日9:00 | `0 0 9 1 * ?` | 每月1日上午 9:00 |
| 工作日9:30 | `0 30 9 * * MON-FRI` | 工作日上午 9:30 |

**后端接口：**

```java
@PostMapping("/scripts/validate-cron")
public Result<Map<String, Object>> validateCron(@RequestBody Map<String, String> request) {
    String cron = request.get("cron");
    Map<String, Object> result = new HashMap<>();

    try {
        CronExpression.parse(cron);
        result.put("valid", true);
        result.put("description", describeCron(cron));  // "每天 9:30 执行"

        // 计算未来5次
        List<String> nextExecutions = calculateNext5Executions(cron);
        result.put("nextExecutions", nextExecutions);

    } catch (Exception e) {
        result.put("valid", false);
        result.put("error", e.getMessage());
    }

    return Result.success(result);
}
```

---

### 2.5 脚本版本与执行记录关联

#### 问题

任务 v3 执行失败了，但我不知道 v3 用的是哪个版本的 mysteel.py。

#### 改进

**执行记录关联脚本版本：**

```sql
-- t_task_execution 表新增字段
ALTER TABLE t_task_execution ADD COLUMN script_version INT;
ALTER TABLE t_task_execution ADD COLUMN script_md5 VARCHAR(32);
```

**保存执行记录时：**

```java
// TaskExecutionService
public void saveExecution(TaskExecution execution, Script script) {
    execution.setScriptVersion(script.getCurrentVersion());
    execution.setScriptMd5(script.getCurrentMd5());
    executionMapper.insert(execution);
}
```

**执行详情页显示：**

```
基本信息的脚本版本字段：
├─ 脚本版本：v3 (MD5: a1b2c3d4)
│          └─ [查看此版本] [对比版本]
```

**"查看此版本"功能：** 显示版本信息（版本号、MD5、上传时间、上传人）

**"对比版本"功能：** 打开版本选择器，默认选中当前版本和上一版本，用户可选择其他版本进行对比。

**执行记录显示历史版本：** 显示最近10条历史执行记录关联的脚本版本。

**版本历史统一入口：** 数据源详情页是完整的版本管理入口，执行详情页的版本按钮是快捷入口，点击后跳转到数据源详情页的版本管理。

---

### 2.6 操作日志和审计

#### 问题

谁在什么时候做了什么操作，无法追溯。

#### 改进

**操作日志表：**

```sql
CREATE TABLE t_operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    operator VARCHAR(100) NOT NULL,
    operation_type VARCHAR(50) NOT NULL,  -- CREATE/UPDATE/DELETE/EXECUTE/ROLLBACK
    target_type VARCHAR(50) NOT NULL,     -- SCRIPT/DATASOURCE/TASK
    target_id BIGINT NOT NULL,
    detail JSON,                           -- 操作详情
    ip VARCHAR(50),
    operate_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_target (target_type, target_id),
    INDEX idx_operator_time (operator, operate_time)
);
```

**实现方式：**

| 方式 | 说明 |
|------|------|
| 记录方式 | 纯自动记录，通过 AOP 拦截所有业务操作，不支持手动触发 |
| 保留时间 | 90天，超期自动删除 |
| 删除规则 | 不支持手动删除，通过保留策略自动清理 |

**日志查看页面：**

```
┌────────────────────────────────────────────────────────────────────┐
│ 操作日志                                           [筛选] [导出]     │
├────────────────────────────────────────────────────────────────────┤
│ 时间              │ 操作人 │ 类型   │ 对象      │ 详情              │
├────────────────────────────────────────────────────────────────────┤
│ 2026-05-15 14:30 │ admin  │ 上传   │ 粮信网    │ 上传 v3，MD5:a1b2 │
│ 2026-05-15 10:00 │ zhang  │ 执行   │ 玉米晨报  │ 手动执行，成功    │
│ 2026-05-15 09:30 │ 系统   │ 定时   │ 玉米晨报  │ Cron 触发         │
│ 2026-05-14 16:00 │ admin  │ 修改   │ 玉米晨报  │ 禁用 → 启用       │
└────────────────────────────────────────────────────────────────────┘
```

**记录操作：**

```java
// AOP 拦截
@Aspect
@Component
public class OperationLogAspect {

    @Autowired
    private OperationLogService logService;

    @AfterReturning("@annotation(OperationLog)")
    public void logOperation(JoinPoint joinPoint, OperationLog annotation) {
        String operator = getCurrentUser();
        String operationType = annotation.type();
        String targetType = annotation.targetType();

        // 获取参数构建 detail
        Object[] args = joinPoint.getArgs();
        Long targetId = extractTargetId(args);

        logService.log(operator, operationType, targetType, targetId, args);
    }
}

// 使用
@OperationLog(type = "UPLOAD", targetType = "DATASOURCE")
@PostMapping("/datasource/upload-collector")
public Result<DataSource> uploadCollector(...) {
    // 业务逻辑
}
```

---

### 2.7 告警通知

#### 问题

任务失败了，必须人工检查才能发现。

#### 改进

**告警规则：**

| 规则 | 条件 | 动作 |
|------|------|------|
| 连续失败告警 | 连续3次失败 | 发送告警 |
| 服务离线告警 | 2分钟无心跳 | 发送告警 |
| 采集数量异常 | 本次=0且上次>0 | 发送告警 |
| 执行超时告警 | 执行超过30分钟 | 发送告警 |

**告警配置：**

| 配置项 | 说明 |
|--------|------|
| 通知渠道 | 钉钉 + 邮件 |
| 规则管理 | 预设规则 + 可编辑阈值（如连续2次 vs 3次才告警） |
| 已读功能 | 发送即完成，无需确认 |

**告警配置表：**

```sql
CREATE TABLE t_alert_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_name VARCHAR(100),
    rule_type VARCHAR(50),              -- CONTINUOUS_FAIL/SERVICE_OFFLINE/ZERO_RESULT/TIMEOUT
    condition JSON,                      -- { threshold: 3, interval: "3h" }
    enabled TINYINT(1) DEFAULT 1,
    notify_channels JSON,               -- ["dingtalk", "email"]
    notify_target VARCHAR(500),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
```

**告警通知服务：**

```java
@Service
public class AlertService {

    public void checkAndAlert() {
        // 检查连续失败
        List<Script> failedScripts = scriptService.getContinuouslyFailedScripts(3);
        for (Script script : failedScripts) {
            sendAlert(script, "连续执行失败");
        }

        // 检查服务心跳
        Long lastHeartbeat = cache.get("heartbeat:python-collector", Long.class);
        if (lastHeartbeat == null || System.currentTimeMillis() - lastHeartbeat > 120000) {
            sendSystemAlert("采集服务离线");
        }
    }

    private void sendAlert(Script script, String message) {
        // 发送到配置的渠道（钉钉 + 邮件）
        for (String channel : script.getAlertChannels()) {
            if ("dingtalk".equals(channel)) {
                sendDingTalkAlert(script, message);
            } else if ("email".equals(channel)) {
                sendEmailAlert(script, message);
            }
        }
    }
}
```

**告警通知内容示例：**

```
【采集管理系统告警】

任务：粮信网玉米晨报
数据源：粮信网
告警类型：连续执行失败
告警内容：连续3次执行失败，上次失败时间 2026-05-15 09:30
可能原因：账号密码错误 / 网站结构变化 / 网络异常

处理建议：
1. 检查账号密码是否正确
2. 检查网站是否改版
3. 查看最新执行日志定位具体错误

查看详情：/scripts/1/executions/latest
```

---

### 2.8 数据源关联任务视图

#### 问题

只知道任务用了哪个数据源，不知道数据源被哪些任务使用。

#### 改进

**数据源详情页增加关联任务列表：**

```
数据源：粮信网

基本信息：
├─ 标识：liangxin
├─ 名称：粮信网
├─ 登录地址：https://my.chinagrain.cn/jinnong/a/login
├─ 认证方式：cookie
└─ 状态：● 启用

关联任务（3个）：                                    [按最后执行时间 ▼]
┌────────────────────────────────────────────────────────────┐
│ 任务名称        │ 状态   │ 最后执行         │ 累计采集    │
├────────────────────────────────────────────────────────────┤
│ 粮信网玉米晨报   │ ✓ 成功 │ 2026-05-15 09:30 │ 150 条      │
│ 粮信网玉米日报   │ ✓ 成功 │ 2026-05-15 18:30 │ 230 条      │
│ 粮信网市场分析   │ ⚠ 失败 │ 2026-05-14 10:00 │ 89 条       │
└────────────────────────────────────────────────────────────┘

脚本版本：
├─ 当前版本：v3 (2026-05-15 上传)
└─ [查看版本历史]
```

**功能说明：**

| 功能 | 说明 |
|------|------|
| 关联任务统计 | 显示累计采集数量和累计执行次数 |
| 版本历史 | 统一入口，数据源详情页完整管理，执行详情页快捷跳转 |
| 排序 | 默认按最后执行时间降序，支持切换为按状态或采集数量 |

**版本历史统一入口：** 数据源详情页的"查看版本历史"是完整的版本管理入口，执行详情页的"查看此版本"按钮是快捷入口，点击后跳转到数据源详情页的版本管理。

---

## 三、数据模型变更

### 3.1 新增表

```sql
-- 操作日志表
CREATE TABLE t_operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    operator VARCHAR(100) NOT NULL,
    operation_type VARCHAR(50) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id BIGINT NOT NULL,
    detail JSON,
    ip VARCHAR(50),
    operate_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 告警规则表
CREATE TABLE t_alert_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_name VARCHAR(100),
    rule_type VARCHAR(50),
    condition JSON,
    enabled TINYINT(1) DEFAULT 1,
    notify_channels JSON,
    notify_target VARCHAR(500),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- 告警记录表
CREATE TABLE t_alert_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_id BIGINT,
    script_id BIGINT,
    alert_type VARCHAR(50),
    message TEXT,
    status VARCHAR(20),  -- SENT/FAILED/READ
    created_at TIMESTAMP
);
```

### 3.2 表结构变更

```sql
-- t_task_execution 新增字段
ALTER TABLE t_task_execution ADD COLUMN script_version INT;
ALTER TABLE t_task_execution ADD COLUMN script_md5 VARCHAR(32);

-- t_data_source 新增字段（用于心跳监控）
ALTER TABLE t_data_source ADD COLUMN last_heartbeat TIMESTAMP;

-- t_collection_script 新增字段（用于告警）
ALTER TABLE t_collection_script ADD COLUMN alert_enabled TINYINT(1) DEFAULT 0;
ALTER TABLE t_collection_script ADD COLUMN alert_channels JSON;
```

---

## 四、文件变更清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `backend/TaskExecution.java` | 修改 | 添加 scriptVersion, scriptMd5 字段 |
| `backend/DataSource.java` | 修改 | 添加 lastHeartbeat 字段 |
| `backend/CollectionScript.java` | 修改 | 添加 alertEnabled, alertChannels 字段 |
| `backend/OperationLog.java` | 新增 | 操作日志实体 |
| `backend/AlertRule.java` | 新增 | 告警规则实体 |
| `backend/AlertRecord.java` | 新增 | 告警记录实体 |
| `backend/TaskExecutionService.java` | 修改 | 保存执行时记录脚本版本 |
| `backend/TaskStatusController.java` | 新增 | 批量查询任务状态接口 |
| `backend/HeartbeatController.java` | 新增 | 心跳接收接口 |
| `backend/AlertService.java` | 新增 | 告警检查和发送服务 |
| `backend/OperationLogAspect.java` | 新增 | 操作日志 AOP |
| `frontend/src/views/scripts/TaskList.vue` | 修改 | 添加实时状态列 |
| `frontend/src/views/scripts/ExecutionDetail.vue` | 新增 | 执行详情独立页面 |
| `frontend/src/views/system/DataSourceDetail.vue` | 修改 | 添加关联任务列表 |
| `frontend/src/views/system/OperationLog.vue` | 新增 | 操作日志页面 |
| `frontend/src/components/ServiceStatus.vue` | 新增 | 服务状态监控组件 |
| `schema.sql` | 修改 | 添加新表和字段 |

---

## 五、优先级

| 功能 | 优先级 | 说明 |
|------|--------|------|
| 任务列表实时状态 | P0 | 高频操作，最影响体验 |
| Python 服务心跳监控 | P0 | 快速定位问题 |
| 执行详情独立页面 | P0 | 排查问题必备 |
| Cron 校验增强 | P1 | 配置错误早发现 |
| 脚本版本关联 | P1 | 问题追溯 |
| 操作日志 | P1 | 审计需要 |
| 告警通知 | P2 | 重要但非紧急 |
| 数据源关联任务视图 | P2 | 辅助分析 |

---

## 六、验证方案

1. **任务列表实时状态**
   - 启动执行后，列表状态自动更新
   - 刷新页面状态保持

2. **服务心跳监控**
   - 停止 Python 服务，前端显示异常
   - 恢复 Python 服务，前端显示正常

3. **执行详情页面**
   - 执行完成后点击"查看"进入详情页
   - 日志完整显示，可下载

4. **Cron 校验**
   - 输入错误格式显示错误提示
   - 输入正确格式显示描述和未来5次

5. **脚本版本关联**
   - 执行记录显示脚本版本号
   - 可查看历史版本

6. **操作日志**
   - 操作后可在日志页面查到记录

7. **告警通知（模拟）**
   - 连续失败3次后显示告警（可先模拟，不实际发送）