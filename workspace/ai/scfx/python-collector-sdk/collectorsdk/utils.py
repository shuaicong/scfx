#!/usr/bin/env python3
"""
工具函数模块

提供 SDK 所需的通用工具函数：
- MD5 计算
- 文件路径处理
- 日期时间格式化
- JSON 序列化辅助
"""

import hashlib
import json
import os
import re
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, Optional


def calculate_md5(content: str) -> str:
    """
    计算字符串内容的 MD5 哈希值

    Args:
        content: 要计算哈希的字符串内容

    Returns:
        32位十六进制 MD5 字符串
    """
    if isinstance(content, str):
        content = content.encode("utf-8")
    return hashlib.md5(content).hexdigest()


def calculate_file_md5(file_path: str) -> str:
    """
    计算文件的 MD5 哈希值

    Args:
        file_path: 文件路径

    Returns:
        32位十六进制 MD5 字符串
    """
    md5_hash = hashlib.md5()
    with open(file_path, "rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            md5_hash.update(chunk)
    return md5_hash.hexdigest()


def get_local_timestamp() -> int:
    """获取本地时间戳（毫秒）"""
    return int(datetime.now(timezone.utc).timestamp() * 1000)


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


# ==================== 文件路径工具 ====================


def get_project_root() -> Path:
    """
    获取项目根目录

    Returns:
        项目根目录路径
    """
    return Path(__file__).parent.parent


def ensure_dir(dir_path: str) -> str:
    """
    确保目录存在，不存在则创建

    Args:
        dir_path: 目录路径

    Returns:
        标准化后的目录路径
    """
    path = Path(dir_path)
    path.mkdir(parents=True, exist_ok=True)
    return str(path.resolve())


def get_data_dir(sub_dir: str = "data") -> str:
    """
    获取数据目录

    Args:
        sub_dir: 子目录名

    Returns:
        数据目录的绝对路径
    """
    data_dir = get_project_root() / sub_dir
    return ensure_dir(str(data_dir))


def get_cache_dir(cache_name: str = "cache") -> str:
    """
    获取缓存目录

    Args:
        cache_name: 缓存子目录名

    Returns:
        缓存目录的绝对路径
    """
    cache_dir = get_project_root() / ".cache" / cache_name
    return ensure_dir(str(cache_dir))


def safe_filename(filename: str) -> str:
    """
    将文件名转换为安全的文件名（移除非法字符）

    Args:
        filename: 原始文件名

    Returns:
        安全的文件名
    """
    # 移除或替换非法字符
    safe = re.sub(r'[<>:"/\\|?*\x00-\x1f]', '_', filename)
    # 移除前后空格和点
    safe = safe.strip('. ')
    # 限制长度
    if len(safe) > 200:
        safe = safe[:200]
    return safe or "unnamed"


def get_config_path(config_name: str = "config.yaml") -> str:
    """
    获取配置文件路径

    Args:
        config_name: 配置文件名

    Returns:
        配置文件路径
    """
    return str(get_project_root() / config_name)


# ==================== 日期时间工具 ====================


def format_datetime(dt: datetime, fmt: str = "%Y-%m-%d %H:%M:%S") -> str:
    """
    格式化日期时间为字符串

    Args:
        dt: datetime 对象
        fmt: 输出格式

    Returns:
        格式化后的时间字符串
    """
    return dt.strftime(fmt)


def get_current_datetime() -> datetime:
    """
    获取当前 datetime（带时区）

    Returns:
        当前 datetime 对象
    """
    return datetime.now(timezone.utc)


def get_current_date_str() -> str:
    """
    获取当前日期字符串

    Returns:
        YYYY-MM-DD 格式的日期字符串
    """
    return datetime.now().strftime("%Y-%m-%d")


def get_current_time_str() -> str:
    """
    获取当前时间字符串

    Returns:
        HH:MM:SS 格式的时间字符串
    """
    return datetime.now().strftime("%H:%M:%S")


def parse_chinese_date(date_str: str) -> Optional[datetime]:
    """
    解析中文日期格式

    支持格式：
    - 2026年4月29日
    - 2026年4月29日 10:30:00
    - 2026年4月29日 10时30分

    Args:
        date_str: 日期字符串

    Returns:
        datetime 对象，解析失败返回 None
    """
    if not date_str:
        return None

    patterns = [
        (r'(\d{4})年(\d{1,2})月(\d{1,2})日\s*(\d{1,2})时(\d{1,2})分', '%Y年%m月%d日 %H时%M分'),
        (r'(\d{4})年(\d{1,2})月(\d{1,2})日', '%Y年%m月%d日'),
    ]

    for pattern, out_fmt in patterns:
        match = re.match(pattern, date_str)
        if match:
            try:
                if '时' in out_fmt:
                    parts = match.groups()
                    return datetime(
                        int(parts[0]), int(parts[1]), int(parts[2]),
                        int(parts[3]), int(parts[4])
                    )
                else:
                    parts = match.groups()
                    return datetime(int(parts[0]), int(parts[1]), int(parts[2]))
            except ValueError:
                continue

    return None


# ==================== JSON 序列化工具 ====================


def to_json(obj: Any, ensure_ascii: bool = False, indent: Optional[int] = None) -> str:
    """
    将对象序列化为 JSON 字符串

    Args:
        obj: 要序列化的对象
        ensure_ascii: 是否转义非ASCII字符
        indent: 缩进空格数，None 表示不缩进

    Returns:
        JSON 字符串
    """
    if indent is not None:
        return json.dumps(obj, ensure_ascii=ensure_ascii, indent=indent)
    return json.dumps(obj, ensure_ascii=ensure_ascii)


def from_json(json_str: str) -> Any:
    """
    从 JSON 字符串反序列化

    Args:
        json_str: JSON 字符串

    Returns:
        反序列化后的对象
    """
    return json.loads(json_str)


def to_json_file(obj: Any, file_path: str, indent: int = 2) -> None:
    """
    将对象序列化并写入文件

    Args:
        obj: 要序列化的对象
        file_path: 文件路径
        indent: 缩进空格数
    """
    with open(file_path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, ensure_ascii=False, indent=indent)


def from_json_file(file_path: str) -> Any:
    """
    从 JSON 文件读取并反序列化

    Args:
        file_path: 文件路径

    Returns:
        反序列化后的对象
    """
    with open(file_path, 'r', encoding='utf-8') as f:
        return json.load(f)


def merge_dict(base: Dict[str, Any], update: Dict[str, Any]) -> Dict[str, Any]:
    """
    合并两个字典，update 中的值会覆盖 base 中的值

    Args:
        base: 基础字典
        update: 更新字典

    Returns:
        合并后的字典
    """
    result = base.copy()
    result.update(update)
    return result