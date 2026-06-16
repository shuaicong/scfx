"""AI 问答 API — 新版 6 事件 SSE 流"""
import json
import logging
import asyncio
import uuid as _uuid
from typing import Optional
import re as _re

from fastapi import APIRouter, Request
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field, field_validator

from app.services.llm import build_messages, generate_answer, generate_answer_stream
from app.services.question_classifier import classify_question, QuestionType
from app.services.sse_manager import SSEResponseGenerator, build_reasoning_event
from app.services.history_manager import HistoryManager
from app.services.counter import CounterFactory
from app.services.redis_client import get_redis_client
from app.services.vector import search_vectors
from app.services.async_writer import get_global_writer
from app.services.session_title import schedule_title_generation
from app.db.qdrant import get_client

logger = logging.getLogger(__name__)

# ============================================================
# 流式标签解析器 — 三状态解析 <reasoning>/<answer> 标签
# ============================================================

class CoTStreamParser:
    """深度思考流式标签解析器 — 逐 chunk 解析 <reasoning>/<answer> 标签。

    三状态：SEARCHING / IN_REASONING / IN_ANSWER
    生命周期：每条新问答初始化一次，流式过程不重置，会话间隔离。
    """

    STATE_SEARCHING = 'SEARCHING'
    STATE_IN_REASONING = 'IN_REASONING'
    STATE_IN_ANSWER = 'IN_ANSWER'

    _REASONING_START = _re.compile(r'<\s*reasoning\s*>', _re.IGNORECASE)
    _REASONING_END = _re.compile(r'<\s*/\s*reasoning\s*>', _re.IGNORECASE)
    _ANSWER_START = _re.compile(r'<\s*answer\s*>', _re.IGNORECASE)
    _ANSWER_END = _re.compile(r'<\s*/\s*answer\s*>', _re.IGNORECASE)

    TIMEOUT_CHUNKS = 20

    def __init__(self):
        self.reset()

    def reset(self):
        """每条新问答请求调用一次，完全重置状态。"""
        self._state = self.STATE_SEARCHING
        self._is_degraded = False
        self._no_match_count = 0
        self._buffer = ''

    @property
    def is_degraded(self) -> bool:
        return self._is_degraded

    def feed(self, chunk: str) -> list[dict]:
        """喂入一个 LLM chunk，返回事件列表。

        返回的每个 dict 格式：
        {"type": "reasoning", "content": str}
        {"type": "content", "content": str}
        """
        events: list[dict] = []

        if self._is_degraded:
            if chunk:
                events.append({"type": "content", "content": chunk})
            return events

        self._buffer += chunk

        if self._state == self.STATE_SEARCHING:
            match = self._REASONING_START.search(self._buffer)
            if match:
                self._state = self.STATE_IN_REASONING
                self._no_match_count = 0
                prefix = self._buffer[:match.start()]
                if prefix:
                    events.append({"type": "content", "content": prefix})
                remainder = self._buffer[match.end():]
                self._buffer = remainder
                if remainder:
                    events.append({"type": "reasoning", "content": remainder})
            else:
                self._no_match_count += 1
                if self._no_match_count >= self.TIMEOUT_CHUNKS:
                    self._is_degraded = True
                    logger.warning(
                        "[AI_QA] [WARN] [cot_tag_timeout] "
                        "no <reasoning> tag after %d chunks, degraded",
                        self.TIMEOUT_CHUNKS,
                    )
                    if self._buffer:
                        events.append({"type": "content", "content": self._buffer})
                        self._buffer = ''
                    return events
                if len(self._buffer) > 64:
                    self._buffer = self._buffer[-64:]

        elif self._state == self.STATE_IN_REASONING:
            end_match = self._REASONING_END.search(self._buffer)
            if end_match:
                content = self._buffer[:end_match.start()]
                if content:
                    events.append({"type": "reasoning", "content": content})
                self._buffer = self._buffer[end_match.end():]
                # 进入 IN_ANSWER 状态，后续等待 <answer> 或直接作为内容输出
                self._state = self.STATE_IN_ANSWER
                # 检查 </reasoning> 后是否紧跟 <answer>（同 chunk 内紧跟）
                answer_match = self._ANSWER_START.search(self._buffer)
                if answer_match:
                    remainder = self._buffer[answer_match.end():]
                    if remainder:
                        events.append({"type": "content", "content": remainder})
                    self._buffer = ''
                elif self._buffer:
                    # </reasoning> 后有内容但无 <answer> 标签 → 直接作为 answer 内容
                    events.append({"type": "content", "content": self._buffer})
                    self._buffer = ''
            else:
                wrong_match = self._ANSWER_START.search(self._buffer)
                if wrong_match:
                    logger.warning(
                        "[AI_QA] [WARN] [cot_tag_wrong_order] "
                        "<answer> before </reasoning>, degrading"
                    )
                    self._is_degraded = True
                    content = self._buffer[:wrong_match.start()]
                    if content:
                        events.append({"type": "reasoning", "content": content})
                    after = self._buffer[wrong_match.end():]
                    if after:
                        events.append({"type": "content", "content": after})
                    self._buffer = ''
                    return events
                if self._buffer:
                    events.append({"type": "reasoning", "content": self._buffer})
                    self._buffer = ''

        elif self._state == self.STATE_IN_ANSWER:
            # 顶层 IN_ANSWER 分支：后续所有 chunk 直接作为 content 输出
            if self._buffer:
                events.append({"type": "content", "content": self._buffer})
                self._buffer = ''

        return events

    def finalize(self) -> list[dict]:
        """流结束时调用，处理缓冲区残留。"""
        events: list[dict] = []
        if self._state == self.STATE_IN_REASONING:
            if self._buffer:
                logger.warning(
                    "[AI_QA] [WARN] [cot_tag_incomplete] "
                    "no </reasoning> tag found, remaining as reasoning"
                )
                events.append({"type": "reasoning", "content": self._buffer})
        elif self._state == self.STATE_SEARCHING and self._buffer:
            events.append({"type": "content", "content": self._buffer})
        self._buffer = ''
        return events


router = APIRouter(prefix="/api", tags=["chat"])

TYPE_MAP = {
    QuestionType.TREND: "trend",
    QuestionType.PRICE: "price",
    QuestionType.POLICY: "policy",
    QuestionType.GENERAL: "general",
}

COLLECTION_NAME = "grain_knowledge"

# ============================================================
# 常量定义（集中管理，避免魔法值）
# ============================================================
SIMILARITY_THRESHOLD = 0.4          # 向量检索相似度阈值
SEARCH_TOP_K_INITIAL = 30           # Qdrant 初始检索数量
SEARCH_TOP_K_PRE_SORT = 10          # 精细排序前截取数量
SEARCH_TOP_K_FINAL = 5              # 最终返回数量
SOURCE_CONTENT_MAX_LEN = 500        # source.content 截断长度
HEARTBEAT_INTERVAL_SEC = 12         # 心跳间隔（秒）
IDEMPOTENT_TTL_SEC = 300            # 幂等 key TTL
SSE_CACHE_TTL_SEC = 300             # SSE 缓存 TTL
HISTORY_MAX_GROUPS = 30             # 历史消息最大组数



@router.get("/health/async-writer")
async def writer_health():
    """MySQL 异步写入器运行状态（用于监控告警）"""
    writer = get_global_writer()
    return {
        "status": "alive" if writer.status()["alive"] else "dead",
        "main_queue_size": writer.status()["main_queue_size"],
        "dlq_size": writer.status()["dlq_size"],
        "sustained_failure_count": writer.status()["sustained_failure_count"],
    }


# ============================================================
# V2 请求模型
# ============================================================


class ChatV2Request(BaseModel):
    session_id: str = Field(..., description="会话 ID，前端 UUID 生成")
    client_msg_id: str = Field(..., description="消息幂等键，前端 UUID 生成")
    question: str = Field(..., min_length=1, max_length=500, description="用户问题")
    deep_thinking: bool = Field(default=False, description="是否启用深度思考模式")
    user_id: str = Field(default="", description="用户 ID（后端覆盖）")

    @field_validator("question")
    @classmethod
    def strip_question(cls, v: str) -> str:
        stripped = v.strip()
        if not stripped:
            raise ValueError("问题不能为纯空白")
        return stripped


# ============================================================
# V2 流式端点 — 6 事件 SSE + 双写 + 分类集成
# ============================================================


@router.post("/chat/v2/stream")
async def chat_v2_stream(request: ChatV2Request, http_request: Request):
    user_id = http_request.headers.get("X-User-Id", request.user_id)
    if not user_id:
        return StreamingResponse(
            iter([json.dumps({"type": "error", "code": "UNAUTHORIZED", "message": "缺少用户信息"})]),
            media_type="text/event-stream",
        )

    # 幂等 key 检查
    redis = get_redis_client()
    idempotent_key = f"idempotent:user:{user_id}:msg:{request.client_msg_id}"
    sse_cache_key = f"sse_cache:user:{user_id}:msg:{request.client_msg_id}"
    if redis.exists(idempotent_key):
        return StreamingResponse(
            iter([json.dumps({"type": "done", "token_used": 0})]),
            media_type="text/event-stream",
        )
    redis.setex(idempotent_key, IDEMPOTENT_TTL_SEC, json.dumps({"status": "running"}))
    try:
        redis.setex(sse_cache_key, SSE_CACHE_TTL_SEC, "")  # TTL 兜底，防止清理失败残留
    except Exception:
        pass

    def _cleanup_idempotent(terminal_type: str):
        """请求终态主动清理幂等 key（done/error/abort 后调用）"""
        try:
            redis.delete(idempotent_key, sse_cache_key)
        except Exception as e:
            logger.warning("[AI_QA] [WARN] [idempotent_cleanup_failed] key=%s type=%s error=%s",
                           idempotent_key, terminal_type, e)

    async def sse_gen():
        rid = getattr(http_request.state, "request_id", "-")
        gen = SSEResponseGenerator(request_id=rid, session_id=request.session_id)
        hm = HistoryManager(user_id, request.session_id)
        counter = CounterFactory.get_counter()

        # ── 后台心跳任务：全程保活，不与 LLM 分片耦合 ──
        hb_queue: asyncio.Queue = asyncio.Queue()

        async def _heartbeat_worker():
            try:
                while True:
                    await asyncio.sleep(HEARTBEAT_INTERVAL_SEC)
                    event = await gen.send_heartbeat()
                    if event:
                        await hb_queue.put(event)
            except asyncio.CancelledError:
                pass

        async def _drain_hb():
            while not hb_queue.empty():
                yield await hb_queue.get_nowait()

        hb_task = asyncio.create_task(_heartbeat_worker())

        try:
            # 1. 读取历史
            yield await gen.send_thought("正在读取对话历史...")
            history = hm.get_recent_history(max_groups=HISTORY_MAX_GROUPS)

            # 2. 问题分类
            qtype = classify_question(request.question)
            yield await gen.send_thought(f"问题类型: {TYPE_MAP.get(qtype, '综合')}")

            # 3. 检索知识库（获取更多结果以供去重筛选）
            yield await gen.send_thought("正在检索知识库...")
            search_results = search_vectors(query=request.question, top_k=SEARCH_TOP_K_INITIAL)
            # 过滤低相似度结果（不再要求 publish_time，支持用户上传的无日期文档）
            filtered = [
                r for r in search_results
                if r.get("similarity", 0) > SIMILARITY_THRESHOLD
            ]
            # 按内容去重（删除真正重复的切片，不同内容的均保留）
            seen_content = set()
            content_deduped = []
            for r in sorted(filtered, key=lambda x: -x.get("similarity", 0)):
                content_hash = r.get("content", "")[:200]  # 取前200字符作为去重依据
                if content_hash not in seen_content:
                    seen_content.add(content_hash)
                    content_deduped.append(r)
            # 精细排序前先截取 top-N，减少排序开销
            content_deduped = content_deduped[:SEARCH_TOP_K_PRE_SORT]
            # 按时间倒序排列（最新数据优先）
            # 同一天内：行情概述切片（内容以标题开头）排前面
            def sort_key(r):
                pt = r.get("publish_time", "") or ""
                ts = 0
                try:
                    import datetime as _dt
                    ts = -_dt.datetime.fromisoformat(pt.replace("Z", "+00:00")).timestamp()
                except Exception:
                    ts = 0
                # 行情概述切片（以文章标题字符开头）优先
                overview = 0 if r.get("content", "").startswith(("（", "(", "《", "【")) else 1
                return (ts, overview)
            content_deduped.sort(key=sort_key)
            search_results = content_deduped[:SEARCH_TOP_K_FINAL]
            sources = []
            for r in search_results:
                raw_content = r.get("content", "")
                sources.append({
                    "index": len(sources),
                    "title": r.get("title", ""),
                    "source": r.get("source", "知识库"),
                    "date": r.get("publish_time", ""),
                    "content": raw_content[:SOURCE_CONTENT_MAX_LEN],  # 截断过长的 content
                    "relevance": r.get("similarity", 0),
                    "kb_id": r.get("kb_id"),
                    "chunk_index": r.get("chunk_index"),
                })
            if sources:
                yield await gen.send_source(sources)
            else:
                yield await gen.send_thought("知识库暂无相关数据")

            # 4. 构建 messages
            messages = build_messages(
                question=request.question,
                history=history,
                sources=search_results,
                qtype=TYPE_MAP.get(qtype, "general"),
                request_id=rid,
                session_id=request.session_id,
                deep_thinking=request.deep_thinking,
            )

            # 5. LLM 流式调用 + 深度思考标签解析
            answer_text = ""
            reasoning_text = ""

            if request.deep_thinking:
                # 深度思考模式：使用标签解析器分流
                cot_parser = CoTStreamParser()
                _reasoning_seq = 0
                async for chunk in generate_answer_stream(
                    messages=messages,
                    model=None,
                ):
                    async for hb in _drain_hb():
                        yield hb
                    if chunk.get("type") == "error":
                        err = await gen.send_error("LLM_FAILED", chunk.get("content", ""))
                        if err:
                            yield err
                        return
                    content = chunk.get("content", "")
                    if not content:
                        continue

                    events = cot_parser.feed(content)
                    for evt in events:
                        if evt["type"] == "reasoning":
                            reasoning_text += evt["content"]
                            _reasoning_seq += 1
                            yield build_reasoning_event(
                                evt["content"],
                                seq=_reasoning_seq,
                            )
                        elif evt["type"] == "content":
                            answer_text += evt["content"]
                            event = await gen.send_content(evt["content"])
                            if event:
                                yield event

                # 流结束：处理残留缓冲区
                final_events = cot_parser.finalize()
                for evt in final_events:
                    if evt["type"] == "reasoning":
                        reasoning_text += evt["content"]
                        _reasoning_seq += 1
                        yield build_reasoning_event(
                            evt["content"],
                            seq=_reasoning_seq,
                        )
                    elif evt["type"] == "content":
                        answer_text += evt["content"]
                        event = await gen.send_content(evt["content"])
                        if event:
                            yield event
            else:
                # 普通模式：不使用标签解析器
                async for chunk in generate_answer_stream(
                    messages=messages,
                    model=None,
                ):
                    async for hb in _drain_hb():
                        yield hb
                    if chunk.get("type") == "error":
                        err = await gen.send_error("LLM_FAILED", chunk.get("content", ""))
                        if err:
                            yield err
                        return
                    content = chunk.get("content", "")
                    if content:
                        answer_text += content
                        event = await gen.send_content(content)
                        if event:
                            yield event

            # 6. 写 Redis（同步）+ 写 MySQL（异步双写）
            # Fix 3: 计数器与持久化分别异常捕获，互不阻断
            _msg_id = None
            _asst_msg_id = None
            try:
                _msg_id = counter.incr("chat_messages")
                _asst_msg_id = counter.incr("chat_messages")
            except Exception as e:
                logger.error("[AI_QA] [ERROR] [counter_incr_failed] user=%s error=%s", user_id, e)
                _msg_id = _asst_msg_id = str(_uuid.uuid4())
            group_id = _msg_id

            # Redis 写入（与 MySQL 异步写入独立异常捕获）
            try:
                hm.add_message(role="user", content=request.question, group_id=group_id, message_id=_msg_id)
                hm.add_message(role="assistant", content=answer_text, group_id=group_id, message_id=_asst_msg_id)
            except Exception as e:
                logger.error("[AI_QA] [ERROR] [redis_history_write_failed] user=%s error=%s", user_id, e)

            # 推理内容入库前空值处理
            reasoning_text_clean = reasoning_text.strip()

            # MySQL 异步写入
            try:
                writer = get_global_writer()
                for role, content, gid, mid, s in [
                    ("user", request.question, group_id, _msg_id, 0),
                    ("assistant", answer_text, group_id, _asst_msg_id, 1),
                ]:
                    writer.enqueue({
                        "user_id": user_id, "request_id": "-",
                        "session_id": request.session_id,
                        "client_msg_id": request.client_msg_id,
                        "role": role, "content": content,
                        "knowledge_ids": None,
                        "reasoning_content": reasoning_text_clean if reasoning_text_clean and role == "assistant" else None,
                        "message_id": mid, "group_id": gid, "seq": s,
                    })
            except Exception as e:
                logger.error("[AI_QA] [ERROR] [async_writer_enqueue_failed] user=%s error=%s", user_id, e)

            # 7. 注册异步标题生成（schedule_title_generation 内部用后台线程 + 30s 静默超时触发）
            try:
                schedule_title_generation(request.session_id, request.question)
            except Exception as e:
                logger.warning("[AI_QA] [WARN] [title_schedule_failed] session=%s error=%s", request.session_id, e)

            # 8. done 事件 + 幂等键清理
            done_event = await gen.send_done(token_used=0)
            if done_event:
                yield done_event
            _cleanup_idempotent('done')

        except asyncio.CancelledError:
            logger.warning("[AI_QA] [WARN] [client_disconnected] session=%s user=%s",
                           request.session_id, user_id)
            abort_event = await gen.send_abort(code="LLM_CANCELLED")
            if abort_event:
                yield abort_event
            _cleanup_idempotent('abort')
            raise
        except Exception as e:
            logger.error("[AI_QA] [ERROR] [chat_v2_failed] session=%s error=%s", request.session_id, e)
            err_event = await gen.send_error("UNKNOWN", "服务异常，请稍后重试")
            if err_event:
                yield err_event
            _cleanup_idempotent('error')
        finally:
            hb_task.cancel()
            try:
                await hb_task
            except asyncio.CancelledError:
                pass

    return StreamingResponse(sse_gen(), media_type="text/event-stream")


# ============================================================
# 旧端点（保持不变）
# ============================================================


class ChatRequest(BaseModel):
    question: str = Field(..., min_length=1, max_length=500)
    top_k: Optional[int] = 5


@router.post("/chat")
async def chat(request: ChatRequest):
    """[DEPRECATED] 旧版同步聊天接口 — 请迁移至 /chat/v2/stream"""
    # 1. 搜索相关知识
    search_results = search_vectors(
        query=request.question,
        top_k=request.top_k,
        source_filter=None
    )

    # 2. 构建上下文
    context_parts = []
    references = []
    for i, result in enumerate(search_results):
        context_parts.append(f"【来源 {i+1}】{result['title']} ({result.get('publish_time', '未知时间')})\n{result['content']}")
        references.append({
            "report_id": result.get('point_id'),
            "title": result['title'],
            "source": result.get('source', '未知'),
            "publish_time": result.get('publish_time'),
            "similarity": result.get('similarity', 0)
        })

    context = "\n\n".join(context_parts) if context_parts else "暂无相关资料"

    # 3. 生成回答
    answer = await generate_answer(
        question=request.question,
        context=context
    )

    return {
        "code": 200,
        "data": {
            "answer": answer,
            "references": references
        }
    }


async def sse_generator(question: str, context: str):
    """SSE流式生成器（旧端点，适配新 generate_answer_stream 签名）"""
    messages = build_messages(
        question=question,
        history=[],
        sources=[{"content": context, "source": "知识库", "relevance": 1.0}],
        qtype="general",
    )
    async for chunk in generate_answer_stream(messages=messages, model=None):
        data = json.dumps(chunk)
        yield f"data: {data}\n\n"


@router.post("/chat/stream")
async def chat_stream(request: ChatRequest):
    """[DEPRECATED] 旧版流式聊天接口 — 请迁移至 /chat/v2/stream"""
    search_results = search_vectors(
        query=request.question,
        top_k=request.top_k,
        source_filter=None
    )

    context_parts = []
    for i, result in enumerate(search_results):
        context_parts.append(f"【来源 {i+1}】{result['title']} ({result.get('publish_time', '未知时间')})\n{result['content']}")

    context = "\n\n".join(context_parts) if context_parts else "暂无相关资料"

    return StreamingResponse(
        sse_generator(question=request.question, context=context),
        media_type="text/event-stream"
    )


@router.get("/chat/messages")
async def get_messages(session_id: str, http_request: Request):
    """获取会话的历史消息列表（Redis + MySQL 补充 reasoning_content）"""
    user_id = http_request.headers.get("X-User-Id", "")
    if not user_id:
        return {"code": 400, "message": "缺少用户信息"}
    hm = HistoryManager(user_id, session_id)
    history = hm.get_recent_history(max_groups=HISTORY_MAX_GROUPS)

    # 查询 MySQL 获取 reasoning_content
    if history:
        try:
            from app.db.mysql import execute_query as mysql_query
            mysql_rows = mysql_query(
                "SELECT message_id, reasoning_content "
                "FROM t_chat_history "
                "WHERE session_id = %s AND user_id = %s AND role = 'assistant' "
                "AND reasoning_content IS NOT NULL "
                "ORDER BY message_id ASC",
                (session_id, user_id),
            )
            reasoning_map = {
                row["message_id"]: row["reasoning_content"]
                for row in mysql_rows
            }
            for item in history:
                mid = item.get("message_id")
                if mid in reasoning_map:
                    item["reasoning_content"] = reasoning_map[mid]
        except Exception as e:
            logger.warning(
                "[AI_QA] [WARN] [reasoning_query_failed] session=%s error=%s",
                session_id, e,
            )

    return {
        "code": 200,
        "data": history,
    }


@router.get("/reports/{report_id}")
async def get_report(report_id: str):
    """获取报告详情 - report_id 可以是 UUID 字符串"""
    client = get_client()
    try:
        results = client.retrieve(
            collection_name=COLLECTION_NAME,
            ids=[report_id]
        )
        if results and len(results) > 0:
            r = results[0]
            return {
                "code": 200,
                "data": {
                    "id": r.id,
                    "title": r.payload.get("title"),
                    "content": r.payload.get("content"),
                    "source": r.payload.get("source"),
                    "publish_time": r.payload.get("publish_time")
                }
            }
        return {"code": 404, "message": "报告不存在"}
    except Exception as e:
        return {"code": 500, "message": str(e)}
