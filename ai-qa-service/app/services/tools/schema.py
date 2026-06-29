"""
工具 JSON Schema 定义（OpenAI function calling 格式）
"""

QUERY_PRICE_SCHEMA = {
    "type": "function",
    "function": {
        "name": "query_price",
        "description": "查询某个品种在指定区域的当前价格。"
                       "适合：海口港玉米多少钱、锦州港今天价格",
        "parameters": {
            "type": "object",
            "properties": {
                "variety": {
                    "type": "string",
                    "enum": ["玉米", "小麦", "进口粮", "国产大豆", "大豆", "生猪", "猪肉"],
                    "description": "品种"
                },
                "region": {
                    "type": "string",
                    "description": "港口名/企业名/省份/原产国/船期"
                },
                "date": {
                    "type": "string",
                    "description": "yyyy-MM-dd，不传则查最近7天。由LLM根据用户问题自动推算："
                                   "'今天'→CURDATE()，'昨天'→CURDATE()-1"
                }
            },
            "required": ["variety", "region"]
        }
    }
}

QUERY_PRICE_TREND_SCHEMA = {
    "type": "function",
    "function": {
        "name": "query_price_trend",
        "description": "查询某个品种在指定区域的价格趋势。"
                       "适合：最近一周玉米走势、今年海口港价格变化",
        "parameters": {
            "type": "object",
            "properties": {
                "variety": {
                    "type": "string",
                    "enum": ["玉米", "小麦", "进口粮", "国产大豆", "大豆", "生猪", "猪肉"],
                    "description": "品种"
                },
                "region": {
                    "type": "string",
                    "description": "港口名/企业名/省份"
                },
                "days": {
                    "type": "integer",
                    "description": "近N天，默认30，最大180。LLM转换：'最近一周'→7，'近三个月'→90"
                },
                "start_date": {
                    "type": "string",
                    "description": "绝对起始日期 yyyy-MM-dd。LLM转换：'今年'→2026-01-01"
                },
                "end_date": {
                    "type": "string",
                    "description": "绝对截止日期 yyyy-MM-dd"
                }
            },
            "required": ["variety", "region"]
        }
    }
}

QUERY_PRICE_COMPARISON_SCHEMA = {
    "type": "function",
    "function": {
        "name": "query_price_comparison",
        "description": "对比多个区域的同品种价格。"
                       "适合：哪个港口最便宜、北港南港价差、"
                       "今日各港口价格对比",
        "parameters": {
            "type": "object",
            "properties": {
                "variety": {
                    "type": "string",
                    "enum": ["玉米", "小麦", "进口粮", "国产大豆", "大豆", "生猪", "猪肉"],
                    "description": "品种"
                },
                "regions": {
                    "type": "array",
                    "items": {"type": "string"},
                    "description": "区域列表，不传或空列表则返回当日全部区域"
                },
                "date": {
                    "type": "string",
                    "description": "对比日期 yyyy-MM-dd，不传则查最新一日"
                }
            },
            "required": ["variety"]
        }
    }
}

TOOL_SCHEMAS = [
    QUERY_PRICE_SCHEMA,
    QUERY_PRICE_TREND_SCHEMA,
    QUERY_PRICE_COMPARISON_SCHEMA,
]
