"""计数器单元测试 — LocalCounter"""
import sys
import os

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from app.services.counter import LocalCounter, CounterFactory


class TestLocalCounter:
    """LocalCounter 单元测试"""

    def setup_method(self):
        self.counter = LocalCounter()

    def test_incr_returns_incremented_value(self):
        """incr 应依次返回 1, 2, 3"""
        assert self.counter.incr("test") == 1
        assert self.counter.incr("test") == 2
        assert self.counter.incr("test") == 3

    def test_reset_clears_key(self):
        """reset 后 incr 应重新从 1 开始"""
        self.counter.incr("test")
        self.counter.incr("test")
        assert self.counter.incr("test") == 3
        self.counter.reset("test")
        assert self.counter.incr("test") == 1

    def test_multiple_keys_independent(self):
        """不同 key 的计数互不影响"""
        assert self.counter.incr("key_a") == 1
        assert self.counter.incr("key_b") == 1
        assert self.counter.incr("key_a") == 2
        assert self.counter.incr("key_b") == 2
        assert self.counter.get("key_a") == 2
        assert self.counter.get("key_b") == 2

    def test_get_returns_current_value(self):
        """get 返回当前值（不减 1）"""
        self.counter.incr("x")
        self.counter.incr("x")
        assert self.counter.get("x") == 2

    def test_get_returns_zero_for_unknown_key(self):
        """不存在的 key 返回 0"""
        assert self.counter.get("nonexistent") == 0


class TestCounterFactory:
    """CounterFactory 单元测试"""

    def teardown_method(self):
        CounterFactory.reset_instance()

    def test_factory_defaults_to_local(self, mocker):
        """工厂默认返回 LocalCounter（无 Redis 时）"""
        mocker.patch('app.services.counter.is_redis_available', return_value=False)
        CounterFactory.reset_instance()
        counter = CounterFactory.get_counter()
        from app.services.counter import LocalCounter
        assert isinstance(counter, LocalCounter)
