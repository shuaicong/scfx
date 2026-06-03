"""上下文预处理：将乱序表格数据重排为可读格式"""

import re


def clean_context(text: str) -> str:
    """
    清理上下文字表混合的HTML表格提取物，将散落数据压缩为紧凑可读格式。

    原始数据来自 HTML <table> 提取后的纯文本，每个 <td> 被独立成行，
    行与行之间有空行 (\\n\\n)，导致表格数据完全不可读。
    """
    lines = text.split("\n")
    result = []
    i = 0

    while i < len(lines):
        stripped = lines[i].strip()

        # 空行保留
        if not stripped:
            result.append("")
            i += 1
            continue

        # 尝试收集表格区域：跳过空行，收集连续的短文本行
        table_cells = []
        j = i

        while j < len(lines):
            s = lines[j].strip()
            if not s:
                # 表格内的空行跳过（但记录位置防止死循环）
                j += 1
                continue
            if len(s) < 35 and is_table_cell(s):
                table_cells.append(s)
                j += 1
            else:
                break

        if len(table_cells) >= 4:
            # 压缩为紧凑的管道分隔行
            result.append(" | ".join(table_cells))
            i = j
            continue

        # 不是表格，原样保留
        result.append(lines[i])
        i += 1

    return "\n".join(result)


def is_table_cell(s: str) -> bool:
    """判断是否属于表格单元格内容"""
    # 纯数字行（含 + - 符号）
    if re.match(r"^[\d+\-.,—]+$", s):
        return True
    # 表格常见关键词开头
    table_keywords = [
        "日期", "大连", "CBOT", "元/吨", "美分", "蒲式耳",
        "5月", "6月", "7月", "8月", "9月", "10月", "11月", "12月",
        "——", "2607", "2609", "2611", "2701",
    ]
    for kw in table_keywords:
        if s.startswith(kw):
            return True
    return False
