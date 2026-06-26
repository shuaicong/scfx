"""
粮达网价格指数采集器 — 入口脚本

使用方式：
    python dev/collectors/liangdawang.py
    或 python main.py run liangdawang --local

环境变量：
    REPORTER_API_BASE: 后端 API 地址（默认 http://localhost:8080/api）
    TASK_ID: 任务ID（默认 1）
"""

import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", ".."))

from collectorsdk import ReporterConfig
from collectorsdk.collectors.liangdawang import LiangdawangCollector


def main():
    api_base = os.getenv("REPORTER_API_BASE", "http://localhost:8080/api")
    task_id = int(os.getenv("TASK_ID", "1"))
    config = ReporterConfig(api_base=api_base, enabled=True)
    collector = LiangdawangCollector(config=config, task_id=task_id)
    result = collector.run()
    print(f"采集结果: {result}")


if __name__ == "__main__":
    main()
