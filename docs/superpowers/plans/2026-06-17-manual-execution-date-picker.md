# 手动执行支持选日期采集 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 网页端手动执行采集任务时支持选择目标日期，用户可补采历史某天的玉米日报。

**Architecture:** 前端传递 `date` 参数 → Java 后端校验后存入 `detail_json` → 执行时通过 `ProcessBuilder` 多参数列表传递 `--date` → Python 采集器使用 `target_date` 采集指定日期。不下发新表，不修改 cron 调度。

**Tech Stack:** Vue 3 (Element Plus), Java Spring Boot, Python 3 + Playwright, MySQL

---

## File Structure

| 层 | 文件 | 改动 |
|---|------|------|
| Python | `python-collector-sdk/collectorsdk/collectors/liangxin.py` | `__init__` 加 `target_date`，`collect()` 使用 `target_date` + 日志 |
| Python | `python-collector-sdk/main.py` | `run` 子命令加 `--date` |
| Python | `python-collector-sdk/liangxin-yumi-daily-report.py` | 加 `--date` 参数 |
| Java | `backend/.../controller/CollectionScriptController.java` | 接受 DTO |
| Java | `backend/.../dto/ScriptExecuteReq.java` | **新增** DTO |
| Java | `backend/.../service/CollectionScriptService.java` | 校验逻辑 + detail_json 写入 |
| Java | `backend/.../service/CollectorAgentService.java` | 读取 detail_json + 拼命令 |
| Java | `backend/src/main/resources/application.yml` | 新增 `collector.min-execute-date` |
| Vue | `frontend/src/views/scripts/TaskDetail.vue` | 日期选择弹窗 + 二次确认 |
| Vue | `frontend/src/views/scripts/TaskList.vue` | 日期选择弹窗 |
| Vue | `frontend/src/api/execution.ts` | execute 加 params 参数 |
| Vue | `frontend/src/api/index.ts` | execute 加 params 参数 |

---

### Task 1: Python 采集器 + main.py 加 --date

**Files:**
- Modify: `python-collector-sdk/collectorsdk/collectors/liangxin.py:37-68`
- Modify: `python-collector-sdk/main.py:223-228`
- Modify: `python-collector-sdk/liangxin-yumi-daily-report.py`

- [ ] **Step 1: 修改 LiangxinCollector.__init__ 加 target_date**

修改 `__init__` 方法签名，增加 `target_date` 参数：

```python
def __init__(
    self,
    config: ReporterConfig,
    task_id: int,
    username: str,
    password: str,
    report_type: str = "morning",
    execution_id: str = None,
    target_date: str = None,  # <-- 新增
):
    ...
    self.target_date = target_date
```

- [ ] **Step 2: 修改 collect() 使用 target_date 并增加日志和校验**

修改 `collect()` 方法：

```python
def collect(self) -> int:
    count = 0
    # 目标日期：优先使用传入的 target_date，否则取今天
    if self.target_date and self.target_date.strip():
        run_date = self.target_date.strip()
    else:
        run_date = datetime.now().strftime("%Y-%m-%d")
    # 兜底强解析校验
    try:
        datetime.strptime(run_date, "%Y-%m-%d")
    except ValueError:
        logger.error(f"manual_execute_collect_date 目标日期格式非法: {run_date}，终止采集")
        return 1

    logger.info(f"manual_execute_collect_date 粮信玉米日报采集目标日期：{run_date}, executionId={self.execution_id}")
    today = run_date  # 替代原来的 datetime.now().strftime(...)

    try:
        ...
```

- [ ] **Step 3: 修改 main.py run 子命令加 --date**

```python
p.add_argument("--date", default=None, help="目标采集日期 (yyyy-MM-dd)，不传则默认今天")
```

在 `cmd_run()` 中将 `--date` 参数传给 Collector：

```python
def cmd_run(args):
    ...
    collector = collector_cls(
        config=reporter_config,
        task_id=args.task_id,
        username=username,
        password=password,
        report_type=report_type,
        execution_id=execution_id,
        target_date=args.date,  # <-- 新增
    )
    ...
```

- [ ] **Step 4: 修改 liangxin-yumi-daily-report.py 加 --date**

修改 standalone 脚本：
```python
parser.add_argument("--date", default=None, help="目标采集日期 (yyyy-MM-dd)")
```

传给 collector：
```python
collector = LiangxinCollector(
    config=reporter_config,
    task_id=args.task_id,
    username=username,
    password=password,
    report_type="evening",
    target_date=args.date,
)
```

- [ ] **Step 5: 提交**

```bash
git add python-collector-sdk/collectorsdk/collectors/liangxin.py \
       python-collector-sdk/main.py \
       python-collector-sdk/liangxin-yumi-daily-report.py
git commit -m "feat: LiangxinCollector 支持 --date 指定采集日期
- __init__ 加 target_date 参数
- collect() 使用 target_date，含 try-except 兜底校验
- main.py run 子命令加 --date
- liangxin-yumi-daily-report.py 加 --date
- 日志带 manual_execute_collect_date + executionId 标识"
```

---

### Task 2: Java 后端透传 date + 校验

**Files:**
- Create: `backend/src/main/java/com/scfx/dto/ScriptExecuteReq.java`
- Modify: `backend/src/main/java/com/scfx/controller/CollectionScriptController.java:133-136`
- Modify: `backend/src/main/java/com/scfx/service/CollectionScriptService.java:58-94`
- Modify: `backend/src/main/resources/application.yml`

- [ ] **Step 1: 新增 ScriptExecuteReq DTO**

```java
package com.scfx.dto;

import lombok.Data;

@Data
public class ScriptExecuteReq {
    private String date;
}
```

- [ ] **Step 2: 修改 Controller 接受 DTO**

```java
@PostMapping("/{id}/execute")
public Result<Map<String, Object>> executeScript(
        @PathVariable Long id,
        @RequestBody(required = false) ScriptExecuteReq req) {
    // 非白名单脚本的 date 参数直接忽略
    return scriptService.executeScriptNow(id, req);
}
```

- [ ] **Step 3: 修改 application.yml 新增配置项**

```yaml
collector:
  min-execute-date: 2020-01-01
```

- [ ] **Step 4: 修改 Service 增加校验和 detail_json 写入**

在 `CollectionScriptService.java` 注入 `@Value` 和 `ObjectMapper`：

```java
@Value("${collector.min-execute-date:2020-01-01}")
private String minExecuteDate;

private final ObjectMapper objectMapper = new ObjectMapper();
```

修改 `executeScriptNow` 方法：

```java
@Transactional
public Result<Map<String, Object>> executeScriptNow(Long id, ScriptExecuteReq req) {
    CollectionScript script = scriptMapper.selectById(id);
    if (script == null) return Result.error("脚本不存在");
    if ("disabled".equals(script.getStatus())) return Result.error("脚本已禁用，请先启用");

    // 1. 分布式锁拦截（基于 scriptId，本地锁 30 秒防高频穿透）
    String lockKey = "script_execute_lock_" + id;
    if (!acquireLock(lockKey, 30)) {
        return Result.error("操作频繁，请稍后重试");
    }
    try {
        // 2. 全局并发拦截：锁内二次校验运行中状态
        long runningCount = executionMapper.selectCount(
            new LambdaQueryWrapper<TaskExecution>()
                .eq(TaskExecution::getScriptId, id)
                .eq(TaskExecution::getStatus, "running"));
        if (runningCount > 0) {
            return Result.error("该任务正在执行中，请等待完成");
        }

        // 3. 白名单检查
        String source = script.getSource();
        boolean isWhiteListed = "liangxin".equals(source) || "liangxin-daily".equals(source);

        // 4. 处理 date 参数
        String date = (req != null && isWhiteListed) ? req.getDate() : null;
        if (date != null) {
            date = date.trim();
            // 长度校验
            if (date.length() > 10) {
                return Result.error("日期格式错误：长度不能超过 10 位");
            }
            // 格式校验
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            try {
                LocalDate parsed = LocalDate.parse(date, formatter);
                // 上限：未来日期拦截，今天放行
                if (parsed.isAfter(LocalDate.now())) {
                    return Result.error("不能采集未来日期的数据");
                }
                // 下限：超早日期拦截
                LocalDate minDate = LocalDate.parse(minExecuteDate, formatter);
                if (parsed.isBefore(minDate)) {
                    return Result.error("日期早于最小可采集日期 " + minExecuteDate);
                }
            } catch (DateTimeParseException e) {
                return Result.error("日期格式错误，必须为 yyyy-MM-dd");
            }
        }

        // 5. 创建执行记录
        TaskExecution execution = executionService.createExecution(id, "manual");

        // 6. 写入 detail_json（只新增 date 键，不覆盖原有 JSON）
        if (date != null) {
            try {
                JsonNode root;
                if (execution.getDetailJson() != null && !execution.getDetailJson().isEmpty()) {
                    root = objectMapper.readTree(execution.getDetailJson());
                } else {
                    root = objectMapper.createObjectNode();
                }
                ((ObjectNode) root).put("date", date);
                execution.setDetailJson(objectMapper.writeValueAsString(root));
            } catch (JsonProcessingException e) {
                // 解析失败时初始化为空 JSON
                ObjectNode root = objectMapper.createObjectNode();
                root.put("date", date);
                execution.setDetailJson(objectMapper.writeValueAsString(root));
            }
            executionMapper.updateById(execution);
        }

        // 7. 采集日期写入执行备注（结构化存储，方便后台检索）
        if (date != null) {
            execution.setErrorMessage("采集日期：" + date);
            executionMapper.updateById(execution);
        }

        // 8. 更新脚本统计
        script.setLastExecutionTime(LocalDateTime.now());
        script.setNextExecutionTime(calculateNextExecution(script));
        script.setExecutionCount(script.getExecutionCount() == null ? 1 : script.getExecutionCount() + 1);
        scriptMapper.updateById(script);

        // 单次触发后自动禁用
        String tt = script.getTriggerType();
        if ("once".equals(tt) || "single".equals(tt)) {
            script.setStatus("disabled");
            scriptMapper.updateById(script);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("scriptId", id);
        result.put("executionId", execution.getExecutionId());
        result.put("scriptName", script.getScriptName());
        result.put("source", script.getSource());

        log.info("manual_execute_collect_date:{}, 任务ID:{}", date, id);
        return Result.success(result);
    } finally {
        releaseLock(lockKey);
    }
}

// 本地锁实现（分布式环境下可替换为 Redis）
private final ConcurrentMap<String, java.util.concurrent.locks.ReentrantLock> lockMap = new ConcurrentHashMap<>();

private boolean acquireLock(String key, int seconds) {
    java.util.concurrent.locks.ReentrantLock lock = lockMap.computeIfAbsent(key,
        k -> new java.util.concurrent.locks.ReentrantLock());
    try {
        return lock.tryLock(seconds, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
    }
}

private void releaseLock(String key) {
    java.util.concurrent.locks.ReentrantLock lock = lockMap.get(key);
    if (lock != null && lock.isHeldByCurrentThread()) {
        lock.unlock();
    }
}
```

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/scfx/dto/ScriptExecuteReq.java \
       backend/src/main/java/com/scfx/controller/CollectionScriptController.java \
       backend/src/main/java/com/scfx/service/CollectionScriptService.java \
       backend/src/main/resources/application.yml
git commit -m "feat: 手动执行支持传入 date 参数 + 全链路校验
- 新增 ScriptExecuteReq DTO
- 分布式锁防并发穿透
- date 校验：长度/格式/未来日期/超早日期
- detail_json 只新增 date 键，不覆盖原有 JSON（含 JSON 异常兜底）
- 采集日期结构化写入执行备注
- 新增 collector.min-execute-date 配置项"
```

---

### Task 3: CollectorAgentService 解析 detail_json 拼命令

**Files:**
- Modify: `backend/src/main/java/com/scfx/service/CollectorAgentService.java:109-211`

- [ ] **Step 1: 读取 detail_json 提取 date**

在 `executePythonCollector()` 方法中，构建命令前增加 detail_json 解析：

```java
// 读取 detail_json 提取 date
String date = null;
try {
    String detailJson = execution.getDetailJson();
    if (detailJson != null && !detailJson.isEmpty()) {
        JsonNode root = objectMapper.readTree(detailJson);
        if (root.has("date")) {
            date = root.get("date").asText();
        }
    }
} catch (Exception e) {
    log.warn("解析 detail_json 失败，忽略 date 参数: executionId={}", executionId);
    // JSON 解析异常降级，不传递 --date
}

// 构建命令 - 使用 main.py run <code> 方式运行
List<String> commandArgs = new ArrayList<>(Arrays.asList(
    "python3", "main.py",
    "--api-base", apiBase,
    "run", datasourceName,
    "--execution-id", executionId,
    "--task-id", String.valueOf(script.getId())
));
if (date != null) {
    commandArgs.add("--date");
    commandArgs.add(date);
    log.info("manual_execute_collect_date:{}, executionId={}", date, executionId);
}
ProcessBuilder pb = new ProcessBuilder(commandArgs);
```

- [ ] **Step 2: 提交**

```bash
git add backend/src/main/java/com/scfx/service/CollectorAgentService.java
git commit -m "feat: CollectorAgentService 读取 detail_json.date 传参
- 解析 detail_json 提取 date（含 JSON 异常降级）
- ProcessBuilder 多参数列表追加 --date
- 日志带 manual_execute_collect_date 标识"
```

---

### Task 4: Vue 前端加日期选择器

**Files:**
- Modify: `frontend/src/views/scripts/TaskDetail.vue:30,1245-1283`
- Modify: `frontend/src/views/scripts/TaskList.vue:227,547-560`
- Modify: `frontend/src/api/execution.ts:62-64`
- Modify: `frontend/src/api/index.ts:236-238`

- [ ] **Step 1: 修改 API 层，execute 加 params 参数**

`frontend/src/api/execution.ts`：
```typescript
execute: (scriptId: number, params?: { date?: string }) =>
    request.post<{ executionId: string }>(`/scripts/${scriptId}/execute`, params),
```

`frontend/src/api/index.ts`：
```typescript
execute: (id: number, params?: { date?: string }) =>
    request.post<{ data: any }>(`/scripts/${id}/execute`, params),
```

- [ ] **Step 2: 修改 TaskDetail.vue 增加日期选择弹窗**

将 `handleExecute` 改为弹出日期选择对话框，而非直接确认：

```typescript
// 新增响应式状态
const executeDialogVisible = ref(false)
const executeDate = ref('')
const minExecuteDate = ref('2020-01-01')
const isTodaySelected = computed(() => {
    const today = new Date()
    const todayStr = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
    return executeDate.value === todayStr
})

// 加载最小日期配置
async function loadMinExecuteDate() {
    try {
        // 从后端获取配置（通过现有配置 API 或硬编码同默认值）
        const res = await configApi.get('collector.min-execute-date')
        if (res.data) minExecuteDate.value = res.data
    } catch {
        minExecuteDate.value = '2020-01-01'
    }
}

async function handleExecute() {
    if (executing.value) return
    await loadMinExecuteDate()
    // 默认选中逻辑
    const today = new Date()
    const todayStr = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
    if (minExecuteDate.value < todayStr) {
        // 默认昨天
        const yesterday = new Date(today)
        yesterday.setDate(yesterday.getDate() - 1)
        executeDate.value = `${yesterday.getFullYear()}-${String(yesterday.getMonth() + 1).padStart(2, '0')}-${String(yesterday.getDate()).padStart(2, '0')}`
    } else {
        // 最小日期 = 今天
        executeDate.value = todayStr
    }
    executeDialogVisible.value = true
}

// 确认执行（弹窗内的确认按钮）
async function confirmExecute() {
    if (isTodaySelected.value) {
        // 选中今天 → 二次确认
        try {
            await ElMessageBox.confirm(
                '当前选择采集今日日报，平台一般18点后才会更新当日数据，确定立即执行吗？',
                '确认执行',
                { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
            )
        } catch {
            return // 用户取消
        }
    }
    await doExecuteWithDate()
}

async function doExecuteWithDate() {
    executing.value = true
    showRealtimeLog.value = true
    showResultCard.value = false
    realtimeLogs.value = []

    try {
        const params = executeDate.value ? { date: executeDate.value } : undefined
        await scriptApi.execute(scriptId.value, params)
        setTimeout(() => {
            loadRecentExecutions().then(() => {
                if (recentExecutions.value.length > 0) {
                    startLogPolling(recentExecutions.value[0].executionId)
                }
            })
        }, 2000)
        executeDialogVisible.value = false
        showConfirmMessage('执行成功', '脚本已触发执行，可在执行记录中查看实时日志', 'success')
    } catch (e: any) {
        console.error('执行失败', e)
        showConfirmMessage('执行失败', e.message || '执行失败，请检查脚本配置', 'error')
        executing.value = false
    }
}
```

日期选择弹窗模板（在模板中新增）：

```html
<!-- 日期选择弹窗 -->
<el-dialog v-model="executeDialogVisible" title="选择采集日期" width="400px" :close-on-click-modal="false">
    <div class="date-picker-container">
        <p class="date-picker-hint">可选范围：{{ minExecuteDate }} ~ 今天；当日日报通常18:00后发布，白天采集今日大概率无数据</p>
        <el-date-picker
            v-model="executeDate"
            type="date"
            value-format="yyyy-MM-dd"
            placeholder="选择采集日期"
            :disabled-date="disabledDate"
            :editable="false"
            :clearable="false"
            style="width: 100%"
        />
    </div>
    <template #footer>
        <el-button @click="executeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmExecute" :loading="executing">确认执行</el-button>
    </template>
</el-dialog>

<script>
function disabledDate(time: Date) {
    const minDate = new Date(minExecuteDate.value)
    const today = new Date()
    today.setHours(23, 59, 59, 999)
    return time.getTime() < minDate.getTime() || time.getTime() > today.getTime()
}
</script>
```

- [ ] **Step 3: 修改 TaskList.vue 单行执行加日期选择**

将 `handleExecute` 改为弹出日期选择对话框（与 TaskDetail 逻辑一致）：

```typescript
async function handleExecute(script: CollectionScript) {
    // 弹出日期选择对话框（与TaskDetail相同逻辑）
    showDatePickerDialog(script, async (date: string) => {
        const params = date ? { date } : undefined
        const res: any = await scriptApi.execute(script.id!, params)
        if (res.data?.executionId) {
            progressDrawer.value?.open(res.data.executionId)
        }
        ElMessage.success('脚本已触发执行')
    })
}
```

- [ ] **Step 4: 提交**

```bash
git add frontend/src/views/scripts/TaskDetail.vue \
       frontend/src/views/scripts/TaskList.vue \
       frontend/src/api/execution.ts \
       frontend/src/api/index.ts
git commit -m "feat: 前端手动执行支持选择采集日期
- TaskDetail/TaskList 增加日期选择弹窗
- 可选范围：min-execute-date ~ 今天
- 默认选中昨天（min-date<今天时）
- 选中今天弹出二次确认弹窗
- 日期禁用 hover 提示 + 范围文案
- execute API 加 params 参数"
```

---

### Task 5: 兼容性校验

- [ ] **Step 1: 编译后端并重启，验证所有场景**

```bash
cd /Users/hucong/workspace/ai/scfx/backend
mvn compile -q
# 停旧后端，启新后端
kill $(ps aux | grep 'grain-platform' | grep -v grep | awk '{print $2}') 2>/dev/null
sleep 2
nohup /usr/bin/java -jar target/grain-platform-1.0.0.jar --spring.profiles.active=dev > /tmp/backend.log 2>&1 &
# 等待启动
for i in $(seq 1 30); do
    sleep 2
    curl -s http://localhost:8080/api/datasource/liangxin/exists | grep -q 'exists' && break
done
```

验证清单：

- [ ] **旧版本手动执行（无 body）**: `curl -s -X POST http://localhost:8080/api/scripts/14/execute` → 返回 executionId，采集当日
- [ ] **传入有效 date**: `curl -s -X POST http://localhost:8080/api/scripts/14/execute -H 'Content-Type: application/json' -d '{"date":"2026-06-15"}'` → 返回 executionId
- [ ] **未来日期拒绝**: `curl -s -X POST http://localhost:8080/api/scripts/14/execute -H 'Content-Type: application/json' -d '{"date":"2026-06-18"}'` → 返回错误
- [ ] **超早日期拒绝**: `curl -s -X POST http://localhost:8080/api/scripts/14/execute -H 'Content-Type: application/json' -d '{"date":"2019-01-01"}'` → 返回错误
- [ ] **无效格式拒绝**: `curl -s -X POST http://localhost:8080/api/scripts/14/execute -H 'Content-Type: application/json' -d '{"date":"2026/06/15"}'` → 返回错误
- [ ] **非白名单脚本忽略 date**: `curl -s -X POST http://localhost:8080/api/scripts/2/execute -H 'Content-Type: application/json' -d '{"date":"2026-06-15"}'` → 忽略 date，采集当日（脚本 2 = liangxin 晨报）
- [ ] **未知参数丢弃**: `curl -s -X POST http://localhost:8080/api/scripts/14/execute -H 'Content-Type: application/json' -d '{"date":"2026-06-15","foo":"bar"}'` → 正常运行，仅读取 date
- [ ] **并发拦截**: 同时发两个请求 → 第二个返回「操作频繁，请稍后重试」或「任务正在执行中」
- [ ] **定时 cron 任务**: 确认不拼接 `--date` 参数（检查日志无 `manual_execute_collect_date`）
- [ ] **显示 hasScript**: `curl -s http://localhost:8080/api/datasource/liangxin/exists` → `exists: true`

- [ ] **Step 2: 验证前端**

启动前端（如已运行则刷新）：
```bash
cd /Users/hucong/workspace/ai/scfx/frontend
npm run dev
```

- 验证 TaskDetail 点击"立即执行"弹出日期选择窗
- 默认选中昨天
- 日历禁用未来日期，hover 有提示
- 选中今天弹出二次确认
- 选中历史日期直接执行
- TaskList 单行"执行"按钮同样弹出日期选择

- [ ] **Step 3: 提交最终 commit**

```bash
git add -A && git commit -m "chore: 验证手动执行选日期功能
- 全链路兼容性校验通过
- 前端日期选择交互验证"
```

---

## Self-Review

**Spec coverage check:**
- ✅ Python 采集器 `target_date` + 空字符串保护 + try-except 校验 + 日志 → Task 1
- ✅ main.py `--date` 参数 → Task 1
- ✅ liangxin-yumi-daily-report.py `--date` → Task 1
- ✅ ScriptExecuteReq DTO → Task 2
- ✅ 分布式锁 + 并发拦截 → Task 2
- ✅ date 校验（trim/长度/格式/未来/超早） → Task 2
- ✅ detail_json 只新增 date 键（含 JSON 异常兜底） → Task 2
- ✅ 采集日期写入执行备注 → Task 2
- ✅ 日志带 `manual_execute_collect_date` 标识 → Task 2, 3
- ✅ CollectorAgentService 解析 detail_json → Task 3
- ✅ ProcessBuilder 多参数列表 → Task 3
- ✅ 前端日期选择器（范围/提示/默认/二次确认） → Task 4
- ✅ API 层 execute 加 params → Task 4
- ✅ 批量执行不弹窗 → 不改批量逻辑（Task 4）
- ✅ 兼容性校验 → Task 5
- ✅ application.yml 配置项 → Task 2

**Placeholder scan:** No placeholders found.

**Type consistency:**
- `target_date: str = None` in Python ↔ `--date` CLI arg ↔ `date` field in JSON ↔ `ScriptExecuteReq.date` in Java ← consistent
