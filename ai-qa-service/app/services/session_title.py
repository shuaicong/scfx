"""
会话标题异步生成模块 — 用户静默30秒后触发智能标题生成

流程：
1. 用户发送消息后，调用 schedule_title_generation() 注册/重置定时器
2. 后台线程每秒扫描一次，检测超时会话
3. 超时后调用 LLM 生成标题，通过 Java API 更新
4. Java 后端校验 title_source=manual 时拒绝更新
"""
import logging
import time
import threading
import httpx
from typing import Optional
from app.services.llm import generate_answer

logger = logging.getLogger(__name__)

_IDLE_TIMEOUT = 30  # 静默30秒触发
_TITLE_CACHE: dict[str, dict] = {}  # session_id -> {"timer": float, "question": str}
_TITLE_LOCK = threading.Lock()

_JAVA_API_BASE = "http://localhost:8080"


def schedule_title_generation(session_id: str, question: str):
    """用户发送消息后调用，注册或重置标题生成定时器"""
    with _TITLE_LOCK:
        _TITLE_CACHE[session_id] = {
            "timer": time.monotonic(),
            "question": question,
        }
        logger.debug("[AI_QA] [DEBUG] [title_timer_scheduled] session=%s", session_id)


def _check_and_generate():
    """后台线程：每秒扫描一次，检测超时会话并生成标题"""
    while True:
        time.sleep(1)
        now = time.monotonic()
        ready_sessions: list[tuple[str, str]] = []
        with _TITLE_LOCK:
            expired = [
                sid for sid, info in _TITLE_CACHE.items()
                if now - info["timer"] >= _IDLE_TIMEOUT
            ]
            for sid in expired:
                ready_sessions.append((sid, _TITLE_CACHE.pop(sid)["question"]))

        for session_id, question in ready_sessions:
            try:
                _generate_title(session_id, question)
            except Exception as e:
                logger.error(
                    "[AI_QA] [ERROR] [title_generation_failed] session=%s error=%s",
                    session_id, e,
                )


def _generate_title(session_id: str, question: str):
    """调用 LLM 生成标题并更新到 Java 后端"""
    # 构建摘要 prompt
    title = generate_answer([
        {"role": "system", "content": "你是粮食价格分析助手。根据用户的提问，生成一个简短（不超过20字）的会话标题。直接输出标题，不要解释。"},
        {"role": "user", "content": question[:200]},
    ])
    title = title.strip().strip('"').strip("'")[:50]
    if not title:
        logger.warning("[AI_QA] [WARN] [title_empty] session=%s", session_id)
        return

    # 通过 Java 后端更新标题（Java 端会校验 title_source=manual）
    try:
        resp = httpx.patch(
            f"{_JAVA_API_BASE}/ai-chat/sessions/{session_id}/title",
            json={"title": title, "source": "auto"},
            timeout=5,
        )
        if resp.status_code == 403:
            logger.info("[AI_QA] [INFO] [title_rejected_manual] session=%s", session_id)
        elif resp.status_code == 200:
            logger.info("[AI_QA] [INFO] [title_generated] session=%s title=%s", session_id, title)
        else:
            logger.warning(
                "[AI_QA] [WARN] [title_update_failed] session=%s status=%s",
                session_id, resp.status_code,
            )
    except httpx.ConnectError:
        logger.warning("[AI_QA] [WARN] [java_backend_unreachable] session=%s", session_id)
    except Exception as e:
        logger.error(
            "[AI_QA] [ERROR] [title_update_error] session=%s error=%s",
            session_id, e,
        )


# 启动后台守护线程
_generator_thread = threading.Thread(target=_check_and_generate, daemon=True)
_generator_thread.start()
logger.info("[AI_QA] [INFO] [title_generator_started] idle_timeout=%ds", _IDLE_TIMEOUT)
