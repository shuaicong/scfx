#!/usr/bin/env python3
"""
采集SDK入口示例

展示如何使用 SDK：
1. 从环境变量或配置文件加载配置
2. 创建采集器实例
3. 运行采集

Usage:
    python main.py                    # 运行示例采集器
    python main.py --help             # 查看帮助
    python main.py collect --help      # 查看 collect 子命令帮助
"""

import argparse
import os
import sys
from typing import Optional

# 添加项目路径
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from collectorsdk import (
    ReporterConfig,
    BaseCollector,
    Source,
    Subject,
    CollectType,
    CollectObject,
    parse_publish_time,
    extract_report_type,
    extract_variety,
    clean_html,
    calculate_md5,
    get_data_dir,
    get_cache_dir,
    to_json,
    from_json,
    get_current_datetime,
    get_current_date_str,
)
from collectorsdk.config import load_config
from collectorsdk.collectors.liangxin import LiangxinCollector


# ==================== 示例采集器实现 ====================

class ExampleCollector(BaseCollector):
    """
    示例采集器 - 展示SDK基本用法

    继承 BaseCollector 自动获得：
    - 生命周期管理（start → collect → complete/error）
    - 日志上报（log_info, log_error 等）
    - 进度上报（report_progress）
    - 数据上报（submit_report）
    """

    def __init__(
        self,
        config: ReporterConfig,
        task_id: int,
        source: str,
        subject: str,
        coll_type: str,
        obj: str,
        remark: str,
    ):
        super().__init__(config=config, task_id=task_id)
        self.set_dimensions(
            source=source,
            subject=subject,
            coll_type=coll_type,
            obj=obj,
            remark=remark,
        )

    def collect(self) -> int:
        """执行采集逻辑 - 子类实现"""
        # 示例：模拟采集
        self.log_info("开始示例采集...")

        # 模拟数据
        reports = [
            {
                "title": "（2026年5月15日）玉米晨报",
                "url": "https://example.com/corn-morning-20260515",
                "content": "今日玉米市场报价稳定...",
                "publish_time": "2026-05-15T08:00:00",
            },
            {
                "title": "玉米日度行情报告",
                "url": "https://example.com/corn-daily-20260515",
                "content": "东北地区玉米价格...",
                "publish_time": "2026-05-15T09:30:00",
            },
        ]

        for i, report in enumerate(reports, 1):
            # 使用SDK工具函数
            content_hash = calculate_md5(report["content"])

            self.submit_report(
                title=report["title"],
                source=self.dimensions.source if self.dimensions else "unknown",
                url=report["url"],
                variety=extract_variety(report["title"]),
                report_type=extract_report_type(report["title"]),
                content=clean_html(report["content"]),
                publish_time=report["publish_time"],
            )

            self.log_info(f"已上报第 {i}/{len(reports)} 条: {report['title']}")
            self.report_progress(i)

        self.log_info(f"采集完成，共 {len(reports)} 条")
        return len(reports)


# ==================== 配置加载 ====================

def load_reporter_config(
    api_base: Optional[str] = None,
    enabled: bool = True,
    config_file: Optional[str] = None,
) -> ReporterConfig:
    """
    加载上报配置

    优先级（从高到低）：
    1. 传入的 api_base 参数
    2. 环境变量 COLLECTOR_API_BASE
    3. 配置文件中的值

    Args:
        api_base: API基础地址（可选）
        enabled: 是否启用上报（默认 True）
        config_file: 配置文件路径（可选）

    Returns:
        ReporterConfig 实例
    """
    if config_file is None:
        config_file = os.path.join(os.path.dirname(__file__), "config.yaml")

    config = load_config(config_file)

    # 允许覆盖 api_base
    if api_base:
        config.api_base = api_base

    # 允许覆盖 enabled
    config.enabled = enabled

    return config


# ==================== 运行模式 ====================

def run_example_collector(args):
    """运行示例采集器"""
    print("=" * 60)
    print("采集SDK示例 - 运行示例采集器")
    print("=" * 60)

    config = load_reporter_config(
        api_base=args.api_base,
        enabled=not args.no_report,
        config_file=args.config,
    )

    print(f"配置信息:")
    print(f"  - API地址: {config.api_base}")
    print(f"  - 上报启用: {config.enabled}")
    print(f"  - 异步模式: {config.async_mode}")
    print()

    # 创建采集器实例
    collector = ExampleCollector(
        config=config,
        task_id=args.task_id,
        source=Source.LIANGXIN.value,
        subject=Subject.CORN.value,
        coll_type=CollectType.LOGIN_CRAWL.value,
        obj=CollectObject.DAILY_REPORT.value,
        remark="示例玉米晨报采集",
    )

    # 运行采集
    try:
        collector.run()
        print("\n采集器运行完成")
        return True
    except Exception as e:
        print(f"\n采集器运行失败: {e}")
        return False


def run_liangxin_collector(args):
    """运行粮信网采集器"""
    print("=" * 60)
    print("粮信网采集器")
    print("=" * 60)

    config = load_reporter_config(
        api_base=args.api_base,
        enabled=not args.no_report,
        config_file=args.config,
    )

    print(f"配置信息:")
    print(f"  - API地址: {config.api_base}")
    print(f"  - 上报启用: {config.enabled}")
    print(f"  - 异步模式: {config.async_mode}")
    print()

    # 创建粮信网采集器
    collector = LiangxinCollector(
        config=config,
        task_id=args.task_id,
        username=args.username or os.getenv("LXW_USERNAME", ""),
        password=args.password or os.getenv("LXW_PASSWORD", ""),
        report_type=args.report_type,
    )

    # 运行采集
    try:
        collector.run()
        print("\n采集完成")
        return True
    except Exception as e:
        print(f"\n采集失败: {e}")
        return False


def show_config_info(args):
    """显示配置信息"""
    config = load_reporter_config(
        api_base=args.api_base,
        config_file=args.config,
    )

    print("当前配置:")
    print(f"  api_base: {config.api_base}")
    print(f"  enabled: {config.enabled}")
    print(f"  retry_times: {config.retry_times}")
    print(f"  retry_delay: {config.retry_delay}")
    print(f"  timeout: {config.timeout}")
    print(f"  cache_size: {config.cache_size}")
    print(f"  async_mode: {config.async_mode}")

    # 显示数据目录
    print(f"\n目录:")
    print(f"  数据目录: {get_data_dir()}")
    print(f"  缓存目录: {get_cache_dir()}")


def test_utils(args):
    """测试工具函数"""
    print("=" * 60)
    print("测试SDK工具函数")
    print("=" * 60)

    test_content = "Hello, World! 你好，世界！"

    print(f"\nMD5计算:")
    print(f"  输入: {test_content}")
    print(f"  MD5: {calculate_md5(test_content)}")

    print(f"\n报告类型提取:")
    titles = [
        "（2026年5月15日）玉米晨报",
        "玉米日度行情报告",
        "玉米市场周报",
        "玉米专题分析",
        "普通资讯",
    ]
    for title in titles:
        print(f"  {title} -> {extract_report_type(title)}")

    print(f"\n品种提取:")
    titles = [
        "玉米价格周报",
        "小麦市场日评",
        "大豆期货行情",
        "稻米市场分析",
        "其他商品",
    ]
    for title in titles:
        print(f"  {title} -> {extract_variety(title)}")

    print(f"\n时间解析:")
    times = [
        "2026-05-15",
        "2026-05-15 10:30:00",
        "2026年5月15日",
        "2026/05/15",
    ]
    for t in times:
        result = parse_publish_time(t)
        print(f"  {t} -> {result}")

    print(f"\nHTML清理:")
    html = "<script>alert('xss')</script><p>Hello <b>World</b>!</p>"
    print(f"  输入: {html}")
    print(f"  输出: {clean_html(html)}")

    print(f"\n当前时间:")
    print(f"  日期: {get_current_date_str()}")
    print(f"  时间: {get_current_datetime().isoformat()}")

    print(f"\nJSON序列化:")
    data = {"name": "测试", "value": 123, "items": ["a", "b", "c"]}
    print(f"  输入: {data}")
    print(f"  JSON: {to_json(data, indent=2)}")


# ==================== 主入口 ====================

def main():
    """主入口函数"""
    parser = argparse.ArgumentParser(
        description="采集SDK示例",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例用法:
  # 运行示例采集器
  python main.py example

  # 运行粮信网采集器
  python main.py liangxin --username 33022 --password qlp707

  # 禁用上报（本地测试）
  python main.py example --no-report

  # 指定API地址
  python main.py example --api-base http://localhost:8080/api

  # 显示配置信息
  python main.py config

  # 测试工具函数
  python main.py test
        """,
    )

    subparsers = parser.add_subparsers(dest="command", help="子命令")

    # example 子命令
    parser_example = subparsers.add_parser(
        "example",
        help="运行示例采集器",
    )
    parser_example.add_argument("--task-id", type=int, default=1, help="任务ID")
    parser_example.add_argument("--api-base", default=os.getenv("COLLECTOR_API_BASE", ""), help="API地址")
    parser_example.add_argument("--config", default=None, help="配置文件路径")
    parser_example.add_argument("--no-report", action="store_true", help="禁用上报")

    # liangxin 子命令
    parser_liangxin = subparsers.add_parser(
        "liangxin",
        help="运行粮信网采集器",
    )
    parser_liangxin.add_argument("--task-id", type=int, default=1, help="任务ID")
    parser_liangxin.add_argument("--username", default=None, help="粮信网用户名")
    parser_liangxin.add_argument("--password", default=None, help="粮信网密码")
    parser_liangxin.add_argument("--report-type", choices=["morning", "evening"], default="morning", help="报告类型")
    parser_liangxin.add_argument("--api-base", default=os.getenv("COLLECTOR_API_BASE", ""), help="API地址")
    parser_liangxin.add_argument("--config", default=None, help="配置文件路径")
    parser_liangxin.add_argument("--no-report", action="store_true", help="禁用上报")

    # config 子命令
    parser_config = subparsers.add_parser(
        "config",
        help="显示配置信息",
    )
    parser_config.add_argument("--api-base", default=os.getenv("COLLECTOR_API_BASE", ""), help="API地址")
    parser_config.add_argument("--config", default=None, help="配置文件路径")

    # test 子命令
    subparsers.add_parser("test", help="测试工具函数")

    args = parser.parse_args()

    if args.command == "example":
        success = run_example_collector(args)
        sys.exit(0 if success else 1)
    elif args.command == "liangxin":
        success = run_liangxin_collector(args)
        sys.exit(0 if success else 1)
    elif args.command == "config":
        show_config_info(args)
    elif args.command == "test":
        test_utils(args)
    else:
        parser.print_help()


if __name__ == "__main__":
    main()