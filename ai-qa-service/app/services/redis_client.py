"""Redis 客户端 — 连接池管理与健康检查"""
import os
import time
import logging
import redis
from redis import Redis

logger = logging.getLogger(__name__)

REDIS_HOST = os.getenv("REDIS_HOST", "localhost")
REDIS_PORT = int(os.getenv("REDIS_PORT", "6379"))
REDIS_USER = os.getenv("REDIS_USER", "")
REDIS_PASSWORD = os.getenv("REDIS_PASSWORD", "")
REDIS_DB = int(os.getenv("REDIS_DB", "0"))
REDIS_POOL_SIZE = int(os.getenv("REDIS_POOL_SIZE", "10"))
REDIS_SOCKET_CONNECT_TIMEOUT = int(os.getenv("REDIS_SOCKET_CONNECT_TIMEOUT", "2"))
REDIS_SOCKET_TIMEOUT = int(os.getenv("REDIS_SOCKET_TIMEOUT", "1"))

_pool: redis.ConnectionPool | None = None
_pool_dead: bool = False
_last_ping_ok: float = 0


def _create_pool() -> redis.ConnectionPool:
    return redis.ConnectionPool(
        host=REDIS_HOST, port=REDIS_PORT,
        username=REDIS_USER or None,
        password=REDIS_PASSWORD or None,
        db=REDIS_DB, max_connections=REDIS_POOL_SIZE,
        socket_connect_timeout=REDIS_SOCKET_CONNECT_TIMEOUT,
        socket_timeout=REDIS_SOCKET_TIMEOUT,
        health_check_interval=30,
        decode_responses=True,
    )


def mark_pool_dead():
    global _pool_dead
    _pool_dead = True


def get_redis_client() -> Redis:
    global _pool, _pool_dead
    if _pool is None or _pool_dead:
        if _pool is not None and _pool_dead:
            try:
                _pool.disconnect()
            except Exception:
                pass
            logger.info("[AI_QA] [INFO] [redis_pool_rebuilding]")
            _pool_dead = False
        _pool = _create_pool()
    return Redis(connection_pool=_pool)


def is_redis_available() -> bool:
    global _last_ping_ok
    now = time.monotonic()
    if _last_ping_ok and now - _last_ping_ok < 5:
        return True
    try:
        get_redis_client().ping()
        _last_ping_ok = now
        return True
    except Exception:
        logger.warning("[AI_QA] [WARN] [redis_unavailable] action=circuit_breaker_open")
        return False


def close_redis_pool():
    global _pool
    if _pool:
        try:
            _pool.disconnect()
        except Exception:
            pass
        _pool = None
