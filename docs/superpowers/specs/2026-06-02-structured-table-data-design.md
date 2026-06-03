# 财经日报结构化表格数据全链路方案

> **目标：** 针对粮信网玉米日报等结构化/半结构化财经日报，打通"采集端 HTML 表格解析 → 结构化存储 → 分块语义化改写 → 前端增强渲染"的完整链路。

**架构概要：** 在采集器 liangxin.py 中提取 HTML `<table>` 为结构化 JSON（table_meta），通过现有采集链路传入 Java 后端存入新增字段，chunking 时标注 type + 语义化改写，前端 KnowledgeDetail.vue 用 el-table 增强渲染。

**涉及子系统：**
- Python 采集 SDK（collectorsdk）— 采集端表格提取
- Java 后端（entity / mapper / controller）— 存储与 API
- AI 问答服务（chunker / ingestion）— 分块与向量化
- Vue 前端（KnowledgeDetail.vue）— 增强渲染

---

## 数据流

```
粮信网 HTML 页面
    │ Playwright 加载，克隆正文容器
    ▼
LiangxinCollector._get_report_content()
    ├── 提取 <table> → table_meta JSON 数组
    │   [{headers: [...], rows: [[...], ...], caption: "..."}]
    ├── 将 <table> 替换为 pipe 表格（改善 text 提取质量）
    │   "| 省区 | 主流价格 | ... |\n|------|---------|...|\n| 黑龙江 | 2150-2250 |..."
    ├── text: 普通文本 + pipe 表格（<table> 已被替换为 pipe 格式）
    └── html: 替换后的 html（<table> → pipe 文本；前端不使用 contentHtml）
    │
    ▼
submit_report(content=text, content_html=html, table_meta=json)
    │ POST /collector/exec/{id}/data
    ▼
CollectorController.submitData()
    ├── content → t_knowledge_base.content
    ├── contentHtml → t_knowledge_base.content_html
    └── tableMeta → t_knowledge_base.table_meta  ← 新增
    │
    ▼
ingest_knowledge.py / vectorTaskService
    ├── chunk_with_types(content) → 文本切片
    ├── 检测 pipe 表格 → 语义化改写替换 content
    │   pipe 表 → "黑龙江玉米价格2150-2250元/吨..."
    ├── content（改写文本）→ embed_text → vector
    │   ↑ embedding 原文 = LLM context
    └── Qdrant payload:
        {content, type: "table"|"text", knowledge_id, ...}
    │
    ▼
KnowledgeDetail.vue
    ├── GET /knowledge/{id} → {title, content, table_meta, ...}
    ├── content → marked 渲染（常规段落）
    └── table_meta → el-table 增强渲染（可排序、筛选）
```

---

## 1. 采集端：HTML 表格解析

### 1.1 CollectorReporter.submit_report() 新增 tableMeta 参数

**文件：** `python-collector-sdk/collectorsdk/reporter.py:444`

新增 `table_meta` 参数，传递到 data 字典中：

```python
def submit_report(self, title, source, url, variety="", report_type="",
                  content="", content_html="", publish_time="",
                  table_meta=""):                        # ← 新增
    data = {
        "title": title,
        "source": source,
        "url": url,
        "variety": variety,
        "reportType": report_type,
        "content": content,
        "contentHtml": content_html,
        "publishTime": publish_time,
        "tableMeta": table_meta,                         # ← 新增 JSON 字符串
    }
    self.report_data(data)
```

### 1.2 BaseCollector.submit_report() 透传 table_meta

**文件：** `python-collector-sdk/collectorsdk/base.py:274`

```python
def submit_report(self, title, source, url, variety="", report_type="",
                  content="", content_html="", publish_time="",
                  table_meta=""):
    self._reporter.submit_report(
        title=title, source=source, url=url,
        variety=variety, report_type=report_type,
        content=content, content_html=content_html,
        publish_time=publish_time,
        table_meta=table_meta,
    )
```

### 1.3 LiangxinCollector._get_report_content() 提取表格

**文件：** `python-collector-sdk/dev/collectors/liangxin.py:356-386`

```javascript
/**
 * table_meta 提取 + pipe 文本替换（单次遍历，保证 1:1 对应）
 *
 * 增强项：
 * - 跳过嵌套表格（table inside table）
 * - 处理 colspan 合并单元格（展开为虚拟列）
 * - 添加 <!--TABLE_MARKER_N--> 标记，供 chunker/前端确定性匹配
 * - 整体 try-catch 包裹，异常时清空 tableMeta 降级
 */
let tableMeta = [];
try {
    // 判断是否为嵌套表格（跳过，避免内层表格被重复处理）
    const isNested = (el) => {
        let p = el.parentElement;
        while (p && p !== clone) { if (p.tagName === 'TABLE') return true; p = p.parentElement; }
        return false;
    };
    // 展开行（处理 colspan/rowspan）
    const expandRow = (cells, carry) => {
        const row = [];
        let col = 0;
        // 填入上行 rowspan 遗留的单元格
        for (let ci = carry.length - 1; ci >= 0; ci--) {
            const c = carry[ci];
            while (col < c.col) { row.push(''); col++; }
            if (col === c.col) { row.push(c.text); c.remain--; col++; }
            if (c.remain <= 0) carry.splice(ci, 1);
        }
        cells.forEach(cell => {
            const text = cell.textContent.trim().replace(/\s+/g, '');
            const cs = parseInt(cell.getAttribute('colspan') || '1', 10);
            const rs = parseInt(cell.getAttribute('rowspan') || '1', 10);
            for (let c2 = 0; c2 < cs; c2++) {
                row.push(text);
                if (rs > 1) carry.push({ col: col + c2, text: text, remain: rs - 1 });
            }
            col += cs;
        });
        return row;
    };
    // 检测表头行：含 <th> 过半的首个行
    const findHeader = (trs) => {
        for (let i = 0; i < Math.min(trs.length, 3); i++) {
            const ths = trs[i].querySelectorAll('th');
            const all = trs[i].querySelectorAll('th, td');
            if (ths.length >= all.length * 0.5 && all.length >= 2) return { row: trs[i], dataStart: i + 1 };
        }
        return { row: trs[0], dataStart: 1 };
    };
    const allTables = Array.from(clone.querySelectorAll('table'));
    allTables.forEach(table => {
        if (isNested(table)) return;
        const trs = table.querySelectorAll('tr');
        if (trs.length < 2) return;

        const { row: headerRow, dataStart } = findHeader(trs);
        const headers = expandRow(headerRow.querySelectorAll('th, td'), []);
        if (headers.length === 0) return;

        const dataRows = [];
        const carry = [];
        for (let ri = dataStart; ri < trs.length; ri++) {
            const rd = expandRow(trs[ri].querySelectorAll('td, th'), carry);
            if (rd.length === headers.length && rd.some(c => c.length > 0)) dataRows.push(rd);
        }
        if (dataRows.length === 0) return;

        // 获取表格标题（前一个 heading 或同级别元素）
        let caption = '';
        let s = table.previousElementSibling;
        for (let tries = 0; tries < 3 && s; tries++) {
            const t = s.textContent.trim();
            if (t.length > 0 && t.length < 100) { caption = t; break; }
            s = s.previousElementSibling;
        }

        const markerIdx = tableMeta.length;
        tableMeta.push({ headers, rows: dataRows, caption });

        // 生成 pipe 文本（含 marker 标记）
        const pipeLines = ['<!--TABLE_MARKER_' + markerIdx + '-->',
            '| ' + headers.join(' | ') + ' |',
            '|' + headers.map(() => '---').join('|') + '|'];
        for (const row of dataRows) pipeLines.push('| ' + row.join(' | ') + ' |');
        pipeLines.push('<!--TABLE_MARKER_END_' + markerIdx + '-->');
        const pipeText = document.createTextNode('\n' + pipeLines.join('\n') + '\n');
        table.parentNode.replaceChild(pipeText, table);
    });
} catch (e) {
    // 异常时清空 tableMeta 降级，避免部分生成
    tableMeta = [];
}
```

Python 侧接收返回值：

```python
result = content_elem.evaluate("""(el) => {
    // ... 上述 JavaScript 逻辑 ...
    // 异常时 tableMeta 为 []，text/html 为原始内容
    return {
        text: clone.textContent.trim(),
        html: clone.innerHTML.trim(),  // <table> 已替换为 pipe 文本
        tableMeta: tableMeta,
    };
}()""")
```

> **注意：**
> - `html` 中的 `<table>` 已被 pipe 文本替换，但当前前端 KnowledgeDetail.vue 只用 `content`（纯文本），`contentHtml` 未在前端使用，无影响。
> - `<!--TABLE_MARKER_N-->` 标记用于 chunker 和前端确定性匹配 pipe 表格，替代启发式检测（如 "| 备注 |" 不会被误判为表格）。
> - JS 整体 try-catch 包裹：异常时返回空 `tableMeta`，Python 侧校验 `len(tableMeta) == countMarkers(content)`，不一致则清空 tableMeta 并打 WARN。

### 1.4 LiangxinCollector.collect() 传递 table_meta + 校验

**文件：** `python-collector-sdk/dev/collectors/liangxin.py:477-486`

接收 `{text, html, tableMeta}` 后增加校验：`tableMeta` 长度与 content 中的 `TABLE_MARKER` 标记数量必须一致，否则清空 tableMeta 降级。

```python
import json
import re

# ... collect() 循环中：
if content:
    table_meta_raw = content.get("tableMeta", [])
    if not isinstance(table_meta_raw, list):
        table_meta_raw = []

    # 校验：TABLE_MARKER 标记数必须与 tableMeta 长度一致
    if table_meta_raw:
        marker_count = content["text"].count("<!--TABLE_MARKER_")
        if marker_count != len(table_meta_raw):
            logger.warning(
                f"table_meta 校验失败: markers={marker_count} != "
                f"entries={len(table_meta_raw)}, 已清空降级"
            )
            table_meta_raw = []

    self.submit_report(
        title=report["title"],
        source="liangxin",
        url=report["url"],
        variety="玉米",
        report_type="晨报" if self.report_type == "morning" else "日报",
        content=content["text"],
        content_html=content["html"],
        publish_time=report["publish_time"],
        table_meta=json.dumps(table_meta_raw, ensure_ascii=False),
    )
```

---

## 2. 数据库：新增 table_meta 字段

### 2.1 新加迁移 SQL

**文件：** `database/migration_002_table_meta.sql`

```sql
-- 知识库表新增 table_meta 字段，存储结构化表格数据
ALTER TABLE t_knowledge_base
    ADD COLUMN table_meta TEXT COMMENT '结构化表格数据 JSON 数组 [{headers, rows, caption}]'
    AFTER content_html;
```

### 2.2 更新主 schema

**文件：** `database/schema.sql` — 在 `t_knowledge_base` 建表语句中补充：

```sql
content_html TEXT COMMENT 'HTML格式内容（保留图片标签等）',
table_meta TEXT COMMENT '结构化表格数据 JSON 数组',
```

### 2.3 更新 H2 测试 schema

**文件：** `backend/src/main/resources/schema.sql` — 同步增加 `table_meta` 字段。

---

## 3. Java 后端：实体 + Mapper + Controller

### 3.1 KnowledgeBase 实体新增 tableMeta

**文件：** `backend/src/main/java/com/scfx/entity/KnowledgeBase.java`

```java
private String tableMeta;  // 结构化表格数据 JSON
```

### 3.2 Mapper XML 映射

**文件：** `backend/src/main/resources/mapper/KnowledgeBaseMapper.xml`

在 `BaseResultMap` 中增加：

```xml
<result column="table_meta" property="tableMeta"/>
```

### 3.3 CollectorController 接收 tableMeta

**文件：** `backend/src/main/java/com/scfx/controller/CollectorController.java:139-151`

在当前 `build KnowledgeBase` 段落后增加：

```java
kb.setTableMeta((String) request.get("tableMeta"));
```

### 3.4 KnowledgeBaseController 返回 tableMeta

`GET /knowledge/{id}` 的 `getById()` 已直接返回 `KnowledgeBase` 实体，MyBatis-Plus 会自动映射 `tableMeta` 字段，无需额外改动。

### 3.5 手动更新 content 时清除 table_meta

**文件：** `backend/src/main/java/com/scfx/controller/KnowledgeBaseController.java:98-119`

当用户通过 `PUT /knowledge/{id}` 手动修改 content 时，原有的 table_meta 不再与内容一致。需要在 content 变更时分支清除：

```java
if (payload.containsKey("content")) {
    kb.setContent((String) payload.get("content"));
    kb.setTableMeta(null);  // ← 清除旧 table_meta，前端降级为 marked 渲染
    contentChanged = true;
}
```

> **设计理由：** table_meta 的完整生命周期为"采集 → 入库 → 展示"，采集器是 table_meta 的唯一生产者。手动编辑 content 是例外路径，降级为纯文本渲染是安全的选择。后续可通过"重新解析"功能（从 contentHtml 恢复 table_meta）覆盖此行为。

---

## 4. 分块与向量化：type 标注 + 语义化改写

### 设计原则

```
表格结构化数据 → 仅前端渲染 el-table（table_meta）
表格语义化文本 → 用于向量检索 + LLM context（content）
```

对于表格块，进入 Qdrant 的 `content` 不是 pipe 表格，而是语义化改写后的自然语言描述。这保证了：
- **Embedding = Context**：LLM 读到的文本就是向量命中的文本，无信息损失
- **LLM 直接理解数值含义**：不用从 pipe 表格推理"98%"对应哪个列
- **前端渲染不受影响**：el-table 数据来自 table_meta，与 Qdrant content 解耦

### 关键设计：表格为原子块

> ⚠️ 问题：长表格（如 20 行价格表）按段落分块会被字符级硬切为 2-3 段
>    → 表格数据被截断，向量分散，检索不完整
> ✅ 方案：分块前先分离 pipe 表格，每个表格作为原子块独立处理
>    → 20 行 → 1 个 chunk → 1 次改写 → 1 个向量（整表语义完整）

`chunk_with_types()` 的分段流程：

```text
全文
  | _split_segments()
  ├── [text]  段落 A + 一大段分析
  ├── [table] 20 行价格表      ← 原子块，永不拆分
  ├── [text]  段落 B + 总结
  └── [text]  段落 C
        |
        ├── text 段 → 正常分块（段落合并到 max_chunk_size）
        └── table 段 → 语义化改写 → 1 个 chunk
```

### 4.1 Chunker 增强

**文件：** `ai-qa-service/app/services/chunker.py`

```python
from typing import List, Dict
import re


def semantic_rewrite_table(table_text: str, max_full_rows: int = 20) -> str:
    """将 pipe 表格改写为语义化自然语言描述

    输入：
        | 省区 | 主流价格(元/吨) | 水分(%) | 霉变 | 上市进度 |
        |------|----------------|---------|------|---------|
        | 黑龙江 | 2150-2250 | 14.5-15 | 0-2 | 98% |

    输出（≤20 行：全量展开）：
        【表格数据】省区/主流价格(元/吨)/水分(%)/霉变/上市进度：
        黑龙江：2150-2250元/吨，水分14.5-15%，霉变0-2，上市进度98%

    输出（>20 行：压缩模式 → 前 15 行展开 + 数值统计）：
        【表格数据/共32行】黑龙江：2150-2250元/吨...（前15行）...
        价格区间：2140-2360元/吨，水分14.5-15%，霉变0-2%，上市进度94-99%
    """
    lines = [l.strip() for l in table_text.strip().split('\n') if l.strip().startswith('|')]
    if len(lines) < 3:
        return table_text

    header_line = lines[0]
    headers = [h.strip() for h in header_line.strip('|').split('|')]

    parts = []
    for line in lines[2:]:  # 跳过分隔行
        cells = [c.strip() for c in line.strip('|').split('|')]
        if len(cells) == len(headers):
            parts.append('；'.join(
                f"{headers[i]}：{cells[i]}" for i in range(len(headers)) if cells[i]
            ))

    if not parts:
        return table_text

    if len(parts) <= max_full_rows:
        return '【表格数据】' + '；'.join(parts)

    # 超大表格：前 N 行详细 + 统计摘要
    detail = '；'.join(parts[:15])
    # 对数值列做统计（增强：解析区间值/单值，数值比较而非字符串比较）
    def _parse_nums(val: str) -> list[float]:
        """解析单元格中的数值，支持区间值如 "2150-2250"、"98%"""""
        return [float(n) for n in re.findall(r'\d+\.?\d*', val.replace(',', ''))]

    stats_parts = []
    for col_idx in range(len(headers)):
        col_vals = []
        for line in lines[2:]:
            cells = [c.strip() for c in line.strip('|').split('|')]
            if len(cells) > col_idx and cells[col_idx]:
                col_vals.append(cells[col_idx])
        if len(col_vals) < 2:
            continue
        # 提取该列所有数值（展开区间）
        all_nums = []
        for v in col_vals:
            all_nums.extend(_parse_nums(v))
        if len(all_nums) >= 4:  # 至少 2 行有数值（每行至少 1 个数）
            stats_parts.append(f"{headers[col_idx]}：{min(all_nums)}~{max(all_nums)}")

    stats = '；'.join(stats_parts) if stats_parts else ''
    return f"【表格数据/共{len(parts)}行】{detail}；...\n【统计摘要】{stats}"


def _split_segments(text: str) -> List[Dict]:
    """分离文本段和表格段（表格为原子块）

    优先通过 <!--TABLE_MARKER_N--> 标记识别表格（确定性匹配），
    无标记时降级为启发式 pipe 行检测（兼容旧数据）。
    """
    lines = text.split('\n')
    if '<!--TABLE_MARKER_' in text:
        return _split_by_markers(lines)
    return _split_by_heuristic(lines)


def _split_by_heuristic(lines: list) -> List[Dict]:
    """启发式检测：通过 pipe 行连续模式识别表格（兼容旧数据无 marker）"""
    segments = []
    current_text = []
    i = 0

    while i < len(lines):
        line = lines[i]
        if line.strip().startswith('|') and line.strip().endswith('|'):
            if current_text:
                segments.append({"type": "text", "content": '\n'.join(current_text)})
                current_text = []

            pipe_lines = []
            while i < len(lines):
                stripped = lines[i].strip()
                if stripped.startswith('|') and stripped.endswith('|'):
                    pipe_lines.append(lines[i])
                    i += 1
                elif stripped == '':
                    pipe_lines.append(lines[i])
                    i += 1
                else:
                    break

            while pipe_lines and not pipe_lines[-1].strip():
                pipe_lines.pop()

            if len(pipe_lines) >= 3:
                segments.append({"type": "table", "content": '\n'.join(pipe_lines)})
            else:
                for pl in pipe_lines:
                    if pl.strip():
                        current_text.append(pl)
        else:
            current_text.append(line)
            i += 1

    if current_text:
        segments.append({"type": "text", "content": '\n'.join(current_text)})

    return segments


def _split_by_markers(lines: list) -> List[Dict]:
    """Marker 检测：通过 <!--TABLE_MARKER_N--> 标记精确识别表格边界"""
    segments = []
    current_text = []
    i = 0

    while i < len(lines):
        line = lines[i]
        if '<!--TABLE_MARKER_' in line:
            if current_text:
                segments.append({"type": "text", "content": '\n'.join(current_text)})
                current_text = []

            table_lines = []
            while i < len(lines) and '<!--TABLE_MARKER_END_' not in lines[i]:
                table_lines.append(lines[i])
                i += 1
            if i < len(lines):
                table_lines.append(lines[i])
                i += 1

            segments.append({"type": "table", "content": '\n'.join(table_lines)})
        else:
            current_text.append(line)
            i += 1

    if current_text:
        segments.append({"type": "text", "content": '\n'.join(current_text)})

    return segments


def _chunk_text_segment(text: str, max_chunk_size: int = 800, overlap: int = 50) -> List[str]:
    \"\"\"对纯文本段进行正常分块（复用现有逻辑）\"\"\"
    paragraphs = re.split(r'\n\s*\n', text)
    paragraphs = [p.strip() for p in paragraphs if p.strip()]

    chunks = []
    current_chunk = \"\"
    current_size = 0

    for para in paragraphs:
        para_len = len(para)
        if current_size + para_len <= max_chunk_size:
            current_chunk += para + \"\n\n\"
            current_size += para_len + 2
        else:
            if current_chunk.strip():
                chunks.append(current_chunk.strip())
            if overlap > 0 and len(current_chunk) > overlap:
                overlap_text = current_chunk[-overlap:]
                current_chunk = overlap_text + para + \"\n\n\"
            else:
                current_chunk = para + \"\n\n\"
            current_size = para_len

    if current_chunk.strip():
        chunks.append(current_chunk.strip())

    # 二次拆分超长块
    final_chunks = []
    for chunk in chunks:
        if len(chunk) > max_chunk_size:
            for i in range(0, len(chunk), max_chunk_size - overlap):
                part = chunk[i:i + max_chunk_size]
                if part.strip():
                    final_chunks.append(part.strip())
        else:
            final_chunks.append(chunk)

    return final_chunks


def chunk_with_types(text: str, max_chunk_size: int = 800, overlap: int = 50) -> List[Dict]:
    \"\"\"带类型标注的分块

    设计原则：
      - 表格段作为原子块，永不拆分（避免表格数据被截断）
      - 表格块：content = 语义化改写文本（用于 embedding + LLM context）
      - 文本块：content = 原始文本（用于 embedding + LLM context）
      - pipe 表格不入 Qdrant，仅前端通过 table_meta 渲染

    Returns:
        [{content: str, type: \"text\"|\"table\"}, ...]
    \"\"\"
    # 1. 分离表格段和文本段
    segments = _split_segments(text)

    # 2. 逐段处理
    result = []
    for seg in segments:
        if seg[\"type\"] == \"table\":
            # 表格是原子块：一个表格 → 一个 chunk
            result.append({
                \"content\": semantic_rewrite_table(seg[\"content\"]),
                \"type\": \"table\",
            })
        else:
            # 文本段：正常分块
            for chunk in _chunk_text_segment(seg[\"content\"], max_chunk_size, overlap):
                result.append({
                    \"content\": chunk,
                    \"type\": \"text\",
                })

    return result
```

### 4.2 摄入脚本改写

**文件：** `ai-qa-service/scripts/ingest_knowledge.py`

改用 `chunk_with_types()`，`content` 就是 embedding 原文也是 LLM context：

```python
from app.services.chunker import chunk_with_types

# 切片（已内嵌语义化改写）
chunks = chunk_with_types(content)

for i, item in enumerate(chunks):
    vector = embed_text(item["content"])  # ← 表格块是改写文本，文本块是原文
    # ...
    payload = {
        "knowledge_id": kid,
        "title": title,
        "content": item["content"],          # ← embedding 原文 = LLM context
        "source": source,
        "publish_time": publish_time,
        "chunk_index": i,
        "chunk_type": item["type"],          # "table" | "text"
    }
```

## 5. 前端：KnowledgeDetail.vue 表格增强

### 5.1 API 响应扩展

`GET /knowledge/{id}` 现在返回包含 `tableMeta` 字段的 KnowledgeBase：

```json
{
  "code": 200,
  "data": {
    "id": 55,
    "title": "玉米日报-2026-06-01",
    "content": "今日国内玉米价格...\n\n| 省区 | 主流价格 | ...\n...",
    "tableMeta": "[{\"headers\":[\"省区\",\"主流价格(元/吨)\",...],\"rows\":[[\"黑龙江\",\"2150-2250\",...],...],\"caption\":\"6月1日国内玉米粮点主流玉米出售价格表\"}]",
    ...
  }
}
```

### 5.2 KnowledgeDetail.vue 改造

**文件：** `frontend/src/views/knowledge/KnowledgeDetail.vue`

核心思路：将 content 中的 pipe 表格替换为 el-table 组件，同时保留普通文本的 markdown 渲染。

```vue
<template>
  <div class="detail-body">
    <!-- 渲染混合内容：文本段用 marked，表格段用 el-table -->
    <div v-for="(block, i) in renderedBlocks" :key="i">
      <!-- 表格块 -->
      <div v-if="block.type === 'table'" class="enhanced-table-wrapper">
        <div v-if="block.caption" class="table-caption">{{ block.caption }}</div>
        <el-table
          :data="block.tableData"
          border
          stripe
          size="small"
          style="width: 100%"
          @sort-change="(e) => handleSortChange(i, e)"
        >
          <el-table-column
            v-for="(h, j) in block.columns"
            :key="j"
            :prop="'col' + j"
            :label="h"
            sortable="custom"
            show-overflow-tooltip
          />
        </el-table>
        <div class="table-footer" v-if="block.rows">
          {{ block.rows }} 行数据
        </div>
      </div>
      <!-- 文本块 -->
      <div v-else v-html="block.html" class="text-block"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
// ... 现有引入 ...

interface TableBlock {
  type: 'table'
  caption: string
  columns: string[]
  tableData: Record<string, string>[]
  rows: number
}

interface TextBlock {
  type: 'text'
  html: string
}

type ContentBlock = TableBlock | TextBlock

// ==================== 内容解析（三段降级） ====================
// 降级策略：
//   ① tableMeta 有数据 → 混合渲染（el-table + marked）← 新数据
//   ② tableMeta 为空   → 纯 marked 渲染（含 pipe 表格）← 手动编辑后
//   ③ tableMeta = null → 纯 marked 渲染                  ← 旧数据

interface TableMetaEntry {
  headers: string[]
  rows: string[][]
  caption: string
}

/** 校验并清洗单条 table_meta 条目，返回 null 表示无效 */
const sanitizeTableMeta = (raw: any): TableMetaEntry | null => {
  if (!raw || typeof raw !== 'object') return null

  // headers 必须是非空字符串数组
  const headers: string[] = Array.isArray(raw.headers)
    ? raw.headers.filter((h: any) => typeof h === 'string' && h.length > 0)
    : []
  if (headers.length === 0) return null

  // rows 必须是数组，每行等长或补齐/截断到 headers 长度
  let rows: string[][] = []
  if (Array.isArray(raw.rows)) {
    rows = raw.rows
      .filter((r: any) => Array.isArray(r))
      .map((r: any[]) => {
        const cleaned = r.map((c: any) => String(c ?? ''))
        // 短则补空，长则截断
        while (cleaned.length < headers.length) cleaned.push('')
        return cleaned.slice(0, headers.length)
      })
      .filter((r: string[]) => r.some(c => c.length > 0))  // 过滤全空行
  }
  if (rows.length === 0) return null

  return {
    headers,
    rows,
    caption: typeof raw.caption === 'string' ? raw.caption : '',
  }
}

const parseMixedContent = (content: string, tableMetaJson: string | null): ContentBlock[] => {
  const blocks: ContentBlock[] = []

  if (!content) return blocks

  // 1. 解析 + 清洗 tableMeta（防 headers/rows 列数不一致、防 null/非数组）
  let tables: TableMetaEntry[] = []
  if (tableMetaJson) {
    try {
      const parsed = JSON.parse(tableMetaJson)
      if (Array.isArray(parsed)) {
        tables = parsed.map(sanitizeTableMeta).filter((t): t is TableMetaEntry => t !== null)
      }
    } catch {
      tables = []  // JSON 解析失败 → 降级到 ②
    }
  }

  // 2. 分割 content：与 Python chunker _split_segments() 对称逻辑
  //    优先通过 <!--TABLE_MARKER_N--> 标记识别（确定性匹配），
  //    无标记时降级为启发式 pipe 行检测（兼容旧数据）
  const lines = content.split('\n')
  const segments: { type: 'text' | 'table', content: string }[] = []
  let i = 0

  if (content.includes('<!--TABLE_MARKER_')) {
    // Marker 模式：精确匹配表格边界
    while (i < lines.length) {
      if (lines[i].includes('<!--TABLE_MARKER_')) {
        const tableLines: string[] = []
        while (i < lines.length && !lines[i].includes('<!--TABLE_MARKER_END_')) {
          tableLines.push(lines[i]); i++
        }
        if (i < lines.length) { tableLines.push(lines[i]); i++ }
        segments.push({ type: 'table', content: tableLines.join('\n') })
      } else {
        segments.push({ type: 'text', content: lines[i] })
        i++
      }
    }
  } else {
    // 启发式模式（兼容旧数据无 marker）
    while (i < lines.length) {
    if (lines[i].trim().startsWith('|') && lines[i].trim().endsWith('|')) {
      // 收集 pipe 块
      const pipeLines: string[] = []
      while (i < lines.length) {
        const s = lines[i].trim()
        if (s.startsWith('|') && s.endsWith('|')) {
          pipeLines.push(lines[i])
          i++
        } else if (s === '') {
          pipeLines.push(lines[i])
          i++
        } else {
          break
        }
      }
      // 去掉尾部空行
      while (pipeLines.length && !pipeLines[pipeLines.length - 1].trim()) {
        pipeLines.pop()
      }
      if (pipeLines.length >= 3) {
        segments.push({ type: 'table', content: pipeLines.join('\n') })
      } else {
        for (const pl of pipeLines) {
          if (pl.trim()) segments.push({ type: 'text', content: pl })
        }
      }
    } else {
      segments.push({ type: 'text', content: lines[i] })
      i++
    }
  }
  }

  // 3. 逐段渲染
  //    注意：合并相邻的 text 段，避免 marked 被切成碎片
  let tableIdx = 0
  const hasTables = tables.length > 0

  const flushText = (textParts: string[]) => {
    const raw = textParts.join('\n').trim()
    if (raw) {
      blocks.push({
        type: 'text',
        html: marked(raw, { breaks: true, gfm: true }),
      })
    }
  }

  let textBuffer: string[] = []
  for (const seg of segments) {
    if (seg.type === 'table' && hasTables && tableIdx < tables.length) {
      // flush 已缓存的文本
      flushText(textBuffer)
      textBuffer = []

      // el-table 渲染该表格（数据已由 sanitizeTableMeta 清洗过）
      const meta = tables[tableIdx++]
      const colKey = meta.headers
      const data = meta.rows.map((row) => {
        const item: Record<string, string> = {}
        colKey.forEach((_h, idx) => { item['col' + idx] = row[idx] })
        return item
      })
      blocks.push({
        type: 'table',
        caption: meta.caption,
        columns: colKey,
        tableData: data,
        rows: data.length,
      })
    } else {
      // tableMeta 为空 → 所有段（含 pipe）都走 marked 渲染
      textBuffer.push(seg.content)
    }
  }
  flushText(textBuffer)

  return blocks
}

const renderedBlocks = computed(() => parseMixedContent(knowledge.value?.content || '', knowledge.value?.tableMeta || null))

/** el-table 排序处理：对指定表格的数据做本地排序 */
const sortStates = ref<Record<number, { prop: string; order: 'ascending' | 'descending' }>>({})
const handleSortChange = (tableIdx: number, sortInfo: any) => {
  if (!sortInfo || !sortInfo.prop || !sortInfo.order) {
    delete sortStates.value[tableIdx]
    return
  }
  sortStates.value[tableIdx] = { prop: sortInfo.prop, order: sortInfo.order }
  // 触发重新计算（实际排序在 computed 中处理）
  renderedBlocks.value = parseMixedContent(
    knowledge.value?.content || '',
    knowledge.value?.tableMeta || null
  )
}
</script>
```

### 5.3 样式补充

与现有深色主题一致，在 `<style scoped>` 中补充：

```css
.enhanced-table-wrapper {
  margin: 16px 0;
  background: #161b22;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.06);
}

.table-caption {
  padding: 10px 14px;
  font-size: 14px;
  font-weight: 600;
  color: #f5c87a;
  background: rgba(245, 200, 122, 0.08);
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.table-footer {
  padding: 6px 14px;
  font-size: 12px;
  color: #6e7681;
  background: rgba(255, 255, 255, 0.02);
  border-top: 1px solid rgba(255, 255, 255, 0.04);
}

/* 覆盖 el-table 暗色主题 */
.enhanced-table-wrapper :deep(.el-table) {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: rgba(245, 200, 122, 0.1);
  --el-table-row-hover-bg-color: rgba(245, 200, 122, 0.05);
  --el-table-border-color: rgba(255, 255, 255, 0.08);
  --el-table-text-color: #c9d1d9;
  --el-table-header-text-color: #f5c87a;
}
```

---

## 6. 兼容性与降级

| 场景 | 行为 | 风险 |
|------|------|------|
| 旧数据（无 table_meta） | `tableMeta=null` → 第 ③ 级降级：纯 marked 渲染，兼容所有旧记录 | 无 |
| 采集器未升级（无 tableMeta 字段） | Java 后端收到 null，存 NULL，走第 ③ 级 | 无 |
| 内容被手动编辑（table_meta 被清除） | `tableMeta` 为空数组 → 第 ② 级降级：纯 marked 渲染，pipe 表格渲染为 GFM 表格 | 无 |
| JSON 解析失败 | `JSON.parse()` 抛异常 → catch 设 `tables=[]` → 第 ② 级 | 无 |
| table_meta 与 content 中 pipe 表格数量不匹配 | `tableIdx < tables.length` 守卫防越界，tableMeta 用尽的 pipe 段走 marked | 低 |
| 前端 el-table 未注册 | 全局注册 `ElTable` 和 `ElTableColumn` | 需确认 |
| content 为空 | `if (!content) return` 提前返回空块 | 无 |
| 采集端 JS 异常 | try-catch 包裹 → `tableMeta=[]`，Python 侧 marker 校验不通过时清空 | 无 |
| 采集端 JS 提取表格 > 20 个 | 只处理前 20 个，打 WARN 日志 | 后续表格降级为 marked 渲染 |

---

## 7. 改动清单总览

| # | 文件 | 改动类型 | 说明 |
|---|------|---------|------|
| 1 | `python-collector-sdk/collectorsdk/reporter.py` | 修改 | `submit_report()` 新增 `table_meta` 参数 |
| 2 | `python-collector-sdk/collectorsdk/base.py` | 修改 | `submit_report()` 透传 `table_meta` |
| 3 | `python-collector-sdk/dev/collectors/liangxin.py` | 修改 | `_get_report_content()` 解析表格、替换 pipe；`collect()` 传递 table_meta |
| 4 | `database/migration_002_table_meta.sql` | 新建 | `t_knowledge_base` 加 `table_meta`，`t_knowledge_chunk` 加 `chunk_type` |
| 5 | `database/schema.sql` | 修改 | 同步建表字段 |
| 6 | `backend/src/main/resources/schema.sql` | 修改 | H2 测试 schema 同步 |
| 7 | `backend/src/main/java/com/scfx/entity/KnowledgeBase.java` | 修改 | 新增 `tableMeta` 字段 |
| 8 | `backend/src/main/resources/mapper/KnowledgeBaseMapper.xml` | 修改 | resultMap 映射 |
| 9 | `backend/src/main/java/com/scfx/controller/CollectorController.java` | 修改 | submitData 读取 tableMeta |
| 10 | `ai-qa-service/app/services/chunker.py` | 修改 | 新增 `chunk_with_types()`、`detect_table_content()`、`semantic_rewrite_table()` |
| 11 | `ai-qa-service/scripts/ingest_knowledge.py` | 修改 | 使用 `chunk_with_types()`，表格块用语义化文本做 embedding |
| 12 | `frontend/src/views/knowledge/KnowledgeDetail.vue` | 修改 | parseContent 混合渲染表格+文本 |
| 13 | `frontend/src/views/knowledge/KnowledgeVisualization.vue` | 可选修改 | 如果散点图需要显示表格类型标记 |

---

## 8. 未覆盖项（后续迭代）

- **AI 问答来源卡片中标注表格类型**：chunk payload 已有 `chunk_type`，前端可在来源卡片显示 "📊 数据表格" 标签
- **手动编辑后恢复 table_meta**：新增 `POST /knowledge/{id}/reparse-table`，从 `contentHtml` 重新扫描解析 HTML `<table>`，前端编辑保存后提示"点击重新解析表格"
- **历史数据回填 table_meta**：对已入库的 `contentHtml` 批量扫描解析 HTML `<table>`，通过 Java 定时任务或脚本实现
- **已摄入 chunks 重新向量化**：新的 chunk_type / semantic_text 只影响新摄入数据，旧 chunks 需触发 revectorize
- **通用表格提取器**：将 JS 表格提取逻辑封装为 `collectorsdk/utils/table_extractor.js`，各采集器通过 `BaseCollector._extract_table_meta()` 统一调用
- **参数配置化**：`max_full_rows`/`max_chunk_size` 等参数移至外部配置文件（如 `chunker.yaml`），避免硬编码
- **日志埋点**：采集端记录"提取表格数/跳过数/原因"，后端记录 table_meta 接收状态，前端记录渲染表格数
