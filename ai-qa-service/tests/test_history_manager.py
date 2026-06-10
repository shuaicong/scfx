"""历史管理器单元测试"""
import json
import sys
import os

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import pytest
from app.services.history_manager import HistoryManager


@pytest.fixture
def hm(mocker):
    mocker.patch("app.services.history_manager.get_redis_client")
    return HistoryManager("u1", "s1")


def test_add_and_get_recent(hm, mocker):
    mock_redis = mocker.patch.object(hm, "redis")
    # ZREVRANGE 返回降序（高 message_id 在前）
    mock_redis.zrevrange.return_value = [
        json.dumps({"role": "assistant", "content": "hello", "message_id": 2, "group_id": 1, "seq": 1}),
        json.dumps({"role": "user", "content": "hi", "message_id": 1, "group_id": 1, "seq": 0}),
    ]
    history = hm.get_recent_history(max_groups=1)
    assert len(history) == 2
    assert history[0]["content"] == "hi"


def test_close_session_deletes_keys(hm, mocker):
    mock_redis = mocker.patch.object(hm, "redis")
    hm.close_session()
    assert mock_redis.delete.called
