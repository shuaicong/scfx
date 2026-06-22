"""粮信网玉米周报采集器

继承 LiangxinCollector，固定 report_type="weekly"。
"""
import logging

from collectorsdk.collectors.liangxin import LiangxinCollector

logger = logging.getLogger(__name__)


class LiangxinWeeklyCollector(LiangxinCollector):
    """粮信网玉米周报采集器

    继承 LiangxinCollector，report_type 固定为 "weekly"（周报）。
    """

    def __init__(self, config, task_id=3, username="", password="", execution_id=None, target_date=None):
        super().__init__(
            config=config,
            task_id=task_id,
            username=username,
            password=password,
            report_type="weekly",
            execution_id=execution_id,
            target_date=target_date,
        )
