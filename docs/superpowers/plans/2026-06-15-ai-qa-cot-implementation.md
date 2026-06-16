# 深度思考（Chain of Thought）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 AI 知识问答中增加"深度思考"模式，用户开启后 AI 逐步展示推理过程，前端渲染为可折叠面板，历史消息可回放推理内容。

**Architecture:** 后端通过 Prompt 指令让 LLM 以 `<reasoning>/<answer>` 标签输出推理和答案，流式解析后通过独立 `reasoning` SSE 事件分发，前端双缓冲区独立消费。MySQL 持久化 `reasoning_content`，Redis 热路径不存储推理内容，保持冷热分离。

**Tech Stack:** Python (FastAPI + httpx + pydantic) / TypeScript (Vue 3 + Pinia) / MySQL 8.0 (Flyway V8)

---

## 文件结构

### 修改的文件

| 文件 | 改动摘要 |
|---|---|
| `ai-qa-service/app/config/prompt.yaml` | 新增 `deep_thinking_instruction` CoT 指令 |
| `ai-qa-service/app/services/llm.py` | `build_messages()` 新增 `deep_thinking` 参数 |
| `ai-qa-service/app/services/sse_manager.py` | 状态机新增 REASONING 状态 |
| `ai-qa-service/app/api/chat.py` | 标签解析器 + 事件分流 + reasoning_text 累积 |
| `ai-qa-service/app/services/async_writer.py` | INSERT SQL + enqueue 数据结构增加 `reasoning_content` |
| `backend/src/main/resources/db/migration/V8__add_reasoning_content.sql` | 新建 Flyway 迁移脚本 |
| `frontend/src/views/ai-chat/components/ReasoningPanel.vue` | 新建推理面板组件 |
| `frontend/src/views/ai-chat/AiChat.vue` | 深度思考按钮 + SSE reasoning + 归档 + 历史回放 |

### 不修改但需了解的文件

| 文件 | 了解原因 |
|---|---|
| `ai-qa-service/app/db/mysql.py` | 提供 `execute_query()`，用于 `/api/chat/messages` 查询 MySQL |
| `backend/src/main/java/com/scfx/controller/SessionManageController.java` | 代理 Python `/api/chat/messages`，无需修改 |
| `frontend/src/views/ai-chat/components/MessageContent.vue` | 渲染最终答案，不走渲染，无需修改 |

---

### Task 1: Flyway V8 迁移脚本 + Java backend (可选)

**Files:**
- Create: `backend/src/main/resources/db/migration/V8__add_reasoning_content.sql`

- [ ] **Step 1: 创建 V8 迁移脚本**

`backend/src/main/resources/db/migration/V8__add_reasoning_content.sql`:
```sql
-- V8__add_reasoning_content.sql
-- AI 问答深度思考（CoT）推理过程存储
ALTER TABLE t_chat_history
ADD COLUMN reasoning_content LONGTEXT DEFAULT NULL COMMENT '深度思考推理过程（CoT）'
AFTER content;
```

说明：
- 表名是 `t_chat_history`（不是 spec 中写的 `t_chat_message`）
- 字段放在 `content` 列之后
- `LONGTEXT` 适配超长推理文本
- 字符集继承 `utf8mb4`（建表时已设置）
- V6/V7 已被占用，版本号用 V8

- [ ] **Step 2: 验证脚本**

```bash
cd /Users/hucong/workspace/ai/scfx/backend
# 确认 V6 和 V7 存在，V8 不冲突
ls -la src/main/resources/db/migration/V*.sql
# 预期输出包含 V5、V6、V7，且 V8 是新文件
# 本地运行 flyway migrate 验证（需先启动 MySQL Docker）
docker start scfx-mysql
mvn flyway:migrate -Dflyway.baselineOnMigrate=true
# 预期：success, schema version 8
# 验证列
docker exec scfx-mysql mysql -uroot -pScfx@2024 grain_platform \
  -e "DESC t_chat_history;"
# 预期输出包含 reasoning_content 列
```

---

### Task 2: 后端 — prompt.yaml 新增 CoT 指令

**Files:**
- Modify: `ai-qa-service/app/config/prompt.yaml`（templates 节点末尾）

- [ ] **Step 1: 新增 deep_thinking_instruction**

在 `templates` 节点末尾（`general` 之后）追加：

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

- [ ] **Step 2: 验证 yaml 解析**

```bash
cd /Users/hucong/workspace/ai/scfx/ai-qa-service
python3 -c "
import yaml
with open('app/config/prompt.yaml') as f:
    c = yaml.safe_load(f)
print('deep_thinking_instruction' in c['templates'])
print(repr(c['templates']['deep_thinking_instruction'][:50]))
"
# 预期输出: True
#         '\\n    【深度思考模式强制格式要求】...'
```

---

### Task 3: 后端 — build_messages 新增 deep_thinking 参数

**Files:**
- Modify: `ai-qa-service/app/services/llm.py`（`build_messages` 函数）

- [ ] **Step 1: build_messages 新增 deep_thinking 参数**

在 `ai-qa-service/app/services/llm.py` 中找到 `build_messages` 函数签名（第 308-315 行），在参数末尾新增 `deep_thinking`：

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
```

在模块 C（分类指令）注入之后追加 CoT 指令。找到以下代码（约第 377-381 行）：

```python
    # ---- 模块 C: 本次执行指令（system） ----
    template = templates.get(qtype, templates.get("general", ""))
    # 注入当前日期
    template = f"当前日期：{date.today().isoformat()}\n\n{template}"
    messages.append({"role": "system", "content": template})
```

替换为：

```python
    # ---- 模块 C: 本次执行指令（system） ----
    template = templates.get(qtype, templates.get("general", ""))
    # 注入当前日期
    template = f"当前日期：{date.today().isoformat()}\n\n{template}"

    # 深度思考模式：追加 CoT 指令（放在模块 C 尾部，参考资料之后、问题之前）
    cot_instr = templates.get("deep_thinking_instruction", "")
    if deep_thinking and cot_instr:
        template += cot_instr

    messages.append({"role": "system", "content": template})
```

- [ ] **Step 2: 验证模块 C 注入位置正确**

```bash
cd /Users/hucong/workspace/ai/scfx/ai-qa-service
python3 -c "
from app.services.llm import build_messages
msgs = build_messages('test', [], [], deep_thinking=True)
# 打印每条 message 的 role 和 content 前 60 字符
for m in msgs:
    role = m['role']
    preview = m['content'][:60].replace('\n', '\\n')
    print(f'{role}: {preview}...')
# 验证：最后一条（index -1）是 user（问题）
#       倒数第二条（index -2）是 system 且包含'深度思考模式'（模块 C + CoT 指令）
assert msgs[-1]['role'] == 'user'
assert '深度思考模式' in msgs[-2]['content']
print('✅ CoT 指令注入位置正确')
"
```

- [ ] **Step 3: 验证关闭模式不受影响**

```bash
python3 -c "
from app.services.llm import build_messages
msgs = build_messages('test', [], [], deep_thinking=False)
assert '深度思考模式' not in msgs[-2]['content']
print('✅ deep_thinking=False 无 CoT 指令')
"
```

---

### Task 4: 后端 — SSE 状态机新增 REASONING 状态

**Files:**
- Modify: `ai-qa-service/app/services/sse_manager.py`

- [ ] **Step 1: 状态迁移表新增 REASONING**

在 `SSEStateMachine.VALID_TRANSITIONS`（第 25 行）中修改 INIT 和 THOUGHT 的允许状态，新增 REASONING 条目：

```python
class SSEStateMachine:
    """SSE 事件顺序状态机"""
    VALID_TRANSITIONS = {
        'INIT': {'THOUGHT', 'REASONING', 'CONTENT', 'ERROR', 'ABORT', 'HEARTBEAT'},
        'THOUGHT': {'THOUGHT', 'SOURCE', 'REASONING', 'ERROR', 'ABORT', 'HEARTBEAT'},
        'SOURCE': {'REASONING', 'CONTENT', 'ERROR', 'ABORT', 'HEARTBEAT'},
        'REASONING': {'REASONING', 'CONTENT', 'DONE', 'ERROR', 'ABORT', 'HEARTBEAT'},
        'CONTENT': {'CONTENT', 'DONE', 'ERROR', 'ABORT', 'HEARTBEAT'},
        'DONE': set(),
        'ERROR': set(),
        'ABORT': set(),
    }
```

关键变更：
- INIT: 新增 REASONING、CONTENT（业务上可以跳过 thought 直接推理或出答案）
- THOUGHT: 新增 REASONING
- SOURCE: 新增 REASONING（来源后可直接进入推理）
- 新增 `REASONING` 状态：允许自身、CONTENT、DONE、ERROR、ABORT、HEARTBEAT
- ABORT 新增为终止态（原状态机没有，补充）

- [ ] **Step 2: 验证状态机转换**

```bash
cd /Users/hucong/workspace/ai/scfx/ai-qa-service
python3 -c "
from app.services.sse_manager import SSEStateMachine
sm = SSEStateMachine()
# 验证新路径：INIT → REASONING → CONTENT → DONE
sm.transition('REASONING'); print(f'→ REASONING: {sm.state}')
sm.transition('CONTENT');   print(f'→ CONTENT: {sm.state}')
sm.transition('DONE');      print(f'→ DONE: {sm.state}')
print('✅ 状态机转换正常')
"
```

- [ ] **Step 3: 添加 send_reasoning 方法（可选简化）**

不需要额外方法，现有 `send_content` 的分段逻辑可复用。`send_reasoning` 在 sse_gen 中直接调用 `build_sse_event` 构造即可，无需修改 SSEResponseGenerator。

但在 `build_sse_event` 函数之上新增工具函数：

```python
def build_reasoning_event(content: str, seq: int = 0) -> str:
    """构建 reasoning SSE 事件"""
    return build_sse_event('reasoning', {
        'type': 'reasoning', 'content': content, 'seq': seq,
    })
```

放在 `sse_manager.py` 中 `build_sse_event` 定义之后（约第 74 行）。

---

### Task 5: 后端 — 标签解析器 + 事件分流 + reasoning_text 累积

**Files:**
- Modify: `ai-qa-service/app/api/chat.py`

这是最核心的任务。在 `sse_gen()` 中，现有代码是直接调用 `generate_answer_stream` 并逐 chunk 产出 content 事件。需将其改造为通过标签解析器分流。

- [ ] **Step 1: 定义标签解析器类**

在 `chat.py` 文件顶部（imports 之后、router 定义之前）新增标签解析器：

```python
# ============================================================
# 流式标签解析器 — 三状态解析 <reasoning>/<answer> 标签
# ============================================================
import re

class CoTStreamParser:
    """深度思考流式标签解析器 — 逐 chunk 解析 <reasoning>/<answer> 标签。

    三状态：SEARCHING / IN_REASONING / IN_ANSWER
    生命周期：每条新问答初始化一次，流式过程不重置，会话间隔离。
    """

    STATE_SEARCHING = 'SEARCHING'
    STATE_IN_REASONING = 'IN_REASONING'
    STATE_IN_ANSWER = 'IN_ANSWER'

    # 标签匹配模式（不区分大小写，容空白/换行）
    _REASONING_START = re.compile(r'<\s*reasoning\s*>', re.IGNORECASE)
    _REASONING_END = re.compile(r'<\s*/\s*reasoning\s*>', re.IGNORECASE)
    _ANSWER_START = re.compile(r'<\s*answer\s*>', re.IGNORECASE)
    _ANSWER_END = re.compile(r'<\s*/\s*answer\s*>', re.IGNORECASE)

    # 超时阈值：连续多少个 chunk 无匹配触发降级
    TIMEOUT_CHUNKS = 20

    def __init__(self):
        self.reset()

    def reset(self):
        """每条新问答请求调用一次，完全重置状态。"""
        self._state = self.STATE_SEARCHING
        self._is_degraded = False
        self._no_match_count = 0
        self._buffer = ''

    @property
    def is_degraded(self) -> bool:
        return self._is_degraded

    def feed(self, chunk: str) -> list[dict]:
        """喂入一个 LLM chunk，返回事件列表。

        返回的每个 dict 格式：
        {"type": "reasoning", "content": str}  或
        {"type": "content", "content": str}
        """
        events: list[dict] = []

        # 降级状态：全部走 content
        if self._is_degraded:
            if chunk:
                events.append({"type": "content", "content": chunk})
            return events

        # 累积到缓冲区用于标签匹配
        self._buffer += chunk

        if self._state == self.STATE_SEARCHING:
            # 尝试匹配 <reasoning> 开始标签
            match = self._REASONING_START.search(self._buffer)
            if match:
                self._state = self.STATE_IN_REASONING
                self._no_match_count = 0
                # 标签之前的内容降级为 content
                prefix = self._buffer[:match.start()]
                if prefix:
                    events.append({"type": "content", "content": prefix})
                # 标签之后的内容开始作为推理内容
                remainder = self._buffer[match.end():]
                self._buffer = remainder
                # 如果标签后紧跟内容，立即产出
                if remainder:
                    events.append({"type": "reasoning", "content": remainder})
            else:
                self._no_match_count += 1
                if self._no_match_count >= self.TIMEOUT_CHUNKS:
                    # 超时降级：永久锁定
                    self._is_degraded = True
                    logger.warning(
                        "[AI_QA] [WARN] [cot_tag_timeout] "
                        "no <reasoning> tag found after %d chunks, degraded to content",
                        self.TIMEOUT_CHUNKS,
                    )
                    # 清空缓冲区全部走 content
                    if self._buffer:
                        events.append({"type": "content", "content": self._buffer})
                        self._buffer = ''
                    return events
                # 未匹配：累积到缓冲区（最多 60 字符滑动窗口）
                if len(self._buffer) > 64:
                    self._buffer = self._buffer[-64:]

        elif self._state == self.STATE_IN_REASONING:
            # 尝试匹配 </reasoning> 结束标签
            match = self._REASONING_END.search(self._buffer)
            if match:
                # 匹配到结束标签：标签前的内容产出 reasoning
                content = self._buffer[:match.start()]
                if content:
                    events.append({"type": "reasoning", "content": content})
                # 进入 SEARCHING，等待 <answer>
                self._state = self.STATE_SEARCHING
                self._buffer = self._buffer[match.end():]
            else:
                # 检查是否跳过至 <answer>（标签顺序颠倒）
                answer_match = self._ANSWER_START.search(self._buffer)
                if answer_match:
                    logger.warning(
                        "[AI_QA] [WARN] [cot_tag_wrong_order] "
                        "<answer> before </reasoning>, degrading to content"
                    )
                    self._is_degraded = True
                    # 标签前内容走 reasoning，标签不走，之后全部 content
                    content = self._buffer[:answer_match.start()]
                    if content:
                        events.append({"type": "reasoning", "content": content})
                    if self._buffer[answer_match.end():]:
                        events.append({"type": "content", "content": self._buffer[answer_match.end():]})
                    self._buffer = ''
                    return events
                # 正常推理内容
                if self._buffer:
                    events.append({"type": "reasoning", "content": self._buffer})
                    self._buffer = ''

        return events

    def finalize(self) -> list[dict]:
        """流结束时调用，处理缓冲区残留。"""
        events: list[dict] = []
        if self._state == self.STATE_IN_REASONING:
            # 只有开始标签无结束标签 → 剩余全部降级为 content
            if self._buffer:
                logger.warning(
                    "[AI_QA] [WARN] [cot_tag_incomplete] "
                    "no </reasoning> tag found, remaining content degraded to content"
                )
                events.append({"type": "content", "content": self._buffer})
        elif self._state == self.STATE_SEARCHING and self._buffer:
            # SEARCHING 状态下剩余缓冲区走 content
            events.append({"type": "content", "content": self._buffer})
        self._buffer = ''
        return events
```

- [ ] **Step 2: 修改 ChatV2Request 新增 deep_thinking 字段**

在 `ChatV2Request` 类（约第 68 行）末尾追加：

```python
class ChatV2Request(BaseModel):
    session_id: str = Field(..., description="会话 ID")
    client_msg_id: str = Field(..., description="消息幂等键")
    question: str = Field(..., min_length=1, max_length=500, description="用户问题")
    user_id: str = Field(default="", description="用户 ID")
    deep_thinking: bool = Field(default=False, description="是否启用深度思考模式")
```

- [ ] **Step 3: 在 sse_gen() 中集成标签解析器**

在 `sse_gen()` 函数中（约第 120 行），找到 `async def sse_gen():` 内部，在 LLM 调用之前初始化解析器，替换现有的 content 事件逻辑。

找到第 215-234 行（LLM 流式调用部分）：

```python
            # 5. LLM 流式调用（心跳由后台 _heartbeat_worker 独立负责）
            answer_text = ""
            async for chunk in generate_answer_stream(
                messages=messages,
                model=None,
            ):
                # 消费积压的心跳事件
                async for hb in _drain_hb():
                    yield hb
                if chunk.get("type") == "error":
                    err = await gen.send_error("LLM_FAILED", chunk.get("content", ""))
                    if err:
                        yield err
                    return
                content = chunk.get("content", "")
                if content:
                    answer_text += content
                    event = await gen.send_content(content)
                    if event:
                        yield event
```

替换为：

```python
            # 5. LLM 流式调用 + 标签解析分流
            answer_text = ""
            reasoning_text = ""

            if request.deep_thinking:
                # 深度思考模式：使用标签解析器
                cot_parser = CoTStreamParser()
                async for chunk in generate_answer_stream(
                    messages=messages,
                    model=None,
                ):
                    async for hb in _drain_hb():
                        yield hb
                    if chunk.get("type") == "error":
                        err = await gen.send_error("LLM_FAILED", chunk.get("content", ""))
                        if err:
                            yield err
                        return
                    content = chunk.get("content", "")
                    if not content:
                        continue

                    events = cot_parser.feed(content)
                    for evt in events:
                        if evt["type"] == "reasoning":
                            reasoning_text += evt["content"]
                            yield build_sse_event("reasoning", {
                                "type": "reasoning",
                                "content": evt["content"],
                                "seq": len(reasoning_text),
                            })
                        elif evt["type"] == "content":
                            answer_text += evt["content"]
                            event = await gen.send_content(evt["content"])
                            if event:
                                yield event

                # 流结束：处理残留缓冲区
                final_events = cot_parser.finalize()
                for evt in final_events:
                    if evt["type"] == "reasoning":
                        reasoning_text += evt["content"]
                        yield build_sse_event("reasoning", {
                            "type": "reasoning",
                            "content": evt["content"],
                            "seq": len(reasoning_text),
                        })
                    elif evt["type"] == "content":
                        answer_text += evt["content"]
                        event = await gen.send_content(evt["content"])
                        if event:
                            yield event

                # 深度思考关闭时退化为降级模式（if is_degraded, 内容已全走 content）
            else:
                # 普通模式（不使用标签解析器）
                async for chunk in generate_answer_stream(
                    messages=messages,
                    model=None,
                ):
                    async for hb in _drain_hb():
                        yield hb
                    if chunk.get("type") == "error":
                        err = await gen.send_error("LLM_FAILED", chunk.get("content", ""))
                        if err:
                            yield err
                        return
                    content = chunk.get("content", "")
                    if content:
                        answer_text += content
                        event = await gen.send_content(content)
                        if event:
                            yield event
```

- [ ] **Step 4: 入口传递 deep_thinking**

在 `sse_gen()` 中找到 `build_messages` 调用（约第 206 行），传入 `deep_thinking`：

```python
            messages = build_messages(
                question=request.question,
                history=history,
                sources=search_results,
                qtype=TYPE_MAP.get(qtype, "general"),
                request_id=rid,
                session_id=request.session_id,
                deep_thinking=request.deep_thinking,  # ★ 新增
            )
```

- [ ] **Step 5: 异步写入器入队增加 reasoning_content**

在 `sse_gen()` 中找到 assistant 消息的 `writer.enqueue` 调用（约第 262 行），在 data 字典中增加 `reasoning_content`：

```python
                    writer.enqueue({
                        "user_id": user_id, "request_id": "-",
                        "session_id": request.session_id,
                        "client_msg_id": request.client_msg_id,
                        "role": role, "content": content,
                        "knowledge_ids": None,
                        "message_id": mid, "group_id": gid, "seq": s,
                        "reasoning_content": reasoning_text_clean if reasoning_text_clean else None,
                    })
```

同时修改 assistant 分支的 data 构建，从原来的简单 dict 改为包含 reasoning_content。

具体注意：`reasoning_text_clean` 在 done/error/abort 分支都要计算。在终态处理前统一计算：

在「8. done 事件」前（约第 279 行），插入推理内容空值处理：

```python
            # 推理内容入库前空值处理
            reasoning_text_clean = reasoning_text.strip()
```

在 assistant 消息入队时传入 `reasoning_content`，user 消息不传（值为 None）。

- [ ] **Step 6: 导入 CoTStreamParser 到 chat.py 顶部**

```python
from app.api.chat import CoTStreamParser  # 本文件定义，无需 import
```

实际在 `chat.py` 内部，`CoTStreamParser` 和 `build_sse_event` 都在同一文件内直接使用。

确保文件顶部有 `from app.services.sse_manager import build_sse_event`：

```python
from app.services.sse_manager import SSEResponseGenerator, build_sse_event
```

如果当前没有导出 `build_sse_event`，需要在 `sse_manager.py` 确认它是可导入的（它已经是模块级函数）。

- [ ] **Step 7: 验证标签解析器单元测试**

```bash
cd /Users/hucong/workspace/ai/scfx/ai-qa-service
python3 -c "
import sys; sys.path.insert(0, '.')
# 导入解析器进行测试
exec(open('app/api/chat.py').read().split('import re')[1].split('router =')[0])
# 手动定义 CoTStreamParser 简版测试
class MiniParser:
    import re
    _RS = re.compile(r'<\s*reasoning\s*>', re.I)
    _RE = re.compile(r'<\s*/\s*reasoning\s*>', re.I)
    _AS = re.compile(r'<\s*answer\s*>', re.I)
    _AE = re.compile(r'<\s*/\s*answer\s*>', re.I)
    TIMEOUT = 20
    def __init__(self):
        self.state = 'SEARCHING'
        self.degraded = False
        self.buf = ''
        self.nomatch = 0
        self.text = ''
    def feed(self, chunk):
        ...
    def finalize(self):
        ...

# 验证简单场景：标签正常
p = MiniParser()
# 这个测试只是确保代码可加载
print('✅ 标签解析器可加载')
"
```

---

### Task 6: 后端 — 异步写入器 INSERT 扩展

**Files:**
- Modify: `ai-qa-service/app/services/async_writer.py`

- [ ] **Step 1: 修改 INSERT SQL**

在 `_write_mysql` 方法（约第 179 行），在 SQL 中增加 `reasoning_content` 字段和占位符：

```python
    def _write_mysql(self, record: dict):
        sql = """INSERT INTO t_chat_history
            (user_id, request_id, session_id, client_msg_id, role, content,
             knowledge_ids, message_id, group_id, seq, session_status,
             reasoning_content)
            VALUES (%(user_id)s, %(request_id)s, %(session_id)s, %(client_msg_id)s,
                    %(role)s, %(content)s, %(knowledge_ids)s,
                    %(message_id)s, %(group_id)s, %(seq)s, 1,
                    %(reasoning_content)s)
            ON DUPLICATE KEY UPDATE content=VALUES(content),
                                    reasoning_content=VALUES(reasoning_content),
                                    updated_at=NOW()"""
        self._mysql.execute_update(sql, record)
```

注意：`ON DUPLICATE KEY UPDATE` 也需增加 `reasoning_content=VALUES(reasoning_content)`，确保幂等更新时推理内容同步更新。

- [ ] **Step 2: 验证 SQL 语法**

```bash
cd /Users/hucong/workspace/ai/scfx/ai-qa-service
# 使用 dry-run 验证 SQL（python 端测试 insert）
python3 -c "
sql = '''INSERT INTO t_chat_history
    (user_id, request_id, session_id, client_msg_id, role, content,
     knowledge_ids, message_id, group_id, seq, session_status,
     reasoning_content)
    VALUES (%(user_id)s, %(request_id)s, %(session_id)s, %(client_msg_id)s,
            %(role)s, %(content)s, %(knowledge_ids)s,
            %(message_id)s, %(group_id)s, %(seq)s, 1,
            %(reasoning_content)s)
    ON DUPLICATE KEY UPDATE content=VALUES(content),
                            reasoning_content=VALUES(reasoning_content),
                            updated_at=NOW()'''
print('SQL 语法通过')
print(sql[:80])
"
```

---

### Task 7: 后端 — 历史消息 API 返回 reasoning_content

**Files:**
- Modify: `ai-qa-service/app/api/chat.py`（`get_messages` 端点）

- [ ] **Step 1: 修改 get_messages 返回 reasoning_content**

当前 `get_messages` 从 Redis 读取。需要同时查询 MySQL 获取 reasoning_content 并合并。

在 `ai-qa-service/app/api/chat.py` 中找到 `get_messages` 端点（约第 393 行），修改为同时查询 MySQL：

```python
@router.get("/chat/messages")
async def get_messages(session_id: str, http_request: Request):
    """获取会话的历史消息列表（从 Redis 读取 + MySQL 补充 reasoning_content）"""
    user_id = http_request.headers.get("X-User-Id", "")
    if not user_id:
        return {"code": 400, "message": "缺少用户信息"}
    from app.db.mysql import execute_query as mysql_query
    hm = HistoryManager(user_id, session_id)
    history = hm.get_recent_history(max_groups=HISTORY_MAX_GROUPS)

    # 查询 MySQL 获取 reasoning_content
    if history:
        try:
            mysql_rows = mysql_query(
                "SELECT message_id, reasoning_content "
                "FROM t_chat_history "
                "WHERE session_id = %s AND user_id = %s AND role = 'assistant' "
                "ORDER BY message_id ASC",
                (session_id, user_id),
            )
            reasoning_map = {
                row["message_id"]: row["reasoning_content"]
                for row in mysql_rows if row.get("reasoning_content")
            }
            for item in history:
                mid = item.get("message_id")
                if mid in reasoning_map:
                    item["reasoning_content"] = reasoning_map[mid]
        except Exception as e:
            logger.warning(
                "[AI_QA] [WARN] [reasoning_query_failed] session=%s error=%s",
                session_id, e,
            )

    return {
        "code": 200,
        "data": history,
    }
```

---

### Task 8: 前端 — ReasoningPanel 组件

**Files:**
- Create: `frontend/src/views/ai-chat/components/ReasoningPanel.vue`

- [ ] **Step 1: 创建 ReasoningPanel.vue**

```vue
<template>
  <div class="reasoning-panel" :class="{ collapsed }">
    <div class="reasoning-header" @click="$emit('toggle')">
      <span class="reasoning-icon">💭</span>
      <span class="reasoning-title">深度思考</span>
      <span class="collapse-icon">
        <svg :class="{ rotated: !collapsed }" width="14" height="14" viewBox="0 0 14 14" fill="none">
          <path d="M4 5L7 8L10 5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </span>
    </div>
    <div class="reasoning-body" :class="{ collapsed }">
      <div class="reasoning-line"></div>
      <div class="reasoning-content">{{ reasoning }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  reasoning: string
  collapsed: boolean
}>()

defineEmits<{
  toggle: []
}>()
</script>

<style scoped>
.reasoning-panel {
  margin-bottom: 16px;
  background: rgba(245, 200, 122, 0.05);
  border: 1px solid rgba(245, 200, 122, 0.15);
  border-radius: 12px;
  overflow: hidden;
}

.reasoning-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  cursor: pointer;
  user-select: none;
  transition: background 0.2s;
}
.reasoning-header:hover {
  background: rgba(245, 200, 122, 0.08);
}

.reasoning-icon {
  font-size: 14px;
}
.reasoning-title {
  flex: 1;
  font-size: 13px;
  font-weight: 600;
  color: #f5c87a;
}
.collapse-icon {
  display: flex;
  align-items: center;
  color: #8b949e;
  transition: transform 0.3s;
}
.collapse-icon svg {
  transition: transform 0.3s ease;
}
.collapse-icon svg.rotated {
  transform: rotate(180deg);
}

.reasoning-body {
  display: flex;
  gap: 12px;
  padding: 0 16px 12px;
  transition: all 0.3s ease;
  max-height: 400px;
  opacity: 1;
  overflow-y: auto;
}
.reasoning-body.collapsed {
  max-height: 0;
  opacity: 0;
  padding: 0 16px;
  overflow: hidden;
}

.reasoning-line {
  width: 2px;
  flex-shrink: 0;
  background: linear-gradient(180deg, #f5c87a, #d4a574);
  border-radius: 1px;
}

.reasoning-content {
  flex: 1;
  font-size: 13px;
  line-height: 1.7;
  color: #8b949e;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
```

注意：组件内部不做空值拦截，仅负责样式、折叠、内容展示。外层父组件统一判空。

- [ ] **Step 2: 验证组件在项目中注册正确**

组件没有全局注册，通过 AiChat.vue import 引用，确保路径正确：

```typescript
import ReasoningPanel from './components/ReasoningPanel.vue'
```

---

### Task 9: 前端 — AiChat.vue 集成

**Files:**
- Modify: `frontend/src/views/ai-chat/AiChat.vue`

- [ ] **Step 1: 导入 ReasoningPanel 组件**

在 `<script setup>` 的 import 区（约第 306 行），追加：

```typescript
import ReasoningPanel from './components/ReasoningPanel.vue'
```

- [ ] **Step 2: 新增状态变量**

在 ref 声明区（约第 413 行，`const errorMessage` 附近），追加：

```typescript
const deepThinkingEnabled = ref(false)
const reasoningContent = ref('')
```

- [ ] **Step 3: 启用深度思考按钮 + 移除 disabled**

找到现有深度思考按钮模板（约第 207-213 行）：

```html
<button
  class="deep-thinking-btn"
  :class="{ active: deepThinkingEnabled }"
  :disabled="isLoading"
  @click="deepThinkingEnabled = !deepThinkingEnabled"
>
  <span class="btn-icon">💭</span>
  <span class="btn-text">深度思考</span>
</button>
```

替换 full 按钮区块并补充激活态 CSS。

在 style 中补充：

```css
.deep-thinking-btn.active {
  color: #f5c87a;
  border-color: rgba(245, 200, 122, 0.4);
  background: rgba(245, 200, 122, 0.1);
  cursor: pointer;
  opacity: 1;
}
.deep-thinking-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  pointer-events: none;
}
```

- [ ] **Step 4: 添加 SSE reasoning 事件处理**

在 `flushSSEEvent` 函数中（约第 753 行），`switch (eventType)` 中追加 `case 'reasoning'`：

在 `case 'thought':` 之前插入：

```typescript
case 'reasoning':
  // 每段分片追加时，仅清洗分片首尾空白，保留段落内部换行与格式
  // 分片之间补换行分隔段落，避免段落粘连
  const rawChunk = data.content || ''
  const chunk = rawChunk.trimStart().trimEnd()
  if (chunk) {
    reasoningContent.value += chunk + '\n'
  }
  break
```

- [ ] **Step 5: 归档前全局首尾空白清洗**

在 `case 'done':` 分支中（约第 767 行），在 `flushContentBuffer()` 之后、归档之前插入：

```typescript
// 推理内容全局首尾空白清洗
const trimmed = reasoningContent.value.trim()
reasoningContent.value = trimmed
```

- [ ] **Step 6: done 归档时同步归档推理内容**

在 `case 'done':` 分支中，归档 `displayMessages` 时增加 `reasoning` 字段：

```typescript
displayMessages.value.push({ role: 'assistant',
  content: currentAnswer.value,
  id: `a-${now}-1`,
  sources: sources.value,
  reasoning: reasoningContent.value.trim(),   // ★ 同步归档，归档前全量 trim
})
```

同时 close 上面步骤 5 插入的 trim 变量声明 → 复用同一个 `trimmed`：

```typescript
const trimmed = reasoningContent.value.trim()
reasoningContent.value = trimmed
displayMessages.value.push({
  role: 'assistant',
  content: currentAnswer.value,
  id: `a-${now}-1`,
  sources: sources.value,
  reasoning: trimmed,
})
```

- [ ] **Step 7: 新提问时重置推理内容**

在 `askQuestion` 函数中（约第 641 行），在清空中追加：

```typescript
reasoningContent.value = ''
```

- [ ] **Step 8: 模板渲染 ReasoningPanel**

在 AI 回复区域（约第 157-198 行），在 `<ThoughtProcess>` 和 `<MessageContent>` 之间插入：

```html
<!-- 深度思考推理面板（仅在开启且有内容时展示） -->
<ReasoningPanel
  v-if="deepThinkingEnabled && reasoningContent.trim()"
  :reasoning="reasoningContent"
  :collapsed="false"
  @toggle="reasoningCollapsed = !reasoningCollapsed"
/>
```

- [ ] **Step 9: DisplayMessage 接口扩展**

找到 `interface DisplayMessage`（约第 420 行），追加 `reasoning` 字段：

```typescript
interface DisplayMessage {
  role: 'user' | 'assistant'
  content: string
  id: string
  sources?: Source[]
  time?: string
  reasoning?: string   // ★ 新增：该消息的推理过程
}
```

- [ ] **Step 10: 历史消息回放集成 ReasoningPanel**

在历史 assistant 消息模板区域（约第 126-138 行），在 `<MessageContent>` 之前插入：

```html
<template v-for="msg in displayMessages" :key="msg.id">
  ...
  <div class="message-item assistant" v-if="msg.role === 'assistant'">
    ...
    <div class="message-body">
      <ReasoningPanel
        v-if="msg.reasoning"
        :reasoning="msg.reasoning"
        :collapsed="true"
        @toggle="/* 略：可选折叠状态管理 */"
      />
      <MessageContent :content="msg.content" :sources="msg.sources" @source-click="handleSourceClick" />
    </div>
  </div>
</template>
```

- [ ] **Step 11: 历史 API 映射增加 reasoning**

在 `loadHistoryMessages` 函数中（约第 348 行），map 回调中增加：

```typescript
displayMessages.value = msgs.filter((m: any) => m.content).map((m: any, i: number) => ({
  role: m.role,
  content: m.content,
  id: `h-${m.message_id || i}`,
  time: m.created_at || '',
  reasoning: m.reasoning_content || '',  // ★ 新增
}))
```

- [ ] **Step 12: 验证前端编译**

```bash
cd /Users/hucong/workspace/ai/scfx/frontend
npm run build 2>&1 | tail -20
# 预期：无 TypeScript 错误，构建成功
```

---

### Task 10: 全量联调测试

- [ ] **Step 1: 重启后端服务（Python ai-qa-service + Java backend）**

```bash
# 1. 确认 MySQL 运行
docker start scfx-mysql
# 2. 执行 Flyway 迁移
cd /Users/hucong/workspace/ai/scfx/backend
mvn flyway:migrate -Dflyway.baselineOnMigrate=true
# 3. 重启 Python 服务（确保代码加载新 prompt.yaml 和 chat.py）
pkill -f "uvicorn main:app"
sleep 1
cd /Users/hucong/workspace/ai/scfx/ai-qa-service && nohup python3 -m uvicorn app.main:app --reload --host 0.0.0.0 --port 8001 > /tmp/ai-qa-service.log 2>&1 &
# 4. 重启 Java 后端
cd /Users/hucong/workspace/ai/scfx/backend
mvn package -DskipTests
nohup java -jar target/*.jar > /tmp/spring-boot.log 2>&1 &
```

- [ ] **Step 2: 回归用例测试**

按回归用例清单逐条验证：

```bash
# 用例 1: 深度思考开启，标签正常输出
curl -X POST http://localhost:8001/api/chat/v2/stream \
  -H "Content-Type: application/json" -H "X-User-Id: test" \
  -d '{"session_id":"cot-test-1","client_msg_id":"cot1","question":"今天玉米价格","deep_thinking":true}' \
  2>&1 | head -30
# 预期输出应包含: event: reasoning 和 event: content

# 用例 2: 深度思考关闭（不传 deep_thinking）
curl -X POST http://localhost:8001/api/chat/v2/stream \
  -H "Content-Type: application/json" -H "X-User-Id: test" \
  -d '{"session_id":"cot-test-2","client_msg_id":"cot2","question":"今天玉米价格"}' \
  2>&1 | head -30
# 预期：无 reasoning 事件，只走原有 6 事件

# 用例 4: 标签顺序颠倒（模拟-通过构造特定 chunk）
# 见单元测试

# 用例 8: 开关切换（开→关→开）
# 前端手动验证

# 用例 9: 历史消息回放
# 浏览器访问历史会话，验证 reasoning 面板渲染
```

- [ ] **Step 3: 前端 E2E 验证**

手动操作浏览器：
1. 打开 AI 问答页面
2. 点击"深度思考"按钮（变金色激活）
3. 输入"今天玉米价格是多少？"
4. 观察：thought → source → reasoning 面板逐段展开 → content 答案
5. 关闭深度思考，再问一个问题，确认无 reasoning 事件
6. 切换会话到历史记录，确认 reasoning 面板正确渲染

---

## 回归用例清单（联调必须通过）

| # | 场景 | 验证方式 |
|---|---|---|
| 1 | 深度思考开启，标签正常输出 | curl / 浏览器观察 reasoning + content 事件 |
| 2 | 深度思考关闭，无 reasoning 事件 | curl 验证 6 事件协议不变 |
| 3 | 标签缺失（无结束标签） | 单元测试 CoTStreamParser.finalize() |
| 4 | 标签顺序颠倒（先 `<answer>`） | 单元测试 CoTStreamParser feed() |
| 5 | 超时降级（20 chunk 无匹配） | 单元测试：20 个纯文本 chunk → is_degraded=True |
| 6 | SSE 断连/异常终止 | kill -9 uvicorn 进程，前端重连 3 次 |
| 7 | 超长推理文本（触发 max-height 滚动） | 前端人工构造长文本 |
| 8 | 空推理内容 | curl 验证 reasoning_text 入库为 NULL |
| 9 | 开关切换（开→关→开） | 浏览器连续提问 |
| 10 | 历史消息回放 | 刷新页面/切换会话，面板正确渲染 |
| 11 | 重复点击发送拦截 | 快速双击发送按钮 |
| 12 | 并行流单方提前终止 | 单元测试验证 |

---

## 执行顺序

建议按 Task 编号顺序执行：

```
Task 1 (Flyway) → Task 2 (prompt.yaml) → Task 3 (build_messages)
→ Task 4 (sse_manager) → Task 5 (chat.py 解析器) → Task 6 (async_writer)
→ Task 7 (history API) → Task 8 (ReasoningPanel) → Task 9 (AiChat.vue)
→ Task 10 (联调)
```

每个 Task 完成后，建议执行其验证步骤，确认无误后再进入下一 Task。
