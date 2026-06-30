"""USDA WASDE 报告采集器

采集世界农业供需预测（WASDE）月度报告的 PDF 和 XML 文件，
PDF 存入 MinIO 并提交到知识库，XML 触发后端解析引擎提取结构化供需数据。
"""

import calendar
import logging
import random
import re
import time
from datetime import datetime, timedelta
from typing import Optional

import requests

from collectorsdk.base import BaseCollector
from collectorsdk.dimensions import Source, Subject, CollectType, CollectObject

logger = logging.getLogger(__name__)

# 浏览器 UA 池（用于反爬检测）
USER_AGENTS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:120.0) Gecko/20100101 Firefox/120.0",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Safari/605.1.15",
]


class UsdaWasdeCollector(BaseCollector):
    """USDA WASDE 月度报告采集器"""

    META = {
        "code": "usda",
        "name": "USDA WASDE",
        "description": "USDA 世界农业供需预测报告采集",
    }

    source = Source.USDA.value
    subject = Subject.REPORT.value
    coll_type = CollectType.FILE_DOWNLOAD.value
    coll_object = CollectObject.MONTHLY_REPORT.value

    # WASDE 文件 URL 模板
    WASDE_BASE_URL = "https://www.usda.gov/oce/commodity/wasde"
    WASDE_PAGE_URL = (
        "https://www.usda.gov/about-usda/general-information/"
        "staff-offices/office-chief-economist/commodity-markets/wasde-report"
    )
    MINIO_BUCKET = "reports"
    DOWNLOAD_TIMEOUT = 15
    MAX_RETRIES = 3
    RETRY_BASE_DELAY = 2  # 秒

    # WASDE 发布日期规则：每月第二周的星期二（美国东部时间）
    # 代码动态计算，不依赖硬编码日历

    @staticmethod
    def _get_wasde_release_day(year: int, month: int) -> int:
        """计算 WASDE 发布日期（每月第二个周二）

        USDA WASDE 在每月第二周的星期二发布（美国东部时间）。
        这是固定的发布规则，自 2019 年起一直遵循此规律。

        Returns:
            发布日（1-31）
        """
        # 获取该月第一天是星期几（0=周一, 6=周日）
        first_weekday, days_in_month = calendar.monthrange(year, month)

        # 找到第一个星期二：周二=1
        # 如果第一天是周二(1)，则第一个周二就是1号
        # 否则：第一个周二 = (1 - first_weekday) % 7 + 1
        if first_weekday <= 1:
            first_tuesday = 1 + (1 - first_weekday)
        else:
            first_tuesday = 1 + (8 - first_weekday)

        # 第二个周二
        second_tuesday = first_tuesday + 7

        # 确保不超过当月最后一天
        return min(second_tuesday, days_in_month)

    def __init__(self, *args, **kwargs):
        kwargs.setdefault('source', self.source)
        kwargs.setdefault('subject', self.subject)
        kwargs.setdefault('coll_type', self.coll_type)
        kwargs.setdefault('obj', self.coll_object)
        kwargs.setdefault('remark', 'USDA WASDE 月度报告采集')
        super().__init__(*args, **kwargs)
        self._failed_months: list = []
        self._minio_client = None
        self._page_cache = None  # USDA 页面缓存，批量回填时复用

        # 从 config 注入后端 API 地址，修复 hardcoded localhost
        raw = self.config.api_base.rstrip('/')
        self._api_base = raw + '/api' if not raw.endswith('/api') else raw

    def _get_minio_client(self):
        """延迟初始化 MinIO 客户端（使用 reports 桶）"""
        if self._minio_client is None:
            from collectorsdk.minio_client import MinioClient
            self._minio_client = MinioClient(bucket=self.MINIO_BUCKET)
        return self._minio_client

    def _random_ua(self) -> str:
        """随机选择一个 UA"""
        return random.choice(USER_AGENTS)

    def _determine_report_months(self) -> list:
        """确定需要采集的报告月份列表

        首次部署/历史回填：从对象参数或配置获取起始月份（如 2026-01）
        常规运行：判断当月发布日是否已过，是则采集当月

        Returns:
            [(year, month), ...] 待采集月份列表
        """
        today = time.localtime()
        current_year = today.tm_year
        current_month = today.tm_mon
        current_day = today.tm_mday

        # 尝试从采集任务参数中获取回填起始月
        backfill_start = getattr(self, 'backfill_start', None)

        months = []
        if backfill_start:
            try:
                start_parts = backfill_start.split('-')
                start_year = int(start_parts[0])
                start_month = int(start_parts[1])
                for year in range(start_year, current_year + 1):
                    end_month = current_month if year == current_year else 12
                    start_m = start_month if year == start_year else 1
                    for month in range(start_m, end_month + 1):
                        if year == current_year and month == current_month:
                            publish_day = self._get_wasde_release_day(year, month)
                            if current_day <= publish_day:
                                self.log_info(
                                    f"当月({year}-{month:02d})尚未到发布日({publish_day}日)，跳过",
                                    phase="crawl", category="skip")
                                continue
                        months.append((year, month))
            except (ValueError, IndexError, AttributeError):
                pass

        if not months:
            # 兜底：只采当月
            publish_day = self._get_wasde_release_day(current_year, current_month)
            if current_day > publish_day:
                months.append((current_year, current_month))

        self.log_info(f"待采集月份: {months}", phase="crawl", category="plan")
        return months

    def _build_url(self, year: int, month: int, ext: str) -> str:
        """构建 WASDE 文件 URL"""
        mm = f"{month:02d}"
        yy = str(year)[-2:]
        return f"{self.WASDE_BASE_URL}/wasde{mm}{yy}v2.{ext}"

    def _build_minio_path(self, year: int, month: int, ext: str) -> str:
        """构建 MinIO 存储路径: wasde/2026/wasde0626v2.pdf"""
        mm = f"{month:02d}"
        yy = str(year)[-2:]
        return f"wasde/{year}/wasde{mm}{yy}v2.{ext}"

    def _fetch_report_date(self, year: int, month: int) -> Optional[str]:
        """从 USDA 页面提取该期报告的真实发布日期

        页面内容会被缓存（self._page_cache），批量回填时只请求一次。

        Args:
            year: 年份
            month: 月份

        Returns:
            YYYY-MM-DD 格式日期字符串，解析失败返回计算出的当月发布日
        """
        try:
            # 缓存页面内容，避免批量回填时重复 HTTP 请求
            if self._page_cache is None:
                resp = requests.get(
                    self.WASDE_PAGE_URL,
                    headers={"User-Agent": self._random_ua()},
                    timeout=15,
                )
                resp.raise_for_status()
                self._page_cache = resp.text

            month_name = [
                "January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"
            ][month - 1]

            # 匹配 "Month DD, YYYY" 格式
            pattern = rf"{month_name}\s+\d+,\s+{year}"
            match = re.search(pattern, self._page_cache)
            if match:
                dt = datetime.strptime(match.group(0), "%B %d, %Y")
                return dt.strftime("%Y-%m-%d")

        except Exception as e:
            self.log_warn(f"获取发布日期失败: {e}", phase="crawl", category="parse")

        # 兜底：返回计算出的发布日
        publish_day = self._get_wasde_release_day(year, month)
        return f"{year}-{month:02d}-{publish_day:02d}"

    def _download_file(self, url: str, label: str = "") -> Optional[bytes]:
        """下载文件，带重试和异常分类

        Args:
            url: 文件下载 URL
            label: 日志标签（如 "PDF" / "XML"）

        Returns:
            文件二进制内容，失败返回 None
        """
        for attempt in range(1, self.MAX_RETRIES + 1):
            try:
                resp = requests.get(
                    url,
                    headers={
                        "User-Agent": self._random_ua(),
                        "Accept": "application/pdf,application/xml,*/*",
                    },
                    timeout=self.DOWNLOAD_TIMEOUT,
                )
                if resp.status_code == 404:
                    self.log_error(f"[{label}] 文件不存在(404): {url}",
                                   phase="crawl", category="error")
                    return None
                resp.raise_for_status()
                self.log_info(f"[{label}] 下载成功: {url} ({len(resp.content)} bytes)",
                              phase="crawl", category="success")
                return resp.content

            except requests.Timeout:
                delay = self.RETRY_BASE_DELAY * attempt
                self.log_warn(f"[{label}] 下载超时(第{attempt}次, {delay}s后重试): {url}",
                              phase="crawl", category="retry")
                time.sleep(delay)

            except requests.RequestException as e:
                self.log_warn(f"[{label}] 下载异常(第{attempt}次): {url} - {e}",
                              phase="crawl", category="retry")
                time.sleep(self.RETRY_BASE_DELAY)

        self.log_error(f"[{label}] 下载失败(已达最大重试): {url}",
                       phase="crawl", category="error")
        return None

    def _collect_single(self, year: int, month: int) -> bool:
        """采集单期 WASDE 报告

        Args:
            year: 年份
            month: 月份

        Returns:
            True 采集成功, False 失败
        """
        ym_desc = f"{year}-{month:02d}"
        report_key = f"wasde_{year}{month:02d}"
        self.log_info(f"开始采集: {ym_desc}, report_key={report_key}",
                      phase="crawl", category="collect")

        pdf_url = self._build_url(year, month, "pdf")
        xml_url = self._build_url(year, month, "xml")
        pdf_path = self._build_minio_path(year, month, "pdf")
        xml_path = self._build_minio_path(year, month, "xml")

        self.log_info(f"PDF URL: {pdf_url}", phase="crawl", category="url")
        self.log_info(f"XML URL: {xml_url}", phase="crawl", category="url")
        self.log_info(f"PDF MinIO: {pdf_path}", phase="crawl", category="path")
        self.log_info(f"XML MinIO: {xml_path}", phase="crawl", category="path")

        # 前置去重：检查 MinIO 文件是否已存在
        mc = self._get_minio_client()
        pdf_exists = mc.stat_object(pdf_path)
        xml_exists = mc.stat_object(xml_path)

        if pdf_exists and xml_exists:
            self.log_info(f"文件已存在，跳过采集: {ym_desc}",
                          phase="crawl", category="skip")
            return True

        # 下载
        pdf_data = self._download_file(pdf_url, label=f"PDF-{ym_desc}")
        if pdf_data is None:
            self._failed_months.append(ym_desc)
            self.log_error(f"PDF 下载失败，跳过该月份: {ym_desc}",
                           phase="crawl", category="error")
            return False

        xml_data = self._download_file(xml_url, label=f"XML-{ym_desc}")
        if xml_data is None:
            self._failed_months.append(ym_desc)
            self.log_error(f"XML 下载失败，跳过该月份: {ym_desc}",
                           phase="crawl", category="error")
            return False

        # 上传到 MinIO
        try:
            mc.put_object(pdf_path, pdf_data, content_type="application/pdf")
            self.log_info(f"PDF 上传成功: {pdf_path}", phase="report", category="minio")

            mc.put_object(xml_path, xml_data, content_type="application/xml")
            self.log_info(f"XML 上传成功: {xml_path}", phase="report", category="minio")
        except Exception as e:
            self.log_error(f"MinIO 上传失败: {e}", phase="report", category="error")
            self._failed_months.append(ym_desc)
            return False

        # 提取发布日期
        report_date = self._fetch_report_date(year, month)
        self.log_info(f"发布日期: {report_date}", phase="crawl", category="date")

        # PDF 提交知识库
        content_text = (
            f"USDA WASDE 报告 {year}年{month}月\n"
            f"报告周期: {report_key}\n"
            f"PDF MinIO 路径: {self.MINIO_BUCKET}/{pdf_path}\n"
        )
        self.submit_report(
            title=f"USDA WASDE 报告 {year}年{month}月",
            source="usda",
            url=pdf_url,
            content=content_text,
            content_html=f"<p>{content_text.replace(chr(10), '<br>')}</p>",
            publish_time=report_date,
        )
        self.log_info(f"知识库提交完成: {ym_desc}", phase="report", category="knowledge")

        # 触发后端解析
        try:
            parse_payload = {
                "source_type": "wasde",
                "report_key": report_key,
                "minio_path": xml_path,
                "report_date": report_date,
            }
            api_base = getattr(self, '_api_base', 'http://localhost:8080/api')
            resp = requests.post(
                f"{api_base}/parse/trigger",
                json=parse_payload,
                headers={"Content-Type": "application/json"},
                timeout=10,
            )
            self.log_info(f"解析触发结果: HTTP {resp.status_code} - {resp.text}",
                          phase="report", category="parse")
        except Exception as e:
            self.log_warn(f"触发解析请求失败（不影响采集）: {e}",
                          phase="report", category="parse")

        self.log_info(f"采集完成: {ym_desc}", phase="crawl", category="complete")
        return True

    def collect(self) -> int:
        """主采集入口

        Returns:
            成功采集的报告数量
        """
        self.log_info("USDA WASDE 采集器启动", phase="crawl", category="start")

        months = self._determine_report_months()
        if not months:
            self.log_info("没有待采集的报告月份", phase="crawl", category="skip")
            return 0

        success_count = 0
        fail_count = 0

        for year, month in months:
            try:
                result = self._collect_single(year, month)
                if result:
                    success_count += 1
                else:
                    fail_count += 1
            except Exception as e:
                self.log_error(f"采集异常: {year}-{month:02d} - {e}",
                               phase="crawl", category="error")
                self._failed_months.append(f"{year}-{month:02d}")
                fail_count += 1

        self.log_info(
            f"采集完成: 成功={success_count}, 失败={fail_count}, "
            f"失败月份={self._failed_months}",
            phase="crawl", category="summary"
        )

        if self._failed_months:
            self.log_error(f"失败月份: {self._failed_months}",
                           phase="crawl", category="error")

        return success_count
