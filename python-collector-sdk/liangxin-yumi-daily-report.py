#!/usr/bin/env python3
"""
粮信网玉米日报采集脚本

基于 SDK 的 LiangxinCollector，固定 report_type="evening" 采集玉米日报。

使用方式（推荐）:
    python liangxin-yumi-daily-report.py

使用方式（搭配后端）:
    python main.py run liangxin --task-id 2 --local

依赖:
    pip install playwright
    playwright install chromium

环境变量:
    LIANGXIN_USERNAME              粮信网账号（默认从 config.yaml 读取）
    LIANGXIN_PASSWORD              粮信网密码（默认从 config.yaml 读取）
    KNOWLEDGE_API_BASE             后端 API 地址（默认 http://localhost:8080/api）
"""
import argparse
import logging
import os
import sys
from datetime import datetime

# 添加项目路径
PROJECT_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, PROJECT_DIR)

from collectorsdk.config import ReporterConfig
from collectorsdk.collectors.liangxin import LiangxinCollector
from collectorsdk.dimensions import Source, Subject, CollectType, CollectObject

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger("liangxin-daily")


def load_config(config_path: str = None) -> dict:
    """加载配置文件"""
    if config_path is None:
        config_path = os.path.join(PROJECT_DIR, "config.yaml")
    if not os.path.exists(config_path):
        logger.warning(f"配置文件不存在: {config_path}，将使用环境变量或默认值")
        return {}
    import yaml
    with open(config_path, "r", encoding="utf-8") as f:
        return yaml.safe_load(f)


def main():
    parser = argparse.ArgumentParser(
        description="粮信网玉米日报采集脚本",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("--username", default=None, help="粮信网账号（覆盖 config.yaml / 环境变量）")
    parser.add_argument("--password", default=None, help="粮信网密码（覆盖 config.yaml / 环境变量）")
    parser.add_argument("--api-base", default=None, help="后端 API 地址（默认 http://localhost:8080/api）")
    parser.add_argument("--task-id", type=int, default=2, help="任务ID（默认 2=日报）")
    parser.add_argument("--no-report", action="store_true", help="禁用上报（仅本地调试）")
    parser.add_argument("--headless", action="store_true", default=True, help="无头模式（默认开启）")
    args = parser.parse_args()

    # 加载配置
    config = load_config()

    # 确定账号密码：优先级 CLI > 环境变量 > config.yaml
    username = (
        args.username
        or os.environ.get("LIANGXIN_USERNAME")
        or config.get("liangxin", {}).get("username")
    )
    password = (
        args.password
        or os.environ.get("LIANGXIN_PASSWORD")
        or config.get("liangxin", {}).get("password")
    )

    if not username or not password:
        logger.error("未提供粮信网账号密码，请通过 --username/--password 参数、环境变量或 config.yaml 设置")
        sys.exit(1)

    api_base = args.api_base or os.environ.get("KNOWLEDGE_API_BASE") or config.get("knowledge_api", {}).get("base_url", "http://localhost:8080/api")

    # 构建上报配置
    reporter_config = ReporterConfig(
        api_base=api_base,
        enabled=not args.no_report,
    )

    # 创建日报采集器
    collector = LiangxinCollector(
        config=reporter_config,
        task_id=args.task_id,
        username=username,
        password=password,
        report_type="evening",
    )

    logger.info("=" * 50)
    logger.info("粮信网玉米日报采集开始")
    logger.info(f"  账号: {username}")
    logger.info(f"  API:  {api_base}")
    logger.info(f"  日期: {datetime.now().strftime('%Y-%m-%d')}")
    logger.info("=" * 50)

    # 执行采集
    result = collector.run()

    # 输出结果
    if result.get("success"):
        logger.info(f"✅ 玉米日报采集成功，共 {result.get('collected_count', 0)} 篇")
    else:
        logger.error(f"❌ 玉米日报采集失败: {result.get('error', '未知错误')}")
        sys.exit(1)


if __name__ == "__main__":
    main()
