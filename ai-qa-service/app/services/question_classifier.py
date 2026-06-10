"""关键词匹配 + 优先级路由 — 从 YAML 加载黑名单和关键词"""
import os
import re
import yaml
import logging
from enum import IntEnum
from typing import Optional

logger = logging.getLogger(__name__)


class QuestionType(IntEnum):
    TREND = 1      # 趋势分析（最高优先级）
    PRICE = 2      # 价格查询
    POLICY = 3     # 政策解读
    GENERAL = 4    # 综合问答（兜底）


_CONFIG_PATH = os.path.join(os.path.dirname(__file__), "..", "config", "keyword_map.yaml")
_BLACKLIST_PATH = os.path.join(os.path.dirname(__file__), "..", "config", "blacklist.yaml")
_DEFAULT_BLACKLIST = {"评价", "国家", "价位", "评论", "评级", "级别", "品质"}


def _load_blacklist() -> set[str]:
    """从 blacklist.yaml 加载黑名单词组"""
    try:
        with open(_BLACKLIST_PATH, encoding="utf-8") as f:
            config = yaml.safe_load(f)
        return set(config.get("words", [])) or _DEFAULT_BLACKLIST
    except Exception:
        logger.warning("[AI_QA] [WARN] [blacklist_load_failed] fallback=default")
        return _DEFAULT_BLACKLIST


def _load_keyword_map() -> dict:
    """加载关键词配置"""
    try:
        with open(_CONFIG_PATH, encoding="utf-8") as f:
            return yaml.safe_load(f)
    except Exception:
        logger.warning("[AI_QA] [WARN] [config_load_failed] path=%s fallback=default", _CONFIG_PATH)
        return {
            "categories": {
                "price": {"keywords": ["价格", "多少钱", "报价"], "aliases": {}},
                "trend": {"keywords": ["走势", "趋势", "行情"], "aliases": {}},
                "policy": {"keywords": ["政策", "储备", "补贴"], "aliases": {}},
            }
        }


def _preprocess(text: str) -> str:
    """文本预处理：全角转半角 → 小写 → 去零宽字符"""
    result = []
    for ch in text:
        code = ord(ch)
        if code == 0x3000:
            result.append(" ")
        elif 0xFF01 <= code <= 0xFF5E:
            result.append(chr(code - 0xFEE0))
        else:
            result.append(ch)
    text = "".join(result).lower()
    text = re.sub(r"[​‌‍﻿]", "", text)
    return text.strip()


def classify_question(question: str) -> QuestionType:
    """输入用户问题，返回问题类型。优先级：趋势 > 价格 > 政策 > 综合"""
    text = _preprocess(question)

    config = _load_keyword_map()
    blacklist = _load_blacklist()
    type_map = {
        "trend": QuestionType.TREND,
        "price": QuestionType.PRICE,
        "policy": QuestionType.POLICY,
    }
    priority_order = ["trend", "price", "policy"]

    matched: set[QuestionType] = set()
    for cat_name in priority_order:
        cat = config["categories"].get(cat_name, {})
        keywords = cat.get("keywords", [])
        aliases = cat.get("aliases", {})

        expanded = text
        for src, dst in aliases.items():
            if src in expanded:
                expanded = expanded.replace(src, dst)

        for bl in blacklist:
            if bl in expanded:
                return QuestionType.GENERAL

        sorted_kw = sorted(keywords, key=len, reverse=True)
        for kw in sorted_kw:
            if kw in expanded:
                matched.add(type_map[cat_name])
                break

    if QuestionType.TREND in matched:
        return QuestionType.TREND
    if QuestionType.PRICE in matched:
        return QuestionType.PRICE
    if QuestionType.POLICY in matched:
        return QuestionType.POLICY

    logger.info("[AI_QA] [INFO] [keyword_classification_failed] fallback=general snippet=%s", question[:30])
    return QuestionType.GENERAL
