# 采集内容切片实施计划

## 背景
当前 liangxin 等采集来源的知识（如玉米晨报）直接整篇向量化，不做切片，导致语义搜索时无法精确匹配段落。需对 ≥500 字的内容统一执行切片后再向量化。

## 切片参数（最终固化）

| 参数 | 值 | 说明 |
|------|-----|------|
| chunk_size | 512 tokens (~768 字符) | 匹配单业务板块体量 |
| chunk_overlap | 64 tokens (~96 字符) | 首切片 32 tokens，其余 64 |
| min_chunk_size | 100 tokens (~150 字符) | 不足则向前合并 |
| 触发阈值 | ≥500 字符 | 500±30 容错区间 |
| 最大切片数 | 10 | 超限强制合并尾片 |

## 分割策略（语义优先）

```
优先级 1: 按 \n\n 分割 → 保住业务结构（行情/政策/区域/总结）
优先级 2: 超长段落按 。？！\n 分割 → 保住句子完整
优先级 3: 单句超 chunk_size → 按 ，,;：分割 → 保住短语完整
兜底:  无标点长文本 → 硬切至 chunk_size
```

## 合并规则

- 任何切片 < minChunk → 向前合并到上一个切片
- 从后向前扫描，确保所有切片（除首片外）均达标
- 首切片永不向前合并（全文语义入口）
- 最大切片数 10，超限时前 9 片不变，剩余全部合并为最后一笔

## Overlap 边界约束

- 首切片 overlap=32 tokens，其余=64 tokens
- overlap 允许跨段落/章节（设计意图——保持上下文连续性）
- 跨章节 overlap 不会引入语义错误：章节衔接处本就是上下文相关的

## 数据模型

t_knowledge_chunk 新增字段：
- chunk_total — 所属文档总切片数
- start_offset — 在原文中的起始字符偏移
- end_offset — 在原文中的结束字符偏移
- is_summary — 1=首切片（摘要）, 0=普通切片

## 改动范围
| 文件 | 操作 | 说明 |
|------|------|------|
| `entity/KnowledgeChunk.java` | 修改 | 加 chunkTotal, startOffset, endOffset, isSummary |
| `resources/schema.sql` | 修改 | t_knowledge_chunk 加 4 列 |
| `util/TextSplitter.java` | 新建 | 分层分割器（段落→句子→短语→硬切四级兜底） |
| `service/impl/VectorTaskServiceImpl.java` | 修改 | computeBgeM3 插入切片 + 逐片串行向量化 |
| `service/impl/KnowledgeBaseServiceImpl.java` | 修改 | removeWithViz 级联删除切片（软删除 is_active=0） |
| `resources/migration-V2-chunk-fields.sql` | 新建 | 数据库迁移脚本 |

## 核心逻辑
```
content ≥ 500字  → TextSplitter.split() → N个切片 → 逐片向量化
content < 500字  → 保持原流程，整篇向量化
```

## 级联删除
删除知识时（`DELETE /knowledge/{id}`），`removeWithViz` 串联执行：
1. `vectorStore.deleteVector(id)` — 清理向量数据
2. `t_knowledge_chunk` 软删除（`is_active=0`） — 清理切片
3. 清空 `viz_x/y/z` — 清理可视化坐标
4. `super.removeById(id)` — 删除主表记录

更新/重新向量化时：先在 `computeBgeM3` 中软删除旧切片，再插入新切片。

## 存量数据
存量 35 条全文向量数据暂不迁移。决策依据：
- 存量极小，不影响主流程
- 新架构稳定后，写一次性 job 重跑即可
- 避免上线复杂度

## 检索聚合设计（暂未实现，文档先行）

### 多路召回策略

| 数据类型 | 召回方式 | 权重 |
|---------|---------|------|
| 切片文档（有 chunks） | 搜 `t_knowledge_chunk` 向量 | summary 切片 ×1.1，普通切片 ×1.0 |
| 非切片文档（chunk_count=0） | 搜 `t_knowledge_viz` 全文向量 | ×1.0（退化为原全文检索） |

两种类型统一接口：外部调用者无需感知切片存在。

### 分数聚合规则

单文档多切片命中时，取 **MAX score**（最大相似度分数）：

```
文档 A 的切片命中了:
  切片0 (summary): score=0.87 → ×1.1 = 0.957
  切片2 (细节):    score=0.92 → ×1.0 = 0.920
文档 A 最终得分 = max(0.957, 0.920) = 0.957
```

理由：逻辑简单、排序稳定，符合"只要有一段高度匹配就优先展示全文"的业务诉求。

### 截断策略

- 每篇文档：不限切片匹配数（聚合发生在服务端，不传切片列表给前端）
- 每次查询：返回 Top-K 文档（推荐 K=20）
- K 的选取不影响召回精度，只影响展示长度

### 去重与过滤

| 风险 | 策略 |
|------|------|
| 重叠区域内容重复召回 | 无害——按 doc_id 聚合，只返回一篇文档。两个相邻切片同时高分说明该段落确实相关 |
| 切片与非切片混合召回 | 以 doc_id 分组统一排序，MAX score 聚合，同一文档不重复出现 |
| 短文本（<500 字）统一 | chunk_count=0 的文档，搜全文向量作为伪切片参与排序，与切片文档在同一分数标尺下 |

### 前端联动（未来实现）

- 后端返回数据中含 `matched_chunk`：`{ chunk_index, start_offset, end_offset, score }`
- 前端利用 `start_offset / end_offset` 实现原文段落高亮
- 无切片的文档，`matched_chunk` 返回 `{ start_offset:0, end_offset: content.length }` 全段标记

## 工程 & 性能 & 运维

### 批量处理压力防护（全部已实现）

| 风险 | 防护策略 |
|------|---------|
| 多片向量化打爆模型 | `computeBgeM3` 逐片串行，不并发 |
| 采集高峰队列堆积 | `@Async` 线程池 core=2, max=5, queue=50 + `CallerRunsPolicy` 背压 |
| 超长文档无限切片 | `TextSplitter.MAX_CHUNKS = 10` |
| 单次推理失败 | 每片 3 次重试，2^retry 秒退避 |

### 异常兜底（全部已实现）

| 场景 | 策略 |
|------|------|
| 切片/向量化失败 | 整篇标记 `failed`，下次触及时重试（旧切片已软删，重新切片） |
| 部分切片失败 | 全有或全无——任一失败整篇失败（比部分成功更安全） |
| 原文更新 | `computeBgeM3` 先软删旧切片 → 重新切片 → 逐片向量化 |
| 原文删除 | `removeWithViz` 级联：删向量 → 软删切片 → 清 viz → 删主表 |
| 超大文本 | 10 片上限，超限尾片强制合并 |

### 监控指标

见 `KnowledgeStatsController` 的 `/knowledge/stats` 端点。

## 业务特殊场景

### 原文二次编辑（已实现）

`PUT /knowledge/{id}` 修改 `content` 后，异步触发 `processSingle` 重新切片+向量化：

1. 旧切片软删除（`is_active=0`）
2. 新内容 → `TextSplitter.split()` → 新切片 → 逐片向量化

不修改 `content` 的编辑（title/source/author）不触发重新向量化。

### 重复内容 / 相似资讯

- 同文档内：被 `knowledge_id` 聚合 + MAX score 天然消重
- 跨文档间：相似切片各自独立存在，搜索时各文档各自计分，不额外去重（这是搜索系统的正确定义——内容相似就应该都被召回）
- 未来可加：相似度去重后置过滤器（当前无此需求）

### 关键词检索（全文检索）

切片逻辑**不影响**现有的关键词检索。原有 `GET /knowledge/list` 按 sourceType/vectorStatus/categoryId 过滤的功能不受任何影响。关键词检索和向量检索是两条独立路径。

## 执行顺序
1. TextSplitter 工具类 ✅
2. 数据库加字段 ✅
3. 向量化集成（VectorTaskServiceImpl） ✅
4. 级联删除（KnowledgeBaseServiceImpl） ✅
5. 检索聚合（KnowledgeSearchService） ✅
6. 搜索端点（KnowledgeBaseController POST /search） ✅
7. 监控端点（KnowledgeStatsController GET /knowledge/stats） ✅
