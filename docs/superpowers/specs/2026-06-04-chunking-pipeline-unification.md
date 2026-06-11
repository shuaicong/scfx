# 切片管道统一设计方案

## 1. 背景与问题

### 1.1 现状

当前系统存在**两套独立的切片/向量化管道**，由不同时期、不同团队建设，服务于不同消费端：

| | **Java DocumentPipeline** | **Python ai-qa-service** |
|---|---|---|
| 建设时间 | 2026年6月 | 2026年5月 |
| 切片算法 | `TextSplitter` 768字/片，分层语义分割（段落→句子→短语四级兜底） | `chunk_text` 500-800字/片，简单段落分割 |
| 表格处理 | ❌ 无 | ✅ `chunk_with_types` 表格原子块 + 语义改写 |
| 向量模型 | BGE-M3 1024d（全文）+ DashScope 768d（可视化） | BGE-large-zh-v1.5 1024d（逐片） |
| 向量存储 | `t_knowledge_viz` + `retrieval_vector`（MySQL） | Qdrant |
| 进度追踪 | ✅ `t_knowledge_task` | ❌ |
| 上游过滤器 | ✅ 合规过滤（敏感词 + 有效文本判断） | ❌ |
| 前端消费 | 详情页切片展示、PCA 可视化散点图 | AI 问答 |

### 1.2 数据入口覆盖情况

| 数据入口 | DocumentPipeline | vectorTaskService (BGE-M3+DashScope) | Python Qdrant |
|---|---|---|---|
| 采集数据（`CollectorController.submitData`） | ❌ 未触发 | ✅ 已触发 | ❌ 未触发 |
| 上传文档（`/knowledge/upload`） | ✅ 已触发 | ✅ 已触发 | ❌ 未触发 |
| 人工录入（`/knowledge/manual`） | ❌ 未触发 | ✅ 已触发 | ❌ 未触发 |
| 重新向量化（`/knowledge/{id}/revectorize`） | ❌ 未触发 | ✅ 已触发 | ❌ 未触发 |

### 1.3 暴露的问题

1. **采集数据的知识详情页显示"0 个切片"** — 因 `DocumentPipeline` 未对采集数据触发，`t_knowledge_chunk` 为空，`chunk_count = 0`
2. **新增数据无法进入 AI 问答** — Qdrant 只在手动调 `revectorize` 时写入，新增数据不会自动同步
3. **切片边界不一致** — 两套管道使用不同切片算法，同一篇文档在两个系统中的切片结果不同，导致"详情页切片数 ≠ AI 答案引用的切片来源数"
4. **管道维护成本翻倍** — 每次改动切片策略需要同步修改 Java 和 Python 两套实现

---

## 2. 设计目标

### 2.1 核心原则

**Java `DocumentPipeline` 作为切片的唯一负责任**，Python 端消费 `t_knowledge_chunk` 表的结果。

### 2.2 目标清单

1. 所有数据入口都触发 `DocumentPipeline`，写入 `t_knowledge_chunk`
2. `DocumentPipeline` 完成后自动同步 Qdrant，使 AI 问答可用
3. Python 端从 `t_knowledge_chunk` 读取切片，不再自行分块
4. Java `TextSplitter` 增加表格原子块支持，和 Python `chunk_with_types` 输出一致
5. 消除知识详情页"0 切片"现象
6. 所有改动向后兼容，不破坏现有功能

### 2.3 非目标

- 不改动 Python 的 LLM 调用逻辑（`chat.py`）
- 不改动 Python 的 embedding 逻辑（`embed.py`）
- 不改动 PCA 可视化流程
- 不迁移存量 `vector_ids` 数据（存量可随下次 `revectorize` 自然更新）

---

## 3. 目标架构

```
采集数据 / 上传文档 / 人工录入
    │
    ▼
┌──────────────────────────────────────┐
│        DocumentPipeline.start()      │  ← 统一的入口
│                                      │
│  ① 合规过滤（ContentFilter）         │
│  ② TextSplitter.split() + 表格原子块  │  ← 唯一切片算法
│  ③ 软删除旧切片 + 批量写入            │
│     t_knowledge_chunk                │  ← 单一切片存储
│  ④ 更新 chunk_count + vector_status  │
│                                      │
│  ┌─ 完成后 ─────────────────────────┐ │
│  │ vectorTaskService.processSingle() │ │  ← BGE-M3 + DashScope
│  │   → t_knowledge_viz              │ │      知识可视化用
│  │   → retrieval_vector             │ │      语义搜索用
│  └──────────────────────────────────┘ │
│                                      │
│  ┌─ 完成后 ─────────────────────────┐ │
│  │ Python revectorize HTTP 调用      │ │  ← Qdrant 同步
│  │   Python 从 t_knowledge_chunk 读  │ │
│  │   → BGE embed → Qdrant           │ │       AI 问答用
│  │   → 回写 vector_ids              │ │
│  └──────────────────────────────────┘ │
└──────────────────────────────────────┘
```

### 3.1 数据结构

`t_knowledge_chunk`（已有表，不做 schema 变更）：

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| knowledge_id | BIGINT | 关联知识 ID |
| category_id | BIGINT | 分类 ID |
| chunk_index | INT | 切片序号（从 0 开始） |
| chunk_total | INT | 该知识总切片数 |
| content | TEXT | 切片文本 |
| start_offset | INT | 在原文中的起始字符偏移 |
| end_offset | INT | 在原文中的结束字符偏移 |
| is_summary | TINYINT | 1=首切片（摘要） |
| token_count | INT | 预估 token 数 |
| chunk_type | VARCHAR(20) | text/table（Phase 2 新增） |
| vector_status | VARCHAR(20) | pending/vectorized/failed |
| is_active | TINYINT | 1=有效，0=软删除 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

### 3.2 数据流对比

**改造前：**

```
liangxin.py → CollectorController → MySQL(t_knowledge_base)
                                        ↓
                                    t_knowledge_chunk=空 → chunk_count=0
                                        ↓
                                    vectorTaskService (BGE-M3全文)
                                        ↓
                                    Qdrant=空 → AI问答无数据
```

**改造后：**

```
liangxin.py → CollectorController → MySQL(t_knowledge_base)
                                        ↓
                                    DocumentPipeline.start()
                                        ├── TextSplitter → t_knowledge_chunk (chunk_count=N)
                                        ├── vectorTaskService → t_knowledge_viz
                                        └── HTTP → Python revectorize
                                                       ↓
                                                Python 读 t_knowledge_chunk
                                                       ↓
                                                BGE embed → Qdrant (vector_ids=UUIDs)
                                                       ↓
                                                回写 MySQL vector_ids
                                                       ↓
                                                AI问答可用
```

---

## 4. 实施计划

### Phase 1：连接断点（立即可做，约 10 行改动）

#### 改动 1：`CollectorController.submitData()` 调 DocumentPipeline

文件：`backend/src/main/java/com/scfx/controller/CollectorController.java`

在第 201 行（`vectorTaskService.triggerCategory()` 之前），加上：

```java
// 6. 触发切片管道
if (kb.getId() != null) {
    documentPipeline.start(kb.getId());
}
```

`DocumentPipeline` 内部会：合规检查 → TextSplitter 切片 → 写入 `t_knowledge_chunk` → 更新 `chunk_count`。

#### 改动 2：DocumentPipeline 异步 + 失败重试 + 完成后串联 Qdrant 同步

文件：`backend/src/main/java/com/scfx/service/DocumentPipeline.java`

`DocumentPipeline` 已是 `@Async("chunkExecutor")` 异步执行，但失败后仅标记 `failed`，没有重试。改动：

1. 将核心逻辑抽取为 `processKnowledge(knowledgeId)`，外层 `start()` 用 while 循环包装重试
2. 指数退避：10s → 20s → 40s，最多 3 次
3. 重试耗尽后调用 `markFailed()` 标记永久失败（不阻塞主流程）
4. `createTask()` 增加重试分支：检测到已有 task 时重置为 `processing` 状态
5. 在 `completeTask()` 末尾追加 HTTP 调用 Python 服务的 `revectorize` 接口，完成后自动同步 Qdrant

```java
private static final int MAX_RETRIES = 3;

@Async("chunkExecutor")
public void start(Long knowledgeId) {
    int attempt = 0;
    while (attempt <= MAX_RETRIES) {
        try {
            processKnowledge(knowledgeId);
            return;
        } catch (Exception e) {
            attempt++;
            if (attempt > MAX_RETRIES) {
                markFailed(knowledgeId, e);
                return;
            }
            long delay = (long) Math.pow(2, attempt - 1) * 10_000;
            log.warn("文档处理失败，{}秒后重试 {}/{}: knowledgeId={}",
                delay / 1000, attempt, MAX_RETRIES, knowledgeId, e);
            try { Thread.sleep(delay); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                markFailed(knowledgeId, e);
                return;
            }
        }
    }
}

// completeTask() 末尾追加：
@Value("${app.ai-qa-service.url}")
private String aiQaServiceUrl;
private final RestTemplate restTemplate = new RestTemplate();

// completeTask() 末尾：
try {
    String url = aiQaServiceUrl + "/api/knowledge/" + kb.getId() + "/revectorize";
    restTemplate.postForEntity(url, null, String.class);
    log.info("Qdrant 同步触发: knowledgeId={}", kb.getId());
} catch (Exception e) {
    log.warn("Qdrant 同步失败（不影响主流程）: knowledgeId={}", kb.getId(), e);
}
```

设计要点：
- 重试在 `@Async` 线程内串行执行（`Thread.sleep` 退避），不额外创建线程
- `processKnowledge()` 每次从 DB 重新读取 KB 和 Task，避免重试时使用过期对象
- Qdrant 同步失败只记 warn，不阻塞 DocumentPipeline 的 `completeTask`

#### 改动 3：`/manual` 和 `/revectorize` 也触发 DocumentPipeline

文件：`backend/src/main/java/com/scfx/controller/KnowledgeBaseController.java`

`manualAdd()`（第 265 行附近）增加：

```java
documentPipeline.start(kb.getId());
```

`revectorize()`（第 131 行附近）增加：

```java
documentPipeline.start(kb.getId());
```

#### Phase 1 自检清单

- [ ] 采集数据→详情页显示正确切片数（不再为 0）
- [ ] 采集数据→AI 问答可用（Qdrant 有数据）
- [ ] 上传文档→AI 问答可用
- [ ] 人工录入→详情页显示切片数 + AI 问答可用
- [ ] Qdrant 同步失败不阻塞主流程

---

### Phase 2：统一切片算法（消除两套算法的差异）

#### 改动 4：Java TextSplitter 增加表格原子块支持

文件：`backend/src/main/java/com/scfx/util/TextSplitter.java`

新增方法：

```java
/**
 * 分离文本段和表格段（表格为原子块，永不拆分）。
 * 优先通过 <!--TABLE_MARKER_N--><!--TABLE_MARKER_END_N--> 标记识别。
 */
public static List<Segment> splitSegments(String content) { ... }

/**
 * 将 pipe 表格改写为语义化自然语言描述（与 Python semantic_rewrite_table 逻辑一致）。
 */
public static String rewriteTable(String tableText) { ... }
```

`TextSplitter.split()` 改为先调用 `splitSegments()` 分离表格/文本段，表格段作为原子块 `new Chunk(text, index, ...)` 直接加入结果，文本段走现有分层分割逻辑。

设计要点：
- 表格标记 `<!--TABLE_MARKER_N-->` 和 `<!--TABLE_MARKER_END_N-->` 已在采集数据中由 liangxin.py 写入（见结构化表格数据计划），Java 端直接使用
- 无标记的旧数据降级为 pipe 行启发式检测（参考 Python `_split_by_heuristic`）
- 改写逻辑与 Python `semantic_rewrite_table()` 保持一致：≤20 行全量展开，>20 行前 15 行详细 + 数值列统计摘要

#### 改动 5：Python revectorize 从 t_knowledge_chunk 读切片

文件：`ai-qa-service/app/api/knowledge.py`

`revectorize` 函数修改：

```python
@router.post("/{kb_id}/revectorize")
async def revectorize(kb_id: int):
    # 1. 从 t_knowledge_chunk 读取切片
    chunks = execute_query(
        "SELECT content, chunk_index, is_summary FROM t_knowledge_chunk "
        "WHERE knowledge_id = %s AND is_active = 1 ORDER BY chunk_index",
        (kb_id,)
    )

    # 2. 如果没有切片（短文本 < 500 字），全文作为单一切片
    if not chunks:
        item = execute_query("SELECT content FROM t_knowledge_base WHERE id = %s", (kb_id,))
        if not item:
            raise HTTPException(404, "Knowledge not found")
        chunks = [{"content": item[0]["content"], "chunk_index": 0, "is_summary": 1}]

    # 3. 删除旧 Qdrant 向量（如有）
    old_ids = execute_query("SELECT vector_ids FROM t_knowledge_base WHERE id = %s", (kb_id,))
    if old_ids and old_ids[0].get("vector_ids"):
        delete_vectors(old_ids[0]["vector_ids"])

    # 4. 逐片 embed → Qdrant（payload 含 chunk_index 以便追溯）
    vector_ids = store_chunk_vectors(
        kb_id=kb_id,
        chunks=chunks,
        ...
    )

    # 5. 回写 vector_ids
    execute_update(
        "UPDATE t_knowledge_base SET vector_ids = %s WHERE id = %s",
        (vector_ids, kb_id)
    )
```

注意：需保留向后兼容——当 `t_knowledge_chunk` 无数据时（旧记录），降级为原有的 `chunk_text()` 逻辑。

#### Phase 2 自检清单

- [ ] 含表格的知识在 Java 和 Python 端切片边界完全一致
- [ ] Python revectorize 从 `t_knowledge_chunk` 读取而非自行 chunk
- [ ] 旧数据（无 `t_knowledge_chunk` 行）正常降级
- [ ] 表格语义改写在两端输出一致
- [ ] 可以删除 Python `chunk_text()` 的大部分代码

---

### Phase 3：代码清理（长期）

1. 移除 Python `app/services/chunker.py` 中不再使用的函数（`chunk_text` 核心逻辑，保留 `semantic_rewrite_table` 作为参考实现）
2. 移除 Python `app/api/knowledge.py` 中的 `/ingest` 端点（已被 Java `CollectorController` 替代）
3. `t_knowledge_base.vector_ids` 改为可选缓存——丢失可从 `t_knowledge_chunk` 重建
4. 考虑将 Java TextSplitter 的表格改写逻辑抽取为公共工具类（`TableRewriter.java`），便于单测

---

## 5. 风险与应对

| 风险 | 影响 | 应对 |
|---|---|---|
| DocumentPipeline 阻塞采集响应 | 采集变慢 | `start()` 已是 `@Async`，不会阻塞 HTTP 响应 |
| Qdrant 同步失败 | AI 问答暂时无新数据 | 只记录 warn，不阻塞主流程；下次 revectorize 可补 |
| Python 从 t_knowledge_chunk 读切片 → 旧数据无 chunk | 旧知识 Qdrant 被清空 | 降级：无 chunk 时走原有的 `chunk_text()` |
| TextSplitter 增加表格支持 → 边界情况 | 表格被错误拆分 | 优先用 marker 精确匹配，旧数据用启发式兜底 |
| /manual 没有 contentHtml 或 tableMeta | 表格检测精度下降 | 无标记时整篇按文本处理，不报错 |

---

## 6. 文件改动清单

| # | 文件 | 操作 | Phase |
|---|---|---|---|
| 1 | `CollectorController.java` | 增加 `documentPipeline.start()` | P1 |
| 2 | `DocumentPipeline.java` | `completeTask()` 追加 HTTP 调 Python revectorize | P1 |
| 3 | `KnowledgeBaseController.java` | `manualAdd()` + `revectorize()` 增加 `documentPipeline.start()` | P1 |
| 4 | `TextSplitter.java` | 增加 `splitSegments()` + `rewriteTable()` 表格原子块支持 | P2 |
| 5 | `KnowledgeChunk.java` | 可选：新增 `chunkType` 字段（text/table） | P2 |
| 6 | `knowledge.py` (Python) | `revectorize` 改从 `t_knowledge_chunk` 读切片 | P2 |
| 7 | `chunker.py` (Python) | 移除 `chunk_text()` 等不再使用的函数 | P3 |
| 8 | `knowledge.py` (Python) | 移除 `/ingest` 端点 | P3 |

---

## 7. 附录：当前两条管道的调用链路全图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        前端                                             │
│  KnowledgeDetail.vue     Knowledge.vue         AiChat.vue              │
│  (切片展示 chunkCount)   (列表展示)            (AI问答)                │
└────────┬────────────────────┬────────────────────┬──────────────────────┘
         │ GET /knowledge/{id} │ GET /knowledge/list │ POST /ai-chat/stream
         ▼                    ▼                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    Spring Boot 后端 (:8080)                              │
│                                                                         │
│  KnowledgeBaseController     CollectorController    AiChatProxyController│
│    /knowledge/{id}            /collector/exec/{id}    /ai-chat/stream   │
│    /knowledge/upload           /data                                     │
│    /knowledge/manual                                                    │
│         │                        │                                      │
│         ▼                        ▼                                      │
│  DocumentPipeline          vectorTaskService       → localhost:5002     │
│  (chunk → t_knowledge)     (BGE-M3 + DashScope)   (SSE proxy)          │
│         │                        │                                      │
│         ▼                        ▼                                      │
│  t_knowledge_chunk         t_knowledge_viz                              │
│  chunk_count ↑              retrieval_vector                             │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
         │                                            │
         ▼                                            ▼
┌──────────────────────┐              ┌────────────────────────────────────┐
│     MySQL            │              │    Python ai-qa-service (:5002)    │
│  t_knowledge_base    │              │                                    │
│  t_knowledge_chunk   │◄─读切片─────│  revectorize / chat/stream         │
│  t_knowledge_viz     │              │                                    │
│                      │              │  search_vectors → Qdrant           │
│                      │              │  generate_answer → LLM API         │
└──────────────────────┘              └────────────────────────────────────┘
```

当前链路中断处：
- `CollectorController → DocumentPipeline`：❌ 缺失（Phase 1 修复）
- `DocumentPipeline → Qdrant sync`：❌ 缺失（Phase 1 修复）
- `revectorize → t_knowledge_chunk`：❌ 读自己的 chunk（Phase 2 修复）
