"""
粮达网价格指数采集器

采集5个品种（玉米/小麦/进口粮/国产大豆/生猪）的港口成交价，
存入 t_price 结构化数据库。

API（公开，无需登录）：
- /varietyNameAndAreaType    -> 品种+区域类型列表
- /getPriceInfo?varietyName=&areaType= -> 当前价格表
- /getPriceChart             -> 历史走势（回填用）

设计文档：docs/superpowers/specs/2026-06-25-liangdawang-price-collection-design.md
"""

import asyncio
import json
import logging
import os
import re
import time
from datetime import date, datetime
from pathlib import Path
from typing import Any, Optional

import httpx

from collectorsdk.config import ReporterConfig
from collectorsdk.base import BaseCollector
from collectorsdk.dimensions import Source, Subject, CollectType, CollectObject

logger = logging.getLogger(__name__)

# ============================================================
# 常量
# ============================================================
API_BASE = "https://www.liangdawang.com/ldw-portal-mer/v1/infoCenter"
PRICE_BATCH_URL = "/api/price/batch"

# 品种 -> Subject 枚举映射
VARIETY_TO_SUBJECT = {
    "玉米": Subject.CORN,
    "小麦": Subject.WHEAT,
    "进口粮": Subject.IMPORTED_GRAIN,
    "国产大豆": Subject.SOYBEAN,
    "生猪": Subject.LIVE_PIG,
}

# 品种 -> unit 映射
VARIETY_UNIT = {
    "玉米": "元/吨",
    "小麦": "元/吨",
    "进口粮": "元/吨",
    "国产大豆": "元/吨",
    "生猪": "元/斤",
}

# 区域类型 -> area_type 映射
AREA_TYPE_MAP = {
    "港口": "port",
    "东北": "enterprise",
    "华北": "enterprise",
    "其他": "enterprise",
}

VARIETY_DEFAULT_AREA_TYPE = {
    "玉米": "port",
    "小麦": "enterprise",
    "进口粮": "shipping",
    "国产大豆": "region",
    "生猪": "region",
}

CHANGE_VAL_MAP = {
    "持平": 0,
    "平": 0,
    "--": None,
    "": None,
}

MIN_EXPECTED_COUNTS = {
    "玉米": 106,
    "小麦": 53,
    "进口粮": 20,
    "国产大豆": 17,
    "生猪": 28,
}

REQUEST_INTERVAL = 1.0
SEMAPHORE_LIMIT = 3
HTTP_TIMEOUT = 30
CONNECT_TIMEOUT = 10
BACKFILL_BATCH_SIZE = 500
BACKFILL_INTERVAL = 0.5


def parse_price_dif(raw: str) -> Optional[float]:
    """解析 priceDif/priceDiff 字符串为数字"""
    if raw is None:
        return None
    s = str(raw).strip()

    if s in CHANGE_VAL_MAP:
        return CHANGE_VAL_MAP[s]

    m = re.match(r'^([+-]?\d+\.?\d*)%$', s)
    if m:
        return float(m.group(1))

    m = re.match(r'^(上涨|涨)\s*(\d+\.?\d*)$', s)
    if m:
        return float(m.group(2))
    m = re.match(r'^(下跌|跌)\s*(\d+\.?\d*)$', s)
    if m:
        return -float(m.group(2))

    m = re.match(r'^([+-]\d+\.?\d*)$', s)
    if m:
        return float(m.group(1))

    m = re.match(r'^(\d+\.?\d*)$', s)
    if m:
        return float(m.group(1))

    logger.warning("[Liangdawang] [WARN] [unrecognized_price_dif] raw=%s", raw)
    return None


def parse_decimal(raw: Any) -> Optional[float]:
    """安全解析 decimal 值"""
    if raw is None:
        return None
    try:
        return float(str(raw).strip())
    except (ValueError, TypeError):
        logger.error("[Liangdawang] [ERROR] [decimal_parse_failed] raw=%s", raw)
        return None


class LiangdawangCollector(BaseCollector):
    """粮达网价格指数采集器"""

    def __init__(
        self,
        config: ReporterConfig,
        task_id: int,
        execution_id: Optional[str] = None,
        api_base_url: Optional[str] = None,
        target_date: Optional[str] = None,
    ):
        super().__init__(
            config=config,
            task_id=task_id,
            execution_id=execution_id,
            source=Source.LIANGDAWANG.value,
            subject=Subject.PRICE.value,
            coll_type=CollectType.API_COLLECT.value,
            obj=CollectObject.PRICE_INDEX.value,
            remark="粮达网价格指数采集",
        )
        self._backend_url = (api_base_url or config.api_base).rstrip("/")
        self._target_date = target_date
        self._httpx_client: Optional[httpx.AsyncClient] = None
        self._semaphore = asyncio.Semaphore(SEMAPHORE_LIMIT)
        self._variety_stats: dict[str, dict] = {}
        self._variety_429_count: dict[str, int] = {}
        self._total_429_count = 0
        self._circuit_breaker_tripped = False
        self._backfill_progress_file = str(
            Path(__file__).parent.parent.parent / "data" / "liangdawang_backfill_progress.json"
        )

    async def _get_client(self) -> httpx.AsyncClient:
        if self._httpx_client is None:
            self._httpx_client = httpx.AsyncClient(
                timeout=httpx.Timeout(HTTP_TIMEOUT, connect=CONNECT_TIMEOUT),
                follow_redirects=True,
            )
        return self._httpx_client

    async def _api_get(self, url: str, params: dict = None, variety: str = "") -> dict:
        async with self._semaphore:
            client = await self._get_client()
            for attempt in range(3):
                try:
                    resp = await client.get(url, params=params)
                    if resp.status_code == 429:
                        retry_after = resp.headers.get("Retry-After", "30")
                        wait = int(retry_after) if retry_after.isdigit() else 30
                        await asyncio.sleep(wait)
                        # 429 熔断计数
                        if variety:
                            self._variety_429_count[variety] = self._variety_429_count.get(variety, 0) + 1
                            self._total_429_count += 1
                            if self._total_429_count >= 5:
                                self._circuit_breaker_tripped = True
                                logger.warning("[Liangdawang] [ALERT] global_circuit_breaker_tripped total_429=%d", self._total_429_count)
                            elif self._variety_429_count[variety] >= 2:
                                logger.warning("[Liangdawang] [WARN] variety_circuit_breaker variety=%s consecutive_429=%d",
                                               variety, self._variety_429_count[variety])
                        continue
                    resp.raise_for_status()
                    data = resp.json()
                    if data.get("code") == "200" or data.get("success"):
                        return data
                    return data
                except (httpx.TimeoutException, httpx.ConnectError) as e:
                    if attempt < 2:
                        wait = [1, 3, 9][attempt]
                        await asyncio.sleep(wait)
                    else:
                        raise
                except httpx.HTTPStatusError as e:
                    if e.response.status_code >= 500 and attempt < 2:
                        await asyncio.sleep([1, 3, 9][attempt])
                    else:
                        raise
            raise RuntimeError(f"请求失败: {url}")

    def _map_to_price_record(self, item: dict, variety: str, area_type: str) -> dict:
        change_raw = item.get("priceDif") or item.get("priceDiff") or ""
        change_val = parse_price_dif(str(change_raw))
        price = parse_decimal(item.get("price"))
        if price is None:
            raise ValueError(f"价格解析失败: {item.get('price')}")
        record_date = item.get("endDate", "")
        if not record_date:
            record_date = str(date.today())
        return {
            "date": record_date,
            "variety": variety,
            "region": item.get("area", ""),
            "province": item.get("province", ""),
            "area_type": area_type,
            "price": price,
            "change_val": change_val,
            "remark": item.get("remark", ""),
            "unit": VARIETY_UNIT.get(variety, "元/吨"),
            "source": "liangdawang",
        }

    async def _fetch_variety_prices(self, variety: str) -> list[dict]:
        all_records: list[dict] = []
        varieties_data = await self._fetch_varieties()
        variety_info = None
        for v in varieties_data:
            if v.get("varietyName") == variety:
                variety_info = v
                break
        if not variety_info:
            return []

        area_types = variety_info.get("areaTypeList", [])
        if not area_types:
            area_types = [{"areaType": at} if isinstance(at, str) else at
                          for at in (variety_info.get("areaType") or [])]

        for at_info in area_types:
            area_type_name = at_info if isinstance(at_info, str) else at_info.get("areaType", "")

            if self._circuit_breaker_tripped:
                break
            if self._variety_429_count.get(variety, 0) >= 2:
                break

            try:
                data = await self._api_get(
                    f"{API_BASE}/getPriceInfo",
                    params={"varietyName": variety, "areaType": area_type_name},
                    variety=variety,
                )
            except Exception as e:
                logger.error("[Liangdawang] [ERROR] fetch_failed variety=%s areaType=%s error=%s",
                             variety, area_type_name, e)
                continue

            price_data_list = data.get("data", [])
            if not price_data_list:
                continue

            # 优先按品种固定映射（生猪→region, 国产大豆→region），再按区域类型名称映射
            area_type_enum = VARIETY_DEFAULT_AREA_TYPE.get(variety)
            if not area_type_enum:
                area_type_enum = AREA_TYPE_MAP.get(area_type_name, "port")

            for province_group in price_data_list:
                province = province_group.get("province", "")
                price_list = province_group.get("priceInfoList", [])
                for item in price_list:
                    item["province"] = province
                    try:
                        record = self._map_to_price_record(item, variety, area_type_enum)
                        all_records.append(record)
                    except (ValueError, KeyError) as e:
                        self._error_count += 1

            await asyncio.sleep(REQUEST_INTERVAL)

        return all_records

    async def _fetch_varieties(self) -> list[dict]:
        data = await self._api_get(f"{API_BASE}/varietyNameAndAreaType")
        return data.get("data", [])

    async def _batch_write_prices(self, records: list[dict], variety: str) -> bool:
        if not records:
            return True
        client = await self._get_client()
        payload = {
            "execution_id": self._execution_id or "",
            "source": "liangdawang",
            "total_records": len(records),
            "records": records,
        }
        try:
            resp = await client.post(f"{self._backend_url}{PRICE_BATCH_URL}", json=payload)
            resp.raise_for_status()
            result = resp.json()
            if result.get("code") == 200:
                return True
            return False
        except Exception as e:
            logger.error("[Liangdawang] [ERROR] batch_write_exception variety=%s error=%s", variety, e)
            return False

    async def _collect_all_varieties(self) -> int:
        varieties = ["玉米", "小麦", "进口粮", "国产大豆", "生猪"]
        total = 0
        for variety in varieties:
            self.log_info(f"开始采集品种: {variety}")
            records = await self._fetch_variety_prices(variety)
            if not records:
                self.log_warn(f"{variety} 无数据，跳过")
                continue
            self._variety_stats[variety] = {"count": len(records), "success": len(records), "error": 0}
            success = await self._batch_write_prices(records, variety)
            if success:
                total += len(records)
                self.log_info(f"{variety} {len(records)}条已写入 t_price")
            else:
                self.log_error(f"{variety} 写入 t_price 失败")
                self._variety_stats[variety]["error"] = len(records)
                self._variety_stats[variety]["success"] = 0
        return total

    def _is_first_run(self) -> bool:
        progress = self._load_backfill_progress()
        return not progress.get("backfill_completed", False)

    def _load_backfill_progress(self) -> dict:
        try:
            if os.path.exists(self._backfill_progress_file):
                with open(self._backfill_progress_file, encoding="utf-8") as f:
                    return json.load(f)
        except Exception as e:
            logger.warning("[Liangdawang] [WARN] load_backfill_progress_failed error=%s", e)
        return {"completed": [], "backfill_completed": False}

    def _save_backfill_progress(self, progress: dict):
        os.makedirs(os.path.dirname(self._backfill_progress_file), exist_ok=True)
        with open(self._backfill_progress_file, "w", encoding="utf-8") as f:
            json.dump(progress, f, ensure_ascii=False, indent=2)

    async def _backfill_history(self):
        self.log_info("开始历史数据回填...")
        progress = self._load_backfill_progress()
        completed = set(progress.get("completed", []))
        varieties_data = await self._fetch_varieties()
        combinations = []
        for v in varieties_data:
            variety = v.get("varietyName", "")
            if not v.get("isChart"):
                continue
            area_types = v.get("areaTypeList", [])
            for at_info in area_types:
                area_type_name = at_info if isinstance(at_info, str) else at_info.get("areaType", "")
                try:
                    data = await self._api_get(
                        f"{API_BASE}/getPriceInfo",
                        params={"varietyName": variety, "areaType": area_type_name},
                        variety=variety,
                    )
                except Exception:
                    continue
                for pg in data.get("data", []):
                    province = pg.get("province", "")
                    for item in pg.get("priceInfoList", []):
                        area = item.get("area", "")
                        key = f"{variety}+{area_type_name}+{province}+{area}"
                        combinations.append({
                            "key": key, "variety": variety, "area_type": area_type_name,
                            "province": province, "area": area,
                        })

        total_combos = len(combinations)
        self.log_info(f"回填组合总数: {total_combos}")

        for idx, combo in enumerate(combinations):
            if combo["key"] in completed:
                continue
            if self._circuit_breaker_tripped:
                self.log_warn("全局熔断已触发，终止回填")
                break
            try:
                chart_data = await self._api_get(
                    f"{API_BASE}/getPriceChart",
                    params={k: combo[k] for k in ("variety", "area_type", "province", "area")},
                    variety=combo["variety"],
                )
            except Exception:
                completed.add(combo["key"])
                progress["completed"] = list(completed)
                self._save_backfill_progress(progress)
                continue

            price_data = chart_data.get("data", {}).get("priceByArea", {}).get("priceIndexBOs", [])
            if not price_data:
                completed.add(combo["key"])
                progress["completed"] = list(completed)
                self._save_backfill_progress(progress)
                continue

            records: list[dict] = []
            for pd in price_data:
                price = parse_decimal(pd.get("price"))
                if price is None:
                    continue
                change_raw = pd.get("priceDiff") or ""
                records.append({
                    "date": pd.get("priceDate", ""),
                    "variety": combo["variety"],
                    "region": combo["area"],
                    "province": combo["province"],
                    "area_type": VARIETY_DEFAULT_AREA_TYPE.get(combo["variety"],
                                                    AREA_TYPE_MAP.get(combo["area_type"], "port")),
                    "price": price,
                    "change_val": parse_price_dif(str(change_raw)),
                    "remark": "",
                    "unit": VARIETY_UNIT.get(combo["variety"], "元/吨"),
                    "source": "liangdawang",
                })

            for i in range(0, len(records), BACKFILL_BATCH_SIZE):
                batch = records[i:i + BACKFILL_BATCH_SIZE]
                await self._batch_write_prices(batch, combo["variety"])
                await asyncio.sleep(BACKFILL_INTERVAL)

            completed.add(combo["key"])
            progress["completed"] = list(completed)
            self._save_backfill_progress(progress)
            self.log_info(f"回填完成: {combo['key']} ({len(records)}条) | 总进度: {idx + 1}/{total_combos}")

        progress["backfill_completed"] = True
        self._save_backfill_progress(progress)
        self.log_info(f"全量历史回填完成: {total_combos}个组合")

    def collect(self) -> int:
        return asyncio.run(self._async_collect())

    async def _async_collect(self) -> int:
        os.environ["TZ"] = "Asia/Shanghai"
        try:
            time.tzset()
        except AttributeError:
            pass
        try:
            total = await self._collect_all_varieties()
            self._success_count = total
            self._collected_count = total
            if self._is_first_run():
                self.log_info("首次运行，开始历史数据回填（不走执行记录）")
                await self._backfill_history()
            for variety, stats in self._variety_stats.items():
                self.log_info(f"\U0001f4ca {variety}: {stats['count']}条, 成功{stats['success']}, 失败{stats['error']}")
            return total
        except Exception as e:
            logger.error("[Liangdawang] [ERROR] collect_failed error=%s", e)
            raise
        finally:
            if self._httpx_client:
                await self._httpx_client.aclose()
                self._httpx_client = None
