#!/usr/bin/env python3
"""
采集SDK入口示例

展示如何使用 SDK 的两种模式：
1. 完全解耦模式（推荐）：继承 BaseCollector
2. 手动模式：直接使用 CollectorReporter
"""

import os
import sys

# 添加项目路径
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from collectorsdk import (
    ReporterConfig,
    BaseCollector,
    Source,
    Subject,
    CollectType,
    CollectObject,
    parse_publish_time,
    extract_report_type,
)


# ==================== 示例1：完全解耦模式 ====================

class LiangxinwangCollector(BaseCollector):
    """
    粮信网采集器

    特点：
    - 采集代码完全不调用任何 HTTP/上报
    - 只需实现 collect() 方法
    - 自动获得上报能力
    """

    LOGIN_URL = "https://my.chinagrain.cn/jinnong/a/login"
    REPORT_URL = "https://www.chinagrain.cn/report/"

    def __init__(self, config: ReporterConfig, task_id: int, username: str, password: str):
        super().__init__(
            config=config,
            task_id=task_id,
            source=Source.LIANGXIN.value,
            subject=Subject.CORN.value,
            coll_type=CollectType.LOGIN_CRAWL.value,
            obj=CollectObject.DAILY_REPORT.value,
            remark="粮信网玉米晨报采集",
        )
        self.username = username
        self.password = password

    def collect(self) -> int:
        """
        执行粮信网数据采集

        注意：此方法内完全不调用任何 HTTP/上报
        所有日志使用 self.log_* 方法
        所有数据提交使用 self.submit_report 方法
        """
        count = 0

        try:
            # 模拟采集逻辑
            self.log_info("开始登录粮信网...")

            # 模拟登录
            self.log_info("访问登录页面...")
            # page.goto(self.LOGIN_URL)

            self.log_info("提交登录表单...")
            # page.fill('#username', self.username)
            # page.fill('#password', self.password)
            # page.click('input[type="submit"]')

            self.log_info("登录成功")

            # 模拟访问报告页面
            self.log_info(f"访问报告页面: {self.REPORT_URL}")
            # page.goto(self.REPORT_URL)

            # 模拟解析报告
            self.log_info("发现 5 篇报告")
            reports = [
                {"title": "（2026年4月29日）玉米晨报", "url": "https://www.chinagrain.cn/report/1"},
                {"title": "（2026年4月28日）玉米日报", "url": "https://www.chinagrain.cn/report/2"},
                {"title": "玉米市场周报", "url": "https://www.chinagrain.cn/report/3"},
                {"title": "（2026年4月27日）玉米晨报", "url": "https://www.chinagrain.cn/report/4"},
                {"title": "玉米专题报告", "url": "https://www.chinagrain.cn/report/5"},
            ]

            for i, report in enumerate(reports, 1):
                self.log_info(f"采集第 {i}/{len(reports)} 篇: {report['title']}")

                # 提交数据
                self.submit_report(
                    title=report["title"],
                    source=Source.LIANGXIN.value,
                    url=report["url"],
                    variety="玉米",
                    report_type=extract_report_type(report["title"]),
                    content=f"【模拟数据】{report['title']}的内容...",
                    publish_time="2026-04-29T08:00:00",
                )

                count += 1
                self.report_progress(count)

            self.log_info(f"采集完成，共 {count} 篇报告")

        except Exception as e:
            self.log_error(f"采集过程发生错误: {e}")
            raise

        return count


# ==================== 示例2：手动模式 ====================

def manual_mode_example():
    """
    手动模式示例

    直接使用 CollectorReporter，灵活控制上报时机
    """
    from collectorsdk import CollectorReporter

    config = ReporterConfig.from_env()
    reporter = CollectorReporter(config)

    # 启动执行
    result = reporter.report_start(task_id=1)
    execution_id = result.get("executionId")
    print(f"执行ID: {execution_id}")

    # 上报日志
    reporter.log_info("开始采集...")

    # 模拟采集
    for i in range(5):
        reporter.report_progress(i + 1)
        reporter.log_info(f"已完成 {i + 1} 条")

    # 提交数据
    reporter.submit_report(
        title="测试报告",
        source=Source.LIANGXIN.value,
        url="https://example.com/1",
        variety="玉米",
        report_type="晨报",
        content="测试内容",
        publish_time="2026-04-29T08:00:00",
    )

    # 完成
    reporter.report_complete("success", 5)
    print("手动模式示例完成")


# ==================== 示例3：关闭上报模式 ====================

def disabled_reporter_example():
    """
    关闭上报示例

    设置 enabled=False，上报完全关闭
    采集代码正常运行，不受影响
    """
    config = ReporterConfig(
        enabled=False,  # 关闭上报
        api_base="http://localhost:8080/api",
    )

    collector = LiangxinwangCollector(
        config=config,
        task_id=1,
        username="33022",
        password="qlp707",
    )

    result = collector.run()
    print(f"采集结果: {result}")
    # 采集正常完成，但不上报到 Java 后端


# ==================== 主入口 ====================

if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="采集SDK示例")
    parser.add_argument("--mode", choices=["collector", "manual", "disabled"], default="collector", help="运行模式")
    parser.add_argument("--task-id", type=int, default=1, help="任务ID")
    parser.add_argument("--username", default=os.getenv("LXW_USERNAME", "33022"), help="粮信网用户名")
    parser.add_argument("--password", default=os.getenv("LXW_PASSWORD", "qlp707"), help="粮信网密码")
    parser.add_argument("--api-base", default=os.getenv("API_BASE", "http://localhost:8080/api"), help="API地址")

    args = parser.parse_args()

    if args.mode == "collector":
        print("=" * 50)
        print("模式1：完全解耦模式（推荐）")
        print("=" * 50)

        config = ReporterConfig(
            api_base=args.api_base,
            enabled=True,
            async_mode=True,
        )

        collector = LiangxinwangCollector(
            config=config,
            task_id=args.task_id,
            username=args.username,
            password=args.password,
        )

        result = collector.run()
        print(f"\n采集结果: {result}")

    elif args.mode == "manual":
        print("=" * 50)
        print("模式2：手动模式")
        print("=" * 50)
        manual_mode_example()

    elif args.mode == "disabled":
        print("=" * 50)
        print("模式3：关闭上报模式")
        print("=" * 50)
        disabled_reporter_example()
