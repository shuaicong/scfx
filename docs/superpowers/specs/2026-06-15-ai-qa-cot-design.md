# AI 知识问答 — 深度思考（Chain of Thought）功能规格

> **版本**: v1.0
> **状态**: 设计完成待实现
> **关联**: [AI 问答会话管理](/Users/hucong/workspace/ai/scfx/docs/superpowers/specs/2026-06-11-ai-qa-history-session-management.md)

---

## 1. 概述

在 AI 知识问答中增加"深度思考"模式，用户开启后 AI 逐步展示推理过程（Chain of Thought），类似 ChatGPT o1 / DeepSeek 的思考链展示。后端通过 Prompt 指令让 LLM 以 `<reasoning>/<answer>` 标签输出推理和答案，流式解析后通过独立 SSE 事件分发，前端渲染为可折叠的推理面板。

### 核心目标

- 用户可开启/关闭深度思考
- AI 推理过程逐步展示，不等待完整输出
- 推理过程与最终答案独立渲染，互不干扰
- 历史消息回放保留推理内容

---

## 2. SSE 协议扩展

### 2.1 新增 `reasoning` 事件

在现有 6 事件协议基础上新增 `reasoning`：

```
event: reasoning
data: {"type":"reasoning","content":"首先分析玉米市场供需...","seq":0}

event: reasoning
data: {"type":"reasoning","content":"东北地区播种面积变化...","seq":1}

event: content
data: {"type":"content","content":"综合来看玉米价格...","seq":2}
```

### 2.2 事件流顺序

```
thought → source → reasoning → content → done
                 ↘         ↙
                reasoning 与 content 可并行
```

并行输出依赖 `seq` 序号做全局保序，前端双缓冲区独立消费，互不干扰。

- `thought` → 过程提示（现有，不变）
- `source` → 知识来源（现有，不变）
- `reasoning` → 推理过程（新增）
- `content` → 最终答案（现有，不变）
- `done` → 结束（现有，不变）
- `error` / `abort` → 终止（现有，不变）
- `heartbeat` → 心跳保活（全周期允许）

### 2.3 状态机变更（sse_manager.py）

新增 `REASONING` 状态，修正完整状态迁移表：

| 当前状态 | 允许下一状态 |
|---|---|
| INIT | THOUGHT, REASONING, CONTENT, ERROR, ABORT, HEARTBEAT |
| THOUGHT | SOURCE, REASONING, ERROR, ABORT, HEARTBEAT |
| SOURCE | REASONING, CONTENT, ERROR, ABORT, HEARTBEAT |
| REASONING **(新增)** | REASONING, CONTENT, DONE, ERROR, ABORT, HEARTBEAT |
| CONTENT | CONTENT, DONE, ERROR, ABORT, HEARTBEAT |
| DONE | (终止态) |
| ERROR | (全局终止态) |
| ABORT | (全局终止态) |

关键规则：
- ERROR / ABORT 为全局终止态，任意状态可直接进入
- HEARTBEAT 全周期允许插入，不切换主状态
- DONE 仅一次，进入后流彻底结束
- `reasoning` 与 `content` 兼容并发输出（REASONING 态可切到 CONTENT，CONTENT 态不再切回 REASONING）
- CONTENT 不再切回 REASONING 为业务约束，前后端同时遵循：答案开始渲染后不再新增推理内容

---

## 3. 后端 Prompt 设计

### 3.1 注入位置

```
A(角色) → B(历史) → D(参考资料) → C(分类指令) + [CoT 指令] → Q(用户问题)
```

`deep_thinking=True` 时，在模块 C（`messages[-2]`）尾部追加 CoT 指令。

### 3.2 指令文本

在 `prompt.yaml` 的 `templates` 节点新增：

```yaml
  deep_thinking_instruction: |

    【深度思考模式强制格式要求】
    你必须严格按照以下格式输出内容：
    1. 先输出推理分析，所有推理内容完整放在 <reasoning> 和 </reasoning> 标签内部；
    2. 推理结束后再输出正式答案，所有答案内容完整放在 <answer> 和 </answer> 标签内部；
    3. <reasoning> 区块必须出现在 <answer> 区块之前，标签成对、不可缺失、不可嵌套；
    4. 若问题简单无需复杂推理，<reasoning> 内部可以为空，但标签仍必须保留。

    标准示例：
    <reasoning>
    第一步：拆解用户提问
    第二步：结合参考资料分析
    第三步：综合整理结论
    </reasoning>
    <answer>
    这里是最终正式回答
    </answer>
```

### 3.3 代码注入逻辑（build_messages 变更）

```python
def build_messages(
    question: str,
    history: list[dict],
    sources: list[dict],
    qtype: str = "general",
    request_id: str = "",
    session_id: str = "",
    deep_thinking: bool = False,     # ★ 新增
) -> list[dict]:
    ...
    # 模块 C 注入后追加 CoT 指令
    cot_instr = templates.get("deep_thinking_instruction", "")
    if deep_thinking and cot_instr:
        messages[-2]["content"] += cot_instr
    ...
```

### 3.4 开关默认值

`deep_thinking: bool = False`，默认关闭，完全向后兼容。

### 3.5 Token 风控

CoT 指令文本、推理内容、答案内容、标签文本统一参与 Token 统计，受 `TOKEN_WARN_LIMIT` / `TOKEN_HARD_LIMIT` 管控，超限走现有历史/资料降级逻辑。

标签文本（`<reasoning></reasoning>` ``）属于 Prompt 输出格式约束，计入 Token 统计；但后端解析后不入库、不向前端推送，仅做格式识别。

---

## 4. 流式标签解析器

### 4.1 三状态解析机

在 `chat.py` 中实现轻量标签解析器，解析 LLM 流式 chunk。解析层位于 `generate_answer_stream` 之后、`SSEResponseGenerator` 之前。

**生命周期规则**：解析器状态（SEARCHING/IN_REASONING/IN_ANSWER、`is_degraded`、缓冲区内容）和 `reasoning_text` 一致，每条新问答请求仅初始化一次，流式过程中绝对不重置，会话间完全隔离。

| 状态 | 含义 | 动作 |
|---|---|---|
| `SEARCHING` | 等待 `<reasoning>` | 缓冲，匹配到标签切 IN_REASONING |
| `IN_REASONING` | 在 `<reasoning>` 内 | 累积内容 → reasoning event |
| `IN_ANSWER` | 在 `<answer>` 内 | 走原有 content 分段逻辑 |

### 4.2 技术细节

**缓冲区**：滑动窗口，最大 60 字符（覆盖最长标签 `<reasoning>` + 前后换行/空格），仅用于标签匹配，不存储完整业务内容。完整推理文本由独立变量 `reasoning_text` 累加，二者隔离，避免缓冲区溢出污染业务数据。

**空白符兼容**：匹配前将缓冲区内容的**副本**统一转为小写，再剔除标签周边多余空白和换行，然后做标签匹配。

**强制规则**：小写转换仅用于标签匹配，不修改原始 chunk 内容。写入 `reasoning_text` 和推送到前端的原始文本保持原有大小写不变。

**标签不追加到内容**：匹配到起始/结束标签时只切换状态，不将标签字符拼入 reasoning_text 或 content。

**超时降级**：SEARCHING 状态下连续 20 个 chunk 仍未匹配到 `<reasoning>` → 触发降级。解析器持有全局标记 `is_degraded = False`，降级后置 `is_degraded = True`。一旦降级，永久不再做标签匹配，所有剩余 chunk 全部走 content 事件直到流结束。`is_degraded` 同一条问答生命周期内永久生效，新问答请求随解析器一起重置，不跨会话残留。

**异常日志**：标签匹配失败、触发超时降级、标签顺序颠倒等异常场景需输出日志，便于线上问题排查。日志关键词：`cot_tag_timeout`（超时降级）、`cot_tag_wrong_order`（标签颠倒）、`cot_tag_incomplete`（标签残缺）。

**残缺标签兜底**：
- 仅有开始标签无结束标签 → 流结束时强制切为 IN_ANSWER
- 标签顺序颠倒（先出现 `<answer>`）→ 直接降级为 content

**分片粒度**：按 LLM 原始 chunk 解析。

### 4.3 reasoning_text 累积

```python
reasoning_text = ""
# IN_REASONING 状态：
reasoning_text += pure_content_chunk  # 不含标签文本
```

**强制规则**：
- 标签字符不拼入 reasoning_text，仅做状态切换
- 仅纯正文片段执行累加
- 标签文本计入 Token 统计（Prompt 输出格式约束），但后端解析后不入库、不向前端推送
- 每条问答独立初始化 `reasoning_text = ""`，流式过程中绝对不重置

---

## 5. 后端 API 变更

### 5.1 ChatV2Request 新增

```python
class ChatV2Request(BaseModel):
    ...
    deep_thinking: bool = Field(default=False)
```

### 5.2 标签解析层在 chat.py sse_gen() 中的位置

```
LLM chunk → 标签解析器 → 分流 reasoning/content → SSE 推送
```

解析层位于 `generate_answer_stream` 之后、`SSEResponseGenerator` 之前，独立内聚。

### 5.3 异步写入器扩展

done / ERROR / ABORT 事件触发时，将 `reasoning_text` 一同入队：

```python
# 入库前空值处理
reasoning_text_clean = reasoning_text.strip()
data = {
    ...
    "content": answer_text,
    "reasoning_content": reasoning_text_clean if reasoning_text_clean else None,
}
writer.enqueue(data)
```

**数据完整性格守则**：
- 每条新问答请求仅初始化一次 `reasoning_text = ""`
- 流式过程中绝对不重置 `reasoning_text`，确保全部推理内容完整收集
- done / ERROR / ABORT 三种终态分支均执行入队逻辑，防止断流丢数据

**异常分支强制入库**：ERROR / ABORT 也必须执行入队逻辑，防止断流丢数据。

### 5.4 MySQL 表结构变更

Flyway V6 脚本：

```sql
ALTER TABLE t_chat_message
ADD COLUMN reasoning_content LONGTEXT DEFAULT NULL
COMMENT '深度思考推理过程（CoT）';
```

字段位置建议放在 `content` 列附近。要求数据表/字段字符集为 `utf8mb4`，确保特殊符号、换行、中文正常存储，避免乱码。

### 5.5 存储分层

| 数据 | 存储 | 用途 |
|---|---|---|
| 实时推理内容 | 前端内存（SSE 推送） | 实时展示 |
| 完整推理内容 | MySQL t_chat_message | 历史回放 |
| 对话历史 | Redis（现有，不变） | 热路径读写 |

Redis HistoryManager 不存储 reasoning_content，保持热路径轻量化。

- Redis 仅存储会话基础信息、对话基础文本（热读写）
- 超长推理文本属于冷数据，统一由 MySQL 承载，降低 Redis 内存与网络开销
- 实时流式推理内容仅保存在前端运行时内存，不落地中间缓存

---

## 6. 前端 UI 设计

### 6.1 ReasoningPanel 组件

新增 `frontend/src/views/ai-chat/components/ReasoningPanel.vue`

**Props：**
```typescript
interface Props {
  reasoning: string          // 完整推理文本
  collapsed?: boolean        // 默认展开 (false)
  showToggle?: boolean       // 是否展示折叠按钮 (true)
}
```

**交互：**
- 点击标题栏切换展开/折叠
- 标题左侧：`💭 深度思考` 图标+文字
- 标题右侧：`▼ / ▶` 折叠箭头
- 流式传输期间默认展开，历史消息默认折叠

**样式：**
- 面板背景：`rgba(245, 200, 122, 0.05)`
- 边框：`rgba(245, 200, 122, 0.15)`
- 左侧渐变竖线：2px，`linear-gradient(180deg, #f5c87a, #d4a574)`
- 标题文字：#f5c87a 金色
- 内容文字：#8b949e 浅灰
- 字号：13px（推理）/ 14px（正文）
- 内容区 max-height: 400px，超长滚动

**折叠动画：**
```css
.reasoning-body {
  transition: all 0.3s ease;
  max-height: 400px;
  opacity: 1;
  overflow: hidden;
}
.reasoning-body.collapsed {
  max-height: 0;
  opacity: 0;
}
```
组件内用 `:class="{ collapsed: collapsed }"` 替代 `v-show` 控制显隐，确保 CSS transition 生效。容器默认 `overflow: hidden`，配合 `max-height` 实现平滑过渡，禁止使用固定高度限制内容。

**空内容防护规则**：组件内部不做空值拦截，仅负责样式、折叠、内容展示。外层父组件统一做内容判空：
```html
<!-- AiChat.vue -->
<ReasoningPanel
  v-if="deepThinkingEnabled && reasoningContent.trim()"
  :reasoning="reasoningContent"
  :collapsed="false"
/>
```
移除组件内部 `reasoning.trim()` 相关 v-if，避免双层判空导致仅有标题栏、无内容的空面板。

### 6.2 AiChat.vue 变更

**新状态：**
```typescript
const deepThinkingEnabled = ref(false)
const reasoningContent = ref('')
```

**深度思考按钮：**
```html
<button class="deep-thinking-btn"
  :class="{ active: deepThinkingEnabled }"
  :disabled="isLoading"           <!-- 流式期间禁用 -->
  @click="deepThinkingEnabled = !deepThinkingEnabled">
  <span class="btn-icon">💭</span>
  <span class="btn-text">深度思考</span>
</button>
```

**SSE reasoning 事件处理（逐分片清洗首尾 + 补换行分段）：**
```typescript
case 'reasoning':
  const rawChunk = data.content || ''
  // 仅清理当前分片首尾空白，分片之间补换行分隔段落，避免段落粘连
  const chunk = rawChunk.trimStart().trimEnd()
  if (chunk) {
    reasoningContent.value += chunk + '\n'
  }
  break
```

**全局首尾空白清洗**（在 done 事件归档前，最终整体 trim，剔除末尾多余的补位换行）：
```typescript
const trimmed = reasoningContent.value.trim()
reasoningContent.value = trimmed  // 归档前全量清洗
```

**新提问时重置：**
```typescript
// askQuestion 中
reasoningContent.value = ''
currentAnswer.value = ''
sources.value = []
thoughts.value = []
```

**模板渲染位置：**
```html
<!-- sources 之后、MessageContent 之前 -->
<ReasoningPanel
  v-if="deepThinkingEnabled && reasoningContent"
  :reasoning="reasoningContent"
  :collapsed="false"
/>
```

**DisplayMessage 接口扩展：**
```typescript
interface DisplayMessage {
  role: 'user' | 'assistant'
  content: string
  id: string
  sources?: Source[]
  time?: string
  reasoning?: string   // ★ 新增
}
```

**done 归档：**
```typescript
displayMessages.value.push({
  role: 'assistant',
  content: currentAnswer.value,
  id: `a-${now}-1`,
  sources: sources.value,
  reasoning: reasoningContent.value.trim(),   // ★ 同步归档，归档前全量 trim
})
```

**历史消息回放：**
```html
<ReasoningPanel
  v-if="msg.reasoning"
  :reasoning="msg.reasoning"
  :collapsed="true"
/>
```

**历史 API 映射：**
```typescript
displayMessages.value = msgs.map((m: any) => ({
  ...
  reasoning: m.reasoning_content || '',
}))
```

---

## 7. 边界场景处理

| 场景 | 处理方式 |
|---|---|
| 深度思考关闭 | LLM 不加 CoT 指令，无 reasoning 事件，6 事件协议不变 |
| 模型不遵守标签格式 | 20 chunk 超时 + `is_degraded=True` → 永久降级为 content |
| 标签残缺（无结束标签） | 流结束时强制切为 IN_ANSWER |
| 标签顺序颠倒 | 直接降级为 content |
| 推理内容为空 | reasoning 面板不渲染（v-if 拦截） |
| SSE 断连 | 已累积的 reasoning 内容正常渲染 |
| 超长推理文本 | max-height: 400px + 纵向滚动 |
| 仅开启深度思考但无推理 | 按钮激活，面板不渲染（外层 `trim()` 判空）；`DisplayMessage.reasoning` 仍赋值空字符串，保证字段结构统一 |
| ERROR/ABORT | reasoning_content 仍入库 |
| 切换会话/页面刷新 | 历史 API 从 MySQL 读取 reasoning_content，`|| ''` 桥接 NULL → 空串，链路完整 |
| 会话未结束重复发起提问 | `isLoading` 拦截新请求，流式期间禁止重复发消息，防止多条流并行串数据 |
| reasoning 流提前结束，content 继续 | 推理面板保持最后内容不再更新，答案正常继续渲染 |
| content 流提前结束，reasoning 继续 | 答案区域定格，推理面板持续更新至流结束 |

---

## 8. 实施计划

### 任务分解

**回归用例清单（联调测试必须覆盖）：**
1. 深度思考开启：标签正常输出
2. 深度思考开启：标签缺失（无结束标签）
3. 深度思考开启：标签顺序颠倒（先出现 `<answer>`）
4. 深度思考开启：超时降级（模型不遵守格式）
5. SSE 断连/异常终止
6. 超长推理文本（触发 max-height 滚动）
7. 空推理内容（标签保留但内部为空）
8. 开关切换（开→关→开）
9. 历史消息回放（reasoning 面板正确渲染）
10. 重复点击发送拦截
11. 并行流场景：reasoning / content 同时流式输出、单方提前终止，验证互不影响

1. **后端 Prompt + build_messages**
   - `prompt.yaml`: 新增 `deep_thinking_instruction`
   - `llm.py`: `build_messages()` 新增 `deep_thinking` 参数、CoT 指令注入、空值兜底

2. **后端 SSE 状态机**
   - `sse_manager.py`: SSEStateMachine 新增 `REASONING` 状态、补全状态迁移表

3. **后端标签解析器**
   - `chat.py`: 实现三状态解析器（缓冲区/空白清洗/标签匹配/超时降级）
   - `chat.py sse_gen()`: 对接解析器分流 reasoning/content 事件
   - `chat.py`: reasoning_text 累积、ERROR/ABORT 分支入库

4. **后端 API + 持久化**
   - ChatV2Request: 新增 `deep_thinking` 字段
   - Flyway V6 脚本: t_chat_message 加 reasoning_content 列
   - 异步写入器: reasoning_content 入队逻辑 + 空值处理

5. **前端 ReasoningPanel 组件**
   - 新建组件，Props/样式/折叠/空内容防护

6. **前端 AiChat.vue 集成**
   - 深度思考按钮启用、SSE reasoning 事件、归档、历史回放

7. **联调测试**
   - 开关开启/关闭、正常标签、标签异常、超时降级、断连场景
   - 全量回归: SSE 状态机、前端、持久化、历史回放

---

## 9. 变更文件清单

| 文件 | 操作 | 说明 |
|---|---|---|
| `ai-qa-service/app/config/prompt.yaml` | 修改 | 新增 `deep_thinking_instruction` |
| `ai-qa-service/app/services/llm.py` | 修改 | `build_messages()` 新增 `deep_thinking` 参数 |
| `ai-qa-service/app/services/sse_manager.py` | 修改 | 状态机新增 REASONING 状态 |
| `ai-qa-service/app/api/chat.py` | 修改 | 标签解析器 + 事件分流 + reasoning_text 累积 |
| `ai-qa-service/app/db/flyway/...V6__add_reasoning_content.sql` | 创建 | 新增 reasoning_content 列 |
| `frontend/src/views/ai-chat/components/ReasoningPanel.vue` | 创建 | 推理面板组件 |
| `frontend/src/views/ai-chat/AiChat.vue` | 修改 | 深度思考按钮 + reasoning 事件 + 归档 |

---

## 10. 未纳入范围

- 深度思考对 token 消耗的影响（无额外调用，仅多一段 prompt 指令）
- 联网搜索阶段（Phase 2 独立功能，关联 thinking 时可独立决策）
- 移动端适配优化（Phase 2 统一做）
- 模型选择差异（当前方案对所有支持指令遵循的模型通用）
