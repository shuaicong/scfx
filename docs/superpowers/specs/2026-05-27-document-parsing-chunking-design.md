# 文档解析切片与向量化设计

**日期：** 2026-05-27
**状态：** 设计稿 v1.1

## 1. 背景与目标

### 1.1 现状

当前系统只存储文档元数据（标题、状态、文件路径等），未对 Word/PDF/文本文件做内容解析和切片。每条 `t_knowledge_base` 记录整体作为一条知识，整个文本内容作为一个单元输入嵌入模型生成单一向量。长文档的语义被压缩到单个向量中，检索精度受限。

### 1.2 目标

实现完整的「上传 → 解析 → 切片 → 向量化 → 检索」管道：

- 用户上传 PDF/Word/TXT 等格式文档
- Tika 自动解析出纯文本
- 按规则切分成语义块（Chunk）
- 每个切片独立生成 BGE-M3 向量
- 存储切片及向量，支持语义检索
- 前端展示切片卡片

### 1.3 非目标（MVP 范围外）

- **OCR 识别**：假设文档均为数字化生成（非扫描件），不做 OCR
- **文档更新/替换**：用户需删除后重新上传，不做增量更新
- **单切片编辑/删除**：仅提供只读查看，编辑/删除需删除整个文档重传
- **切片级可视化**：PCA/MDS 散点图仅展示文档级坐标，不展示切片

## 2. 架构总览

```
┌─ 用户上传 ─────────────────────────────────────────────────┐
│  Knowledge.vue 上传对话框                                   │
│  ├─ 双层进度条：上传进度 + 处理进度                         │
│  └─ POST /knowledge/upload (FormData)                      │
└─────────────────────────────────────┬──────────────────────┘
                                      │
                                      ▼
┌─ KnowledgeBaseController ──────────────────────────────────┐
│  1. FileStorageService.save(file) → 磁盘                   │
│  2. t_knowledge_base 记录创建（content 暂空）               │
│  3. t_knowledge_task 记录创建（status = pending）           │
│  4. 触发 @Async DocumentPipeline.start(knowledgeId)        │
└─────────────────────────────────────┬──────────────────────┘
                                      │
                                      ▼
┌─ DocumentPipeline（3 阶段异步 + 完成回调） ──────────────────┐
│                                                             │
│  Step 1: parsing                                            │
│  ├─ Tika.parseToString(file) → plainText                   │
│  ├─ 存入 t_knowledge_base.content                          │
│  ├─ 更新任务状态: parsing                                   │
│  └─ 完成后进入 chunking                                     │
│                                                             │
│  Step 2: chunking                                           │
│  ├─ TextSplitter.split(content) → List<ChunkSegment>       │
│  ├─ 批量 INSERT t_knowledge_chunk                          │
│  ├─ t_knowledge_base.chunk_count = N                       │
│  ├─ 更新任务状态: chunking                                  │
│  └─ 完成后进入 vectorizing                                  │
│                                                             │
│  Step 3: vectorizing                                        │
│  ├─ 更新任务状态: vectorizing                               │
│  ├─ FOR each chunk:                                         │
│  │   ├─ BGE-M3 embedding API call                          │
│  │   └─ 存储 vector_bge_m3 + vector_id                     │
│  ├─ 全部完成后 → complete()                                 │
│  │   ├─ t_knowledge_base.vector_status = 'vectorized'      │
│  │   └─ 任务状态: completed                                 │
│  └─ 部分失败 → 切片标记 failed，其他正常完成                │
│                                                             │
│  任一步骤失败 → 任务状态 = failed + error_message            │
└─────────────────────────────────────┬──────────────────────┘
                                      │
                                      ▼
┌─ 数据存储 ──────────────────────────────────────────────────┐
│  t_knowledge_base（文档元数据）    磁盘（源文件 + 预览）      │
│  t_knowledge_chunk（切片 + 向量）                           │
│  t_knowledge_task（任务状态）                                │
└──────┬──────────────────────────────────┬──────────────────┘
       │                                  │
       ▼                                  ▼
┌─ 切片检索 ──────────────────┐  ┌─ 前端展示 ───────────────┐
│  POST /knowledge/search     │  │ 文档列表（状态 + 查看切片）│
│  query → BGE-M3 embed →    │  │ └─ el-drawer 600px        │
│  余弦相似度 → top-K 结果    │  │   └─ 切片卡片（虚拟滚动）  │
└─────────────────────────────┘  └──────────────────────────┘
```

## 3. 核心数据模型

### 3.1 `t_knowledge_chunk` — 文档切片表

```sql
CREATE TABLE t_knowledge_chunk (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    knowledge_id    BIGINT NOT NULL,              -- FK → t_knowledge_base.id
    category_id     BIGINT NOT NULL,              -- 冗余，便于按分类查询
    chunk_index     INT NOT NULL,                 -- 切片在文档内的序号（从 0 开始）
    content         TEXT NOT NULL,                 -- 切片纯文本
    token_count     INT DEFAULT 0,                -- 切片 token 数（BGE-M3 API usage 回填）
    vector_status   VARCHAR(20) DEFAULT 'pending', -- pending / processing / vectorized / failed
    vector_id       VARCHAR(100),                  -- BGE-M3 返回的向量 ID（硅基流动侧引用）
    vector_bge_m3   BLOB,                           -- BGE-M3 1024 维完整向量（约 4096 字节，用 BLOB 避免长度硬上限）
    error_message   TEXT,                          -- 向量化失败原因
    is_active       TINYINT DEFAULT 1,             -- 1=正常, 0=已删除（软删除，支持审计与重试回溯）
    content_terms   TEXT,                          -- 保留字段：未来用于全文检索的关键词/分词结果
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_knowledge (knowledge_id),
    INDEX idx_category_vector (category_id, vector_status),
    INDEX idx_knowledge_active (knowledge_id, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 3.2 `t_knowledge_task` — 文档处理任务状态表

```sql
CREATE TABLE t_knowledge_task (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    knowledge_id      BIGINT NOT NULL,            -- FK → t_knowledge_base.id
    category_id       BIGINT NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'pending',
                      -- pending → parsing → chunking → vectorizing → completed
                      -- 任一步骤失败 → failed
    current_step      VARCHAR(20),                 -- parsing / chunking / vectorizing
    progress          INT DEFAULT 0,               -- 0-100 整体进度
    error_message     TEXT,                        -- 失败时详细错误
    total_chunks      INT DEFAULT 0,               -- 切片总数（chunking 后回填）
    processed_chunks  INT DEFAULT 0,               -- 已向量化切片数
    retry_count       INT DEFAULT 0,               -- 重试次数
    file_size         BIGINT DEFAULT 0,            -- 源文件大小（字节）
    file_type         VARCHAR(20),                 -- pdf / docx / txt / md
    created_by        VARCHAR(50),
    updated_by        VARCHAR(50),
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_knowledge (knowledge_id),
    INDEX idx_category_status (category_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 3.3 `t_knowledge_base` — 最小 Schema 变更

现有字段复用：
- `content`（LONGTEXT）复用为解析后纯文本的存储位置
- `file_path` 复用于存储上传文件的磁盘路径
- `file_type` 复用于存储文件扩展名
- `chunk_count` 在切片完成后回填实际切片数

**新增列：**
```sql
ALTER TABLE t_knowledge_base
    ADD COLUMN file_md5 VARCHAR(64) AFTER file_type,
    ADD INDEX idx_md5_category (file_md5, category_id);
```

`file_md5` 用于上传去重（详见 8.5 节），仅存储不做查询过滤。`(file_md5, category_id)` 索引覆盖去重查询。

### 3.4 级联删除规则

删除文档时必须清理所有关联数据。切片使用**软删除**（保留审计追溯能力）：

```java
// 删除文档逻辑（KnowledgeBaseController 或 Service 层）
// 1. 软删除切片（保留数据供审计 / 未来恢复）
knowledgeChunkMapper.updateByKnowledgeId(
    id, new LambdaUpdateWrapper<KnowledgeChunk>()
        .set(KnowledgeChunk::getIsActive, 0));

// 2. 硬删除任务（任务状态无保留价值，减少数据膨胀）
knowledgeTaskMapper.deleteByKnowledgeId(id);

// 3. 删除磁盘文件
fileStorageService.delete(kb.getFilePath());
```

**硬删除 vs 软删除的选择依据：**

| 表 | 删除方式 | 原因 |
|----|---------|------|
| `t_knowledge_chunk` | 软删除（`is_active=0`） | 重试时保留旧数据供排查，已删除文档的切片在搜索中通过 `is_active=1` 过滤 |
| `t_knowledge_task` | 硬删除 | 任务记录无保留价值，减少数据膨胀 |
| `t_knowledge_base` | 软删除（已有 `deleted` 字段） | 保留元数据供关联查询 |

**切片软删除后的检索过滤：** 所有查询 `t_knowledge_chunk` 的地方必须加 `is_active = 1` 条件（详见 7.6 节）。

**硬删除 vs 软删除对比：**

### 3.5 数据库批量操作规范

**批量插入分片（防大事务）：**

```java
public void batchInsert(List<KnowledgeChunk> chunks) {
    int batchSize = 200;
    for (int i = 0; i < chunks.size(); i += batchSize) {
        int end = Math.min(i + batchSize, chunks.size());
        knowledgeChunkMapper.insertBatch(chunks.subList(i, end));
    }
}
```

每批 200 条，避免单条 SQL 过大导致主从延迟和锁竞争。

**切片状态批量更新（替代逐条 UPDATE）：**

```java
@Transactional
public void batchUpdateStatus(List<KnowledgeChunk> chunks) {
    int batchSize = 200;
    for (int i = 0; i < chunks.size(); i += batchSize) {
        int end = Math.min(i + batchSize, chunks.size());
        knowledgeChunkMapper.batchUpdateStatus(chunks.subList(i, end));
    }
}
```

```xml
<update id="batchUpdateStatus" parameterType="list">
    <foreach collection="list" item="c" separator=";">
        UPDATE t_knowledge_chunk
        SET vector_status = #{c.vectorStatus},
            vector_id = #{c.vectorId},
            vector_bge_m3 = #{c.vectorBgeM3},
            token_count = #{c.tokenCount},
            error_message = #{c.errorMessage}
        WHERE id = #{c.id}
    </foreach>
</update>
```

**架构扩展预留：**
- `t_knowledge_chunk` 按 `knowledge_id` 分表（hash 分片）
- 按时间归档完成的任务（`t_knowledge_task`）

### 3.6 接口幂等设计

上传和重试接口使用幂等键防重复处理：

```java
@PostMapping("/upload")
public Result<?> upload(
        @RequestParam("file") MultipartFile file,
        @RequestParam("idempotentKey") String idempotentKey,  // 前端生成 UUID
        ...) {
    // 幂等校验：同一 key 已处理过 → 返回已有结果
    KnowledgeTask existing = knowledgeTaskMapper.selectByIdempotentKey(idempotentKey);
    if (existing != null) {
        return Result.success(existing);  // 返回已有任务
    }
    // ... 正常创建流程
}
```

**状态级幂等：** 任务处于 `parsing/chunking/vectorizing` 中时，直接拒绝二次触发：

```java
KnowledgeTask task = knowledgeTaskMapper.selectByKnowledgeId(knowledgeId);
if (List.of("parsing", "chunking", "vectorizing").contains(task.getStatus())) {
    return Result.error(400, "文档正在处理中，请勿重复操作");
}
```

**并发上传分布式锁：**

幂等键基于浏览器生成的 `idempotentKey`，但多用户同时上传同一文件（不同 idempotentKey）时仍可能并发创建重复任务。使用基于 `knowledgeId` 的分布式锁：

```java
@Transactional
public KnowledgeTask createTask(Long knowledgeId) {
    // 先检查是否已有进行中的任务
    KnowledgeTask existing = knowledgeTaskMapper.selectByKnowledgeIdAndStatusIn(
        knowledgeId, List.of("pending", "parsing", "chunking", "vectorizing"));
    if (existing != null) {
        throw new IllegalStateException("该文档正在处理中，请勿重复操作");
    }
    // 创建新任务
    KnowledgeTask task = new KnowledgeTask();
    task.setKnowledgeId(knowledgeId);
    task.setStatus("pending");
    knowledgeTaskMapper.insert(task);
    return task;
}
```

**多标签页防护：** 前端在上传前检查 `idempotentKey` 是否已在当前 session 中使用过。同一浏览器两标签页使用不同 idempotentKey 不受限，后端通过 `knowledgeId` 唯一约束兜底。

## 4. 解析引擎

### 4.1 技术选型

使用 **Apache Tika 2.9.2** 统一解析，tika-parsers-standard-package 内部已整合 POI（Word）、PDFBox（PDF）等解析器。

**Pom.xml 新增依赖：**

```xml
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-core</artifactId>
    <version>2.9.2</version>
</dependency>
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-parsers-standard-package</artifactId>
    <version>2.9.2</version>
</dependency>
```

### 4.2 使用方式

```java
// Tika 自动检测文件格式并选择合适的解析器
Tika tika = new Tika();
String content = tika.parseToString(inputStream);
```

一行代码覆盖 .pdf / .docx / .doc / .txt / .md / .pptx / .xlsx 等格式。自动识别 MIME 类型，出错时不会抛出空指针（Tika 有完善的异常处理）。

### 4.3 边界情况处理

| 场景 | 处理 |
|------|------|
| 空文件 | 解析后 content 为空 → chunking 步骤直接完成（0 切片）|
| 损坏文件 | Tika 抛异常 → 任务 failed，error_message 提示「文件格式不支持或已损坏」 |
| 超大文件（>20MB） | application.yml 限制 `max-file-size: 20MB`，超限在 Controller 层拦截返回错误 |
| 加密 PDF | Tika 无法解析 → failed，提示「文件已加密无法解析」 |
| 纯文本文件 | Tika 直接读取，UTF-8 自动检测编码 |
| 图片 PDF（无文字层） | 解析出空字符串或少量杂乱文字 → 有效文本判断 < 50 字 → 标记无效文档 |
| 乱码文件（编码问题） | Tika 解析出大量不可读字符 → 有效文本判断 < 50 字 → failed，提示「文档内容不可解析」|
| 空白/全符号文档 | 解析后内容无有效文字 → chunking 跳过，0 切片 |

**有效文本判断逻辑（解析完成后执行）：**

```java
/** 最小有效字符数：低于此值视为无效文档 */
private static final int MIN_MEANINGFUL_CHARS = 50;

// Parsing 步骤完成后调用
String content = tika.parseToString(file);
if (content == null || content.isBlank()) {
    completeTask(knowledgeId, 0);  // 0 切片，正常完成
    return;
}
long meaningfulChars = countMeaningfulChars(content);
if (meaningfulChars < MIN_MEANINGFUL_CHARS) {
    failTask(knowledgeId, "文档无可解析的有效文本内容（有效字符 < " + MIN_MEANINGFUL_CHARS + " 字）");
    return;
}

/**
 * 统计有效字符数：排除空白、标点、控制字符后，仅计字母（含中日韩）和数字
 */
long countMeaningfulChars(String text) {
    return text.chars()
        .filter(c -> Character.isLetter(c) || Character.isDigit(c))
        .count();
}
```

### 4.4 敏感内容过滤

**合规要求：** 上传文档必须经过基本的安全审查，防止违规内容入库。

**MVP 最小实现（简单敏感词过滤 + 日志留存）：**

```java
@Component
public class ContentFilter {

    /** 最小有效字符数，低于此值视为无效文档 */
    private static final int MIN_MEANINGFUL_CHARS = 50;

    /** 敏感词正则列表，从配置加载 */
    private List<Pattern> sensitivePatterns;

    public ContentFilter(@Value("${app.content-filter.sensitive-words:}") String words) {
        this.sensitivePatterns = words.isBlank() ? List.of()
            : Arrays.stream(words.split(","))
                .map(w -> Pattern.compile("(?i)" + Pattern.quote(w.trim())))
                .collect(Collectors.toList());
    }

    /** 检查内容是否合规。返回 null = 通过，返回字符串 = 违规原因 */
    public String check(String content) {
        if (content == null || content.isBlank()) return null;

        // 1. 有效文本长度判断
        long meaningful = content.chars()
            .filter(c -> Character.isLetter(c) || Character.isDigit(c))
            .count();
        if (meaningful < MIN_MEANINGFUL_CHARS) {
            return "文档无可解析的有效文本内容（有效字符 < " + MIN_MEANINGFUL_CHARS + " 字）";
        }

        // 2. 敏感词匹配
        for (Pattern p : sensitivePatterns) {
            if (p.matcher(content).find()) {
                return "文档包含违规内容，已拦截";
            }
        }
        return null;  // 通过
    }
}
```

**设计要点：**
- 敏感词列表从 `application.yml` 加载（`app.content-filter.sensitive-words`），初始为空列表，业务方自行配置
- 使用 `Pattern.quote` 防止特殊字符导致正则异常
- 文档内容解析完成后 → `ContentFilter.check(content)` → 违规则任务标记 `failed` + 提示原因
- **不存储违规文档原文**：`t_knowledge_base.content` 置空，仅保留文件名和元数据
- 操作日志记录：用户 ID、文件名、触发规则、时间

**文档内容合规处理规则：**

| 检测结果 | 任务状态 | content 存储 | 前端提示 |
|---------|---------|-------------|---------|
| 通过 | 继续执行 | 正常存储 | — |
| 违规 | failed | content 清空，仅存文件名 | 「文档包含违规内容，已拦截」 |
| 无效文本 | failed | content 清空 | 「文档无可解析的有效文本内容」 |

**后续优化（MVP 后）：**
- 对接第三方内容审核 API（阿里云内容安全、百度大脑等）
- 图片审核（扫描件场景）
- 审核记录持久化，支持人工复审

## 5. 切片策略（TextSplitter）

### 5.1 算法 — 语义优先切片

**设计原则：** 优先按自然语义边界（段落/换行）切分，保留段落完整性；仅对超长段落做 Token 二次切分。

**输入保护（防 OOM / 防大量切片）：**
- 文本长度上限 `max-text-chars: 500000`（约 50 万字符，超限自动截断并日志告警）
- 切片数上限 `max-chunks: 500`（超限时截断后段，保留前 N 片）
- 单段最大字符数 `max-chunk-chars: 3000`（超限段落强制二次切分）

```
语义优先切分流程：

原始文本
    │
    ▼
Step 1: 按段落边界切分（连续换行符 \n\n 或单个 \n）
    │
    ├── 段落 ≤ maxTokens → 直接作为候选片段
    │
    └── 段落 > maxTokens → 进入 Step 2
            │
            ▼
    Step 2: 按 Token 固定窗口二次切分
            ├── 每片 maxTokens，重叠 overlapTokens
            └── 保持句子完整（回退到最近的句号/换行）
                    │
                    ▼
    所有候选片段 → 合并超短片段（< 50 tokens）到上一片 → 返回 List<ChunkSegment>
```

```java
@Component
public class TextSplitter {

    /** BGE-M3 窗口 512，留 10% 给系统提示，实际用 460 */
    @Value("${chunk.max-tokens:460}")
    private int maxTokens;

    /** 相邻切片重叠 50 token */
    @Value("${chunk.overlap-tokens:50}")
    private int overlapTokens;

    /** 单文档最大字符数，超限截断 */
    @Value("${chunk.max-text-chars:500000}")
    private int maxTextChars;

    /** 单文档最大切片数，超限截断 */
    @Value("${chunk.max-chunks:500}")
    private int maxChunks;

    /** 单段最大字符数，超限段落强制二次切分 */
    @Value("${chunk.max-chunk-chars:3000}")
    private int maxChunkChars;

    public List<ChunkSegment> split(String text) {
        // 0. 长度保护
        if (text.length() > maxTextChars) {
            log.warn("文本长度超限，截断处理 | 原始长度={} | 上限={}",
                text.length(), maxTextChars);
            text = text.substring(0, maxTextChars);
        }

        // 1. 按段落边界切分（连续换行符或单个换行）
        String[] paragraphs = text.split("\\n\\n+|\\n");

        // 2. 每段独立处理：短段落直接保留，长段落 Token 二次切分
        List<ChunkSegment> segments = new ArrayList<>();
        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;

            int estimatedTokens = estimateTokenCount(trimmed);
            if (estimatedTokens <= maxTokens) {
                segments.add(new ChunkSegment(trimmed, estimatedTokens, segments.size()));
            } else {
                // 超长段落：按 Token 固定窗口二次切分
                segments.addAll(splitLongParagraph(trimmed, segments.size()));
            }
        }

        // 3. 合并超短片段（< 50 tokens）到上一片
        segments = mergeShortSegments(segments);

        // 4. 切片数保护
        if (segments.size() > maxChunks) {
            log.warn("切片数超限，截断处理 | 原始切片数={} | 上限={}",
                segments.size(), maxChunks);
            segments = segments.subList(0, maxChunks);
        }

        return segments;
    }

    private List<ChunkSegment> splitLongParagraph(String text, int startIndex) {
        List<ChunkSegment> result = new ArrayList<>();
        int start = 0;
        int idx = startIndex;
        while (start < text.length()) {
            int end = Math.min(start + maxChunkChars, text.length());
            // 回退到最近的句号/换行
            if (end < text.length()) {
                int backtrack = Math.max(
                    text.lastIndexOf('。', end),
                    Math.max(text.lastIndexOf('.', end),
                             text.lastIndexOf('\n', end)));
                if (backtrack > start + maxChunkChars / 2) {
                    end = backtrack + 1;
                }
            }
            String segment = text.substring(start, end).trim();
            if (!segment.isEmpty()) {
                result.add(new ChunkSegment(segment, estimateTokenCount(segment), idx++));
            }
            start = end;
        }
        return result;
    }

    private List<ChunkSegment> mergeShortSegments(List<ChunkSegment> segments) {
        if (segments.isEmpty()) return segments;
        List<ChunkSegment> merged = new ArrayList<>();
        ChunkSegment current = segments.get(0);
        for (int i = 1; i < segments.size(); i++) {
            ChunkSegment next = segments.get(i);
            if (current.getTokenCount() < 50 && current.getTokenCount() + next.getTokenCount() <= maxTokens) {
                // 合并到上一片
                String mergedContent = current.getContent() + "\n" + next.getContent();
                int mergedTokens = estimateTokenCount(mergedContent);
                current = new ChunkSegment(mergedContent, mergedTokens, current.getIndex());
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        // 重排 index
        for (int i = 0; i < merged.size(); i++) {
            merged.get(i).setIndex(i);
        }
        return merged;
    }
}
```

**语义优先 vs 纯固定 Token 对比：**

| 维度 | 纯固定 Token | 语义优先（当前） |
|------|------------|----------------|
| 段落完整性 | ❌ 可能切断段落中间 | ✅ 优先保留段落边界 |
| 超长段处理 | ❌ 无差别截断 | ✅ Token 二次切分 + 句子回退 |
| 超短段处理 | ❌ 产生大量碎片切片 | ✅ 合并到上一段落 |
| 检索精度 | 中等（碎片干扰语义） | 较高（段落语义完整） |
| 实现复杂度 | 低 | 中 |

### 5.2 Token 估算（多语言支持）

不对 BGE-M3 做精确 tokenize（需加载完整 tokenizer 库），使用统一近似公式：

```
tokenCount = CJK 字符数 × 1.5 + 拉丁字符数 × 0.25 + 其他字符 × 0.5
```

**字符分类与权重：**

| 字符范围 | 分类 | 权重 | 包含语言 |
|---------|------|------|---------|
| U+4E00–U+9FFF 中日韩统一表意文字 | CJK | × 1.5 | 中文、日文汉字、韩文汉字 |
| U+3040–U+30FF 日文假名 | CJK | × 1.5 | 日文平假名、片假名 |
| U+31F0–U+31FF 假名扩展 | CJK | × 1.5 | 日文 |
| U+AC00–U+D7AF 韩文谚文 | CJK | × 1.5 | 韩文 |
| U+0000–U+007F ASCII 拉丁字母/数字/符号 | Latin | × 0.25 | 英文、数字、常见符号 |
| 其他 Unicode（西里尔、阿拉伯、泰文等） | 其他 | × 0.5 | 俄文、阿拉伯文、泰文等 |

```java
public int estimateTokenCount(String text) {
    long tokens = 0;
    for (int i = 0; i < text.length(); ) {
        int cp = text.codePointAt(i);
        int charCount = Character.charCount(cp);
        if (Character.isIdeographic(cp)
                || (cp >= 0x3040 && cp <= 0x30FF)
                || (cp >= 0xAC00 && cp <= 0xD7AF)) {
            tokens += (long) Math.ceil(1.5 * charCount);    // CJK
        } else if (cp < 0x0080) {
            tokens += (long) Math.ceil(0.25 * charCount);   // ASCII
        } else {
            tokens += (long) Math.ceil(0.5 * charCount);    // 其他
        }
        i += charCount;
    }
    return (int) Math.min(tokens, Integer.MAX_VALUE);
}
```

误差 ±15% 以内，对切片均匀性影响可忽略。

### 5.3 后续优化方向（MVP 后）

- 自适应重叠比例（短文档用 10%，长文档用 20%）
- Markdown 标题感知切分（识别 `#` / `##` 层级作为语义边界）
- 基于 Embedding 相似度的语义边界检测（跨段合并：两段语义相似度 > 0.85 时合并为一片）

## 6. 异步管道（DocumentPipeline）

### 6.1 状态机

```
pending ──→ 取消 → cancelled
   │
   ▼
parsing ──→ 失败 → failed
   │          │
   │          └──→ 取消 → cancelled
   ▼
chunking ──→ 失败 → failed
   │          │
   │          └──→ 取消 → cancelled
   ▼
vectorizing ──→ 失败 → failed
   │             │
   │             └──→ 取消 → cancelled
   │
   ├──→ 全部成功 → completed
   │
   └──→ 部分失败 → completed_with_errors
                        │
                        ▼
                   (可重试失败切片)
```

**取消规则：** 仅 `pending / parsing / chunking / vectorizing` 状态可取消，`completed / failed / cancelled` 不可取消。取消后清空切片和向量，保留原始文件和 `t_knowledge_base` 记录（详见 6.4 节）。

### 6.2 步骤详情（事务拆分 + 并行向量化）

**事务原则：** 拆除全局 `@Transactional`，每步骤独立小事务，单步提交，失败不牵连前置数据。

#### Step 1: Parsing（独立事务）

```java
@Async("uploadParseExecutor")
public void start(Long knowledgeId) throws TikaException, IOException {
    try {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(knowledgeId);
        File file = fileStorageService.load(kb.getFilePath());

        // Tika 解析（IO 密集型，不在事务内）
        String content = tika.parseToString(file);

        // 空内容处理：直接标记完成，0 切片
        if (content == null || content.isBlank()) {
            complete(knowledgeId, 0);
            return;
        }

        // 独立事务：仅提交 content + 任务状态
        doParseAndUpdate(knowledgeId, content);

        // 进入下一步
        chunk(knowledgeId);
    } catch (Exception e) {
        failTask(knowledgeId, "解析失败：" + e.getMessage());
    }
}

@Transactional
public void doParseAndUpdate(Long knowledgeId, String content) {
    KnowledgeBase kb = knowledgeBaseMapper.selectById(knowledgeId);
    kb.setContent(content);
    knowledgeBaseMapper.updateById(kb);

    KnowledgeTask task = knowledgeTaskMapper.selectByKnowledgeId(knowledgeId);
    task.setStatus("parsing");
    task.setCurrentStep("parsing");
    knowledgeTaskMapper.updateById(task);
}
```

#### Step 2: Chunking（独立事务）

```java
@Transactional
public void doChunk(Long knowledgeId, List<ChunkSegment> segments) {
    // 软删除旧切片（重试场景：保留旧数据供排查）
    knowledgeChunkMapper.updateByKnowledgeId(
        knowledgeId, new LambdaUpdateWrapper<KnowledgeChunk>()
            .set(KnowledgeChunk::getIsActive, 0));

    // 批量插入新切片
    List<KnowledgeChunk> chunks = segments.stream()
        .map(s -> new KnowledgeChunk(knowledgeId, kb.getCategoryId(), s))
        .collect(Collectors.toList());
    knowledgeChunkMapper.batchInsert(chunks);

    // 更新文档切片数 + 任务状态
    KnowledgeBase kb = knowledgeBaseMapper.selectById(knowledgeId);
    kb.setChunkCount(chunks.size());
    knowledgeBaseMapper.updateById(kb);

    KnowledgeTask task = knowledgeTaskMapper.selectByKnowledgeId(knowledgeId);
    task.setStatus("chunking");
    task.setCurrentStep("chunking");
    task.setTotalChunks(chunks.size());
    knowledgeTaskMapper.updateById(task);
}
```

#### Step 3: Vectorizing（并行 + 逐条独立事务 + 批量 API）

```java
private void vectorize(Long knowledgeId) {
    if (isCancelled(knowledgeId)) return;
    List<KnowledgeChunk> chunks = knowledgeChunkMapper
        .selectByKnowledgeIdAndIsActive(knowledgeId, 1);

    // 专用线程池并行处理（IO 密集型，控制并发数）
    CountDownLatch latch = new CountDownLatch(chunks.size());
    AtomicInteger processed = new AtomicInteger(0);

    List<KnowledgeChunk> completedChunks = Collections.synchronizedList(new ArrayList<>());

    for (KnowledgeChunk chunk : chunks) {
        if (isCancelled(knowledgeId)) break;
        vectorEmbedExecutor.submit(() -> {
            try {
                if (isCancelled(knowledgeId)) {
                    chunk.setVectorStatus("pending");
                    return;
                }
                rateLimiter.acquire();
                EmbeddingResult result = callWithRetry(chunk.getContent(), 2);
                chunk.setVectorId(result.getVectorId());
                chunk.setVectorBgeM3(result.getVector());
                chunk.setVectorStatus("vectorized");
                chunk.setTokenCount(result.getUsage());
            } catch (Exception e) {
                chunk.setVectorStatus("failed");
                chunk.setErrorMessage(e.getMessage());
            } finally {
                completedChunks.add(chunk);
                // 每满 200 条或最后一批，批量更新
                if (completedChunks.size() >= 200 || processed.incrementAndGet() == chunks.size()) {
                    List<KnowledgeChunk> batch = new ArrayList<>(completedChunks);
                    completedChunks.clear();
                    knowledgeChunkMapper.batchUpdateStatus(batch);
                }
                updateTaskProgress(knowledgeId, processed.get(), chunks.size());
                latch.countDown();
            }
        });
    }

    try {
        latch.await(30, TimeUnit.MINUTES); // 总超时
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
    }

    if (!isCancelled(knowledgeId)) {
        doComplete(knowledgeId, chunks.size());
    }
}
```

**批量 API（MVP 优化，5-10x 吞吐提升）：**

BGE-M3 硅基流动接口支持批量 embedding（一次请求传多条文本），将并行循环改为批次发送，减少 HTTP 往返次数：

```java
/** 批量 embedding：收集 10-20 条后一次 API 调用 */
private void vectorizeInBatches(Long knowledgeId) {
    List<KnowledgeChunk> chunks = knowledgeChunkMapper
        .selectByKnowledgeIdAndIsActive(knowledgeId, 1);
    if (chunks.isEmpty()) return;

    int batchSize = 15;  // 每批 15 条
    for (int i = 0; i < chunks.size(); i += batchSize) {
        if (isCancelled(knowledgeId)) break;

        int end = Math.min(i + batchSize, chunks.size());
        List<KnowledgeChunk> batch = chunks.subList(i, end);

        rateLimiter.acquire(batch.size());  // 按批大小获取 token
        try {
            List<EmbeddingResult> results = siliconFlowClient.embedBatch(
                batch.stream().map(KnowledgeChunk::getContent).collect(Collectors.toList()));

            for (int j = 0; j < results.size(); j++) {
                KnowledgeChunk chunk = batch.get(j);
                EmbeddingResult r = results.get(j);
                chunk.setVectorId(r.getVectorId());
                chunk.setVectorBgeM3(r.getVector());
                chunk.setVectorStatus("vectorized");
                chunk.setTokenCount(r.getUsage());
            }
        } catch (Exception e) {
            for (KnowledgeChunk chunk : batch) {
                chunk.setVectorStatus("failed");
                chunk.setErrorMessage(e.getMessage());
            }
        }

        knowledgeChunkMapper.batchUpdateStatus(batch);
        updateTaskProgress(knowledgeId, end, chunks.size());
    }

    if (!isCancelled(knowledgeId)) {
        doComplete(knowledgeId, chunks.size());
    }
}
```

**批量 vs 逐条对比：**

| 维度 | 逐条并发（逐条调用） | 批量（batch 模式） |
|------|-------------------|-------------------|
| HTTP 请求数 | 500 条 = 500 次 | 500 条 = 34 次（batchSize=15）|
| 吞吐量 | ~10 QPS × 5 并发 ≈ 50 条/秒 | 15 条/请求 × 10 QPS = 150 条/秒 |
| 实现复杂度 | 简单 | 中等（需处理部分成功） |
| 推荐场景 | MVP 快速验证 | 生产环境优化（上线后切换） |

**批量处理的边界情况：**

| 场景 | 处理方式 |
|------|---------|
| 单批部分失败 | 整批标记 `failed`，不拆分（API 层面不支持部分成功） |
| 批次过大（>20 条） | API 超时风险增加，固定 batchSize=15 |
| 内容超长（>512 token） | BGE-M3 自动截断，不影响批处理 |

/** 批量更新切片状态（替代逐条 UPDATE，减少 DB 交互） */
@Transactional
public void batchUpdateChunks(List<KnowledgeChunk> chunks) {
    knowledgeChunkMapper.batchUpdateStatus(chunks);
}

/** BGE-M3 API 调用 + 退避重试 */
private EmbeddingResult callWithRetry(String content, int maxRetries) {
    Exception lastEx = null;
    for (int i = 0; i <= maxRetries; i++) {
        try {
            return siliconFlowClient.embed(content);
        } catch (Exception e) {
            lastEx = e;
            if (i < maxRetries) {
                Thread.sleep((long) Math.pow(2, i) * 1000); // 退避：1s, 2s
            }
        }
    }
    throw new RuntimeException("BGE-M3 API 调用失败", lastEx);
}
```

#### Step 4: Complete（独立事务）

```java
@Transactional
public void doComplete(Long knowledgeId, int totalChunks) {
    KnowledgeBase kb = knowledgeBaseMapper.selectById(knowledgeId);
    kb.setChunkCount(totalChunks);
    knowledgeBaseMapper.updateById(kb);

    // 检查是否有切片向量化失败
    long failedCount = knowledgeChunkMapper.countByKnowledgeIdAndStatus(knowledgeId, "failed");
    boolean hasErrors = failedCount > 0;

    if (!hasErrors) {
        kb.setVectorStatus("vectorized");
        knowledgeBaseMapper.updateById(kb);
    }

    KnowledgeTask task = knowledgeTaskMapper.selectByKnowledgeId(knowledgeId);
    task.setStatus(hasErrors ? "completed_with_errors" : "completed");
    task.setProgress(100);
    task.setTotalChunks(totalChunks);
    task.setErrorMessage(hasErrors ? "处理完成（" + failedCount + "个切片向量化失败）" : null);
    knowledgeTaskMapper.updateById(task);
}

@Transactional
public void failTask(Long knowledgeId, String error) {
    KnowledgeTask task = knowledgeTaskMapper.selectByKnowledgeId(knowledgeId);
    task.setStatus("failed");
    task.setErrorMessage(error);
    knowledgeTaskMapper.updateById(task);
}
```

### 6.3 重试机制

- 任务级重试：用户点击「重试」→ 从头走完整管道
- parsing 步骤幂等：`if kb.content != null → skip`，直接进入 chunking
- 最多重试 3 次（`retry_count > 3` 时禁用重试按钮）
- **重试时先软删除旧切片**（`is_active = 0`），再插入新切片。保留旧切片数据供排查，同时新切片使用新 ID 避免 `INSERT` 主键冲突。检索时通过 `is_active = 1` 过滤看不到旧切片。

### 6.4 任务取消规则

**触发条件：** 仅 `parsing / chunking / vectorizing` 状态可取消，`completed / failed` 不可取消。

**真正终止执行（基于 Future）：**

在 DocumentPipeline 中维护 `ConcurrentHashMap<Long, Future<?>>`，存储每个异步任务的 Future 引用：

```java
@Component
public class DocumentPipeline {

    private final ConcurrentHashMap<Long, Future<?>> taskFutureMap = new ConcurrentHashMap<>();

    public void registerTask(Long knowledgeId, Future<?> future) {
        taskFutureMap.put(knowledgeId, future);
    }

    /** 取消指定任务（立即中断线程） */
    public void cancel(Long knowledgeId) {
        Future<?> future = taskFutureMap.get(knowledgeId);
        if (future != null && !future.isDone()) {
            future.cancel(true);  // mayInterruptIfRunning = true
        }
    }

    private boolean isCancelled(Long knowledgeId) {
        Future<?> future = taskFutureMap.get(knowledgeId);
        return future != null && future.isCancelled();
    }
}
```

**各步骤快速失败检查：**

```java
@Transactional
public void doParseAndUpdate(Long knowledgeId, String content) {
    if (isCancelled(knowledgeId)) return;
    // ... 正常逻辑
}

@Transactional
public void doChunk(Long knowledgeId, List<ChunkSegment> segments) {
    if (isCancelled(knowledgeId)) return;
    // ... 正常逻辑
}

private void vectorize(Long knowledgeId) {
    if (isCancelled(knowledgeId)) return;
    for (KnowledgeChunk chunk : chunks) {
        if (isCancelled(knowledgeId)) break;  // 取消剩余切片
        // ... 向量化
    }
}
```

**取消后处理：**
- Controller 调 `documentPipeline.cancel(knowledgeId)`，触发 Future cancel
- 管道内 `isCancelled` 检查 → 各步骤快速返回
- 任务状态 → `cancelled`，前端展示「已取消」
- 清空已生成的切片和向量（`deleteByKnowledgeId`）
- 清理 `taskFutureMap` 中的引用
- **保留原始上传文件和 `t_knowledge_base` 记录**，用户可直接点击重试，无需重新选文件

**前端交互：**
- 点击「取消」弹出二次确认：「确定终止当前文档处理？」
- 取消后按钮变为「重试」，保持表单保留

### 6.5 线程池隔离

拆分三个独立线程池，物理隔离，避免单类任务阻塞全服务：

| 线程池 | 核心/最大 | 队列 | 用途 | 拒绝策略 |
|--------|----------|------|------|---------|
| `uploadParseExecutor` | 2 / 4 | 100 | 文档解析（IO+CPU 混合） | `CallerRunsPolicy` |
| `chunkExecutor` | 2 / 4 | 200 | 切片入库（DB IO） | `CallerRunsPolicy` |
| `vectorEmbedExecutor` | 5 / 10 | 500 | BGE-M3 API 调用（IO 密集型） | `AbortPolicy` + 前端排队提示 |

```java
@Configuration
public class ThreadPoolConfig {

    @Bean("uploadParseExecutor")
    public Executor uploadParseExecutor() {
        ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
        e.setCorePoolSize(2);
        e.setMaxPoolSize(4);
        e.setQueueCapacity(100);
        e.setThreadNamePrefix("parse-");
        e.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        e.initialize();
        return e;
    }

    @Bean("vectorEmbedExecutor")
    public Executor vectorEmbedExecutor() {
        ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
        e.setCorePoolSize(5);
        e.setMaxPoolSize(10);
        e.setQueueCapacity(500);
        e.setThreadNamePrefix("embed-");
        e.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        e.initialize();
        return e;
    }
}
```

**链路追踪：** 异步任务透传 TraceId（MDC），接入 SkyWalking / 自定义日志过滤器，全链路可排查。

### 6.6 全局限流（令牌桶）

替换硬编码 `Thread.sleep(300)`，使用本地令牌桶全局控流：

```java
@Component
public class EmbeddingRateLimiter {

    private final RateLimiter limiter = RateLimiter.create(10.0); // 10 QPS

    /** 获取令牌（阻塞直到可用） */
    public void acquire() {
        limiter.acquire();
    }

    /** 尝试获取令牌（超时返回 false） */
    public boolean tryAcquire(long timeout, TimeUnit unit) {
        return limiter.tryAcquire(timeout, unit);
    }
}
```

**参数：** BGE-M3 API 允许 10 QPS，令牌桶 10 个 / 秒，burst 20。所有 `vectorEmbedExecutor` 线程共享同一令牌桶实例。

### 6.7 BGE-M3 API 熔断与降级

**熔断规则（基于 Sentinel / 自定义断路器）：**

| 条件 | 动作 |
|------|------|
| 最近 10 秒内失败率 > 50% | 熔断器打开，后续请求直接降级 |
| 熔断持续时间 | 30 秒后自动半开，尝试 1 个请求 |
| 半开请求成功 | 关闭熔断器，恢复正常调用 |
| 半开请求失败 | 再次打开熔断器，等待 60 秒 |

**舱壁隔离：** `vectorEmbedExecutor` 线程池独立，故障不扩散到解析/入库线程池。

**降级策略：**

```java
public void vectorizeChunk(KnowledgeChunk chunk) {
    if (circuitBreaker.isOpen()) {
        // 熔断中：切片标记 pending，不阻塞用户
        chunk.setVectorStatus("pending");
        chunk.setErrorMessage("向量化服务暂不可用，系统将在恢复后自动重试");
        knowledgeChunkMapper.updateById(chunk);
        return;
    }
    try {
        EmbeddingResult result = callWithRetry(chunk.getContent(), 2);
        chunk.setVectorId(result.getVectorId());
        chunk.setVectorBgeM3(result.getVector());
        chunk.setVectorStatus("vectorized");
        chunk.setTokenCount(result.getUsage());
        knowledgeChunkMapper.updateById(chunk);
    } catch (Exception e) {
        circuitBreaker.recordFailure();
        chunk.setVectorStatus("failed");
        chunk.setErrorMessage(e.getMessage());
        knowledgeChunkMapper.updateById(chunk);
    }
}
```

**后台补发机制：** 熔断恢复后，扫描 `vector_status='pending'` 的切片，自动批量补发向量化（Cron 任务，每小时扫描一次）。

### 6.8 网络 IO 优化

**HTTP 客户端连接池（OkHttp）：**

```java
@Bean
public OkHttpClient embeddingHttpClient() {
    return new OkHttpClient.Builder()
        .connectionPool(new ConnectionPool(10, 30, TimeUnit.SECONDS))  // 池化复用
        .connectTimeout(10, TimeUnit.SECONDS)    // TCP 建连超时
        .readTimeout(30, TimeUnit.SECONDS)       // 响应读取超时
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)           // 幂等请求自动重试
        .build();
}
```

**超时参数汇总：**

| 维度 | 值 | 说明 |
|------|----|------|
| 连接超时 | 10s | TCP 握手 + TLS 握手 |
| 读取超时 | 30s | BGE-M3 API 响应时间（含推理）|
| 写入超时 | 30s | 请求体上传 |
| 连接池大小 | 10 | 与 `vectorEmbedExecutor` 最大线程数匹配 |
| 连接空闲保活 | 30s | 空闲连接 30 秒后关闭 |

## 6.9 僵尸任务自动修复

定时任务每分钟扫描 `parsing / chunking / vectorizing` 状态超过 30 分钟的任务，自动标记为 `failed`：

```java
@Component
@Slf4j
public class ZombieTaskScanner {

    @Scheduled(fixedRate = 60_000)  // 每分钟执行一次
    public void scanAndFixZombieTasks() {
        List<KnowledgeTask> zombies = knowledgeTaskMapper.selectZombieTasks(
            List.of("parsing", "chunking", "vectorizing"),
            LocalDateTime.now().minusMinutes(30));

        for (KnowledgeTask task : zombies) {
            log.warn("检测到僵尸任务 | knowledgeId={} | status={} | createdAt={}",
                task.getKnowledgeId(), task.getStatus(), task.getCreatedAt());
            task.setStatus("failed");
            task.setErrorMessage("任务执行超时，系统已自动终止（超过 30 分钟未完成）");
            knowledgeTaskMapper.updateById(task);
        }
    }
}
```

**设计要点：**
- `fixedRate = 60_000`：每分钟扫描一次，频率低不影响 DB
- 条件：状态为 `parsing/chunking/vectorizing` 且 `updated_at < now - 30min`
- 只修改 `t_knowledge_task` 的状态，不清理切片数据（保留现场供排查）
- 用户看到 `failed` 后可手动点击重试

**开启调度：** 在配置类上加 `@EnableScheduling`，ZombieTaskScanner 自动生效。

## 7. 切片检索

### 7.1 接口设计

```
POST /knowledge/search
Content-Type: application/json

Request:
{
    "query": "玉米价格近期走势",
    "categoryId": 1,
    "topK": 10
}

Response:
{
    "code": 200,
    "data": {
        "results": [
            {
                "chunkId": 101,
                "knowledgeId": 5,
                "docTitle": "玉米市场周报.pdf",
                "docId": 5,
                "content": "玉米价格整体稳中有升，山东地区深加工企业收购价...",
                "score": 0.923,
                "chunkIndex": 3,
                "totalChunks": 15,
                "vectorStatus": "vectorized"
            }
        ],
        "queryTime": "42ms"
    }
}
```

### 7.2 检索流程（分批加载防 OOM）

```
1. 前端 POST /knowledge/search { query, categoryId, topK }
2. 后端接收请求
3. 调硅基流动 BGE-M3 embedding API 生成 query 向量（1024-dim）
4. 从 t_knowledge_chunk 分批拉取向量（每批 500 条），批次内计算余弦相似度
   └─ 条件: vector_status='vectorized' AND vector_bge_m3 IS NOT NULL
   └─ 总上限 LIMIT 10000（防止恶意请求全表扫描）
5. 每批计算后合并 topK 候选，最终取全局 topK
6. 回填文档标题（连接 t_knowledge_base）
7. 返回结果
```

**分批计算代码示意：**

```java
public List<SearchResult> search(String query, Long categoryId, int topK) {
    float[] queryVec = siliconFlowClient.embed(query);     // 1 次 API 调用
    int offset = 0;
    int batchSize = 500;
    PriorityQueue<SearchResult> heap = new PriorityQueue<>(topK);

    while (true) {
        List<KnowledgeChunk> batch = knowledgeChunkMapper.selectPage(
            categoryId, "vectorized", offset, batchSize);
        if (batch.isEmpty()) break;

        for (KnowledgeChunk c : batch) {
            if (c.getVectorBgeM3() == null) continue;
            double score = cosineSimilarity(queryVec, c.getVectorBgeM3());
            if (heap.size() < topK || score > heap.peek().getScore()) {
                if (heap.size() >= topK) heap.poll();
                heap.offer(new SearchResult(c, score));
            }
        }
        offset += batchSize;
    }
    // ... 回填文档标题
}
```

### 7.3 JVM 防护

检索接口增加 CPU 熔断，CPU 使用率 > 80% 时临时关闭语义检索：

```java
@Component
public class SearchCircuitBreaker {

    @Value("${search.cpu-threshold:0.8}")
    private double cpuThreshold;

    public boolean isAvailable() {
        OperatingSystemMXBean os = (OperatingSystemMXBean)
            ManagementFactory.getOperatingSystemMXBean();
        return os.getSystemLoadAverage() < cpuThreshold * Runtime.getRuntime().availableProcessors();
    }
}
```

CPU 熔断触发时前端返回兜底：「语义检索暂时不可用，请稍后重试」。

### 7.4 检索结果展示规则

| 规则 | 说明 |
|------|------|
| `topK` 限制 | 后端限制 `1 ≤ topK ≤ 50`，超出自动修正或返回参数错误 |
| 空结果兜底 | 前端展示「未找到相关内容，请调整关键词重试」|
| 分数说明 | 结果列表底部小字提示「分数越接近 1，内容匹配度越高」|

## 7.5 检索关键词高亮

**需求：** 用户搜索关键词后，前端需要高亮匹配内容中的关键词。

**后端返回 `matchRanges`：**

在 `SearchResult` 中新增 `matchRanges` 字段，标记查询词在 `content` 中的字符偏移范围：

```json
{
    "results": [
        {
            "chunkId": 101,
            "content": "玉米价格整体稳中有升，山东地区深加工企业收购价...",
            "score": 0.923,
            "matchRanges": [
                { "start": 0, "end": 2 },
                { "start": 16, "end": 18 }
            ]
        }
    ]
}
```

```java
@Data
public class SearchResult {
    private Long chunkId;
    private Long knowledgeId;
    private String docTitle;
    private String content;
    private double score;
    private int chunkIndex;
    private int totalChunks;
    private String vectorStatus;

    /** 查询词在 content 中的字符偏移范围列表 */
    private List<MatchRange> matchRanges;
}

@Data
@AllArgsConstructor
public class MatchRange {
    private int start;  // 字符偏移起点（含）
    private int end;    // 字符偏移终点（不含）
}
```

**后端匹配算法：**

```java
public List<MatchRange> findMatchRanges(String content, String query) {
    List<MatchRange> ranges = new ArrayList<>();
    if (content == null || query == null || query.isBlank()) return ranges;

    // 将 query 按空格分词，每个 token 独立匹配
    String[] tokens = query.trim().split("\\s+");
    for (String token : tokens) {
        if (token.length() < 2) continue;  // 单字符跳过，避免过多噪音
        int idx = 0;
        while (true) {
            idx = content.indexOf(token, idx);
            if (idx == -1) break;
            ranges.add(new MatchRange(idx, idx + token.length()));
            idx += token.length();
        }
    }

    // 合并重叠范围
    return mergeOverlapping(ranges);
}
```

**前端 `<mark>` 渲染：**

```vue
<template>
  <p class="search-result-content">
    <template v-for="(part, i) in highlightedContent" :key="i">
      <mark v-if="part.highlight" class="search-highlight">{{ part.text }}</mark>
      <span v-else>{{ part.text }}</span>
    </template>
  </p>
</template>
```

```typescript
interface HighlightPart {
  text: string
  highlight: boolean
}

function computeHighlightParts(content: string, matchRanges: MatchRange[]): HighlightPart[] {
  if (!matchRanges?.length) return [{ text: content, highlight: false }]

  const parts: HighlightPart[] = []
  let cursor = 0

  // 按 start 升序排列
  const sorted = [...matchRanges].sort((a, b) => a.start - b.start)
  for (const r of sorted) {
    if (r.start > cursor) {
      parts.push({ text: content.slice(cursor, r.start), highlight: false })
    }
    parts.push({ text: content.slice(r.start, r.end), highlight: true })
    cursor = r.end
  }
  if (cursor < content.length) {
    parts.push({ text: content.slice(cursor), highlight: false })
  }
  return parts
}
```

**样式：**

```css
.search-highlight {
  background: #ffd54f;
  color: #333;
  padding: 0 2px;
  border-radius: 2px;
}
```

### 7.6 已删除文档过滤与孤立切片清理

**问题：** 级联删除可能因事务失败、延迟删除或异常中断而导致切片残留。检索时这些已删除文档的切片仍会被搜到，用户点击后 404。

**检索侧过滤（必做）：**

```java
// 在 search 方法中增加 JOIN 过滤 + is_active 条件
public List<SearchResult> search(String query, Long categoryId, int topK) {
    // 分批查询时 JOIN t_knowledge_base 过滤已删除，同时过滤软删除切片
    List<KnowledgeChunk> batch = knowledgeChunkMapper.selectPageWithValidDoc(
        categoryId, "vectorized", offset, batchSize);
    // SQL: SELECT c.* FROM t_knowledge_chunk c
    //      INNER JOIN t_knowledge_base b ON c.knowledge_id = b.id
    //      WHERE b.deleted = 0 AND c.is_active = 1 AND ...
}
```

**孤立切片定时清理（兜底）：**

```java
@Component
@Slf4j
public class OrphanChunkCleaner {

    @Scheduled(cron = "0 0 3 * * ?")  // 每天凌晨 3 点
    public void cleanOrphanChunks() {
        // 删除 t_knowledge_base 中已不存在的 chunk 记录
        int deleted = knowledgeChunkMapper.deleteOrphans();
        if (deleted > 0) {
            log.warn("清理孤立切片 | count={}", deleted);
        }
    }
}
```

```sql
-- Mapper SQL：清理孤立切片（硬删除，不保留已删除文档的切片）
DELETE c FROM t_knowledge_chunk c
LEFT JOIN t_knowledge_base b ON c.knowledge_id = b.id
WHERE b.id IS NULL;
```

**设计要点：**
- 检索时 INNER JOIN 过滤，从源头杜绝 404
- 定时清理仅作为兜底，每天一次，不影响主流程
- 删除量 > 0 时日志告警（暗示级联删除有遗漏）

### 7.7 文档标题/文件名搜索

**需求：** 用户需要通过文档标题或文件名快速定位已有文档，不依赖语义检索。

**接口：**

```
GET /knowledge/search-by-title?categoryId=1&keyword=玉米&status=completed&page=1&size=20
```

**后端实现：**

```java
@GetMapping("/search-by-title")
public Result<PageResult<KnowledgeBaseVO>> searchByTitle(
        @RequestParam Long categoryId,
        @RequestParam String keyword,
        @RequestParam(required = false) String status,     // 按任务状态筛选
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size) {

    LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<KnowledgeBase>()
        .eq(KnowledgeBase::getCategoryId, categoryId)
        .and(w -> w.like(KnowledgeBase::getTitle, keyword)
                   .or()
                   .like(KnowledgeBase::getFileName, keyword))
        .orderByDesc(KnowledgeBase::getUpdatedAt);

    // 可选按任务状态筛选
    if (status != null && !status.isEmpty()) {
        // JOIN t_knowledge_task 查询最新任务状态
        wrapper.inSql(KnowledgeBase::getId,
            "SELECT knowledge_id FROM t_knowledge_task WHERE status = '" + status + "'");
    }

    Page<KnowledgeBase> pageResult = knowledgeBaseMapper.selectPage(
        new Page<>(page, size), wrapper);
    return Result.success(PageResult.of(pageResult));
}
```

**前端：** 知识管理页面顶部增加标题搜索框（与语义搜索并列），支持按状态筛选，结果以文档列表展示。

## 8. 文件存储

### 8.1 配置

```yaml
app:
  upload:
    dir: ${UPLOAD_DIR:${user.home}/workspace/ai/scfx/data/uploads}
    max-file-size: 20MB

spring:
  servlet:
    multipart:
      max-file-size: 20MB
      max-request-size: 25MB
```

### 8.2 目录结构

```
data/uploads/
  └── {category_id}/
      └── {knowledge_id}/
          └── {knowledge_id}_{timestamp}_{original_filename}
```

### 8.3 文件名校名与特殊文件处理

| 场景 | 处理规则 |
|------|---------|
| 超长文件名 | 储存时保留原文件名，前端展示截断 >20 字符加省略号，hover 悬浮显示完整名称 |
| 特殊字符 | 储存时自动过滤 `/ \ : * ? " < > |` 等文件系统非法字符 |
| 同名文件 | 存储路径已包含 `knowledge_id + timestamp`，天然唯一，前端通过 ID 区分 |

### 8.4 FileStorageService（IO 安全防护）

```java
@Service
public class FileStorageService {
    @Value("${app.upload.dir}")
    private String uploadDir;

    /** 保存文件（原子写入：先写 tmp 再 rename） */
    public String save(Long categoryId, Long knowledgeId, MultipartFile file) {
        String dir = uploadDir + "/" + categoryId + "/" + knowledgeId;
        Files.createDirectories(Paths.get(dir));

        // 前置校验：磁盘空间
        File storeDir = new File(dir);
        long freeBytes = storeDir.getFreeSpace();
        if (freeBytes < file.getSize()) {
            throw new RuntimeException("磁盘空间不足，剩余 " + (freeBytes / 1024 / 1024)
                + "MB，文件需要 " + (file.getSize() / 1024 / 1024) + "MB");
        }
        // 前置校验：目录可写
        if (!storeDir.canWrite()) {
            throw new RuntimeException("目录不可写：" + dir);
        }

        String filename = knowledgeId + "_" + System.currentTimeMillis()
            + "_" + file.getOriginalFilename();
        String relativePath = categoryId + "/" + knowledgeId + "/" + filename;

        // 原子写入：先写临时文件，写入完成后再重命名
        File tempFile = new File(dir, filename + ".tmp");
        try (InputStream is = new BufferedInputStream(file.getInputStream());
             OutputStream os = new BufferedOutputStream(new FileOutputStream(tempFile))) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                os.write(buffer, 0, len);
            }
            os.flush();
        } catch (IOException e) {
            tempFile.delete();  // 写入失败清理临时文件
            throw new RuntimeException("文件写入失败", e);
        }

        // rename 是原子操作（同一文件系统内）
        File target = new File(dir, filename);
        if (!tempFile.renameTo(target)) {
            tempFile.delete();
            throw new RuntimeException("文件重命名失败");
        }

        return relativePath;
    }

    public File load(String relativePath) {
        return new File(uploadDir, relativePath);
    }

    public void delete(String relativePath) {
        File file = load(relativePath);
        if (file.exists()) file.delete();
    }
}
```

## 8.5 文件 MD5 去重

**需求：** 同一分类下上传相同文件时应提示用户，避免重复处理。

**实现方式：** 上传前计算文件 MD5，入库前检查同分类下是否已存在相同 MD5：

```java
// Controller 层
@PostMapping("/upload")
public Result<?> upload(
        @RequestParam("file") MultipartFile file,
        @RequestParam("categoryId") Long categoryId,
        @RequestParam("idempotentKey") String idempotentKey) {

    // 1. 计算 MD5
    String md5 = DigestUtils.md5DigestAsHex(file.getInputStream());

    // 2. 同分类下 MD5 去重检查
    KnowledgeBase existing = knowledgeBaseMapper.selectByCategoryIdAndMd5(categoryId, md5);
    if (existing != null) {
        return Result.error(409, "已存在相同文档：「" + existing.getTitle() + "」，请勿重复上传");
    }

    // 3. 存储 MD5 到 t_knowledge_base
    // ... 正常上传流程
    kb.setFileMd5(md5);
    knowledgeBaseMapper.updateById(kb);
}
```

**字段变更：** `t_knowledge_base` 新增字段 `file_md5 VARCHAR(64)`（仅存储，不参与查询过滤），需建普通索引 `idx_md5 (file_md5, category_id)`。

**边界情况：**
| 场景 | 处理 |
|------|------|
| 相同 MD5 + 相同分类 | 40 秒内已有同分类同 MD5 记录 → 提示「已存在相同文档」并阻止上传 |
| 相同 MD5 + 不同分类 | 允许上传（不同分类下可重复） |
| MD5 计算失败（IO 异常） | 降级：跳过去重检查，正常上传（返回 200）|
| 超大文件 MD5 | 使用 `BufferedInputStream` 流式计算，不一次性加载到内存 |

```java
// 流式 MD5 计算（防 OOM）
public static String computeMd5(InputStream inputStream) throws IOException {
    BufferedInputStream bis = new BufferedInputStream(inputStream);
    MessageDigest md = MessageDigest.getInstance("MD5");
    byte[] buffer = new byte[8192];
    int len;
    while ((len = bis.read(buffer)) != -1) {
        md.update(buffer, 0, len);
    }
    return Hex.encodeHexString(md.digest());
}
```

**前端展示：** 后端返回 409 时，前端 `ElMessage.warning` 展示重复提示，不清除已选文件（允许用户换文件后重新上传）。

## 8.6 上传韧性与临时文件清理

**问题场景：** 上传到一半断网、用户刷新页面、关闭浏览器，可能导致：
1. 磁盘残留 `.tmp` 临时文件
2. `t_knowledge_base` 记录已创建但文件不完整
3. `t_knowledge_task` 卡在 `pending` 状态

**Controller 层超时与失败清理：**

```java
@PostMapping("/upload")
public Result<?> upload(...) {
    try {
        // 设置上传读取超时（前端 XHR timeout + 后端 Servlet 超时双保险）
        // 正常流程...
    } catch (Exception e) {
        // 上传异常后清理已创建的资源
        if (knowledgeId != null) {
            knowledgeBaseMapper.deleteById(knowledgeId);   // 清理文档记录
            knowledgeTaskMapper.deleteByKnowledgeId(knowledgeId);  // 清理任务
            fileStorageService.delete(relativePath);        // 清理磁盘文件
        }
        throw e;
    }
}
```

**`t_knowledge_task` 中 `pending` 超时自愈：**

在 ZombieTaskScanner（6.9 节）中增加对 `pending` 状态的扫描：

```java
// ZombieTaskScanner 扩展
List<KnowledgeTask> zombies = knowledgeTaskMapper.selectZombieTasks(
    List.of("pending", "parsing", "chunking", "vectorizing"),  // pending 也纳入
    LocalDateTime.now().minusMinutes(30));
```

**临时文件定时清理：**

```java
@Component
@Slf4j
public class TempFileCleaner {

    @Scheduled(cron = "0 0 4 * * ?")  // 每天凌晨 4 点
    public void cleanTempFiles() {
        Path uploadDir = Paths.get(uploadDirPath);
        // 查找 uploadDir 下所有 .tmp 文件，超过 24 小时的删除
        try (Stream<Path> files = Files.walk(uploadDir)) {
            files.filter(p -> p.toString().endsWith(".tmp"))
                .filter(p -> {
                    try {
                        return Files.getLastModifiedTime(p).toMillis()
                            < System.currentTimeMillis() - 24 * 60 * 60 * 1000L;
                    } catch (IOException e) {
                        return false;
                    }
                })
                .forEach(p -> {
                    try {
                        Files.delete(p);
                        log.info("清理过期临时文件: {}", p);
                    } catch (IOException e) {
                        log.warn("临时文件清理失败: {}", p, e);
                    }
                });
        }
    }
}
```

**前端上传超时与重试：**

```typescript
// XHR 上传超时 120s（默认），超时后显示「上传超时，请重试」
const xhr = new XMLHttpRequest()
xhr.upload.timeout = 120_000
xhr.upload.ontimeout = () => {
  ElMessage.error('上传超时，请检查网络后重试')
  uploadVisible.value = false
}
```

### 8.7 批量操作

**需求：** 运营人员需要对多个文档执行批量删除、批量重试、批量重新向量化。

**接口设计：** 使用统一路径 `POST /knowledge/batch/{action}`，`action` 为操作类型：

```
POST /knowledge/batch/delete       # 批量删除
POST /knowledge/batch/retry        # 批量重试
POST /knowledge/batch/revectorize  # 批量重算（仅服务端重新向量化已完成的切片）
```

**请求体：**
```json
{
    "knowledgeIds": [1, 5, 12, 24],
    "categoryId": 3
}
```

**实现要点：**

| 操作 | 后端行为 | 前端表现 |
|------|---------|---------|
| 批量删除 | `@Transactional` 循环删除每个文档（级联清理切片+任务+文件） | 二次确认弹窗「确定删除选中的 N 个文档？」执行后刷新列表 |
| 批量重试 | 循环调单个重试接口，跳过状态为 `parsing/chunking/vectorizing` 的 | loading 进度「正在重试 (3/5)」完成后刷新 |
| 批量重算 | 仅对 `vectorized` 状态的切片重新调用 BGE-M3 API | 跳过未完成文档，UI 显示「重算完成，N 个文档已更新」 |

**批量重试约束：**
- 跳过 `pending/parsing/chunking/vectorizing` 状态（处理中）
- 跳过已达最大重试次数（`retry_count > 3`）的文档
- 重试结束返回汇总：`{ total: 10, success: 7, skipped: 2, failed: 1 }`

```java
@PostMapping("/batch/retry")
public Result<BatchResult> batchRetry(@RequestBody BatchRequest request) {
    int total = 0, success = 0, skipped = 0, failed = 0;
    for (Long id : request.getKnowledgeIds()) {
        total++;
        try {
            KnowledgeTask task = knowledgeTaskMapper.selectByKnowledgeId(id);
            if (task == null) { skipped++; continue; }
            if (List.of("parsing", "chunking", "vectorizing", "pending").contains(task.getStatus())) {
                skipped++; continue;
            }
            if (task.getRetryCount() != null && task.getRetryCount() >= 3) {
                skipped++; continue;
            }
            documentPipeline.start(id);
            success++;
        } catch (Exception e) {
            failed++;
        }
    }
    return Result.success(new BatchResult(total, success, skipped, failed));
}
```

**全选逻辑：** 兼容量大文档数（>1000），全选时仅提交已选 ID 列表（不分页），避免一次性加载过多。前端使用 `el-table` 的 `selectable` + `@selection-change` 跟踪选中行。

### 8.8 导出切片/报告

**需求：** 企业用户高频需求——将文档的切片导出为文本或报告，用于人工审核、标注、备份。

**两种导出模式：**

| 模式 | 范围 | 格式 | 用途 |
|------|------|------|------|
| 单文档导出 | 单个文档的所有切片 | `.txt` / `.csv` | 人工审核切片质量 |
| 批量导出报告 | 按分类+状态筛选的文档集合 | `.csv` | 运营汇总报告 |

**单文档导出接口：**

```
GET /knowledge/{knowledgeId}/chunks/export?format=txt
```

响应：直接返回文件流（`Content-Disposition: attachment`），支持 `.txt` 和 `.csv` 两种格式。

```txt
# 导出格式 (.txt) — 每个切片用分隔线隔开
========================================
文档: 玉米市场周报.pdf
切片数: 12
导出时间: 2026-05-27 10:30:00
========================================

--- 切片 #1 (token: 420, 状态: vectorized) ---
玉米价格整体稳中有升，山东地区深加工企业收购价...

--- 切片 #2 (token: 385, 状态: vectorized) ---
华北地区玉米价格稳中有涨，河北、河南地区...
```

```csv
# 导出格式 (.csv)
knowledgeId, chunkIndex, content, tokenCount, vectorStatus, errorMessage, createdAt
5, 0, "玉米价格整体稳中有升...", 420, vectorized, , 2026-05-27 10:30:00
5, 1, "华北地区玉米价格稳中有涨...", 385, vectorized, , 2026-05-27 10:30:00
```

**批量导出报告接口：**

```
POST /knowledge/export-report
Content-Type: application/json

{
    "categoryId": 3,
    "status": "completed_with_errors",    // 可选筛选
    "dateFrom": "2026-05-01",
    "dateTo": "2026-05-27"
}
```

响应：`.csv` 文件流，每行一个文档的汇总数据：

```csv
# report columns
knowledgeId, fileName, fileType, fileSize, status, totalChunks, vectorizedChunks, failedChunks, errorMessage, createdAt, updatedAt
5, 玉米市场周报.pdf, pdf, 2.3MB, completed, 12, 12, 0, , 2026-05-27 10:00, 2026-05-27 10:30
8, 大豆分析.docx, docx, 1.1MB, completed_with_errors, 8, 6, 2, "向量化失败: API 超时", 2026-05-26 14:00, 2026-05-26 14:05
```

**前端：**
- 切片抽屉中增加「导出」按钮（右上角）：`单文档 → .txt` / `单文档 → .csv`
- 文档列表上方工具栏增加「导出报告」按钮（批量导出）
- 导出请求直接开新窗口/使用 `a.download` 触发下载，不阻塞 UI

## 9. 前端改动

### 9.1 上传改造（Knowledge.vue）

**双层进度条：**

```
┌─ 上传对话框 ──────────────────────────────────┐
│  文件: 玉米市场周报.pdf                        │
│  标题: [玉米市场周报]                          │
│  来源: [自定义     ]  作者: [张三]             │
│                                               │
│  ████████████████░░░░ 上传 80%                │ ← 真实 xhr 上传进度
│  ██████░░░░░░░░░░░░░░ 处理中（解析文档）         │ ← 后端任务进度
│                                               │
│  [取消]                    [上传]              │
└───────────────────────────────────────────────┘
```

处理中阶段显示文字（面向业务用户）：

| 原技术状态 | 前端展示文案 |
|-----------|------------|
| parsing | 正在解析文档内容 |
| chunking | 正在拆分文本内容 |
| vectorizing (3/12) | 正在生成语义索引（3/12）|
| cancelled | 任务已取消 |
| failed | 处理失败：{原因} |
| completed | 处理完成 |
| completed_with_errors | 处理完成（{failedCount}个切片失败）|

**双层进度条逻辑：**
- **上传进度：** 基于 XHR `upload.onprogress` 原生进度，0→100%，上传完成后固定 100%
- **处理进度：** 由后端 `task.progress` 字段驱动，0→100%
- **阶段联动：** 文件未上传完成时，禁用「上传」「取消」以外所有操作

**交互细节：**
- 已上传的文件和表单在失败后保留，支持一键重试（不重新选文件）
- 处理中点击「取消」→ 终止后端任务（需后端支持 interrupt 或标记取消）
- 完成后自动关闭对话框（3 秒倒计时），也可手动关闭

### 9.2 文档列表（含状态筛选 + 批量操作 + 标题搜索）

**顶部工具栏：**

```
┌─────────────────────────────────────────────────────────────┐
│  🔍 [标题搜索框________________________]  [全部 ▼]  [批量▼] │
│    输入标题/文件名搜索                  状态筛选    批量操作 │
├─────────────────────────────────────────────────────────────┤
│  ☐  标题                状态       日期         操作        │
│  ├──────────────────────────────────────────────────────────┤
│  ☐  玉米市场周报.pdf   已完成     05-27  [📄切片] [🗑]      │
│  ☐  大豆分析.docx      解析失败   05-26  [🔄重试] [📄切片]   │
│  ☐  小麦价格.txt       处理中     05-27  [进度: 50%]       │
│  ☐  大豆价格.pdf       已取消     05-25  [🔄重试]          │
│  ☐  棉花报告.docx      完成含错   05-24  [📄切片] [❗详情]   │
└─────────────────────────────────────────────────────────────┘
  [全选] [批量删除] [批量重试] [批量重算]
```

**状态筛选 dropdown：**
| 筛选值 | 匹配的任务状态 |
|--------|-------------|
| 全部 | 不筛选 |
| 进行中 | `pending / parsing / chunking / vectorizing` |
| 已完成 | `completed` |
| 完成含错 | `completed_with_errors` |
| 失败 | `failed` |
| 已取消 | `cancelled` |

**标题搜索：** 输入关键词 → 调 `GET /knowledge/search-by-title` 实时搜索（防抖 300ms），结果在列表内刷新。

**列表列定义：**
- 左侧复选框（批量选择用）
- 标题（展示文件名 + hover 悬浮显示完整路径 + 状态小图标）
- 状态（彩色标签：绿=已完成、橙=处理中、红=失败、灰=已取消、黄=完成含错）
- 日期（上传时间）
- 操作（📄切片 / 🔄重试 / 🗑删除 / ❗详情）

**操作规则：**
- 📄 切片：文档至少有一个切片（vectorized）时才可点击
- 处理中状态：只显示进度文本，禁用其他按钮
- 失败状态：「🔄 重试」+「📄 切片」（有已成功的切片时）
- ❗ 详情：`completed_with_errors` 状态显示详情按钮，点击展示失败切片明细
- 🗑 删除：弹出二次确认「确定删除该文档及其所有切片？」

### 9.3 文档切片区（el-drawer）

**规格参数：**
- 宽度：600px（桌面）、100%（≤768px）
- Tab 切换：「基本信息」/「文档切片」/「内容预览」
- 默认 Tab：「文档切片」
- 虚拟滚动（当切片数 > 50 时启用）

**内容预览 Tab：**
- 展示 `t_knowledge_base.content` 完整解析后的纯文本（文档原文所有文字）
- 头部显示文档元数据：文件名、字数（中文字符数 + 英文单词数）、上传时间
- 内容区支持全量滚动（默认），大文档（>10000 字）自动启用分页（每页 5000 字）
- 顶部搜索框：输入关键词后，前端在 content 中做 `indexOf` 匹配，跳转到首处匹配位置并高亮所有匹配
- 搜索匹配数在输入框底部显示：「共 N 处匹配」
- 点击匹配上下箭头 → 依次跳转到各个匹配位置
- 无内容时展示空态占位：「暂无解析内容」（解析失败或纯空文档）
- 全零向量/空向量文档在预览区顶部显示黄色警告条：「该文档向量全零，语义检索可能不准确」

```
┌─ 玉米市场周报.pdf ────────────────── 状态: 已完成 ─┐
│                                                     │
│  ┌──────────────────┬──────────┬───────────┐       │
│  │   基本信息       │ 文档切片  │  内容预览  │       │
│  └──────────────────┴──────────┴───────────┘       │
│                                                     │
│  ┌─ 切片卡片 #1 ──────────────────────────────┐    │
│  │ 玉米市场整体行情分析。玉米价格整体稳中有     │    │
│  │ 升，山东地区深加工企业收购价...              │    │
│  │  字数: 420    状态: ✅ 已完成                │    │
│  └─────────────────────────────────────────────┘    │
│  ┌─ 切片卡片 #2 ──────────────────────────────┐    │
│  │ 华北地区玉米价格稳中有涨，河北、河南地区      │    │
│  │ 深加工企业挂牌价...                          │    │
│  │  字数: 385    状态: ✅ 已完成                │    │
│  └─────────────────────────────────────────────┘    │
│  ┌─ 切片卡片 #3 ──────────────────────────────┐    │
│  │ 东北产区天气影响，黑龙江地区降雨偏多...      │    │
│  │  字数: 410    状态: ❌ 失败                  │    │
│  └─────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────┘
```

**切片卡片交互：**
- hover 阴影效果
- 卡片内容：切片序号 + 前 100 字摘要 + 字数 + 文档来源 + 上传时间 + 状态图标
- 点击卡片 → 复制切片文本到剪贴板 → 成功绿色 toast「文本已复制到剪贴板」，失败提示「复制失败，请手动复制」
- 状态图标：✅ 已完成 / ⏳ 处理中 / ❌ 失败
- ❌ 失败切片 hover 时展示完整 `error_message`，方便用户排查

**大文档提示：**
切片数 > 100 时，卡片列表顶部增加提示「当前文档共 XX 个切片，已启用滚动加载」

### 9.4 轮询逻辑（useTaskPolling.ts）

```typescript
export function useTaskPolling(knowledgeId: Ref<number | null>) {
  const taskStatus = ref<TaskStatus | null>(null)
  let abortController: AbortController | null = null
  let timeoutId: ReturnType<typeof setTimeout> | null = null

  function startPolling() {
    const poll = async () => {
      abortController = new AbortController()
      try {
        const res = await getTaskStatus(knowledgeId.value!, {
          signal: abortController.signal
        })
        taskStatus.value = res.data
        if (!['completed', 'failed'].includes(res.data.status)) {
          timeoutId = setTimeout(poll, 3000)  // 串行，等待上一个完成
        } else {
          onComplete(res.data)
        }
      } catch (err: any) {
        if (err.name !== 'AbortError') {
          timeoutId = setTimeout(poll, 3000)  // 失败也继续轮询
        }
      }
    }
    poll()
  }

  function stopPolling() {
    abortController?.abort()
    if (timeoutId) clearTimeout(timeoutId)
  }

  onUnmounted(stopPolling)
  return { taskStatus, startPolling, stopPolling }
}
```

**关键设计：**
- `setTimeout` 串行执行，不堆积请求
- `AbortController` 组件卸载时取消 pending 请求
- 切换分类/关闭抽屉时自动终止轮询

### 9.5 切片搜索页

**略（搜索页面属于独立 UI 模块，不作为本设计文档的主要内容，仅说明后端接口）**

前端可通过现有搜索入口调用 `POST /knowledge/search`，返回结果包含切片内容、所属文档、相似度分数，用户可点击跳转到文档详情查看完整上下文。

## 10. 错误处理

### 10.1 错误矩阵（含失败原因分类）

**失败原因分类体系：** 所有任务失败原因归入以下五类，前端按类别展示不同图标和文案：

| 错误类别 | 图标 | 业务文案 | 触发场景 |
|---------|------|---------|---------|
| `FILE_CORRUPTED` | 📁❌ | 文件损坏或格式不支持 | Tika 抛异常、加密 PDF |
| `PARSE_FAILED` | 📝❌ | 文档内容解析失败 | 图片 PDF 无文字层、乱码文件 |
| `API_TIMEOUT` | ⏱️❌ | 向量化服务超时 | BGE-M3 API 读取超时（>30s）|
| `QUOTA_EXCEEDED` | 📊❌ | 向量化配额已用尽 | API 返回 429 / 配额限制 |
| `CONTENT_INVALID` | ⚠️❌ | 文档内容不合规或无效 | 敏感内容拦截、有效字符 < 50 |
| `UNKNOWN` | ❌ | 处理异常，请重试 | 其他未分类异常 |

**错误矩阵：**

| 步骤 | 错误类别 | 常见原因 | error_message |
|------|---------|---------|--------------|
| parsing | `FILE_CORRUPTED` | 文件损坏/加密 | "文件格式不支持或已损坏" |
| parsing | `PARSE_FAILED` | 图片 PDF / 乱码 | "文档无可解析的有效文本内容" |
| parsing | `CONTENT_INVALID` | 敏感内容 | "文档包含违规内容，已拦截" |
| chunking | `PARSE_FAILED` | 解析内容为空 | "解析内容为空，无法切片" |
| vectorizing | `API_TIMEOUT` | BGE-M3 超时 | "向量化超时: 服务响应超过 30s" |
| vectorizing | `QUOTA_EXCEEDED` | API 配额耗尽 | "向量化配额已用尽，请稍后重试" |
| vectorizing | `UNKNOWN` | 其他网络异常 | "向量化失败: {具体原因}" |

**前端展示规则：**

```
┌─ 失败原因展示 ────────────────────────────────────┐
│  📁❌ 文件损坏或格式不支持                         │
│  └─ 该文件可能是加密 PDF 或非标准格式，请检查后重试 │
│                                                    │
│  ⏱️❌ 向量化服务超时                                │
│  └─ BGE-M3 服务响应超时，系统将在 30 秒后重试      │
│                                                    │
│  📊❌ 向量化配额已用尽                              │
│  └─ API 配额不足，已降级为 pending，等待自动恢复    │
└────────────────────────────────────────────────────┘
```

**任务详情弹窗：** 点击失败任务的错误信息 → 弹出详情，展示：
- 错误类别（图标 + 分类名称）
- 错误时间
- 完整 `error_message`
- 建议操作（如「检查文件格式后重试」「等待配额恢复后重试」）

### 10.2 降级策略

| 场景 | 处理 |
|------|------|
| 单切片向量化失败 | 不影响其他切片，失败切片标记 failed，其余正常完成 |
| 部分切片失败 | 任务标记 `completed_with_errors`，前端展示「处理完成（X个切片失败）」并提供一键重试 |
| 全部切片失败 | 任务标记 `failed`，`error_message` 记录原因，支持重试 |
| API 超时 | 单次调用超时设为 30s，失败后跳过该切片继续后续 |
| 文件超过大小限制 | Controller 层 `MaxUploadSizeExceededException` 拦截，提示用户 |

### 10.3 重试逻辑

```
重试按钮 → POST /knowledge/{id}/retry
  ├─ retry_count++
  ├─ if retry_count > 3 → 永久禁用重试按钮，hover 提示「已达最大重试次数，请删除后重新上传」
  ├─ if content 已存在 → 跳过 parsing，进入 chunking
  ├─ if 已有 chunk 记录 → 清空后重新切片
  │   └─ knowledgeChunkMapper.deleteByKnowledgeId(knowledgeId)  ← 必做，否则切片重复
  ├─ 重试中任务状态回到 pending，前端进度条重置
  └─ else → 重新走完整管道

**权限控制：** 仅文档创建人 (`created_by`) 或管理员可重试

### 10.4 超时场景统一兜底

| 场景 | 超时时间 | 兜底行为 |
|------|---------|---------|
| 所有后端 API 接口 | 15s | 前端捕获超时 → 提示「请求超时，请稍后重试」 |
| 向量化调用 BGE-M3 API | 30s | 单切片超时跳过，不影响其他切片 |
| 轮询状态获取 | 连续 5 次失败 | 自动停止轮询，任务标记 `failed`，提示「任务状态获取失败」|

### 10.5 全局异常捕获

在 `@RestControllerAdvice` 中统一处理文档解析相关异常：

| 异常 | 捕获目标 | HTTP | 前端 message |
|------|---------|------|-------------|
| `TikaException` | 文件损坏/格式不支持 | 500 | "解析失败：文件格式不支持或已损坏" |
| `IOException` | 文件读写异常 | 500 | "文件读写异常，请检查文件权限" |
| `MaxUploadSizeExceededException` | 文件超出 20MB | 413 | "文件大小超过限制（最大 20MB）" |
| `SiliconFlowApiException` | BGE-M3 API 异常 | 502 | "向量化服务暂时不可用，请稍后重试" |
| `IllegalArgumentException` | 参数校验失败 | 400 | 透传异常 message |

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TikaException.class)
    public Result<?> handleTikaException(TikaException e) {
        log.error("文档解析失败", e);
        return Result.error(500, "解析失败：文件格式不支持或已损坏");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<?> handleMaxUpload(MaxUploadSizeExceededException e) {
        return Result.error(413, "文件大小超过限制（最大 20MB）");
    }

    @ExceptionHandler(SiliconFlowApiException.class)
    public Result<?> handleSiliconFlow(SiliconFlowApiException e) {
        log.error("BGE-M3 API 调用异常", e);
        return Result.error(502, "向量化服务暂时不可用，请稍后重试");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<?> handleIllegalArg(IllegalArgumentException e) {
        return Result.error(400, e.getMessage());
    }
}
```
```

## 11. 配置汇总

```yaml
# application.yml 新增配置

app:
  upload:
    dir: ${UPLOAD_DIR:${user.home}/workspace/ai/scfx/data/uploads}
    max-file-size: 20MB

chunk:
  max-tokens: 460            # BGE-M3 窗口 80%，预留空间给前缀提示词
  overlap-tokens: 50         # 重叠比例 ~10%
  max-text-chars: 500000     # 单文档最大字符数（超限截断，防 OOM）
  max-chunks: 500            # 单文档最大切片数（超限截断，防 DB 爆炸）
  max-chunk-chars: 3000      # 单段最大字符数，超限段落强制 Token 二次切分

app:
  content-filter:
    sensitive-words: ""       # 敏感词列表，逗号分隔，空 = 不启用

spring:
  servlet:
    multipart:
      max-file-size: 20MB
      max-request-size: 25MB
```

## 12. 改动清单

### 后端

| # | 文件 | 操作 | 说明 |
|---|------|------|------|
| 1 | `pom.xml` | 新增依赖 | tika-core + tika-parsers-standard-package 2.9.2 + okhttp 4.12.0 |
| 2 | `application.yml` | 新增配置 | upload.dir / chunk.* / content-filter / multipart |
| 3 | `schema.sql` | 新增表 | t_knowledge_chunk + t_knowledge_task DDL + t_knowledge_base ADD file_md5 |
| 4 | `entity/KnowledgeChunk.java` | 新增 | 切片实体，MyBatis-Plus 注解 |
| 5 | `entity/KnowledgeTask.java` | 新增 | 任务实体 |
| 6 | `mapper/KnowledgeChunkMapper.java` | 新增 | 切片 CRUD + batchInsert + 分页查询 |
| 7 | `mapper/KnowledgeTaskMapper.java` | 新增 | 任务 CRUD + 状态更新 |
| 8 | `service/FileStorageService.java` | 新增 | 文件存储工具类（原子写入 + 前置校验 + 缓冲流）|
| 9 | `service/TextSplitter.java` | 新增 | 文本切片器（固定 token + 重叠 + 长度/切片数保护）|
| 10 | `service/DocumentPipeline.java` | 新增 | 异步管道（步骤级事务 + 并行向量化 + Future 取消 + completed_with_errors）|
| 11 | `service/EmbeddingRateLimiter.java` | 新增 | 令牌桶控流（替换硬编码 Thread.sleep）|
| 12 | `service/SearchCircuitBreaker.java` | 新增 | CPU 熔断，超阈值时关闭语义检索 |
| 13 | `config/ThreadPoolConfig.java` | 新增 | 3 个独立线程池 + TraceId 透传 |
| 14 | `service/EmbeddingRateLimiter.java` | 新增 | 令牌桶控流（10 QPS，替换硬编码 Thread.sleep）|
| 15 | `service/CircuitBreaker.java` | 新增 | BGE-M3 API 熔断（50% 失败率→熔断 30s→半开→降级 pending）|
| 16 | `service/SearchCircuitBreaker.java` | 新增 | CPU 熔断，超阈值（80%）时关闭语义检索 |
| 17 | `controller/KnowledgeBaseController.java` | 修改 | upload（幂等键 + MD5 去重 + 内容过滤 + 状态拦截）/ retry / delete（级联清理）|
| 18 | `controller/KnowledgeSearchController.java` | 新增 | 切片搜索接口（分批加载 + topK 限制 + matchRanges + 已删除文档过滤）|
| 19 | `service/ZombieTaskScanner.java` | 新增 | 僵尸任务自动修复（扫描 >30min → failed，含 pending 超时）|
| 20 | `service/DocumentPipeline.java` | 增强 | ConcurrentHashMap<Long, Future<?>> + isCancelled 检查 + completed_with_errors |
| 21 | `service/ContentFilter.java` | 新增 | 敏感词过滤 + 有效文本长度判断 |
| 22 | `service/TempFileCleaner.java` | 新增 | 过期 .tmp 文件清理（每天凌晨 4 点）|
| 23 | `service/OrphanChunkCleaner.java` | 新增 | 孤立切片清理（每天凌晨 3 点）|
| 24 | `common/GlobalExceptionHandler.java` | 修改 | 新增 Tika / SiliconFlow / MaxUploadSizeExceeded 异常捕获 |
| 25 | `controller/KnowledgeBaseController.java` | 增强 | 新增 search-by-title 标题搜索、batch/delete/retry/revectorize 批量操作 |
| 26 | `controller/KnowledgeExportController.java` | 新增 | 导出切片（.txt/.csv）+ 批量导出报告 |

### 前端

| # | 文件 | 操作 | 说明 |
|---|------|------|------|
| 1 | `api/knowledge.ts` | 修改 | 新增 search、taskStatus、searchByTitle、batch*、exportChunks 方法 |
| 2 | `views/knowledge/Knowledge.vue` | 修改 | 上传对话框双层进度条 + 重试 + 取消 + MD5 409 提示 |
| 3 | `views/knowledge/components/ChunkDrawer.vue` | 新增 | 文档切片抽屉组件（含「内容预览」Tab + 搜索高亮 + 导出按钮）|
| 4 | `views/knowledge/components/ChunkCard.vue` | 新增 | 切片卡片组件 |
| 5 | `composables/useTaskPolling.ts` | 新增 | 轮询 composable（setTimeout 串行 + AbortController）|
| 6 | `utils/highlight.ts` | 新增 | matchRanges → <mark> 渲染工具函数 + computeHighlightParts |
| 7 | `views/knowledge/components/FailureDetail.vue` | 新增 | 失败原因详情弹窗（分类图标 + 建议操作）|

## 13. 执行顺序

1. Schema 变更：建表（t_knowledge_chunk + t_knowledge_task）+ ALTER t_knowledge_base ADD file_md5 + INDEX idx_md5_category
2. 新增实体 + Mapper（KnowledgeChunk + KnowledgeTask）
3. 新增 FileStorageService（文件存储）
4. 新增 ContentFilter（敏感词过滤 + 有效文本判断）
5. 新增 TextSplitter（文本切片器，含 max-text-chars / max-chunks 保护）
6. 新增 DocumentPipeline（异步管道，含 Future 取消机制 + isCancelled 检查 + completed_with_errors + 失败原因分类）
7. 新增 ZombieTaskScanner（僵尸任务修复，@EnableScheduling 生效）
8. 新增 TempFileCleaner + OrphanChunkCleaner（定时清理任务）
9. 修改 KnowledgeBaseController（upload 端点实现 + MD5 去重 + contentFilter.check + retry + search-by-title）
10. 新增 KnowledgeExportController（导出切片 + 批量报告）
11. 新增 batch/delete/retry/revectorize 批量操作接口
12. 修改 KnowledgeSearchController（分批加载 + matchRanges + INNER JOIN 过滤已删除文档）
13. Pom.xml 加入 Tika 依赖
14. application.yml 配置（含 chunk.* / content-filter / multipart）
15. 新增 highlight.ts 工具函数（matchRanges → <mark> 渲染）
16. 新增 FailureDetail.vue 失败原因分类展示组件
17. 前端：api/knowledge.ts 新增方法
18. 前端：useTaskPolling composable
19. 前端：ChunkCard + ChunkDrawer 组件（含内容预览 Tab + 关键词高亮 + 导出）
20. 前端：Knowledge.vue 上传对话框改造 + 文档列表接入（含状态筛选 + 批量操作 + completed_with_errors 展示 + 409 去重提示 + XHR 超时 120s）

## 14. 后续迭代方向（MVP 后）

- **OCR 支持**：集成 Tesseract 处理扫描件 PDF
- **文档更新/替换**：版本管理 + 旧切片清理 + 重新切片向量化
- **单切片编辑/删除**：精细化管理
- **切片级可视化**：切片向量参与 PCA/MDS 降维
- **混合检索**：切片级检索 + 文档级检索合并结果
- **段落边界感知**：按标题/换行符优先切分
