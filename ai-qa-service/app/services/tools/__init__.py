"""
AI 工具注册入口

导出所有工具函数和 schema，供 chat.py 调用。
"""

from . import price_tools
from . import db
from .schema import TOOL_SCHEMAS

TOOL_REGISTRY = {
    "query_price": {
        "handler": price_tools.query_price,
        "schema": next(s for s in TOOL_SCHEMAS if s["function"]["name"] == "query_price"),
    },
    "query_price_trend": {
        "handler": price_tools.query_price_trend,
        "schema": next(s for s in TOOL_SCHEMAS if s["function"]["name"] == "query_price_trend"),
    },
    "query_price_comparison": {
        "handler": price_tools.query_price_comparison,
        "schema": next(s for s in TOOL_SCHEMAS if s["function"]["name"] == "query_price_comparison"),
    },
}

__all__ = ["TOOL_REGISTRY", "TOOL_SCHEMAS", "price_tools", "db"]
