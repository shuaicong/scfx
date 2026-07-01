"""CONAB 巴西玉米周报采集器

采集巴西国家商品供应公司（CONAB）每周玉米市场分析报告 PDF，
存入 MinIO 并提交到知识库。仅 PDF 格式，无 XML 结构化数据。
"""

import logging
import os
import random
import re
import time
import unicodedata
from datetime import datetime
from typing import Optional
from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup

from collectorsdk.base import BaseCollector
from collectorsdk.dimensions import Source, Subject, CollectType, CollectObject

logger = logging.getLogger(__name__)

# 浏览器 UA 池
USER_AGENTS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Safari/605.1.15",
]


class ConabCornCollector(BaseCollector):
    """CONAB 巴西玉米周报采集器"""

    META = {
        "code": "conab",
        "name": "CONAB Corn Weekly",
        "description": "CONAB 巴西玉米周报采集",
    }

    source = Source.GOV_BR.value
    subject = Subject.CORN.value
    coll_type = CollectType.FILE_DOWNLOAD.value
    coll_object = CollectObject.WEEKLY_REPORT.value

    # CONAB 玉米周报页面 URL 模板
    CONAB_BASE_URL = (
        "https://www.gov.br/conab/pt-br/atuacao/informacoes-agropecuarias/"
        "analises-do-mercado-agropecuario-e-extrativista/analises-de-mercado/"
        "historico-semanal/copy4_of_historico-semanal-do-algodao"
    )
    CONAB_YEAR_URL = CONAB_BASE_URL + "/milho-conjuntura-semanal-{year}"

    MINIO_BUCKET = "reports"
    DOWNLOAD_TIMEOUT = 15
    MAX_RETRIES = 3
    RETRY_BASE_DELAY = 2  # 秒
    DOWNLOAD_INTERVAL = 0.5  # 单文件下载间隔（防止限流）

    def __init__(self, config=None, task_id=None, execution_id=None):
        super().__init__(
            config=config,
            task_id=task_id or 0,
            execution_id=execution_id,
            source=self.source,
            subject=self.subject,
            coll_type=self.coll_type,
            obj=self.coll_object,
            remark='CONAB 巴西玉米周报采集',
        )
        self._minio_client = None
        # 从 config 注入后端 API 地址
        raw = self.config.api_base.rstrip('/')
        self._api_base = raw + '/api' if not raw.endswith('/api') else raw

    def _get_minio_client(self):
        """延迟初始化 MinIO 客户端"""
        if self._minio_client is None:
            from collectorsdk.minio_client import MinioClient
            self._minio_client = MinioClient(bucket=self.MINIO_BUCKET)
        return self._minio_client

    @staticmethod
    def _random_ua() -> str:
        return random.choice(USER_AGENTS)

    @staticmethod
    def _extract_date_range(filename: str) -> Optional[str]:
        """从 PDF 文件名中提取日期范围

        CONAB 文件名格式: "02-milho-conjuntura-semanal-05-01-a-09-01-2026.pdf"
        返回: "2026/01/05-01/09"
        """
        pattern = r'(\d{2})-(\d{2})-a-(\d{2})-(\d{2})-(\d{4})'
        match = re.search(pattern, filename)
        if match:
            start_day, start_mon, end_day, end_mon, year = match.groups()
            return f"{year}/{start_mon}/{start_day}-{end_mon}/{end_day}"
        return None

    @staticmethod
    def _sanitize_filename(filename: str) -> str:
        """文件名安全转义：保留字母数字、连字符、下划线、点号"""
        name = unicodedata.normalize('NFKD', filename)
        name = name.encode('ascii', 'ignore').decode('ascii')
        name = name.replace(' ', '-')
        name = re.sub(r'[^a-zA-Z0-9\-_\.]', '', name)
        return name

    def _download_file(self, url: str, label: str = "") -> Optional[bytes]:
        """下载文件，带重试"""
        for attempt in range(1, self.MAX_RETRIES + 1):
            try:
                resp = requests.get(url, headers={
                    "User-Agent": self._random_ua(),
                    "Accept-Language": "pt-BR,zh-CN;q=0.9",
                }, timeout=self.DOWNLOAD_TIMEOUT)
                resp.encoding = 'ISO-8859-1'
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

    def collect(self) -> int:
        """主采集入口

        Returns:
            成功采集的报告数量
        """
        self.log_info("CONAB 玉米周报采集器启动", phase="crawl", category="start")

        year = datetime.now().year
        page_url = self.CONAB_YEAR_URL.format(year=year)
        self.log_info(f"目标页面: {page_url}", phase="crawl", category="url")

        # 抓取页面
        try:
            resp = requests.get(page_url, headers={
                "User-Agent": self._random_ua(),
                "Accept-Language": "pt-BR,zh-CN;q=0.9",
            }, timeout=15)
            resp.encoding = 'ISO-8859-1'
            resp.raise_for_status()
        except requests.RequestException as e:
            self.log_error(f"页面抓取失败: {page_url} - {e}",
                           phase="crawl", category="error")
            return 0

        # 解析 PDF 链接
        soup = BeautifulSoup(resp.text, 'html.parser')
        pdf_links = []
        for a_tag in soup.find_all('a', href=re.compile(r'\.pdf$', re.I)):
            href = a_tag.get('href', '')
            text = a_tag.get_text(strip=True)
            if 'milho' in href.lower() or 'milho' in text.lower():
                pdf_url = urljoin(page_url, href)
                pdf_links.append((text, pdf_url))

        if not pdf_links:
            self.log_warn(f"页面未解析到 PDF 链接: {page_url}",
                          phase="crawl", category="parse")
            return 0

        self.log_info(f"解析到 {len(pdf_links)} 份 PDF", phase="crawl", category="parse")

        # 逐个采集
        mc = self._get_minio_client()
        new_count = 0
        skip_count = 0
        fail_count = 0

        for text, pdf_url in pdf_links:
            filename = pdf_url.rstrip('/').split('/')[-1]
            safe_name = self._sanitize_filename(filename)
            minio_path = f"conab/{year}/{safe_name}"

            # MinIO 去重（跳过下载，但仍需尝试知识库提交）
            exists_in_minio = mc.stat_object(minio_path)
            if not exists_in_minio:
                # 下载
                time.sleep(self.DOWNLOAD_INTERVAL)
                data = self._download_file(pdf_url, label=safe_name)
                if data is None:
                    fail_count += 1
                    continue

                # 上传 MinIO
                try:
                    mc.put_object(minio_path, data, content_type="application/pdf")
                    self.log_info(f"PDF 上传成功: {minio_path}",
                                  phase="report", category="minio")
                except Exception as e:
                    self.log_error(f"MinIO 上传失败: {minio_path} - {e}",
                                   phase="report", category="error")
                    fail_count += 1
                    continue
            else:
                skip_count += 1
                self.log_info(f"文件已存在，跳过下载: {minio_path}",
                              phase="crawl", category="skip")

            # 提取日期范围用于标题
            date_range = self._extract_date_range(filename)
            if not date_range:
                date_range = text  # 正则失败时使用页面显示文本兜底
            title = f"CONAB 玉米周报 {date_range}"
            content_text = (
                f"CONAB 玉米周报\n"
                f"覆盖日期: {date_range}\n"
                f"MinIO: {self.MINIO_BUCKET}/{minio_path}\n"
                f"原始文件: {filename}"
            )

            # 提交知识库
            self.submit_report(
                title=title,
                source="conab",
                url=pdf_url,
                variety="corn",
                report_type="weekly_report",
                content=content_text,
                content_html=f"<p>{content_text.replace(chr(10), '<br>')}</p>",
                publish_time=f"{year}",
            )
            self.log_info(f"知识库提交完成: {title}",
                          phase="report", category="knowledge")
            new_count += 1

        # 统计日志
        summary = (f"CONAB 玉米周报采集完毕: "
                   f"页面={page_url}, "
                   f"新增={new_count}, 跳过={skip_count}, 失败={fail_count}")
        self.log_info(summary, phase="crawl", category="summary")

        # 连续多文件失败告警
        if fail_count >= 3:
            self.log_error(
                f"CONAB 玉米周报采集异常: 连续 {fail_count} 份 PDF 下载失败",
                phase="crawl", category="alert"
            )

        return new_count
