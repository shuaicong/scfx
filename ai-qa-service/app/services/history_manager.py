"""对话历史管理器 — Redis SortedSet 读写 + 滑动窗口压缩 + 摘要生成"""
import json
import logging
import threading
import time
from typing import Optional
from app.services.redis_client import get_redis_client

logger = logging.getLogger(__name__)

MAX_GROUPS = 10
SUMMARY_TTL = 7 * 86400
INACTIVE_TRIM_DAYS = 15


class HistoryManager:
    def __init__(self, user_id: str, session_id: str):
        self.user_id = user_id
        self.session_id = session_id
        self.redis = get_redis_client()
        self._history_key = f"chat:user:{user_id}:session:{session_id}"
        self._summary_key = f"chat:summary:user:{user_id}:session:{session_id}"
        self._counter_key = f"chat:counter:user:{user_id}:session:{session_id}"
        self._cache: dict[str, tuple[float, list[dict]]] = {}
        self._cache_ttl = 1.0
        self._cache_lock = threading.Lock()  # ★ 线程安全保护

    def _cache_get(self, key: str) -> list[dict] | None:
        with self._cache_lock:
            entry = self._cache.get(key)
            if entry and (time.monotonic() - entry[0]) < self._cache_ttl:
                return entry[1]
            return None

    def _cache_set(self, key: str, result: list[dict]):
        with self._cache_lock:
            self._cache[key] = (time.monotonic(), result)

    def _cache_clear(self):
        with self._cache_lock:
            self._cache.clear()

    def add_message(self, role: str, content: str, group_id: int,
                    message_id: int, knowledge_ids: list[int] | None = None,
                    created_at: str | None = None) -> None:
        from datetime import datetime, timezone
        member = json.dumps({
            "role": role, "content": content,
            "message_id": message_id, "group_id": group_id,
            "seq": 0 if role == "user" else 1,
            "knowledge_ids": knowledge_ids,
            "request_id": "-",
            "created_at": created_at or datetime.now(timezone.utc).isoformat(),
        })
        self.redis.zadd(self._history_key, {member: message_id})
        self.redis.expire(self._history_key, SUMMARY_TTL)
        self._cache_clear()
        self._check_compress()

    def get_recent_history(self, max_groups: int = 10) -> list[dict]:
        cache_key = f"history_{max_groups}"
        cached = self._cache_get(cache_key)
        if cached is not None:
            return cached
        members = self.redis.zrevrange(self._history_key, 0, -1)
        messages = [json.loads(m) for m in members]
        messages.reverse()
        seen: set[int] = set()
        all_recent = []
        for m in messages:
            if m["group_id"] not in seen:
                seen.add(m["group_id"])
                if len(seen) > max_groups:
                    continue
            all_recent.append(m)
        result = all_recent[-max_groups * 2:]
        self._cache_set(cache_key, result)
        return result

    def _check_compress(self):
        count = self.redis.zcard(self._history_key)
        if count <= MAX_GROUPS * 2:
            return
        self.redis.zremrangebyrank(self._history_key, 0, -(MAX_GROUPS * 2 + 1))

    def get_summary(self) -> Optional[str]:
        data = self.redis.get(self._summary_key)
        if not data:
            return None
        try:
            return json.loads(data).get("content")
        except (json.JSONDecodeError, TypeError):
            return None

    def rebuild_from_mysql(self, mysql_rows: list[dict]) -> int:
        pipeline = self.redis.pipeline()
        pipeline.delete(self._history_key)
        for row in mysql_rows:
            member = json.dumps({
                "role": row["role"], "content": row["content"],
                "message_id": row["message_id"], "group_id": row["group_id"],
                "seq": row["seq"],
                "knowledge_ids": row.get("knowledge_ids"),
                "request_id": row.get("request_id", "-"),
            })
            pipeline.zadd(self._history_key, {member: row["message_id"]})
        pipeline.expire(self._history_key, SUMMARY_TTL)
        pipeline.execute()
        return len(mysql_rows)

    def close_session(self):
        self.redis.delete(self._history_key, self._summary_key, self._counter_key)
