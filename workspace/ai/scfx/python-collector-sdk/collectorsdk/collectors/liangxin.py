"""粮信网采集器"""
import time
import logging
from datetime import datetime
from typing import List, Dict, Optional

from playwright.sync_api import sync_playwright, Page

from ..config import ReporterConfig
from ..base import BaseCollector
from .knowledge_api import KnowledgeAPIClient
from ..dimensions import Source, Subject, CollectType, CollectObject

logger = logging.getLogger(__name__)


class LiangxinCollector(BaseCollector):
    """粮信网采集器

    用于采集粮信网玉米晨报/日报等报告
    """

    LOGIN_URL = "https://my.chinagrain.cn/jinnong/a/login"
    REPORT_LIST_URL = "https://my.chinagrain.cn/jinnong/liangyou_news.htm"
    REPORT_DETAIL_URL = "https://my.chinagrain.cn/jinnong/liangyou_vipController.htm"

    def __init__(
        self,
        config: ReporterConfig,
        task_id: int,
        username: str,
        password: str,
        report_type: str = "morning"  # morning / evening
    ):
        """初始化粮信网采集器

        Args:
            config: 上报配置
            task_id: 任务ID
            username: 粮信网账号
            password: 粮信网密码
            report_type: 报告类型 morning=晨报 evening=日报
        """
        super().__init__(
            config=config,
            task_id=task_id,
            source=Source.LIANGXIN.value,
            subject=Subject.CORN.value,
            coll_type=CollectType.LOGIN_CRAWL.value,
            obj=CollectObject.DAILY_REPORT.value,
            remark=f"粮信网玉米{'晨报' if report_type == 'morning' else '日报'}采集",
        )
        self.username = username
        self.password = password
        self.report_type = report_type
        self._page: Optional[Page] = None
        self._logged_in = False
        self._knowledge_api = KnowledgeAPIClient(
            base_url=config.api_base or "http://localhost:5002"
        )

    def _create_browser(self):
        """创建浏览器"""
        self.playwright = sync_playwright().start()
        self.browser = self.playwright.chromium.launch(
            headless=True,
            args=['--no-sandbox', '--disable-dev-shm-usage']
        )
        self.context = self.browser.new_context(
            viewport={'width': 1920, 'height': 1080},
            user_agent='Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36'
        )
        self._page = self.context.new_page()

    def _close_browser(self):
        """关闭浏览器"""
        if self.browser:
            self.browser.close()
        if self.playwright:
            self.playwright.stop()

    def _login(self) -> bool:
        """执行登录

        Returns:
            True=登录成功，False=登录失败
        """
        try:
            self._page.goto(self.LOGIN_URL)
            self._page.wait_for_timeout(3000)

            # 填入账号密码
            self._page.fill("input[name=username]", self.username)
            self._page.fill("input[name=password]", self.password)

            # 点击登录按钮
            self._page.click("input[type=submit]")

            # 等待跳转
            self._page.wait_for_timeout(5000)

            # 检查是否登录成功
            current_url = self._page.url
            if "login" not in current_url.lower():
                logger.info(f"登录成功，当前URL: {current_url}")
                self._logged_in = True
                return True
            else:
                logger.warning(f"登录失败，当前URL: {current_url}")
                return False

        except Exception as e:
            logger.error(f"登录异常: {e}")
            return False

    def _get_report_list(self, date_str: str) -> List[Dict]:
        """获取指定日期的报告列表

        Args:
            date_str: 日期字符串，格式 YYYY-MM-DD

        Returns:
            报告列表 [{title, url, publish_time}]
        """
        reports = []

        # 构造筛选URL：按品种筛选（玉米1061）
        url = f"{self.REPORT_LIST_URL}?type=7008&producttype=1061&key="
        self._page.goto(url)
        self._page.wait_for_timeout(5000)

        # 查找今日发布的报告
        try:
            # 遍历页面上的报告链接
            links = self._page.query_selector_all("a[href*='liangyou_vipController']")

            for link in links:
                href = link.get_attribute("href")
                text = link.text_content() or ""

                # 判断是否是指定日期的报告
                if date_str in text:
                    reports.append({
                        "title": text.strip(),
                        "url": href,
                        "publish_time": date_str
                    })
                    logger.info(f"找到报告: {text.strip()}")

        except Exception as e:
            logger.error(f"解析报告列表异常: {e}")

        return reports

    def _get_report_content(self, url: str) -> Optional[str]:
        """获取报告正文

        Args:
            url: 报告详情页URL

        Returns:
            报告正文内容，失败返回None
        """
        try:
            self._page.goto(url)
            self._page.wait_for_timeout(3000)

            # 查找正文容器
            content_elem = self._page.query_selector(".article-conte-infor")
            if content_elem:
                content = content_elem.text_content()
                if content:
                    return content.strip()

            # 如果找不到正文容器，可能需要登录后查看
            logger.warning(f"未找到正文内容: {url}")
            return None

        except Exception as e:
            logger.error(f"获取报告内容异常: {e}")
            return None

    def collect(self) -> int:
        """执行采集

        Returns:
            采集数量
        """
        count = 0
        today = datetime.now().strftime("%Y-%m-%d")

        try:
            # 创建浏览器并登录
            self._create_browser()
            if not self._login():
                raise Exception("登录失败")

            # 获取报告列表
            reports_meta = self._get_report_list(today)
            logger.info(f"今日找到 {len(reports_meta)} 篇报告")

            # 收集所有报告数据
            reports = []
            for report in reports_meta:
                self.log_info(f"采集报告: {report['title']}")

                content = self._get_report_content(report["url"])
                if content:
                    reports.append({
                        "title": report["title"],
                        "content": content,
                        "source": "liangxin",
                        "url": report["url"],
                        "variety": "玉米",
                        "report_type": "晨报" if self.report_type == "morning" else "日报",
                        "publish_time": report["publish_time"],
                    })
                else:
                    self.log_warn(f"报告内容为空: {report['title']}")

            # 批量提交到知识库
            if reports:
                result = self._knowledge_api.ingest_reports(reports)
                count = result["ingested"]
                logger.info(f"成功提交 {count} 条报告到知识库")

        except Exception as e:
            self.log_error(f"采集异常: {e}")
            raise
        finally:
            self._close_browser()

        return count

    def run(self) -> dict:
        """重写run方法，直接采集不经过上报器"""
        result = {
            "success": False,
            "execution_id": None,
            "collected_count": 0,
            "error": None,
        }

        try:
            count = self.collect()
            result["success"] = True
            result["collected_count"] = count
        except Exception as e:
            result["error"] = str(e)

        return result