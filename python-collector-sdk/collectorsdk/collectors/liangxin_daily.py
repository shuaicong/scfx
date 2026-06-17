"""粮信网玉米日报采集器

继承 LiangxinCollector，固定 report_type="evening"。
"""
import logging

from collectorsdk.collectors.liangxin import LiangxinCollector

logger = logging.getLogger(__name__)


class LiangxinDailyCollector(LiangxinCollector):
    """粮信网玉米日报采集器

    继承 LiangxinCollector，report_type 固定为 "evening"（日报）。

    使用方式：
        python main.py run liangxin-daily
    """

    def __init__(self, config, task_id=2, username="", password="", execution_id=None, target_date=None):
        """初始化日报采集器

        Args:
            config: 上报配置
            task_id: 任务ID（默认 2=日报）
            username: 粮信网账号
            password: 粮信网密码
            execution_id: 执行ID
            target_date: 目标采集日期 (yyyy-MM-dd)，不传则默认今天
        """
        super().__init__(
            config=config,
            task_id=task_id,
            username=username,
            password=password,
            report_type="evening",
            execution_id=execution_id,
            target_date=target_date,
        )
