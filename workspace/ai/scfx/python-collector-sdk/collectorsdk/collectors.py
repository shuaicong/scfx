"""
采集器基类 - 完全不侵入采集逻辑

采集代码只需继承此类，自动获得上报能力

Mixin模式：继承BaseCollector获得自动上报功能，不影响原有采集逻辑
"""

from typing import Optional

from .config import ReporterConfig
from .dimensions import Dimensions
from .reporter import CollectorReporter


class BaseCollector:
    """
    采集器基类 - 完全不侵入采集逻辑

    采集代码只需继承此类，自动获得上报能力

    特性：
    - 与CollectorReporter无缝集成，自动上报
    - 生命周期管理：run()方法处理start→collect→complete/error
    - 维度设置辅助方法
    - 完全非侵入，不影响采集逻辑

    使用方式：
        class MyCollector(BaseCollector):
            def __init__(self, config, task_id):
                super().__init__(config, task_id)
                self.set_dimensions(
                    source="mysteel",
                    subject="corn",
                    coll_type="api_collect",
                    obj="price",
                    remark="采集玉米价格"
                )

            def collect(self):
                # 真正的采集逻辑
                return 100

        collector = MyCollector(config, task_id=123)
        collector.run()
    """

    def __init__(self, config: ReporterConfig, task_id: int):
        """
        初始化采集器

        Args:
            config: ReporterConfig配置实例
            task_id: 任务ID
        """
        self.reporter = CollectorReporter(config)
        self.task_id = task_id
        self.execution_id: Optional[str] = None
        self._dimensions: Optional[Dimensions] = None

    def set_dimensions(
        self,
        source: str,
        subject: str,
        coll_type: str,
        obj: str,
        remark: str = "",
    ) -> None:
        """
        设置5个核心维度

        Args:
            source: 采集来源（数据来自哪个网站/系统）
            subject: 采集主体（业务主体，如玉米、小麦）
            coll_type: 采集类型（采集方式）
            obj: 采集对象（具体采集目标）
            remark: 采集描述（任务一句话说明）
        """
        self._dimensions = Dimensions(
            source=source,
            subject=subject,
            coll_type=coll_type,
            obj=obj,
            remark=remark,
        )
        self.reporter.set_dimensions(self._dimensions)

    def collect(self) -> int:
        """
        子类实现真正的采集逻辑

        Returns:
            采集数量

        Raises:
            NotImplementedError: 子类必须实现此方法
        """
        raise NotImplementedError("子类必须实现collect方法返回采集数量")

    def run(self) -> None:
        """
        执行入口，自动管理生命周期

        流程：
        1. 调用reporter.report_start获取execution_id
        2. 调用子类的collect()方法执行采集
        3. 调用reporter.report_complete上报成功
        4. 捕获异常并调用reporter.report_error和report_complete上报失败
        """
        try:
            result = self.reporter.report_start(self.task_id)
            self.execution_id = result.get("executionId")
            self.reporter.set_execution_id(self.execution_id)

            count = self.collect()

            self.reporter.report_complete("success", count)
        except Exception as e:
            self.reporter.report_error(str(e))
            self.reporter.report_complete("failed", 0)
            raise

    # ==================== 便捷属性/方法 ====================

    @property
    def dimensions(self) -> Optional[Dimensions]:
        """获取当前维度配置"""
        return self._dimensions

    def log_debug(self, message: str) -> None:
        """快捷方法：记录DEBUG日志"""
        self.reporter.log_debug(message)

    def log_info(self, message: str) -> None:
        """快捷方法：记录INFO日志"""
        self.reporter.log_info(message)

    def log_warn(self, message: str) -> None:
        """快捷方法：记录WARN日志"""
        self.reporter.log_warn(message)

    def log_error(self, message: str) -> None:
        """快捷方法：记录ERROR日志"""
        self.reporter.log_error(message)

    def report_progress(self, collected_count: int) -> None:
        """
        上报进度

        Args:
            collected_count: 已采集数量
        """
        self.reporter.report_progress(collected_count)

    def report_data(self, data: dict) -> None:
        """
        上报采集数据

        Args:
            data: 采集的数据
        """
        self.reporter.report_data(data)