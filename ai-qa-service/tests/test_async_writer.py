import threading
import pytest
from app.services.async_writer import MemoryQueue, DeadLetterQueue, MySQLAsyncWriter


class TestMemoryQueue:
    def test_put_get(self):
        q = MemoryQueue(maxsize=10)
        assert q.put({"id": 1})
        assert q.qsize() == 1
        assert q.get() == {"id": 1}
        assert q.qsize() == 0

    def test_full_discard(self):
        q = MemoryQueue(maxsize=2)
        assert q.put({"id": 1})
        assert q.put({"id": 2})
        assert not q.put({"id": 3})  # 队列满

    def test_concurrent_put_get_no_crash(self):
        q = MemoryQueue(maxsize=500)
        errors = []
        def worker():
            for i in range(100):
                try:
                    assert q.put({"i": i, "t": threading.get_ident()})
                    item = q.get()
                    assert item is not None
                except Exception as e:
                    errors.append(e)
        threads = [threading.Thread(target=worker) for _ in range(20)]
        for t in threads: t.start()
        for t in threads: t.join()
        assert len(errors) == 0, f"Concurrency errors: {errors}"


class TestDeadLetterQueue:
    def test_discard_oldest_when_full(self):
        q = DeadLetterQueue(maxsize=3)
        q.put({"id": 1}); q.put({"id": 2}); q.put({"id": 3})
        q.put({"id": 4})
        assert q.qsize() == 3
        assert q.get()["id"] == 2


class TestMySQLAsyncWriter:
    def test_timeout_moves_to_dlq(self, mocker):
        writer = MySQLAsyncWriter(mocker.Mock())
        writer._write_mysql = mocker.Mock(side_effect=Exception("timeout"))
        writer._main_queue.put({"session_id": "s1", "group_id": 1, "seq": 0})
        writer._process_task(writer._main_queue.get())
        assert writer._dlq.qsize() == 1
