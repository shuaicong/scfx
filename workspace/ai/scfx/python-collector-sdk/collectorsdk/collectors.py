"""
采集器基类

完全不侵入采集逻辑
采集代码只需继承此类，自动获得上报能力
"""

from abc import ABC, abstractmethod
from typing import Optional

from .config import ReporterConfig
from .dimensions import Dimensions, Source, Subject, CollectType, CollectObject
from .reporter import CollectorReporter


class BaseCollector(ABC):
    """
    采集器基类

    使用方式：
    1. 继承此类
    2. 实现 collect() 方法（纯采集逻辑，不调用任何HTTP/上报）
    3. 调用 run() 方法启动采集

    特性：
    - 自动管理生命周期（启动→执行→完成）
    - 异步上报，不阻塞采集
    - 上报失败不影响采集流程
    - 自动携带5个核心维度
    """

    def __init__(
        self,
        config: ReporterConfig,
        task_id: int,
        source: Optional[str] = None,
        subject: Optional[str] = None,
        coll_type: Optional[str] = None,
        obj: Optional[str] = None,
        remark: str = "",
    ):
        """
        初始化采集器

        Args:
            config: 上报配置
            task_id: 任务ID
            source: 采集来源（如 liangxin）
            subject: 采集主体（如 corn）
            coll_type: 采集类型（如 login_crawl）
            obj: 采集对象（如 daily_report）
            remark: 采集描述
        """
        self.config = config
        self.task_id = task_id
        self._reporter = CollectorReporter(config)
        self._execution_id: Optional[str] = None
        self._collected_count = 0

        # 设置维度
        if source and subject and coll_type and obj:
            self._dimensions = Dimensions(
                source=source,
                subject=subject,
                coll_type=coll_type,
                obj=obj,
                remark=remark,
            )
            self._reporter.set_dimensions(self._dimensions)
        else:
            self._dimensions = None

    def set_dimensions(
        self,
        source: str,
        subject: str,
        coll_type: str,
        obj: str,
        remark: str = "",
    ):
        """
        设置5个核心维度

        Args:
            source: 采集来源（如 liangxin, mysteel）
            subject: 采集主体（如 corn, wheat）
            coll_type: 采集类型（如 login_crawl, public_crawl）
            obj: 采集对象（如 daily_report, price）
            remark: 采集描述（如 "粮信网玉米晨报采集"）
        """
        self._dimensions = Dimensions(
            source=source,
            subject=subject,
            coll_type=coll_type,
            obj=obj,
            remark=remark,
        )
        self._reporter.set_dimensions(self._dimensions)

    def set_dimensions_from_enum(
        self,
        source: Source,
        subject: Subject,
        coll_type: CollectType,
        obj: CollectObject,
        remark: str = "",
    ):
        """
        使用枚举设置维度

        Args:
            source: 来源枚举
            subject: 主体枚举
            coll_type: 类型枚举
            obj: 对象枚举
            remark: 采集描述
        """
        self.set_dimensions(
            source=source.value,
            subject=subject.value,
            coll_type=coll_type.value,
            obj=obj.value,
            remark=remark,
        )

    @abstractmethod
    def collect(self) -> int:
        """
        执行采集逻辑（子类实现）

        注意事项：
        - 此方法内不要调用任何 HTTP/上报 API
        - 如需记录日志，使用 self.log_* 方法
        - 如需提交数据，使用 self.submit_report 方法
        - 返回采集数量

        Returns:
            采集数量
        """
        raise NotImplementedError("子类必须实现 collect() 方法")

    def run(self) -> dict:
        """
        执行采集流程（自动管理生命周期）

        流程：
        1. 上报任务开始
        2. 执行 collect()
        3. 上报任务完成

        Returns:
            执行结果字典
        """
        result = {
            "success": False,
            "execution_id": None,
            "collected_count": 0,
            "error": None,
        }

        try:
            # 1. 启动执行
            self._reporter.log_info(f"任务开始，任务ID: {self.task_id}")
            start_result = self._reporter.report_start(self.task_id)
            self._execution_id = start_result.get("executionId")
            result["execution_id"] = self._execution_id
            self._reporter.log_info(f"执行ID: {self._execution_id}")

            # 2. 执行采集
            self._reporter.log_info("开始执行采集...")
            self._collected_count = self.collect()
            self._reporter.log_info(f"采集完成，共 {self._collected_count} 条数据")

            # 3. 完成执行
            self._reporter.report_complete("success", self._collected_count)
            result["success"] = True
            result["collected_count"] = self._collected_count

        except Exception as e:
            error_msg = str(e)
            self._reporter.log_error(f"采集失败: {error_msg}")
            self._reporter.report_error(error_msg)
            self._reporter.report_complete("failed", self._collected_count)
            result["error"] = error_msg
            result["collected_count"] = self._collected_count

        return result

    # ==================== 上报便捷方法 ====================

    def log_debug(self, message: str):
        """快捷方法：记录DEBUG日志"""
        self._reporter.log_debug(message)

    def log_info(self, message: str):
        """快捷方法：记录INFO日志"""
        self._reporter.log_info(message)

    def log_warn(self, message: str):
        """快捷方法：记录WARN日志"""
        self._reporter.log_warn(message)

    def log_error(self, message: str):
        """快捷方法：记录ERROR日志"""
        self._reporter.log_error(message)

    def report_progress(self, count: int):
        """快捷方法：上报进度"""
        self._reporter.report_progress(count)

    def submit_report(
        self,
        title: str,
        source: str,
        url: str,
        variety: str = "",
        report_type: str = "",
        content: str = "",
        publish_time: str = "",
    ):
        """
        快捷方法：提交报告数据

        Args:
            title: 报告标题
            source: 数据源
            url: 原文链接
            variety: 品种
            report_type: 报告类型
            content: 正文内容
            publish_time: 发布时间
        """
        self._reporter.submit_report(
            title=title,
            source=source,
            url=url,
            variety=variety,
            report_type=report_type,
            content=content,
            publish_time=publish_time,
        )

    def get_execution_id(self) -> Optional[str]:
        """获取执行ID"""
        return self._execution_id

    @property
    def reporter(self) -> CollectorReporter:
        """获取上报器实例"""
        return self._reporter
