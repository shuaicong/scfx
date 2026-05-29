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