"""计数器模块 — 接口定义 + Redis 实现 + Local 回退 + 工厂"""
import logging
import itertools
from abc import ABC, abstractmethod
from typing import Optional

from app.services.redis_client import get_redis_client, is_redis_available

from app.constants.sensitive_patterns import desensitize

logger = logging.getLogger(__name__)

_KEY_PREFIX = "ai_qa:counter:"


def _safe_key(key: str) -> str:
    """在日志中脱敏 key（长 key 截断末尾）"""
    safe = desensitize(key)
    if len(safe) > 32:
        safe = safe[:16] + "..." + safe[-8:]
    return safe


class CounterInterface(ABC):
    """计数器接口 — 允许基于内部存储或外部存储的实现"""

    @abstractmethod
    def incr(self, key: str, amount: int = 1) -> int:
        ...

    @abstractmethod
    def get(self, key: str) -> int:
        ...

    @abstractmethod
    def reset(self, key: str) -> None:
        ...

    @abstractmethod
    def expire(self, key: str, seconds: int) -> None:
        ...


class RedisCounter(CounterInterface):
    """基于 Redis INCR 的分布式计数器，自动处理键前缀"""

    def incr(self, key: str, amount: int = 1) -> int:
        full_key = _KEY_PREFIX + key
        try:
            val = get_redis_client().incrby(full_key, amount)
            logger.info(
                "[AI_QA] [INFO] [counter_incr] key=%s amount=%d new_val=%d",
                _safe_key(key), amount, val,
            )
            return val
        except Exception:
            logger.exception(
                "[AI_QA] [ERROR] [counter_incr_failed] key=%s", _safe_key(key),
            )
            # 熔断：标记连接池死亡，下次重建
            from app.services.redis_client import mark_pool_dead
            mark_pool_dead()
            raise

    def get(self, key: str) -> int:
        full_key = _KEY_PREFIX + key
        try:
            val = get_redis_client().get(full_key)
            return int(val) if val is not None else 0
        except Exception:
            logger.exception(
                "[AI_QA] [ERROR] [counter_get_failed] key=%s", _safe_key(key),
            )
            from app.services.redis_client import mark_pool_dead
            mark_pool_dead()
            raise

    def reset(self, key: str) -> None:
        full_key = _KEY_PREFIX + key
        try:
            get_redis_client().delete(full_key)
            logger.info(
                "[AI_QA] [INFO] [counter_reset] key=%s", _safe_key(key),
            )
        except Exception:
            logger.exception(
                "[AI_QA] [ERROR] [counter_reset_failed] key=%s", _safe_key(key),
            )
            from app.services.redis_client import mark_pool_dead
            mark_pool_dead()
            raise

    def expire(self, key: str, seconds: int) -> None:
        full_key = _KEY_PREFIX + key
        try:
            get_redis_client().expire(full_key, seconds)
        except Exception:
            logger.exception(
                "[AI_QA] [ERROR] [counter_expire_failed] key=%s", _safe_key(key),
            )
            from app.services.redis_client import mark_pool_dead
            mark_pool_dead()
            raise


class LocalCounter(CounterInterface):
    """基于 itertools.count 的单机计数器 — P1 单实例部署时使用"""

    def __init__(self):
        self._store: dict[str, itertools.count] = {}

    def incr(self, key: str, amount: int = 1) -> int:
        if key not in self._store:
            self._store[key] = itertools.count(1)
        return next(self._store[key])

    def get(self, key: str) -> int:
        return next(self._store[key]) - 1 if key in self._store else 0

    def reset(self, key: str) -> None:
        self._store.pop(key, None)

    def expire(self, key: str, seconds: int) -> None:
        pass  # 本地模式无需过期


class CounterFactory:
    """计数器工厂 — 优先尝试 Redis，失败回退 LocalCounter"""

    _instance: Optional[CounterInterface] = None
    _fallback_reported: bool = False

    @classmethod
    def get_counter(cls) -> CounterInterface:
        if cls._instance is not None:
            return cls._instance

        try:
            if is_redis_available():
                cls._instance = RedisCounter()
                logger.info("[AI_QA] [INFO] [counter_backend] backend=redis")
                return cls._instance
        except Exception:
            pass

        if not cls._fallback_reported:
            logger.warning("[AI_QA] [WARN] [counter_fallback] backend=local")
            cls._fallback_reported = True
        cls._instance = LocalCounter()
        return cls._instance

    @classmethod
    def reset_instance(cls) -> None:
        cls._instance = None
