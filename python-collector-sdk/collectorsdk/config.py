"""Configuration management for the collector SDK."""

import os
from dataclasses import dataclass, fields
from typing import Optional


@dataclass
class ReporterConfig:
    """上报器配置"""

    enabled: bool = True  # 上报开关
    api_base: str = ""  # Java后端地址
    retry_times: int = 3  # 重试次数
    retry_delay: float = 1.0  # 重试延迟(秒)
    timeout: float = 5.0  # 超时时间
    cache_size: int = 100  # 缓存队列大小
    async_mode: bool = True  # 异步模式

    def validate(self) -> None:
        """Validate the configuration.

        Raises:
            ValueError: If required fields are missing or invalid.
        """
        if not self.api_base:
            raise ValueError("api_base is required but not set")

        if self.retry_times < 0:
            raise ValueError("retry_times must be non-negative")

        if self.retry_delay <= 0:
            raise ValueError("retry_delay must be positive")

        if self.timeout <= 0:
            raise ValueError("timeout must be positive")

        if self.cache_size <= 0:
            raise ValueError("cache_size must be positive")

    @classmethod
    def from_env(cls) -> "ReporterConfig":
        """Create a ReporterConfig from environment variables.

        Environment variables should be prefixed with COLLECTOR_.
        For example: COLLECTOR_API_BASE, COLLECTOR_ENABLED, etc.

        Returns:
            ReporterConfig populated from environment variables.
        """
        def get_env(name: str, default: str) -> str:
            return os.environ.get(f"COLLECTOR_{name}", default)

        return cls(
            enabled=_str_to_bool(get_env("ENABLED", "true")),
            api_base=get_env("API_BASE", ""),
            retry_times=int(get_env("RETRY_TIMES", "3")),
            retry_delay=float(get_env("RETRY_DELAY", "1.0")),
            timeout=float(get_env("TIMEOUT", "5.0")),
            cache_size=int(get_env("CACHE_SIZE", "100")),
            async_mode=_str_to_bool(get_env("ASYNC_MODE", "true")),
        )

    def to_env(self) -> None:
        """Write current config values to environment variables.

        This is useful for propagating config to child processes.
        """
        env_mappings = {
            "ENABLED": str(self.enabled).lower(),
            "API_BASE": self.api_base,
            "RETRY_TIMES": str(self.retry_times),
            "RETRY_DELAY": str(self.retry_delay),
            "TIMEOUT": str(self.timeout),
            "CACHE_SIZE": str(self.cache_size),
            "ASYNC_MODE": str(self.async_mode).lower(),
        }
        for name, value in env_mappings.items():
            os.environ[f"COLLECTOR_{name}"] = value

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
        }


def _str_to_bool(value: str) -> bool:
    """Convert string to boolean."""
    return value.lower() in ("true", "1", "yes", "on")


def load_config(config_file: Optional[str] = None) -> ReporterConfig:
    """Load configuration from file and/or environment variables.

    Args:
        config_file: Optional path to a config file. If provided, should contain
                     key=value pairs, one per line. Lines starting with # are comments.

    Returns:
        ReporterConfig loaded from the specified sources.
    """
    config = ReporterConfig.from_env()

    if config_file and os.path.exists(config_file):
        with open(config_file, "r") as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith("#"):
                    continue
                if "=" in line:
                    key, value = line.split("=", 1)
                    key = key.strip()
                    value = value.strip()
                    _apply_config_field(config, key, value)

    return config


def _apply_config_field(config: ReporterConfig, key: str, value: str) -> None:
    """Apply a configuration field from a config file to a ReporterConfig."""
    field_map = {
        "enabled": ("enabled", _str_to_bool),
        "api_base": ("api_base", str),
        "retry_times": ("retry_times", int),
        "retry_delay": ("retry_delay", float),
        "timeout": ("timeout", float),
        "cache_size": ("cache_size", int),
        "async_mode": ("async_mode", _str_to_bool),
    }

    if key in field_map:
        field_name, converter = field_map[key]
        for f in fields(config):
            if f.name == field_name:
                setattr(config, field_name, converter(value))
                break


# Default config instance
default_config = ReporterConfig()