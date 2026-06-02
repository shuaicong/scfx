import pytest
import sys, os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
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
    assert len(segs) >= 2
    assert any(s["type"] == "table" for s in segs)


def test_chunk_with_types():
    text = "今日玉米价格\n\n" + PIPE_TABLE + "\n\n市场分析"
    chunks = chunk_with_types(text)
    assert len(chunks) >= 2
    assert chunks[0]["type"] == "text"
    assert any(c["type"] == "table" for c in chunks)
