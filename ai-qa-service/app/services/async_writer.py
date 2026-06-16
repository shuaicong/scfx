"""
MySQL 异步写入队列 + 死信队列
"""
import os
import time
import json
import logging
import threading
from abc import ABC, abstractmethod
from collections import deque
from typing import Optional

import pymysql
from pymysql.cursors import DictCursor

logger = logging.getLogger(__name__)


class QueueInterface(ABC):
    @abstractmethod
    def put(self, item: dict, timeout: float = 1.0) -> bool: ...
    @abstractmethod
    def get(self, timeout: float = 1.0) -> Optional[dict]: ...
    @abstractmethod
    def qsize(self) -> int: ...
    @abstractmethod
    def full(self) -> bool: ...
    @property
    @abstractmethod
    def name(self) -> str: ...


class MemoryQueue(QueueInterface):
    def __init__(self, maxsize: int = 200, name: str = "memory"):
        self._maxsize = maxsize
        self._name = name
        self._queue: deque[dict] = deque()
        self._lock = threading.Lock()

    def put(self, item: dict, timeout: float = 1.0) -> bool:
        with self._lock:
            if len(self._queue) >= self._maxsize:
                return False
            self._queue.append(item)
            return True

    def get(self, timeout: float = 1.0) -> Optional[dict]:
        with self._lock:
            if not self._queue:
                return None
            return self._queue.popleft()

    def qsize(self) -> int:
        with self._lock:
            return len(self._queue)

    def full(self) -> bool:
        with self._lock:
            return len(self._queue) >= self._maxsize

    @property
    def name(self) -> str:
        return self._name


class DeadLetterQueue(MemoryQueue):
    def __init__(self, maxsize: int = 50):
        super().__init__(maxsize=maxsize, name="dead_letter")

    def put(self, item: dict, timeout: float = 1.0) -> bool:
        with self._lock:
            if len(self._queue) >= self._maxsize:
                dropped = self._queue.popleft()
                logger.error(
                    "[AI_QA] [ERROR] [dlq_full] session_id=%s group_id=%s seq=%s action=discard_oldest",
                    item.get("session_id", "-"), item.get("group_id", "-"),
                    item.get("seq", "-"),
                )
            self._queue.append(item)
            return True


class MySQLAsyncWriter:
    def __init__(self, mysql_conn, maxsize: int = 200, dlq_maxsize: int = 50):
        self._mysql = mysql_conn
        self._main_queue = MemoryQueue(maxsize=maxsize)
        self._dlq = DeadLetterQueue(maxsize=dlq_maxsize)
        self._running = False
        self._sustained_failure_count = 0

    def enqueue(self, record: dict) -> bool:
        if self._main_queue.put(record):
            return True
        logger.error("[AI_QA] [ERROR] [mysql_queue_full] session_id=%s group_id=%s seq=%s",
                     record.get("session_id", "-"), record.get("group_id", "-"), record.get("seq", "-"))
        return False

    def start(self):
        if self._running:
            return
        self._running = True
        self._consumer_main = threading.Thread(target=self._main_consumer_loop, daemon=True)
        self._consumer_main.start()
        self._consumer_dlq = threading.Thread(target=self._dlq_consumer_loop, daemon=True)
        self._consumer_dlq.start()

    def stop(self, timeout: float = 5.0):
        self._running = False
        if hasattr(self, '_consumer_main') and self._consumer_main.is_alive():
            self._consumer_main.join(timeout=timeout)
        if hasattr(self, '_consumer_dlq') and self._consumer_dlq.is_alive():
            self._consumer_dlq.join(timeout=timeout)
        remaining = self._main_queue.qsize()
        if remaining:
            logger.warning("[AI_QA] [WARN] [mysql_writer_stopped_with_pending] queue_pending=%d action=lost_on_restart", remaining)

    def queue_size(self) -> int:
        return self._main_queue.qsize()

    def dlq_size(self) -> int:
        return self._dlq.qsize()

    def status(self) -> dict:
        return {
            "main_queue_size": self._main_queue.qsize(),
            "dlq_size": self._dlq.qsize(),
            "alive": self._running,
            "sustained_failure_count": self._sustained_failure_count,
        }

    def _main_consumer_loop(self):
        while self._running:
            try:
                task = self._main_queue.get(timeout=0.5)
                if task is None:
                    continue
                self._process_task(task)
            except Exception:
                logger.exception("[AI_QA] [ERROR] [main_consumer_unexpected]")

    def _process_task(self, task: dict):
        task_start = time.monotonic()
        last_error = None
        retry_intervals = [1, 5, 15]
        for attempt, delay in enumerate(retry_intervals, 1):
            elapsed = time.monotonic() - task_start
            if elapsed > 20:
                self._dlq.put(task)
                logger.warning("[AI_QA] [ALERT] [mysql_write_task_timeout] session_id=%s group_id=%s seq=%s elapsed_ms=%d",
                               task.get("session_id", "-"), task.get("group_id", "-"), task.get("seq", "-"), int(elapsed * 1000))
                return
            try:
                self._write_mysql(task)
                self._sustained_failure_count = 0
                return
            except Exception as e:
                last_error = e
                if attempt < len(retry_intervals):
                    time.sleep(delay)
        self._dlq.put(task)
        self._sustained_failure_count += 1
        logger.error("[AI_QA] [ERROR] [mysql_write_permanent_failure] session_id=%s group_id=%s seq=%s error=%s",
                     task.get("session_id", "-"), task.get("group_id", "-"), task.get("seq", "-"), last_error)
        if self._sustained_failure_count >= 10:
            logger.error("[AI_QA] [ALERT] [mysql_write_sustained_failure] count=%d", self._sustained_failure_count)

    def _dlq_consumer_loop(self):
        while self._running:
            time.sleep(60)
            while True:
                task = self._dlq.get(timeout=0.1)
                if task is None:
                    break
                try:
                    self._write_mysql(task)
                except Exception as e:
                    logger.error("[AI_QA] [ERROR] [dlq_recover_failed] session_id=%s error=%s", task.get("session_id", "-"), e)

    def _write_mysql(self, record: dict):
        sql = """INSERT INTO t_chat_history
            (user_id, request_id, session_id, client_msg_id, role, content,
             knowledge_ids, message_id, group_id, seq, session_status,
             reasoning_content)
            VALUES (%(user_id)s, %(request_id)s, %(session_id)s, %(client_msg_id)s,
                    %(role)s, %(content)s, %(knowledge_ids)s,
                    %(message_id)s, %(group_id)s, %(seq)s, 1,
                    %(reasoning_content)s)
            ON DUPLICATE KEY UPDATE content=VALUES(content),
                                    reasoning_content=VALUES(reasoning_content),
                                    updated_at=NOW()"""
        self._mysql.execute_update(sql, record)


_global_writer: Optional[MySQLAsyncWriter] = None
_global_writer_lock = threading.Lock()


def get_global_writer() -> MySQLAsyncWriter:
    global _global_writer
    if _global_writer is None:
        with _global_writer_lock:
            if _global_writer is None:
                from app.db import mysql as mysql_module
                _global_writer = MySQLAsyncWriter(mysql_module)
                _global_writer.start()
    return _global_writer
