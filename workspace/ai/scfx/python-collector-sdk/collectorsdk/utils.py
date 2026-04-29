"""
工具函数模块
"""

import re
import uuid
from datetime import datetime, timezone
from typing import Optional


def generate_execution_id() -> str:
    """生成唯一执行ID"""
    return str(uuid.uuid4())


def parse_publish_time(time_str: str) -> Optional[str]:
    """
    解析发布时间字符串为 ISO 格式

    支持格式：
    - 2026-04-28
    - 2026-04-28 10:30:00
    - 2026年4月28日
    - 2026/04/28
    """
    if not time_str:
        return None

    # 清理空白字符
    time_str = time_str.strip()

    # 尝试标准格式
    formats = [
        "%Y-%m-%d %H:%M:%S",
        "%Y-%m-%d",
        "%Y/%m/%d",
        "%Y年%m月%d日",
        "%Y年%m月%d日 %H:%M:%S",
    ]

    for fmt in formats:
        try:
            dt = datetime.strptime(time_str, fmt)
            return dt.isoformat()
        except ValueError:
            continue

    return None


def extract_report_type(title: str) -> str:
    """
    从标题提取报告类型

    Args:
        title: 报告标题

    Returns:
        报告类型：晨报/日报/周报/月报/专题/其他
    """
    if not title:
        return "其他"

    if "晨报" in title:
        return "晨报"
    elif "日报" in title:
        return "日报"
    elif "周报" in title:
        return "周报"
    elif "月报" in title:
        return "月报"
    elif "专题" in title:
        return "专题"
    elif "资讯" in title:
        return "资讯"
    else:
        return "其他"


def extract_variety(title: str) -> str:
    """
    从标题提取品种

    Args:
        title: 报告标题

    Returns:
        品种：玉米/小麦/稻米/大豆/其他
    """
    if not title:
        return "其他"

    if "玉米" in title:
        return "玉米"
    elif "小麦" in title:
        return "小麦"
    elif "稻米" in title or "稻谷" in title:
        return "稻米"
    elif "大豆" in title:
        return "大豆"
    elif "大麦" in title:
        return "大麦"
    else:
        return "其他"


def clean_html(html: str, length: int = 2000) -> str:
    """
    清理HTML标签，提取纯文本

    Args:
        html: HTML内容
        length: 最大长度

    Returns:
        清理后的纯文本
    """
    if not html:
        return ""

    # 移除script和style标签
    html = re.sub(r'<script[^>]*>.*?</script>', '', html, flags=re.DOTALL | re.IGNORECASE)
    html = re.sub(r'<style[^>]*>.*?</style>', '', html, flags=re.DOTALL | re.IGNORECASE)

    # 移除HTML标签
    text = re.sub(r'<[^>]+>', '', html)

    # 清理空白字符
    text = re.sub(r'\s+', ' ', text).strip()

    # 截断
    if len(text) > length:
        text = text[:length] + "..."

    return text


def get_local_timestamp() -> int:
    """获取本地时间戳（毫秒）"""
    return int(datetime.now(timezone.utc).timestamp() * 1000)


def mask_sensitive(text: str) -> str:
    """
    脱敏处理（用于日志输出）

    Args:
        text: 原始文本

    Returns:
        脱敏后的文本
    """
    if not text:
        return ""

    # 密码脱敏
    if len(text) > 4:
        return text[:2] + "*" * (len(text) - 4) + text[-2:]
    return "****"
