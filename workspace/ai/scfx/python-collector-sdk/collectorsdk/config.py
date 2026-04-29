"""
采集SDK配置模块
"""

import os
from dataclasses import dataclass, field
from typing import Optional


@dataclass
class ReporterConfig:
    """上报器配置"""

    enabled: bool = True  # 上报开关，可关闭
    api_base: str = ""  # Java后端地址
    retry_times: int = 3  # 重试次数
    retry_delay: float = 1.0  # 重试延迟(秒)
    timeout: float = 5.0  # HTTP超时时间(秒)
    cache_size: int = 100  # 异步队列缓存大小
    async_mode: bool = True  # 异步模式
    task_id: int = 0  # 任务ID

    @classmethod
    def from_env(cls):
        """从环境变量创建配置"""
        return cls(
            api_base=os.getenv("API_BASE", "http://localhost:8080/api"),
            enabled=os.getenv("REPORTER_ENABLED", "true").lower() == "true",
            retry_times=int(os.getenv("REPORTER_RETRY_TIMES", "3")),
            retry_delay=float(os.getenv("REPORTER_RETRY_DELAY", "1.0")),
            timeout=float(os.getenv("REPORTER_TIMEOUT", "5.0")),
            cache_size=int(os.getenv("REPORTER_CACHE_SIZE", "100")),
            async_mode=os.getenv("REPORTER_ASYNC_MODE", "true").lower() == "true",
            task_id=int(os.getenv("TASK_ID", "1")),
        )

    def to_dict(self) -> dict:
        """转为字典"""
        return {
            "enabled": self.enabled,
            "api_base": self.api_base,
            "retry_times": self.retry_times,
            "retry_delay": self.retry_delay,
            "timeout": self.timeout,
            "cache_size": self.cache_size,
            "async_mode": self.async_mode,
            "task_id": self.task_id,
        }
