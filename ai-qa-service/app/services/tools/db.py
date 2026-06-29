"""
t_price 数据库查询封装

使用 aiomysql 直连 MySQL，独立于 ORM 层。
连接参数从环境变量读取。
"""

import os
import logging
from typing import Optional

import aiomysql

logger = logging.getLogger(__name__)

DB_CONFIG = {
    "host": os.getenv("MYSQL_HOST", "localhost"),
    "port": int(os.getenv("MYSQL_PORT", "3306")),
    "user": os.getenv("MYSQL_USER", "root"),
    "password": os.getenv("MYSQL_PASSWORD", "root"),
    "db": os.getenv("MYSQL_DATABASE", "grain_platform"),
    "charset": "utf8mb4",
}

_pool: Optional[aiomysql.Pool] = None


async def get_pool() -> aiomysql.Pool:
    global _pool
    if _pool is None:
        _pool = await aiomysql.create_pool(
            **DB_CONFIG,
            minsize=1,
            maxsize=5,
            autocommit=True,
        )
    return _pool


async def query_price_records(
    variety: str,
    region: str,
    date_str: Optional[str] = None,
    limit: int = 7,
) -> list[dict]:
    """查询价格记录"""
    pool = await get_pool()
    async with pool.acquire() as conn:
        async with conn.cursor(aiomysql.DictCursor) as cur:
            if date_str:
                sql = """
                    SELECT `date`, price, change_val, remark, unit, source, province, area_type
                    FROM t_price
                    WHERE variety = %s AND region = %s AND `date` = %s
                    ORDER BY `date` DESC
                    LIMIT %s
                """
                await cur.execute(sql, (variety, region, date_str, limit))
            else:
                sql = """
                    SELECT `date`, price, change_val, remark, unit, source, province, area_type
                    FROM t_price
                    WHERE variety = %s AND region = %s
                    ORDER BY `date` DESC
                    LIMIT %s
                """
                await cur.execute(sql, (variety, region, limit))
            rows = await cur.fetchall()
            return [dict(r) for r in rows]


async def query_price_trend_records(
    variety: str,
    region: str,
    days: int = 30,
    start_date: Optional[str] = None,
    end_date: Optional[str] = None,
    limit: int = 180,
) -> list[dict]:
    """查询价格趋势"""
    pool = await get_pool()
    async with pool.acquire() as conn:
        async with conn.cursor(aiomysql.DictCursor) as cur:
            if start_date and end_date:
                sql = """
                    SELECT `date`, price, change_val, remark, unit, source
                    FROM t_price
                    WHERE variety = %s AND region = %s
                      AND `date` BETWEEN %s AND %s
                    ORDER BY `date` ASC
                    LIMIT %s
                """
                await cur.execute(sql, (variety, region, start_date, end_date, limit))
            else:
                sql = """
                    SELECT `date`, price, change_val, remark, unit, source
                    FROM t_price
                    WHERE variety = %s AND region = %s
                      AND `date` >= DATE_SUB(CURDATE(), INTERVAL %s DAY)
                    ORDER BY `date` ASC
                    LIMIT %s
                """
                await cur.execute(sql, (variety, region, days, limit))
            rows = await cur.fetchall()
            return [dict(r) for r in rows]


async def query_price_comparison_records(
    variety: str,
    regions: Optional[list[str]] = None,
    date_str: Optional[str] = None,
    limit: int = 30,
) -> list[dict]:
    """查询多区域对比价格"""
    pool = await get_pool()
    async with pool.acquire() as conn:
        async with conn.cursor(aiomysql.DictCursor) as cur:
            if date_str:
                if regions:
                    placeholders = ",".join(["%s"] * len(regions))
                    sql = f"""
                        SELECT region, province, price, change_val, remark, unit, `date`, area_type
                        FROM t_price
                        WHERE variety = %s AND region IN ({placeholders}) AND `date` = %s
                        ORDER BY price ASC
                        LIMIT %s
                    """
                    await cur.execute(sql, (variety, *regions, date_str, limit))
                else:
                    sql = """
                        SELECT region, province, price, change_val, remark, unit, `date`, area_type
                        FROM t_price
                        WHERE variety = %s AND `date` = %s
                        ORDER BY price ASC
                        LIMIT %s
                    """
                    await cur.execute(sql, (variety, date_str, limit))
            else:
                if regions:
                    placeholders = ",".join(["%s"] * len(regions))
                    sql = f"""
                        SELECT region, province, price, change_val, remark, unit, `date`, area_type
                        FROM t_price
                        WHERE variety = %s AND region IN ({placeholders}) AND `date` = CURDATE()
                        ORDER BY price ASC
                        LIMIT %s
                    """
                    await cur.execute(sql, (variety, *regions, limit))
                else:
                    sql = """
                        SELECT region, province, price, change_val, remark, unit, `date`, area_type
                        FROM t_price
                        WHERE variety = %s AND `date` = CURDATE()
                        ORDER BY price ASC
                        LIMIT %s
                    """
                    await cur.execute(sql, (variety, limit))
            rows = await cur.fetchall()
            # 当日无数据时回退到最近日期
            if not rows and not date_str:
                max_date_sql = "SELECT MAX(`date`) AS max_date FROM t_price WHERE variety = %s"
                await cur.execute(max_date_sql, (variety,))
                max_row = await cur.fetchone()
                if max_row and max_row["max_date"]:
                    if regions:
                        placeholders = ",".join(["%s"] * len(regions))
                        sql = f"""
                            SELECT region, province, price, change_val, remark, unit, `date`, area_type
                            FROM t_price
                            WHERE variety = %s AND region IN ({placeholders}) AND `date` = %s
                            ORDER BY price ASC
                            LIMIT %s
                        """
                        await cur.execute(sql, (variety, *regions, max_row["max_date"], limit))
                    else:
                        sql = """
                            SELECT region, province, price, change_val, remark, unit, `date`, area_type
                            FROM t_price
                            WHERE variety = %s AND `date` = %s
                            ORDER BY price ASC
                            LIMIT %s
                        """
                        await cur.execute(sql, (variety, max_row["max_date"], limit))
                    rows = await cur.fetchall()
            return [dict(r) for r in rows]
