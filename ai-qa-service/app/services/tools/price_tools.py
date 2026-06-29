"""
价格查询 AI 工具 — query_price / query_price_trend / query_price_comparison

每个工具函数从 t_price 数据库查询数据，返回结构化的 dict，
包含 content（文字回答）、sources（来源引用）、visualization（可视化数据）。
"""

import logging
from typing import Optional
from urllib.parse import quote

from . import db

logger = logging.getLogger(__name__)

# ============================================================
# 省份/区域映射字典
# 用于 AI 模糊搜索：用户输入"广东"时,LLM 可将 region 映射到"南港"下的港口
# ============================================================
PROVINCE_MAP = {
    "北港": "北港",
    "南港": "南港",
    "黑龙江": "黑龙江省",
    "吉林": "吉林省",
    "辽宁": "辽宁省",
    "内蒙古": "内蒙古自治区",
    "河北": "河北省",
    "山东": "山东省",
    "河南": "河南省",
    "江苏": "江苏省",
    "安徽": "安徽省",
    "四川": "四川省",
    "陕西": "陕西省",
    "广东": "广东省",
    "广西": "广西壮族自治区",
    "福建": "福建省",
    "湖南": "湖南省",
    "湖北": "湖北省",
    "山西": "山西省",
    "甘肃": "甘肃省",
    "宁夏": "宁夏回族自治区",
    "新疆": "新疆维吾尔自治区",
    "云南": "云南省",
    "贵州": "贵州省",
    "上海": "上海市",
    "天津": "天津市",
    "北京": "北京市",
    "重庆": "重庆市",
    "美湾": "美国",
    "美国": "美国",
    "法国": "法国",
    "阿根廷": "阿根廷",
    "澳大利亚": "澳大利亚",
    "乌克兰": "乌克兰",
    "东北": "东北",
    "华北": "华北",
    "华东": "华东",
    "华中": "华中",
    "华南": "华南",
    "西南": "西南",
    "西北": "西北",
    "其他": "其他",
}

# 品种别名 → 标准名映射（LLM 传了"猪肉"时自动转为"生猪"）
VARIETY_MAP = {
    "猪肉": "生猪",
    "猪": "生猪",
    "大豆": "国产大豆",
}

# 品种固定单位映射
UNIT_MAP = {
    "玉米": "元/吨",
    "小麦": "元/吨",
    "进口粮": "元/吨",
    "国产大豆": "元/吨",
    "生猪": "元/斤",
}

# 粮质标准说明（粮达网统一标准）
GRAIN_STANDARD = "容重二等以上，水分14%"


def _format_change(val) -> str:
    """格式化涨跌值

    Args:
        val: 数值或 None

    Returns:
        "+10" / "-10" / "持平" / "--
    """
    if val is None:
        return "--"
    try:
        v = float(val)
        if v > 0:
            return f"+{v}"
        elif v < 0:
            return f"{v}"
        else:
            return "持平"
    except (ValueError, TypeError):
        return "--"


def _build_source_url(variety: str, region: str) -> str:
    """生成粮达网价格指数页面 URL

    Args:
        variety: 品种
        region: 区域

    Returns:
        完整 URL
    """
    encoded_variety = quote(variety, safe="")
    encoded_region = quote(region, safe="")
    return (
        f"https://www.liangdawang.com/#/priceIndex"
        f"?varietyName={encoded_variety}&area={encoded_region}"
    )


def _build_visualization_table(
    title: str,
    rows: list[dict],
) -> dict:
    """构建表格可视化数据

    Args:
        title: 表格标题
        rows: 数据行列表，每项包含 region/province/price/change/remark

    Returns:
        可视化 dict
    """
    return {
        "type": "table",
        "title": title,
        "data": {
            "rows": rows,
        },
    }


def _build_visualization_line(
    title: str,
    labels: list[str],
    dataset_label: str,
    dataset_data: list,
    unit: str = "",
) -> dict:
    """构建折线图可视化数据

    Args:
        title: 图表标题
        labels: X 轴标签（日期）
        dataset_label: 数据集名称
        dataset_data: Y 轴数据
        unit: 数据单位

    Returns:
        可视化 dict
    """
    return {
        "type": "line",
        "title": title,
        "data": {
            "xAxis": labels,
            "series": [
                {
                    "name": dataset_label,
                    "data": dataset_data,
                    "type": "line",
                }
            ],
            "unit": unit,
        },
    }


def _build_visualization_comparison(
    title: str,
    labels: list[str],
    datasets: list[dict],
    unit: str = "",
) -> dict:
    """构建多系列折线图可视化数据（用于多区域对比）

    Args:
        title: 图表标题
        labels: X 轴标签
        datasets: 数据集列表，每项 {"label": str, "data": list}
        unit: 数据单位

    Returns:
        可视化 dict
    """
    series = [
        {"name": ds["label"], "data": ds["data"], "type": "line"}
        for ds in datasets
    ]
    return {
        "type": "line",
        "title": title,
        "data": {
            "xAxis": labels,
            "series": series,
            "unit": unit,
        },
    }


# ============================================================
# 工具 1: query_price — 查价格
# ============================================================


async def query_price(
    variety: str,
    region: str,
    date: Optional[str] = None,
) -> dict:
    """查询某个品种在指定区域的价格

    Args:
        variety: 品种（玉米/小麦/进口粮/国产大豆/生猪）
        region: 港口名/企业名/省份名/原产国/船期
        date: 可选，日期 yyyy-MM-dd。不传则返回最近 7 天

    Returns:
        dict with content/sources/visualization
    """
    # 品种别名归一化（猪肉→生猪, 大豆→国产大豆）
    variety = VARIETY_MAP.get(variety, variety)
    try:
        records = await db.query_price_records(
            variety=variety,
            region=region,
            date_str=date,
            limit=7,
        )
    except Exception as e:
        logger.error("[PRICE_TOOL] query_price db error: %s", e)
        return {
            "content": f"查询价格时数据库异常：{e}",
            "sources": [],
            "visualization": None,
        }

    if not records:
        date_hint = f"日期 {date}" if date else "最近 7 天"
        return {
            "content": f"暂无 {variety} 在 {region} 的价格数据（{date_hint}）。",
            "sources": [],
            "visualization": None,
        }

    # 从第一条记录获取单位和来源
    unit = records[0].get("unit") or UNIT_MAP.get(variety, "")
    source = records[0].get("source", "liangdawang")

    # 组装返回数据
    prices = []
    for r in records:
        prices.append({
            "date": str(r.get("date", "")),
            "price": float(r["price"]) if r.get("price") is not None else None,
            "change": _format_change(r.get("change_val")),
            "remark": r.get("remark") or "",
        })

    # 构建文字内容
    lines = [
        f"【{variety} — {region} 价格】",
        f"数据来源：粮达网 | 单位：{unit} | 粮质标准：{GRAIN_STANDARD}",
        "",
    ]
    for p in prices:
        change_str = p["change"]
        remark_str = f"（{p['remark']}）" if p["remark"] else ""
        lines.append(f"  {p['date']}：{p['price']} {unit}  {change_str}{remark_str}")

    content = "\n".join(lines)

    # 构建来源
    source_url = _build_source_url(variety, region)
    date_info = date if date else f"{str(records[0].get('date', ''))}等{len(records)}天"
    sources = [
        {
            "type": "sql",
            "query_summary": f"{variety} {region} 共{len(records)}条",
            "source_name": "粮达网",
            "date": date_info,
            "url": source_url,
            "grain_standard": GRAIN_STANDARD,
        }
    ]

    # 构建表格可视化
    table_rows = []
    for p in prices:
        table_rows.append({
            "region": p["date"],
            "province": "",
            "price": p["price"],
            "change": p["change"],
            "remark": p["remark"],
        })
    visualization = _build_visualization_table(
        title=f"{variety} — {region} 价格",
        rows=table_rows,
    )

    return {
        "content": content,
        "sources": sources,
        "visualization": visualization,
        "data": {
            "variety": variety,
            "region": region,
            "prices": prices,
            "unit": unit,
            "source": "粮达网",
            "grain_standard": GRAIN_STANDARD,
        },
    }


# ============================================================
# 工具 2: query_price_trend — 查趋势
# ============================================================


async def query_price_trend(
    variety: str,
    region: str,
    days: int = 30,
    start_date: Optional[str] = None,
    end_date: Optional[str] = None,
) -> dict:
    """查询某个品种在指定区域的价格趋势

    Args:
        variety: 品种（玉米/小麦/进口粮/国产大豆/生猪）
        region: 港口名/企业名/省份
        days: 近 N 天，默认 30，最大 180
        start_date: 绝对起始日期 yyyy-MM-dd
        end_date: 绝对截止日期 yyyy-MM-dd

    Returns:
        dict with content/sources/visualization
    """
    variety = VARIETY_MAP.get(variety, variety)
    # 约束校验
    if days > 180:
        days = 180
    if days < 1:
        days = 30

    try:
        records = await db.query_price_trend_records(
            variety=variety,
            region=region,
            days=days,
            start_date=start_date,
            end_date=end_date,
            limit=180,
        )
    except Exception as e:
        logger.error("[PRICE_TOOL] query_price_trend db error: %s", e)
        return {
            "content": f"查询价格趋势时数据库异常：{e}",
            "sources": [],
            "visualization": None,
        }

    if not records:
        if start_date and end_date:
            period = f"{start_date} ~ {end_date}"
        else:
            period = f"近 {days} 天"
        return {
            "content": f"暂无 {variety} 在 {region} 的价格趋势数据（{period}）。",
            "sources": [],
            "visualization": None,
        }

    unit = records[0].get("unit") or UNIT_MAP.get(variety, "")

    # 组装时间序列
    points = []
    for r in records:
        points.append({
            "date": str(r.get("date", "")),
            "price": float(r["price"]) if r.get("price") is not None else None,
            "change": _format_change(r.get("change_val")),
        })

    # 计算统计信息
    prices_list = [p["price"] for p in points if p["price"] is not None]
    if prices_list:
        avg_price = sum(prices_list) / len(prices_list)
        max_price = max(prices_list)
        min_price = min(prices_list)
        first_price = prices_list[0]
        last_price = prices_list[-1]
        total_change = last_price - first_price
    else:
        avg_price = max_price = min_price = first_price = last_price = total_change = 0

    # 构建文字内容
    period_desc = f"{start_date} ~ {end_date}" if start_date and end_date else f"近 {days} 天"
    change_word = "上涨" if total_change > 0 else ("下跌" if total_change < 0 else "持平")
    lines = [
        f"【{variety} — {region} 价格趋势】（{period_desc}）",
        f"数据来源：粮达网 | 单位：{unit} | 粮质标准：{GRAIN_STANDARD}",
        "",
        f"统计概览：",
        f"  - 开盘价：{first_price:.0f} {unit}",
        f"  - 最新价：{last_price:.0f} {unit}",
        f"  - 期间{change_word}：{abs(total_change):.0f} {unit}",
        f"  - 最高价：{max_price:.0f} {unit}",
        f"  - 最低价：{min_price:.0f} {unit}",
        f"  - 均价：{avg_price:.0f} {unit}",
        f"  - 数据点数：{len(points)} 条",
        "",
        "详细数据：",
    ]
    for p in points:
        lines.append(f"  {p['date']}：{p['price']:.0f} {unit}  {p['change']}")

    content = "\n".join(lines)

    # 构建来源
    source_url = _build_source_url(variety, region)
    date_info = f"{start_date} ~ {end_date}" if start_date and end_date else f"近{days}天"
    sources = [
        {
            "type": "sql",
            "query_summary": f"{variety} {region} 共{len(records)}条",
            "source_name": "粮达网",
            "date": date_info,
            "url": source_url,
            "grain_standard": GRAIN_STANDARD,
        }
    ]

    # 构建折线图可视化
    labels = [p["date"] for p in points]
    data = [p["price"] for p in points]
    visualization = _build_visualization_line(
        title=f"{variety} — {region} 价格趋势（{period_desc}）",
        labels=labels,
        dataset_label=f"{region} {variety}",
        dataset_data=data,
        unit=unit,
    )

    return {
        "content": content,
        "sources": sources,
        "visualization": visualization,
        "data": {
            "variety": variety,
            "region": region,
            "points": points,
            "unit": unit,
            "stats": {
                "avg_price": round(avg_price, 2),
                "max_price": round(max_price, 2),
                "min_price": round(min_price, 2),
                "first_price": round(first_price, 2),
                "last_price": round(last_price, 2),
                "total_change": round(total_change, 2),
                "count": len(points),
            },
            "source": "粮达网",
            "grain_standard": GRAIN_STANDARD,
        },
    }


# ============================================================
# 工具 3: query_price_comparison — 多区域对比
# ============================================================


async def query_price_comparison(
    variety: str,
    regions: Optional[list[str]] = None,
    date: Optional[str] = None,
) -> dict:
    """对比多个区域的同品种价格

    Args:
        variety: 品种（玉米/小麦/进口粮/国产大豆/生猪）
        regions: 区域列表，不传或空列表则返回当日全部区域
        date: 对比日期 yyyy-MM-dd，不传则查最新一日

    Returns:
        dict with content/sources/visualization
    """
    variety = VARIETY_MAP.get(variety, variety)
    try:
        records = await db.query_price_comparison_records(
            variety=variety,
            regions=regions if regions else None,
            date_str=date,
            limit=30,
        )
    except Exception as e:
        logger.error("[PRICE_TOOL] query_price_comparison db error: %s", e)
        return {
            "content": f"查询价格对比时数据库异常：{e}",
            "sources": [],
            "visualization": None,
        }

    if not records:
        date_hint = f"日期 {date}" if date else "最新一日"
        region_hint = f" ({', '.join(regions)})" if regions else ""
        return {
            "content": f"暂无 {variety}{region_hint} 在 {date_hint} 的价格对比数据。",
            "sources": [],
            "visualization": None,
        }

    unit = records[0].get("unit") or UNIT_MAP.get(variety, "")
    query_date = str(records[0].get("date", "")) if records else (date or "")

    # 组装对比数据
    items = []
    for r in records:
        items.append({
            "region": r.get("region", ""),
            "province": r.get("province", ""),
            "price": float(r["price"]) if r.get("price") is not None else None,
            "change": _format_change(r.get("change_val")),
            "remark": r.get("remark") or "",
            "area_type": r.get("area_type", ""),
        })

    # 计算统计信息
    prices_list = [it["price"] for it in items if it["price"] is not None]
    if prices_list:
        avg_price = sum(prices_list) / len(prices_list)
        max_item = max(items, key=lambda x: x["price"] if x["price"] is not None else 0)
        min_item = min(items, key=lambda x: x["price"] if x["price"] is not None else float("inf"))
        price_spread = max(prices_list) - min(prices_list)
    else:
        avg_price = price_spread = 0
        max_item = min_item = {"region": "", "price": None}

    # 按价格升序排列
    items.sort(key=lambda x: x["price"] if x["price"] is not None else float("inf"))

    # 构建文字内容
    region_hint = f"（指定区域）" if regions else "（全区域）"
    lines = [
        f"【{variety} 多区域价格对比】{region_hint}",
        f"对比日期：{query_date}",
        f"数据来源：粮达网 | 单位：{unit} | 粮质标准：{GRAIN_STANDARD}",
        "",
        f"统计概览：",
        f"  - 最高价：{max_item['region']} {max_item['price']:.0f} {unit}（{max_item.get('province', '')}）" if max_item["price"] else "",
        f"  - 最低价：{min_item['region']} {min_item['price']:.0f} {unit}（{min_item.get('province', '')}）" if min_item["price"] else "",
        f"  - 均价：{avg_price:.0f} {unit}",
        f"  - 价差：{price_spread:.0f} {unit}",
        f"  - 统计区域数：{len(items)}",
        "",
        "价格排序（从低到高）：",
    ]
    for it in items:
        remark_str = f"（{it['remark']}）" if it["remark"] else ""
        province_str = f"[{it['province']}] " if it.get("province") else ""
        lines.append(
            f"  {province_str}{it['region']}：{it['price']:.0f} {unit}  {it['change']}{remark_str}"
        )

    content_lines = [l for l in lines if l]  # 去除空行（条件判断可能产生空字符串）
    content = "\n".join(content_lines)

    # 构建来源
    source_url = _build_source_url(variety, "")
    date_info = str(records[0].get("date", "")) if records else (date or "")
    sources = [
        {
            "type": "sql",
            "query_summary": f"{variety} 多区域对比 共{len(records)}条",
            "source_name": "粮达网",
            "date": date_info,
            "url": source_url,
            "grain_standard": GRAIN_STANDARD,
        }
    ]

    # 构建表格可视化
    table_rows = []
    for it in items:
        table_rows.append({
            "region": it["region"],
            "province": it.get("province", ""),
            "price": it["price"],
            "change": it["change"],
            "remark": it["remark"],
        })
    visualization = _build_visualization_table(
        title=f"{variety} 区域价格对比（{query_date}）",
        rows=table_rows,
    )

    return {
        "content": content,
        "sources": sources,
        "visualization": visualization,
        "data": {
            "variety": variety,
            "date": query_date,
            "items": items,
            "unit": unit,
            "stats": {
                "avg_price": round(avg_price, 2),
                "price_spread": round(price_spread, 2),
                "highest": {
                    "region": max_item["region"] if max_item["price"] else None,
                    "price": round(max_item["price"], 2) if max_item["price"] else None,
                },
                "lowest": {
                    "region": min_item["region"] if min_item["price"] else None,
                    "price": round(min_item["price"], 2) if min_item["price"] else None,
                },
                "count": len(items),
            },
            "source": "粮达网",
            "grain_standard": GRAIN_STANDARD,
        },
    }
