# 手动执行支持选日期采集设计

> **背景：** 粮信网玉米日报采集器目前只支持采集当天日期。用户有时需要补采历史某天的日报（如周末漏采、数据回溯）。需要在网页端手动执行时支持选择目标日期。
>
> **范围约束：** 本次仅针对 `liangxin-daily`（粮信网玉米日报）采集脚本开放指定日期参数，其他采集脚本暂不支持。
>
> **脚本白名单：** `liangxin`、`liangxin-daily`，精确匹配 `t_collection_script.source` 字段值（大小写敏感）。

---

## 数据流

```
前端选日期（日历组件，yyyy-MM-dd，可回溯可选范围）
  → POST /scripts/{id}/execute { date: "2026-06-15" }
    → Java 层 ScriptExecuteReq DTO 接收，框架自动丢弃未知字段
    → Java 层 trim + DateTimeFormatter 强校验 yyyy-MM-dd
    → Java 层限制 date 字符串长度 ≤ 10
    → Java 层拦截未来日期；今天合法放行
    → Java 层拦截超早日期（早于 collector.min-execute-date）
    → Java 层基于 scriptId 的分布式锁（30s），双重防并发穿透
    → Java 层全局拦截该脚本运行中任务重复执行
    → 存到 t_task_execution.detail_json（只新增 date 键，不覆盖原有 JSON）
      → CollectorAgentService 读 detail_json.date → 使用 ProcessBuilder 列表方式传参 --date
        → main.py run liangxin-daily --date 2026-06-15
          → Python 层 try-except 兜底 yyyy-MM-dd 格式解析校验
            → LiangxinCollector 用 target_date 去粮信网搜指定日期
```

**批量执行：** 不弹日期选择窗，不接收 `date` 参数，请求携带则直接丢弃，固定采当日。

---

## 日期可选规则

```
可选范围：collector.min-execute-date ≤ 选中日期 ≤ 今天
```

| 日期 | 可选 | 说明 |
|------|------|------|
| 昨天及所有历史日期 | ✅ 正常可选 | 日常补采首选 |
| 今天 | ✅ 可选 | 18:00 后日报发布可正常采集；白天采集大概率无数据但放行 |
| 未来日期 | ❌ 禁用 | 前后端统一拦截 |

## 默认选中逻辑

```
if 最小可采集日期 < 今天:
    默认选中昨天   # 昨天一定在可选范围内，不会被误禁用
else:  # 最小可采集日期 == 今天
    默认选中今天   # 昨天早于下限被禁用，自动降级
```

## 二次确认规则

| 选中日期 | 弹窗 |
|---------|------|
| 今天 | 弹出二次确认：「当前选择采集今日日报，平台一般18点后才会更新当日数据，确定立即执行吗？」 |
| 昨天及更早 | **不弹窗**，直接确认执行 |

---

## 改动范围

### 1. Python 采集器层

**LiangxinCollector**（`collectorsdk/collectors/liangxin.py`）
- `__init__` 加 `target_date: str = None` 参数
- `collect()` 改为：

```python
if self.target_date and self.target_date.strip():
    run_date = self.target_date.strip()
else:
    run_date = datetime.now().strftime("%Y-%m-%d")
# 兜底强解析校验（try-except 异常捕获，优雅退出）
try:
    datetime.strptime(run_date, "%Y-%m-%d")
except ValueError:
    logger.error(f"manual_execute_collect_date 目标日期格式非法: {run_date}，终止采集")
    return 1  # 参数异常返回非0，区分业务正常（含0条数据）和系统异常
```

- `collect()` 首行日志输出目标日期 + executionId，带统一关键字 `manual_execute_collect_date`
- 校验失败通过 `logger.error` 输出明确日志后 `return 1` 优雅退出，不抛出未捕获异常
- 返回值规范：
  - `0` = 业务正常结束（不论采到多少条，含 0 条）
  - `1` = 参数异常/系统异常终止

**main.py**（`python-collector-sdk/main.py`）
- `run` 子命令加 `--date` 可选参数
- 解析后传给 Collector 的 `target_date`

**liangxin-yumi-daily-report.py**（独立脚本）
- 同样加 `--date` 参数

### 2. Java 后端层

无需 DB 迁移，复用 `t_task_execution.detail_json`（已有 TEXT 字段）。

**CollectionScriptController.java**
- `POST /scripts/{id}/execute` 接受 `@RequestBody(required=false) ScriptExecuteReq req`
- 非脚本白名单（`liangxin`、`liangxin-daily`，精确匹配 `source`，大小写敏感）的 `date` 参数直接忽略

**ScriptExecuteReq（新增 DTO）**

```java
@Data
public class ScriptExecuteReq {
    private String date;
}
```

Spring MVC 自动绑定，框架层面只会接收 `date` 字段，天然丢弃其他未知参数。

**CollectionScriptService.java `executeScriptNow()`**
- 前置校验（按顺序）：
  1. **分布式锁拦截**：基于 `scriptId` 获取锁（例如 Redis 锁或本地 `ReentrantLock`），锁定时间 30 秒。获取失败返回「操作频繁，请稍后重试」，防止同一毫秒多次穿透
  2. **全局并发拦截**：获取锁后再次校验该 `scriptId` 是否有 `status=running` 的执行记录，有则返回「该任务正在执行中，请等待完成」。不限用户，同脚本全局仅允许一个执行中
  3. **date 判空**：无 date 参数则当日采集，跳过后两步校验
  4. **长度校验**：`date.length() > 10` 则拒绝（`yyyy-MM-dd` 固定 10 位）
  5. **trim 去空格**：`date.trim()`
  6. **格式校验**：`DateTimeFormatter.ofPattern("yyyy-MM-dd")` 强校验，非法直接返回业务异常，不生成执行记录
  7. **上限校验**：禁止未来日期，`LocalDate.parse(date).isAfter(LocalDate.now())`，拒绝执行。今天合法放行。注意：Java 的 `LocalDate` 只比对年月日，与前端的 `today.setHours(23,59,59,999)` 规则自然对齐
  8. **下限校验**：检查是否早于可配置的最早采集日期（配置项 `collector.min-execute-date`，默认 2020-01-01），超早则拒绝执行
- 校验通过后，写入 `detail_json`：
  - **只新增 `date` 键，不覆盖原有 JSON 内容**
  - 使用 Jackson `ObjectMapper` 读取原有 `detail_json`：**必须捕获 `JsonProcessingException`**，解析失败（NULL/空/非法 JSON）时初始化为空 `JsonNode`，追加 `date` 字段后写回。不抛出全局异常
  - 原有 `detail_json` 为 NULL、为空、为非法 JSON 时，创建新 JSON `{"date":"2026-06-15"}`
- 采集日期结构化写入执行备注字段（`error_message` 或其他备注字段），格式 `采集日期：2026-06-15`，方便后台表格检索筛选补采任务
- 执行前打印日志：`manual_execute_collect_date:{date}, 任务ID:{id}`
- 命令构建使用 `ProcessBuilder` 多参数列表方式，不拼接字符串，从源头杜绝 Shell 注入

**CollectorAgentService.java `executePythonCollector()`**
- 读取 `detail_json`：
  - 先判断非 NULL、是合法 JSON 再读取
  - JSON 解析异常则忽略参数，降级当日采集
  - 读取 `date` 字段，若存在则追加到 ProcessBuilder 命令参数列表
- 使用 `ProcessBuilder` 多参数列表方式：
  ```java
  List<String> command = new ArrayList<>(Arrays.asList(
      "python3", "main.py", "--api-base", apiBase,
      "run", datasourceName,
      "--execution-id", executionId,
      "--task-id", String.valueOf(script.getId())
  ));
  if (date != null) {
      command.add("--date");
      command.add(date);  // 列表方式，不拼接字符串，原生防注入
  }
  pb.command(command);
  ```
- 执行前打印日志：`manual_execute_collect_date:{date}, executionId={executionId}`

### 3. Vue 前端层

**TaskDetail.vue**（任务详情页）
- "立即执行"按钮改为弹对话框：
  - 日期选择器（`el-date-picker`，`type="date"`，`value-format="yyyy-MM-dd"`）
  - 禁用手动输入，只能日历选择
  - 可选范围：`collector.min-execute-date` ~ 今天（含今天）
  - 最小日期接口结果缓存（组件级别，避免每次打开弹窗重复请求）
  - 禁用规则：
    ```javascript
    disabledDate(time) {
      const minDate = new Date(minExecuteDate)
      const today = new Date()
      today.setHours(23, 59, 59, 999)  // 对齐后端 LocalDate 年月日比对
      return time.getTime() < minDate.getTime() || time.getTime() > today.getTime()
    }
    ```
  - 日期选择旁显示提示文案：`可选范围：{最早日期} ~ 今天；当日日报通常18:00后发布，白天采集今日大概率无数据`
  - 今天日历单元格 hover 提示：`当日日报一般18:00后发布，白天采集大概率无公开数据`
  - 默认选中逻辑：
    - 最小可采集日期 < 今天：默认选中昨天
    - 最小可采集日期 = 今天：默认选中今天
  - **二次确认规则**：
    - 选中今天 → 弹出二次确认：`当前选择采集今日日报，平台一般18点后才会更新当日数据，确定立即执行吗？`
    - 选中昨天及更早 → 直接确认执行，**不弹二次确认窗**
  - 取消/确认按钮
- 调用 `scriptApi.execute(id, { date })`

**TaskList.vue**（任务列表页）
- 单行"执行"按钮同样弹出日期选择器（同上规则）
- 批量执行按钮不弹日期选择窗，不传 `date`

**scriptApi / executionApi**
- `execute(id)` → `execute(id, params?)`，POST body 带上 `params`

### 4. 执行记录可观测性

| 层级 | 输出位置 | 日志标识 | 内容 |
|------|---------|---------|------|
| Java | 启动执行前 | `manual_execute_collect_date` | `manual_execute_collect_date:{date}, 任务ID:{id}` |
| Python | 脚本启动首行 | `manual_execute_collect_date` | `manual_execute_collect_date 粮信玉米日报采集目标日期：{target_date}, executionId={executionId}` |
| 前端 | 执行详情页 | - | 从 `detail_json` 解析日期，展示「自定义日期：2026-06-15」或「当日默认采集」 |
| 前端 | 执行详情页 | - | 0 条结果时备注「指定日期 2026-06-15 暂无公开数据」；选中今天时提示「今日日报尚未发布，暂无公开数据」 |
| 结果 | 采到 0 条 | - | 提示「指定日期暂无公开数据」，将目标日期写入执行备注，任务状态标记为执行成功 |

**日志检索：** 统一关键字 `manual_execute_collect_date`，配合 `executionId` 可在 ELK 中精准筛选单条采集任务日志。

---

## 边界情况

| 场景 | 行为 |
|------|------|
| 不传 date / detail_json 为 NULL | 采集器默认采今天，向下兼容 |
| date 传空白字符串 | Java 层 trim 后判空，跳过后两步校验，当日采集 |
| date 超长字符串（>10 位） | 长度校验拦截，拒绝执行 |
| 传无效日期格式（2026/06/15、2026-13-01） | Java 层 `DateTimeFormatter` 校验拦截，返回业务异常，不生成执行记录 |
| 传未来日期 | Java 层二次拦截，直接拒绝执行 |
| 传今天（18 点前） | 允许执行，返回 0 条数据，提示「今日日报尚未发布，暂无公开数据」，任务成功 |
| 传今天（18 点后） | 正常采集当日最新日报，可拿到业务数据 |
| 传早于最早采集日期 | Java 层拦截，拒绝执行 |
| 传早于数据最早归档日期（但晚于最早采集日期） | Python 采集时搜不到 → 采到 0 篇，提示「指定日期暂无公开数据」 |
| 选中历史日期（昨天及更早） | 直接确认执行，**无需二次弹窗** |
| 选中今天 | 弹出二次确认，用户确认后执行 |
| 短时间内多次点击执行同一脚本 | 分布式锁拦截（30s），提示「操作频繁，请稍后重试」，防止并发穿透 |
| 脚本已在执行中再次点击执行 | 分布式锁内二次校验运行中状态，返回「该任务正在执行中，请等待完成」 |
| 同一脚本多用户同时执行 | 全局拦截，同脚本仅允许一个执行中 |
| 非白名单脚本误传 date | Java 层忽略 `date`，`detail_json` 不存，当日采集 |
| 批量执行携带 date | 直接丢弃，不存 `detail_json`，当日采集 |
| detail_json 原有内容 + 新增 date | 只新增 `date` 键，不覆盖原有 JSON |
| detail_json 解析异常（NULL/非法 JSON） | `JsonProcessingException` 捕获，初始化为空 JSON 对象，正常写入新 date |
| ProcessBuilder 传参含特殊字符 | 多参数列表方式传参，不拼接字符串，无注入风险 |
| 定时 cron 任务 | 不受影响，`detail_json` 为空，不拼接 `--date` |
| 旧版本手动执行（无 body） | 兼容，`date` 为 null，正常当日采集 |
| 最小采集日期 = 今天 | 昨天禁用，默认选中今天，可正常采集 |
| 最小采集日期 < 今天 | 昨天永久可选，默认选中昨天 |
| 前端拉取最小日期接口失败 | 兜底默认值 2020-01-01 |
| 前后端日期比对规则对齐 | 前端 `today.setHours(23,59,59,999)` 与后端 `LocalDate` 均只比对年月日，无时区/时分秒差异 |
| 请求体包含未知参数（如 other_param） | DTO 框架自动丢弃，只绑定 `date` 字段 |
| Python 日期格式校验异常 | try-except 捕获，`logger.error` 后 `return 1` 优雅退出 |
| Python 采集正常（含 0 条） | `return 0`，区分业务正常和系统异常 |

---

## 不做的事

- 不新增数据库字段（复用 `detail_json`）
- 不修改 cron 定时调度逻辑
- 不支持日期范围（单次只采一天，批量补采用 shell 循环）
- 其他采集脚本暂不支持 `--date` 参数
- 不对今天做强制拦截（通过提示+二次确认劝阻白天误操作，把选择权交给用户）

---

## 配置项

新增配置 `application.yml`（用于下限拦截）：

```yaml
collector:
  min-execute-date: 2020-01-01
```

前端可通过 API 获取此配置值，用于日期选择器的可选范围下限。

---

## 实施计划

### Task 1: Python 采集器 + main.py 加 --date

- [ ] 修改 `LiangxinCollector.__init__` 加 `target_date`
- [ ] 修改 `LiangxinCollector.collect()` 使用 `target_date`（含空字符串保护）
- [ ] Python 层 `try-except` 兜底 `yyyy-MM-dd` 格式解析校验
- [ ] 首行日志输出目标日期 + executionId（带 `manual_execute_collect_date` 标识）
- [ ] 校验失败优雅退出（`return 1` + 日志）
- [ ] 修改 `main.py` run 子命令加 `--date`
- [ ] 修改 `liangxin-yumi-daily-report.py` 加 `--date`

### Task 2: Java 后端透传 date + 校验

- [ ] 新增 `ScriptExecuteReq` DTO
- [ ] `CollectionScriptController.executeScript()` 接受 DTO
- [ ] 基于 scriptId 的分布式锁（30s）
- [ ] 全局运行中并发拦截检查（锁内二次校验）
- [ ] Date 长度校验（≤10）
- [ ] Date 格式校验（trim → yyyy-MM-dd）
- [ ] 未来日期拦截（今天放行）
- [ ] 超早日期拦截（配置项 `collector.min-execute-date`）
- [ ] 非白名单脚本忽略 date
- [ ] detail_json 只新增 date 键，不覆盖原有 JSON（捕获 `JsonProcessingException`，解析失败视为空 JSON）
- [ ] 采集日期结构化写入执行备注
- [ ] 执行前日志埋点（带 `manual_execute_collect_date` 标识）
- [ ] 使用 ProcessBuilder 多参数列表方式传参

### Task 3: CollectorAgentService 解析 detail_json 拼命令

- [ ] 读取 `detail_json` 提取 date（含空值/非法 JSON 异常兜底）
- [ ] JSON 解析异常降级
- [ ] ProcessBuilder 多参数列表追加 `--date`

### Task 4: Vue 前端加日期选择器

- [ ] 修改 `TaskDetail.vue` 执行弹窗加日期选择
- [ ] 修改 `TaskList.vue` 单行执行加日期选择
- [ ] 批量执行不弹窗
- [ ] 日期范围限制（下限 min-execute-date，上限今天）
- [ ] 最小日期接口结果缓存
- [ ] 日期范围提示文案（含 18:00 发布提示）
- [ ] 今天日历单元格 hover 提示
- [ ] 选中今天 → 二次确认弹窗；历史日期 → 直接执行
- [ ] 默认选中昨天/今天（min-date=今天时选中今天）
- [ ] 修改 `api/index.ts` / `execution.ts` 的 `execute` 方法
- [ ] 执行详情页展示采集日期（自定义/当日默认 + 0 条备注，含今日无数据提示）

### Task 5: 兼容性校验

- [ ] 定时 cron 任务不受 `--date` 影响
- [ ] 旧版本手动执行（无 body）不受影响
- [ ] 非白名单脚本不受 `date` 参数影响
- [ ] 批量执行不受 `date` 参数影响
- [ ] 请求体未知参数不影响执行
