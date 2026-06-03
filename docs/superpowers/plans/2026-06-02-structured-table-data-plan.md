# 财经日报结构化表格数据全链路 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 打通"采集端 HTML 表格解析 → 结构化存储 → 分块语义化改写 → 前端增强渲染"的完整链路，使粮信网玉米日报中的价格表、库存表等结构化数据以可排序 el-table 形式展示。

**Architecture:** 采集器 liangxin.py 中通过 Playwright evaluate() 解析 HTML `<table>` 为 table_meta JSON，经 SDK reporter 透传到 Java 后端存入新增字段；chunking 时用 _split_segments() 分离表格块并做语义化改写（embedding = LLM context）；前端 KnowledgeDetail.vue 混合渲染 el-table（可排序筛选）+ marked 文本。

**Tech Stack:** Python (Playwright, BGE embedding), Java (Spring Boot, MyBatis-Plus), MySQL, Qdrant, Vue 3 (el-table, marked)

---

## 文件改动清单

| # | 文件 | 改动 | 任务 |
|---|------|------|------|
| 1 | `python-collector-sdk/collectorsdk/reporter.py` | `submit_report()` 新增 `table_meta` 参数 | Task 1 |
| 2 | `python-collector-sdk/collectorsdk/base.py` | `submit_report()` 透传 `table_meta` | Task 1 |
| 3 | `python-collector-sdk/dev/collectors/liangxin.py` | JS 表格提取 + marker + collect() 校验 | Task 2 |
| 4 | `database/migration_002_table_meta.sql` | t_knowledge_base 加 table_meta TEXT | Task 3 |
| 5 | `database/schema.sql` | 同步建表字段 | Task 3 |
| 6 | `backend/src/main/resources/schema.sql` | H2 测试 schema 同步 | Task 3 |
| 7 | `backend/src/main/java/com/scfx/entity/KnowledgeBase.java` | 新增 `tableMeta` 字段 | Task 4 |
| 8 | `backend/src/main/resources/mapper/KnowledgeBaseMapper.xml` | resultMap 映射 | Task 4 |
| 9 | `backend/src/main/java/com/scfx/controller/CollectorController.java` | submitData 读取 tableMeta | Task 4 |
| 10 | `backend/src/main/java/com/scfx/controller/KnowledgeBaseController.java` | PUT content 时清除 tableMeta | Task 4 |
| 11 | `ai-qa-service/app/services/chunker.py` | 新增 chunk_with_types + 辅助函数 | Task 5 |
| 12 | `ai-qa-service/scripts/ingest_knowledge.py` | 改用 chunk_with_types | Task 6 |
| 13 | `frontend/src/views/knowledge/KnowledgeDetail.vue` | el-table 混合渲染 + 排序 | Task 7 |

---

### Task 1: Python SDK 基础层 — table_meta 参数透传

**Files:**
- Modify: `python-collector-sdk/collectorsdk/reporter.py:444-468`
- Modify: `python-collector-sdk/collectorsdk/base.py:274-311`

- [ ] **Step 1: CollectorReporter.submit_report() 增加 table_meta 参数**

修改 `python-collector-sdk/collectorsdk/reporter.py` 中的 `submit_report()` 方法签名和 data 字典：

```python
def submit_report(self, title: str, source: str, url: str, variety: str = "",
                  report_type: str = "", content: str = "", content_html: str = "",
                  publish_time: str = "", table_meta: str = ""):  # ← 新增
    """
    Args:
        table_meta: 结构化表格数据 JSON 字符串（由采集器在提取 HTML 时生成）
    """
    data = {
        "title": title,
        "source": source,
        "url": url,
        "variety": variety,
        "reportType": report_type,
        "content": content,
        "contentHtml": content_html,
        "publishTime": publish_time,
        "tableMeta": table_meta,  # ← 新增
    }
    self.report_data(data)
```

- [ ] **Step 2: BaseCollector.submit_report() 透传 table_meta**

修改 `python-collector-sdk/collectorsdk/base.py` 中的 `submit_report()` 方法：

```python
def submit_report(
    self,
    title: str,
    source: str,
    url: str,
    variety: str = "",
    report_type: str = "",
    content: str = "",
    content_html: str = "",
    publish_time: str = "",
    table_meta: str = "",       # ← 新增
):
    if not content or not content.strip():
        logger.warning(f"报告内容为空，跳过提交: {title}")
        self._error_count += 1
        return
    self._reporter.submit_report(
        title=title,
        source=source,
        url=url,
        variety=variety,
        report_type=report_type,
        content=content,
        content_html=content_html,
        publish_time=publish_time,
        table_meta=table_meta,  # ← 新增
    )
```

- [ ] **Step 3: 验证**

```bash
cd /Users/hucong/workspace/ai/scfx/python-collector-sdk
python3 -c "
from collectorsdk.reporter import CollectorReporter
from collectorsdk.config import ReporterConfig
import inspect
sig = inspect.signature(CollectorReporter.submit_report)
assert 'table_meta' in sig.parameters, 'table_meta parameter missing'
print('✅ table_meta parameter added to CollectorReporter.submit_report()')
"
```

- [ ] **Step 4: Commit**

```bash
git add python-collector-sdk/collectorsdk/reporter.py python-collector-sdk/collectorsdk/base.py
git commit -m "feat(collector-sdk): add table_meta parameter to submit_report chain"
```

---

### Task 2: LiangxinCollector — 表格提取 + marker + 校验

**Files:**
- Modify: `python-collector-sdk/dev/collectors/liangxin.py`

- [ ] **Step 1: 修改 `_get_report_content()` — 在 evaluate() 中增加表格提取**

定位到 `_get_report_content()` 方法中的 `content_elem.evaluate()` 调用（约原第 356-386 行）。提取逻辑位于 content_elem 的 DOM 操作中。

需要将 evaluete 返回值从 `{text, html}` 改为 `{text, html, tableMeta}`。

在 evaluate 函数体内部，在现有的干扰元素移除代码之后、返回语句之前，插入表格提取逻辑：

```javascript
// ==== 新增：表格提取 + pipe 替换（单次遍历，保证 1:1 对应）====
let tableMeta = [];
try {
    const isNested = (el) => {
        let p = el.parentElement;
        while (p && p !== clone) { if (p.tagName === 'TABLE') return true; p = p.parentElement; }
        return false;
    };
    const expandRow = (cells, carry) => {
        const row = []; let col = 0;
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
                if (rs > 1) carry.push({ col: col + c2, text, remain: rs - 1 });
            }
            col += cs;
        });
        return row;
    };
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
        const dataRows = []; const carry = [];
        for (let ri = dataStart; ri < trs.length; ri++) {
            const rd = expandRow(trs[ri].querySelectorAll('td, th'), carry);
            if (rd.length === headers.length && rd.some(c => c.length > 0)) dataRows.push(rd);
        }
        if (dataRows.length === 0) return;
        // 表格标题
        let caption = ''; let s = table.previousElementSibling;
        for (let tries = 0; tries < 3 && s; tries++) {
            const t = s.textContent.trim();
            if (t.length > 0 && t.length < 100) { caption = t; break; }
            s = s.previousElementSibling;
        }
        const markerIdx = tableMeta.length;
        tableMeta.push({ headers, rows: dataRows, caption });
        // 生成 pipe 文本（含 marker）
        const pipeLines = ['<!--TABLE_MARKER_' + markerIdx + '-->',
            '| ' + headers.join(' | ') + ' |',
            '|' + headers.map(() => '---').join('|') + '|'];
        for (const row of dataRows) pipeLines.push('| ' + row.join(' | ') + ' |');
        pipeLines.push('<!--TABLE_MARKER_END_' + markerIdx + '-->');
        const pipeText = document.createTextNode('\n' + pipeLines.join('\n') + '\n');
        table.parentNode.replaceChild(pipeText, table);
    });
} catch (e) {
    tableMeta = [];
}
// ==== 结束：表格提取 ====

// 修改返回值：
return {
    text: clone.textContent.trim(),
    html: clone.innerHTML.trim(),
    tableMeta: tableMeta,
};
```

- [ ] **Step 2: 修改 `collect()` — 接收 tableMeta + 校验 + 传递**

在 `collect()` 方法中，`content = self._get_report_content(report["url"])` 调用后，接收返回值并增加校验：

```python
content = self._get_report_content(report["url"])
if content:
    import json
    import re

    table_meta_raw = content.get("tableMeta", [])
    if not isinstance(table_meta_raw, list):
        table_meta_raw = []

    # 校验：TABLE_MARKER 数量必须与 tableMeta 长度一致
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
    count += 1
    self._success_count += 1
```

- [ ] **Step 3: 日志增加表格提取统计**

在 collect() 方法的报告循环结束后，增加日志：

```python
self.log_info(
    f"采集完成: {count} 篇报告，提取表格 {sum(1 for ...)} 个",
    phase="report", category="metric"
)
```

- [ ] **Step 4: 提交**

```bash
git add python-collector-sdk/dev/collectors/liangxin.py
git commit -m "feat(liangxin): extract HTML tables to table_meta with markers"
```

---

### Task 3: 数据库 — 新增 table_meta 字段

**Files:**
- Create: `database/migration_002_table_meta.sql`
- Modify: `database/schema.sql`
- Modify: `backend/src/main/resources/schema.sql`

- [ ] **Step 1: 创建迁移脚本**

`database/migration_002_table_meta.sql`:

```sql
-- 知识库表新增 table_meta 字段，存储结构化表格数据 JSON 数组
-- 格式: [{"headers": ["省区","价格"], "rows": [["黑龙江","2150"],...], "caption": "..."}]
ALTER TABLE t_knowledge_base
    ADD COLUMN table_meta TEXT COMMENT '结构化表格数据 JSON 数组 [{headers, rows, caption}]'
    AFTER content_html;
```

- [ ] **Step 2: 更新主 schema**

`database/schema.sql` 中找到 `t_knowledge_base` 的 CREATE TABLE 语句，在 `content_html` 行后增加：

```sql
content_html TEXT COMMENT 'HTML格式内容（保留图片标签等）',
table_meta TEXT COMMENT '结构化表格数据 JSON 数组',
```

- [ ] **Step 3: 更新 H2 测试 schema**

`backend/src/main/resources/schema.sql` 中找到 `t_knowledge_base` 定义，同步增加 `table_meta` 字段。

- [ ] **Step 4: 提交**

```bash
git add database/migration_002_table_meta.sql database/schema.sql backend/src/main/resources/schema.sql
git commit -m "feat(db): add table_meta column to t_knowledge_base"
```

---

### Task 4: Java 后端 — Entity + Mapper + Controller

**Files:**
- Modify: `backend/src/main/java/com/scfx/entity/KnowledgeBase.java`
- Modify: `backend/src/main/resources/mapper/KnowledgeBaseMapper.xml`
- Modify: `backend/src/main/java/com/scfx/controller/CollectorController.java`
- Modify: `backend/src/main/java/com/scfx/controller/KnowledgeBaseController.java`

- [ ] **Step 1: KnowledgeBase 实体新增 tableMeta**

`backend/src/main/java/com/scfx/entity/KnowledgeBase.java` 在现有字段后增加：

```java
private String tableMeta;  // 结构化表格数据 JSON
```

- [ ] **Step 2: Mapper XML 映射**

`backend/src/main/resources/mapper/KnowledgeBaseMapper.xml` 的 `BaseResultMap` 中增加：

```xml
<result column="table_meta" property="tableMeta"/>
```

- [ ] **Step 3: CollectorController 接收 tableMeta**

`backend/src/main/java/com/scfx/controller/CollectorController.java` 在 `submitData()` 方法中，构建 KnowledgeBase 对象后（约第 139-151 行），增加：

```java
kb.setTableMeta((String) request.get("tableMeta"));
```

- [ ] **Step 4: KnowledgeBaseController 手动编辑 content 时清除 tableMeta**

`backend/src/main/java/com/scfx/controller/KnowledgeBaseController.java` 的 `update()` 方法（约第 98-119 行），找到 `if (payload.containsKey("content"))` 分支：

```java
if (payload.containsKey("content")) {
    kb.setContent((String) payload.get("content"));
    kb.setTableMeta(null);  // ← 清除旧 table_meta，前端降级为 marked 渲染
    contentChanged = true;
}
```

- [ ] **Step 5: 验证编译**

```bash
cd /Users/hucong/workspace/ai/scfx/backend
mvn compile -q 2>&1 | tail -5
```

- [ ] **Step 6: 提交**

```bash
git add backend/src/main/java/com/scfx/entity/KnowledgeBase.java \
       backend/src/main/resources/mapper/KnowledgeBaseMapper.xml \
       backend/src/main/java/com/scfx/controller/CollectorController.java \
       backend/src/main/java/com/scfx/controller/KnowledgeBaseController.java
git commit -m "feat(backend): add tableMeta field, clear on content edit"
```

---

### Task 5: Chunker 增强 — 语义化改写 + 原子块分割

**Files:**
- Modify: `ai-qa-service/app/services/chunker.py`

这个任务需要在 `chunker.py` 中新增 4 个函数，原有 `chunk_text()` 保留不动。

- [ ] **Step 1: 新增 `semantic_rewrite_table()`**

```python
import re
from typing import List, Dict


def semantic_rewrite_table(table_text: str, max_full_rows: int = 20) -> str:
    """将 pipe 表格改写为语义化自然语言描述。

    ≤max_full_rows 行：全量展开
    >max_full_rows 行：前 15 行详细 + 数值列统计

    输入：
        | 省区 | 主流价格(元/吨) | 水分(%) | 霉变 | 上市进度 |
        |------|----------------|---------|------|---------|
        | 黑龙江 | 2150-2250 | 14.5-15 | 0-2 | 98% |

    输出：
        【表格数据】省区/主流价格(元/吨)/水分(%)/霉变/上市进度：
        黑龙江：2150-2250元/吨，水分14.5-15%，霉变0-2，上市进度98%
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

    # 超大表格：前 15 行详细 + 统计摘要
    detail = '；'.join(parts[:15])

    def _parse_nums(val: str) -> list[float]:
        """解析单元格中的数值，支持区间值如 '2150-2250'、'98%'"""
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
        all_nums = []
        for v in col_vals:
            all_nums.extend(_parse_nums(v))
        if len(all_nums) >= 4:
            stats_parts.append(f"{headers[col_idx]}：{min(all_nums)}~{max(all_nums)}")

    stats = '；'.join(stats_parts) if stats_parts else ''
    return f"【表格数据/共{len(parts)}行】{detail}；...\n【统计摘要】{stats}"
```

- [ ] **Step 2: 新增 `_split_segments()` + 辅助函数**

```python
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


def _split_segments(text: str) -> List[Dict]:
    """分离文本段和表格段（表格为原子块）

    优先通过 <!--TABLE_MARKER_N--> 标记识别表格（确定性匹配），
    无标记时降级为启发式 pipe 行检测（兼容旧数据）。
    """
    lines = text.split('\n')
    if '<!--TABLE_MARKER_' in text:
        return _split_by_markers(lines)
    return _split_by_heuristic(lines)
```

- [ ] **Step 3: 新增 `_chunk_text_segment()`**

```python
def _chunk_text_segment(text: str, max_chunk_size: int = 800, overlap: int = 50) -> List[str]:
    """对纯文本段进行正常分块"""
    paragraphs = re.split(r'\n\s*\n', text)
    paragraphs = [p.strip() for p in paragraphs if p.strip()]
    chunks = []
    current_chunk = ""
    current_size = 0
    for para in paragraphs:
        para_len = len(para)
        if current_size + para_len <= max_chunk_size:
            current_chunk += para + "\n\n"
            current_size += para_len + 2
        else:
            if current_chunk.strip():
                chunks.append(current_chunk.strip())
            if overlap > 0 and len(current_chunk) > overlap:
                overlap_text = current_chunk[-overlap:]
                current_chunk = overlap_text + para + "\n\n"
            else:
                current_chunk = para + "\n\n"
            current_size = para_len
    if current_chunk.strip():
        chunks.append(current_chunk.strip())
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
```

- [ ] **Step 4: 新增 `chunk_with_types()`**

```python
def chunk_with_types(text: str, max_chunk_size: int = 800, overlap: int = 50) -> List[Dict]:
    """带类型标注的分块

    - 表格段作为原子块，永不拆分
    - 表格块：content = 语义化改写文本（用于 embedding + LLM context）
    - 文本块：content = 原始文本

    Returns:
        [{content: str, type: "text"|"table"}, ...]
    """
    segments = _split_segments(text)
    result = []
    for seg in segments:
        if seg["type"] == "table":
            content = semantic_rewrite_table(seg["content"])
            # 如果改写后仍超长，按行拆分为多个 table chunk
            if len(content) > max_chunk_size:
                # 解析表格，按约 max_full_rows 行拆分
                parts = semantic_rewrite_table(seg["content"], max_full_rows=10)
                # 每部分作为一个独立 table chunk
                for sub_content in parts.split('；...\n'):
                    if sub_content.strip():
                        result.append({"content": sub_content.strip(), "type": "table"})
            else:
                result.append({"content": content, "type": "table"})
        else:
            for chunk in _chunk_text_segment(seg["content"], max_chunk_size, overlap):
                result.append({"content": chunk, "type": "text"})
    return result
```

- [ ] **Step 5: 单元测试**

创建 `ai-qa-service/tests/test_chunker.py`：

```python
import pytest
from app.services.chunker import (
    semantic_rewrite_table,
    _split_segments,
    chunk_with_types,
)

PIPE_TABLE = """| 省区 | 主流价格(元/吨) | 水分(%) | 霉变 | 上市进度 |
|------|----------------|---------|------|---------|
| 黑龙江 | 2150-2250 | 14.5-15 | 0-2 | 98% |
| 吉林 | 2230-2300 | 14.5-15 | 0-2 | 95% |
| 辽宁 | 2280-2340 | 14.5-15 | 0-2 | 99% |"""

MARKER_TABLE = """<!--TABLE_MARKER_0-->
| 省区 | 价格 |
|------|------|
| 黑龙江 | 2150 |
<!--TABLE_MARKER_END_0-->"""


def test_semantic_rewrite_normal():
    result = semantic_rewrite_table(PIPE_TABLE)
    assert result.startswith("【表格数据】")
    assert "黑龙江" in result
    assert "2150-2250" in result


def test_semantic_rewrite_compressed():
    # 超 20 行的表格
    many_rows = PIPE_TABLE + "\n" + "\n".join([f"| 省{i} | 2100 | 14 | 0-2 | 90% |" for i in range(20)])
    result = semantic_rewrite_table(many_rows)
    assert "统计摘要" in result
    assert "共" in result


def test_split_segments_heuristic():
    text = "普通段落\n\n" + PIPE_TABLE + "\n\n更多文字"
    segs = _split_segments(text)
    assert len(segs) == 3
    assert segs[1]["type"] == "table"
    assert segs[0]["type"] == "text"


def test_split_segments_marker():
    text = "开头\n\n" + MARKER_TABLE + "\n\n结尾"
    segs = _split_segments(text)
    assert len(segs) >= 2  # marker 模式至少 2 段
    assert any(s["type"] == "table" for s in segs)


def test_chunk_with_types():
    text = "今日玉米价格\n\n" + PIPE_TABLE + "\n\n市场分析"
    chunks = chunk_with_types(text)
    assert len(chunks) >= 2
    assert chunks[0]["type"] == "text"
    assert any(c["type"] == "table" for c in chunks)
```

```bash
cd /Users/hucong/workspace/ai/scfx/ai-qa-service
python3 -m pytest tests/test_chunker.py -v
```

- [ ] **Step 6: 提交**

```bash
git add ai-qa-service/app/services/chunker.py ai-qa-service/tests/test_chunker.py
git commit -m "feat(chunker): add chunk_with_types with table atomic blocks and semantic rewrite"
```

---

### Task 6: 摄入脚本 — 改用 chunk_with_types

**Files:**
- Modify: `ai-qa-service/scripts/ingest_knowledge.py`

- [ ] **Step 1: 改用 chunk_with_types + 增加 chunk_type 标注**

找到 `ingest_knowledge.py` 中原来的 `from app.services.chunker import chunk_text`，改为：

```python
from app.services.chunker import chunk_with_types
```

然后将切片和向量化循环（约第 68-112 行）修改为：

```python
# 切片（含语义化改写）
chunks = chunk_with_types(content)
if not chunks:
    print(f"  ⏭️  ID={kid} 切片为空，跳过")
    continue

print(f"  📄 ID={kid} 「{title[:40]}」 → {len(chunks)} 个切片")

# 逐片向量化 + 写入 Qdrant
points = []
vector_ids = []
for i, item in enumerate(chunks):
    try:
        vector = embed_text(item["content"])
    except Exception as e:
        print(f"    ❌ 向量化失败 chunk[{i}]: {e}")
        total_errors += 1
        continue

    point_id = str(uuid.uuid4())
    vector_ids.append(point_id)
    points.append({
        "id": point_id,
        "vector": vector,
        "payload": {
            "knowledge_id": kid,
            "title": title,
            "content": item["content"],          # ← embedding 原文 = LLM context
            "source": source,
            "publish_time": publish_time,
            "chunk_index": i,
            "chunk_type": item["type"],          # "table" | "text"
        },
    })
```

- [ ] **Step 2: 验证**

```bash
cd /Users/hucong/workspace/ai/scfx/ai-qa-service
export $(cat ai-qa-service.env | xargs)
python3 -c "
from app.services.chunker import chunk_with_types
result = chunk_with_types('测试文本\n\n| A | B |\n|---|---|\n| 1 | 2 |')
assert result[0]['type'] == 'table'
print('✅ chunk_with_types works')
"
```

- [ ] **Step 3: 提交**

```bash
git add ai-qa-service/scripts/ingest_knowledge.py
git commit -m "feat(ingest): use chunk_with_types, add chunk_type to Qdrant payload"
```

---

### Task 7: 前端 KnowledgeDetail.vue — el-table 混合渲染

**Files:**
- Modify: `frontend/src/views/knowledge/KnowledgeDetail.vue`

- [ ] **Step 1: 在模板中替换 detail-body 为混合渲染**

找到 `<div class="detail-body" v-html="renderedContent"></div>` 替换为：

```vue
<div class="detail-body">
  <!-- 混合渲染：文本用 marked，表格用 el-table -->
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
      <div class="table-footer" v-if="block.rows">{{ block.rows }} 行数据</div>
    </div>
    <!-- 文本块 -->
    <div v-else v-html="block.html" class="text-block"></div>
  </div>
</div>
```

- [ ] **Step 2: 在 script setup 中新增类型定义 + 解析函数 + 排序逻辑**

在 `<script setup lang="ts">` 中，保留现有的 `loading`、`error`、`knowledge`、`formatDate`、`goBack`，将 `renderedContent` 替换为以下内容：

```typescript
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

interface TableMetaEntry {
  headers: string[]
  rows: string[][]
  caption: string
}

// ==================== 三段降级策略 ====================
// ① tableMeta 有数据 → 混合渲染（el-table + marked）
// ② tableMeta 为空   → 纯 marked 渲染（含 GFM pipe 表格）
// ③ tableMeta = null → 纯 marked 渲染（旧数据兼容）

const sanitizeTableMeta = (raw: any): TableMetaEntry | null => {
  if (!raw || typeof raw !== 'object') return null
  const headers: string[] = Array.isArray(raw.headers)
    ? raw.headers.filter((h: any) => typeof h === 'string' && h.length > 0)
    : []
  if (headers.length === 0) return null
  let rows: string[][] = []
  if (Array.isArray(raw.rows)) {
    rows = raw.rows
      .filter((r: any) => Array.isArray(r))
      .map((r: any[]) => {
        const cleaned = r.map((c: any) => String(c ?? ''))
        while (cleaned.length < headers.length) cleaned.push('')
        return cleaned.slice(0, headers.length)
      })
      .filter((r: string[]) => r.some(c => c.length > 0))
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

  // 1. 解析 + 清洗 tableMeta
  let tables: TableMetaEntry[] = []
  if (tableMetaJson) {
    try {
      const parsed = JSON.parse(tableMetaJson)
      if (Array.isArray(parsed)) {
        tables = parsed.map(sanitizeTableMeta).filter((t): t is TableMetaEntry => t !== null)
      }
    } catch {
      tables = []
    }
  }

  // 2. 分割 content（标记优先，启发式降级）
  const lines = content.split('\n')
  const segments: { type: 'text' | 'table', content: string }[] = []
  let i = 0

  if (content.includes('<!--TABLE_MARKER_')) {
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
    while (i < lines.length) {
      if (lines[i].trim().startsWith('|') && lines[i].trim().endsWith('|')) {
        if (segments.length > 0 && segments[segments.length - 1].type === 'text' &&
            !segments[segments.length - 1].content) {
          segments.pop()
        }
        const pipeLines: string[] = []
        while (i < lines.length) {
          const s = lines[i].trim()
          if (s.startsWith('|') && s.endsWith('|')) { pipeLines.push(lines[i]); i++ }
          else if (s === '') { pipeLines.push(lines[i]); i++ }
          else { break }
        }
        while (pipeLines.length && !pipeLines[pipeLines.length - 1].trim()) pipeLines.pop()
        if (pipeLines.length >= 3) {
          segments.push({ type: 'table', content: pipeLines.join('\n') })
        } else {
          for (const pl of pipeLines) { if (pl.trim()) segments.push({ type: 'text', content: pl }) }
        }
      } else {
        segments.push({ type: 'text', content: lines[i] })
        i++
      }
    }
  }

  // 3. 逐段渲染（合并相邻文本段）
  let tableIdx = 0
  const hasTables = tables.length > 0

  const flushText = (textParts: string[]) => {
    const raw = textParts.join('\n').trim()
    if (raw) blocks.push({ type: 'text', html: marked(raw, { breaks: true, gfm: true }) })
  }

  let textBuffer: string[] = []
  for (const seg of segments) {
    if (seg.type === 'table' && hasTables && tableIdx < tables.length) {
      flushText(textBuffer)
      textBuffer = []
      const meta = tables[tableIdx++]
      const colKey = meta.headers
      const data = meta.rows.map((row) => {
        const item: Record<string, string> = {}
        colKey.forEach((_h, idx) => { item['col' + idx] = row[idx] })
        return item
      })
      blocks.push({ type: 'table', caption: meta.caption, columns: colKey, tableData: data, rows: data.length })
    } else {
      textBuffer.push(seg.content)
    }
  }
  flushText(textBuffer)

  return blocks
}

const renderedBlocks = ref<ContentBlock[]>([])

// 排序状态 + 处理（按 tableIdx 找到对应的 table block 做本地排序）
const handleSortChange = (tableIdx: number, sortInfo: any) => {
  const blocks = parseMixedContent(knowledge.value?.content || '', knowledge.value?.tableMeta || null)
  let tableFound = 0
  for (const block of blocks) {
    if (block.type === 'table') {
      if (tableFound === tableIdx && sortInfo && sortInfo.prop && sortInfo.order) {
        const prop = sortInfo.prop as string
        const desc = sortInfo.order === 'descending'
        block.tableData.sort((a: Record<string, string>, b: Record<string, string>) => {
          const na = parseFloat(a[prop]), nb = parseFloat(b[prop])
          if (!isNaN(na) && !isNaN(nb)) return desc ? nb - na : na - nb
          return desc ? b[prop].localeCompare(a[prop]) : a[prop].localeCompare(b[prop])
        })
        break
      }
      tableFound++
    }
  }
  renderedBlocks.value = blocks
}

// 在 onMounted 中增加渲染
onMounted(async () => {
  // ... 现有 API 请求 ...
  // 在获取到 knowledge 后：
  renderedBlocks.value = parseMixedContent(knowledge.value?.content || '', knowledge.value?.tableMeta || null)
})
```

- [ ] **Step 3: 样式补充**

在 `<style scoped>` 中新增：

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

- [ ] **Step 4: 提交**

```bash
git add frontend/src/views/knowledge/KnowledgeDetail.vue
git commit -m "feat(knowledge-detail): mixed el-table + marked rendering with sort"
```

---

## 执行顺序

建议按 **Task 1 → 3 → 4 → 5 → 6 → 2 → 7** 顺序执行：

1. **Task 1** (SDK 基础层) + **Task 3** (DB) — 无依赖，可并行
2. **Task 4** (Java 后端) — 依赖 Task 3（字段已存在）
3. **Task 5** (Chunker) — 独立，可先于采集器
4. **Task 6** (摄入脚本) — 依赖 Task 5
5. **Task 2** (采集器) — 生产数据，需要后端已就绪（Task 4）
6. **Task 7** (前端) — 依赖后端 API 返回 tableMeta

---

## 验证方式

1. **采集端测试**：运行一次粮信网采集，确认日志输出"提取表格 N 个"，检查 `t_knowledge_base.table_meta` 有数据
2. **Chunker 测试**：`python3 -m pytest ai-qa-service/tests/test_chunker.py -v`
3. **摄入测试**：运行 `python3 scripts/ingest_knowledge.py`，确认 Qdrant payload 中有 `chunk_type: "table"`
4. **前端测试**：打开 `/knowledge/{id}` 详情页，验证表格使用 el-table 渲染，可排序
