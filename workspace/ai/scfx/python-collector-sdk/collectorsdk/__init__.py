"""
采集SDK - 解耦式采集状态上报SDK

完全解耦、异步非阻塞、失败隔离、可插拔

使用方式：
1. 创建配置
2. 继承 BaseCollector 实现采集逻辑
3. 调用 run() 启动采集

示例：
    from collectorsdk import ReporterConfig, BaseCollector, Source, Subject, CollectType, CollectObject

    class MyCollector(BaseCollector):
        def collect(self) -> int:
            # 纯采集逻辑
            return 10

    config = ReporterConfig.from_env()
    collector = MyCollector(
        config=config,
        task_id=1,
        source=Source.LIANGXIN.value,
        subject=Subject.CORN.value,
        coll_type=CollectType.LOGIN_CRAWL.value,
        obj=CollectObject.DAILY_REPORT.value,
        remark="粮信网玉米晨报采集"
    )
    result = collector.run()
"""

from .config import ReporterConfig
from .dimensions import (
    Source,
    Subject,
    CollectType,
    CollectObject,
    Dimensions,
)
from .reporter import CollectorReporter
from .collectors import BaseCollector
from .utils import (
    parse_publish_time,
    extract_report_type,
    extract_variety,
    clean_html,
)

__version__ = "1.0.0"
__all__ = [
    "ReporterConfig",
    "Source",
    "Subject",
    "CollectType",
    "CollectObject",
    "Dimensions",
    "CollectorReporter",
    "BaseCollector",
    "parse_publish_time",
    "extract_report_type",
    "extract_variety",
    "clean_html",
]
