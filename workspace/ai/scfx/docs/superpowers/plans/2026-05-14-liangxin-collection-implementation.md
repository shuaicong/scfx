# 粮信网采集系统实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现粮信网玉米晨报/日报的自动化采集，数据存入知识库

**Architecture:** Python采集器使用Playwright模拟浏览器登录，数据通过HTTP API存入知识库。定时调度使用APScheduler。去重基于"标题+发布时间"唯一索引。

**Tech Stack:** Python 3.14 + Playwright + APScheduler + requests

---

## 文件结构

```
python-collector-sdk/
├── collectorsdk/
│   ├── __init__.py
│   ├── config.py              # 已有配置管理
│   ├── dimensions.py          # 已有维度枚举
│   ├── reporter.py            # 已有上报器
│   ├── collectors/
│   │   ├── __init__.py
│   │   └── liangxin.py       # 【新】粮信网采集器
│   └── utils.py              # 已有工具函数
├── scheduler/
│   ├── __init__.py
│   └── corn_scheduler.py      # 【新】玉米采集调度器
├── tests/
│   ├── __init__.py
│   └── test_liangxin.py       # 【新】采集器测试
├── main.py                    # 【改】入口文件
├── requirements.txt           # 【新】依赖
└── config.yaml               # 【新】配置文件
```

---

## Task 1: 项目初始化 - 创建目录和依赖文件

**Files:**
- Create: `python-collector-sdk/requirements.txt`
- Create: `python-collector-sdk/config.yaml`
- Create: `python-collector-sdk/scheduler/__init__.py`
- Create: `python-collector-sdk/tests/__init__.py`

- [ ] **Step 1: 创建requirements.txt**

```txt
playwright>=1.40.0
apscheduler>=3.10.0
requests>=2.31.0
pyyaml>=6.0
python-dotenv>=1.0.0
```

- [ ] **Step 2: 创建config.yaml**

```yaml
liangxin:
  username: "33022"
  password: "qlp707"
  login_url: "https://my.chinagrain.cn/jinnong/a/login"
  report_list_url: "https://my.chinagrain.cn/jinnong/liangyou_news.htm"
  report_detail_url: "https://my.chinagrain.cn/jinnong/liangyou_vipController.htm"

corn:
  variety: "玉米"
  product_type: 1061  # 玉米在粮信网的品种代码
  report_types:
    morning:
      name: "晨报"
      collect_times: ["09:30", "10:00", "10:30"]
    evening:
      name: "日报"
      collect_times: ["18:30", "19:00", "19:30"]

knowledge_api:
  base_url: "http://localhost:5002"
  ingest_endpoint: "/api/knowledge/ingest"

collection:
  retry_times: 3
  retry_delay: 30  # 秒
  content_selector: ".article-conte-infor"
  title_selector: ".article-title"
  meta_selector: ".article-time"
```

- [ ] **Step 3: 创建scheduler/__init__.py**

```python
"""采集调度模块"""
from .corn_scheduler import CornScheduler

__all__ = ["CornScheduler"]
```

- [ ] **Step 4: 创建tests/__init__.py**

```python
"""测试模块"""
```

- [ ] **Step 5: 提交代码**

```bash
cd /Users/hucong/workspace/ai/scfx/python-collector-sdk
git add requirements.txt config.yaml scheduler/ tests/
git commit -m "feat(collector): init project structure"
```

---

## Task 2: 粮信网采集器核心开发

**Files:**
- Create: `python-collector-sdk/collectorsdk/collectors/liangxin.py`
- Test: `python-collector-sdk/tests/test_liangxin.py`

- [ ] **Step 1: 创建LiangxinCollector类**

```python
"""粮信网采集器"""
import time
import logging
from datetime import datetime
from typing import List, Dict, Optional

from playwright.sync_api import sync_playwright, Page

from ..config import ReporterConfig
from ..collectors import BaseCollector
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
            reports = self._get_report_list(today)
            logger.info(f"今日找到 {len(reports)} 篇报告")

            # 遍历每个报告，采集正文
            for report in reports:
                self.log_info(f"采集报告: {report['title']}")

                content = self._get_report_content(report["url"])
                if content:
                    # 提交到知识库
                    self.submit_report(
                        title=report["title"],
                        source=Source.LIANGXIN.value,
                        url=report["url"],
                        variety="玉米",
                        report_type="晨报" if self.report_type == "morning" else "日报",
                        content=content,
                        publish_time=report["publish_time"],
                    )
                    count += 1
                    self.report_progress(count)
                else:
                    self.log_warn(f"报告内容为空: {report['title']}")

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
```

- [ ] **Step 2: 运行采集器测试**

Run: `cd /Users/hucong/workspace/ai/scfx/python-collector-sdk && python3 -c "from collectorsdk.collectors.liangxin import LiangxinCollector; print('Import OK')"`
Expected: 输出 "Import OK"

- [ ] **Step 3: 提交代码**

```bash
git add collectorsdk/collectors/liangxin.py
git commit -m "feat(liangxin): add LiangxinCollector for corn report collection"
```

---

## Task 3: 采集调度器开发

**Files:**
- Create: `python-collector-sdk/scheduler/corn_scheduler.py`
- Modify: `python-collector-sdk/main.py`

- [ ] **Step 1: 创建CornScheduler调度器**

```python
"""玉米采集调度器"""
import logging
from datetime import datetime
from apscheduler.schedulers.blocking import BlockingScheduler
from apscheduler.triggers.cron import CronTrigger

from collectorsdk.config import ReporterConfig
from collectorsdk.collectors.liangxin import LiangxinCollector

logger = logging.getLogger(__name__)


class CornScheduler:
    """玉米采集调度器

    定时执行玉米晨报/日报采集任务
    """

    def __init__(self, config_path: str = "config.yaml"):
        """初始化调度器

        Args:
            config_path: 配置文件路径
        """
        self.scheduler = BlockingScheduler()
        self._load_config(config_path)

    def _load_config(self, config_path: str):
        """加载配置文件"""
        import yaml
        with open(config_path, 'r', encoding='utf-8') as f:
            self.config = yaml.safe_load(f)

        self.username = self.config["liangxin"]["username"]
        self.password = self.config["liangxin"]["password"]
        self.knowledge_api_base = self.config["knowledge_api"]["base_url"]
        self.retry_times = self.config["collection"]["retry_times"]

    def _collect_morning(self):
        """采集晨报"""
        logger.info("=" * 50)
        logger.info("开始采集玉米晨报...")
        logger.info("=" * 50)

        config = ReporterConfig(
            enabled=True,
            api_base=self.knowledge_api_base,
            async_mode=False,  # 同步模式
        )

        collector = LiangxinCollector(
            config=config,
            task_id=1,
            username=self.username,
            password=self.password,
            report_type="morning",
        )

        result = collector.run()

        if result["success"]:
            logger.info(f"晨报采集成功，共 {result['collected_count']} 条")
        else:
            logger.error(f"晨报采集失败: {result['error']}")

    def _collect_evening(self):
        """采集日报"""
        logger.info("=" * 50)
        logger.info("开始采集玉米日报...")
        logger.info("=" * 50)

        config = ReporterConfig(
            enabled=True,
            api_base=self.knowledge_api_base,
            async_mode=False,
        )

        collector = LiangxinCollector(
            config=config,
            task_id=2,
            username=self.username,
            password=self.password,
            report_type="evening",
        )

        result = collector.run()

        if result["success"]:
            logger.info(f"日报采集成功，共 {result['collected_count']} 条")
        else:
            logger.error(f"日报采集失败: {result['error']}")

    def start(self):
        """启动调度器"""
        # 晨报调度：每天 09:30, 10:00, 10:30
        morning_times = self.config["corn"]["report_types"]["morning"]["collect_times"]
        for t in morning_times:
            hour, minute = map(int, t.split(":"))
            self.scheduler.add_job(
                self._collect_morning,
                CronTrigger(hour=hour, minute=minute),
                id=f"morning_{t.replace(':', '')}",
                name=f"玉米晨报 {t}",
                replace_existing=True,
            )

        # 日报调度：每天 18:30, 19:00, 19:30
        evening_times = self.config["corn"]["report_types"]["evening"]["collect_times"]
        for t in evening_times:
            hour, minute = map(int, t.split(":"))
            self.scheduler.add_job(
                self._collect_evening,
                CronTrigger(hour=hour, minute=minute),
                id=f"evening_{t.replace(':', '')}",
                name=f"玉米日报 {t}",
                replace_existing=True,
            )

        logger.info("调度器已启动")
        logger.info(f"晨报调度: {morning_times}")
        logger.info(f"日报调度: {evening_times}")

        try:
            self.scheduler.start()
        except KeyboardInterrupt:
            logger.info("调度器已停止")
            self.scheduler.shutdown()

    def stop(self):
        """停止调度器"""
        self.scheduler.shutdown()
```

- [ ] **Step 2: 修改main.py添加调度命令**

```python
#!/usr/bin/env python3
"""
采集SDK入口
"""

import argparse
import logging

from collectorsdk.config import ReporterConfig
from collectorsdk.collectors.liangxin import LiangxinCollector
from scheduler.corn_scheduler import CornScheduler

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)


def run_collector(args):
    """运行采集器"""
    config = ReporterConfig(
        enabled=args.enabled,
        api_base=args.api_base,
        async_mode=True,
    )

    collector = LiangxinCollector(
        config=config,
        task_id=args.task_id,
        username=args.username,
        password=args.password,
        report_type=args.report_type,
    )

    result = collector.run()
    print(f"采集结果: {result}")


def run_scheduler(args):
    """运行调度器"""
    scheduler = CornScheduler(config_path=args.config)
    scheduler.start()


def main():
    parser = argparse.ArgumentParser(description="采集SDK")

    subparsers = parser.add_subparsers(dest='command', help='子命令')

    # 采集命令
    collect_parser = subparsers.add_parser('collect', help='运行一次采集')
    collect_parser.add_argument('--task-id', type=int, default=1, help='任务ID')
    collect_parser.add_argument('--username', default='33022', help='粮信网用户名')
    collect_parser.add_argument('--password', default='qlp707', help='粮信网密码')
    collect_parser.add_argument('--report-type', default='morning', choices=['morning', 'evening'], help='报告类型')
    collect_parser.add_argument('--enabled', type=bool, default=False, help='是否启用上报')
    collect_parser.add_argument('--api-base', default='', help='API地址')

    # 调度命令
    schedule_parser = subparsers.add_parser('schedule', help='启动调度器')
    schedule_parser.add_argument('--config', default='config.yaml', help='配置文件路径')

    args = parser.parse_args()

    if args.command == 'collect':
        run_collector(args)
    elif args.command == 'schedule':
        run_scheduler(args)
    else:
        parser.print_help()


if __name__ == "__main__":
    main()
```

- [ ] **Step 3: 测试调度器导入**

Run: `cd /Users/hucong/workspace/ai/scfx/python-collector-sdk && python3 -c "from scheduler.corn_scheduler import CornScheduler; print('Import OK')"`
Expected: 输出 "Import OK"

- [ ] **Step 4: 提交代码**

```bash
git add scheduler/corn_scheduler.py main.py
git commit -m "feat(scheduler): add CornScheduler with APScheduler"
```

---

## Task 4: 知识库API对接

**Files:**
- Create: `python-collector-sdk/collectorsdk/collectors/knowledge_api.py`
- Modify: `python-collector-sdk/collectorsdk/collectors/liangxin.py`

- [ ] **Step 1: 创建KnowledgeAPIClient**

```python
"""知识库API客户端"""
import logging
import requests
from typing import List, Dict, Optional

logger = logging.getLogger(__name__)


class KnowledgeAPIError(Exception):
    """知识库API异常"""
    pass


class KnowledgeAPIClient:
    """知识库API客户端

    用于将采集数据推送到知识库系统
    """

    def __init__(self, base_url: str, timeout: int = 30):
        """初始化

        Args:
            base_url: 知识库API基础地址
            timeout: 请求超时时间（秒）
        """
        self.base_url = base_url.rstrip('/')
        self.timeout = timeout

    def ingest_reports(self, reports: List[Dict]) -> Dict:
        """批量提交报告到知识库

        Args:
            reports: 报告列表，每个报告包含:
                - title: 标题
                - content: 正文
                - source: 来源
                - url: 原文链接
                - variety: 品种
                - report_type: 报告类型
                - publish_time: 发布时间

        Returns:
            提交结果 {
                "success": True/False,
                "ingested": 数量,
                "failed": 数量,
                "results": [...]
            }
        """
        url = f"{self.base_url}/api/knowledge/ingest"

        payload = {
            "executionId": f"liangxin-{datetime.now().strftime('%Y%m%d%H%M%S')}",
            "source": "liangxin",
            "reports": reports
        }

        try:
            resp = requests.post(url, json=payload, timeout=self.timeout)
            resp.raise_for_status()

            data = resp.json()
            if data.get("code") == 200:
                logger.info(f"成功提交 {len(reports)} 条报告到知识库")
                return {
                    "success": True,
                    "ingested": data["data"].get("ingested", 0),
                    "failed": data["data"].get("failed", 0),
                    "results": data["data"].get("results", [])
                }
            else:
                raise KnowledgeAPIError(f"API返回错误: {data.get('message')}")

        except requests.RequestException as e:
            logger.error(f"提交报告到知识库失败: {e}")
            raise KnowledgeAPIError(f"网络请求失败: {e}")
        except Exception as e:
            logger.error(f"提交报告异常: {e}")
            raise


from datetime import datetime
```

- [ ] **Step 2: 更新LiangxinCollector使用KnowledgeAPI**

```python
# 在LiangxinCollector中添加知识库API调用

def __init__(self, ...):
    # ... 现有初始化代码 ...

    # 添加知识库API客户端
    self._knowledge_api = KnowledgeAPIClient(
        base_url=config.api_base or "http://localhost:5002"
    )


def collect(self) -> int:
    # ... 现有采集代码 ...

    # 将self.submit_report()替换为直接调用知识库API
    reports = []
    for report in report_list:
        reports.append({
            "title": report["title"],
            "content": content,
            "source": "liangxin",
            "url": report["url"],
            "variety": "玉米",
            "report_type": "晨报",
            "publish_time": report["publish_time"],
        })

    # 批量提交
    if reports:
        result = self._knowledge_api.ingest_reports(reports)
        count = result["ingested"]

    # ... 后续代码 ...
```

- [ ] **Step 3: 测试API客户端导入**

Run: `cd /Users/hucong/workspace/ai/scfx/python-collector-sdk && python3 -c "from collectorsdk.collectors.knowledge_api import KnowledgeAPIClient; print('Import OK')"`
Expected: 输出 "Import OK"

- [ ] **Step 4: 提交代码**

```bash
git add collectorsdk/collectors/knowledge_api.py collectorsdk/collectors/liangxin.py
git commit -m "feat(knowledge-api): add KnowledgeAPIClient for ingesting reports"
```

---

## Task 5: 本地测试验证

**Files:**
- Modify: `python-collector-sdk/config.yaml`

- [ ] **Step 1: 手动触发采集测试**

Run: `cd /Users/hucong/workspace/ai/scfx/python-collector-sdk && python3 main.py collect --username 33022 --password qlp707 --report-type morning --enabled false`
Expected: 输出采集结果

- [ ] **Step 2: 检查知识库是否收到数据**

Run: `curl http://localhost:5002/api/knowledge/list?page=1&size=10`
Expected: 返回包含粮信网报告的列表

- [ ] **Step 3: 提交测试结果**

```bash
git add config.yaml
git commit -m "test(collector): manual test successful - corn morning report collected"
```

---

## 实施检查清单

- [ ] Task 1: 项目初始化 - 目录和依赖文件 ✅
- [ ] Task 2: 粮信网采集器核心开发 ✅
- [ ] Task 3: 采集调度器开发 ✅
- [ ] Task 4: 知识库API对接 ✅
- [ ] Task 5: 本地测试验证 ✅

---

## 下一步

第一阶段完成后，进入**第二阶段：状态透明化**
- 添加采集日志API
- 添加采集状态API
- 前端Knowledge.vue状态栏展示