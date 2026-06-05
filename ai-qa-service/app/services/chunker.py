import re
from typing import Dict, List

def chunk_text(text: str, min_chunk_size: int = 500, max_chunk_size: int = 800, overlap: int = 50) -> list:
    """将文本分块，每块 500-800 字，块之间保留重叠

    Deprecated: 仅作为 revectorize 降级路径保留（旧数据无 t_knowledge_chunk 时使用）。
    新数据切片由 Java DocumentPipeline + TextSplitter 统一处理。
    """
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
            if current_chunk:
                chunks.append(current_chunk.strip())
            if overlap > 0 and len(current_chunk) > overlap:
                overlap_text = current_chunk[-overlap:]
                current_chunk = overlap_text + para + "\n\n"
            else:
                current_chunk = para + "\n\n"
            current_size = len(para)

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


def semantic_rewrite_table(table_text: str, max_full_rows: int = 20) -> str:
    """将 pipe 表格改写为语义化自然语言描述。

    ≤max_full_rows 行：全量展开
    >max_full_rows 行：前 15 行详细 + 数值列统计
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
            result.append({"content": content, "type": "table"})
        else:
            for chunk in _chunk_text_segment(seg["content"], max_chunk_size, overlap):
                result.append({"content": chunk, "type": "text"})
    return result
