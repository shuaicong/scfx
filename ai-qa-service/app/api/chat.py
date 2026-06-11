"""AI 问答 API — 新版 6 事件 SSE 流"""
import json
import logging
import asyncio
import time as time_module
from fastapi import APIRouter, Request
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field
from typing import Optional, List

from app.services.llm import build_messages, generate_answer, generate_answer_stream
from app.services.question_classifier import classify_question, QuestionType
from app.services.sse_manager import SSEResponseGenerator
from app.services.history_manager import HistoryManager
from app.services.counter import CounterFactory, LocalCounter
from app.services.redis_client import get_redis_client
from app.services.vector import search_vectors
from app.db.qdrant import get_client

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api", tags=["chat"])

TYPE_MAP = {
    QuestionType.TREND: "trend",
    QuestionType.PRICE: "price",
    QuestionType.POLICY: "policy",
    QuestionType.GENERAL: "general",
}

COLLECTION_NAME = "grain_knowledge"


# ============================================================
# 监控端点
# ============================================================


@router.get("/health/async-writer")
async def writer_health():
    """MySQL 异步写入器运行状态（用于监控告警）"""
    from app.services.async_writer import get_global_writer
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
    user_id: str = Field(default="", description="用户 ID（后端覆盖）")


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
    redis.setex(idempotent_key, 300, json.dumps({"status": "running"}))

    def _cleanup_idempotent(terminal_type: str):
        """请求终态主动清理幂等 key（done/error/abort 后调用）"""
        try:
            if terminal_type in ("error", "abort"):
                redis.delete(idempotent_key, sse_cache_key)
            elif terminal_type == "done":
                redis.expire(idempotent_key, 60)
                redis.delete(sse_cache_key)
        except Exception:
            pass  # 清理失败依赖 TTL 兜底（300s）

    async def sse_gen():
        rid = getattr(http_request.state, "request_id", "-")
        gen = SSEResponseGenerator(request_id=rid, session_id=request.session_id)
        hm = HistoryManager(user_id, request.session_id)
        counter = CounterFactory.get_counter()

        try:
            # 1. 读取历史
            yield await gen.send_thought("正在读取对话历史...")
            history = hm.get_recent_history()

            # 2. 问题分类
            qtype = classify_question(request.question)
            yield await gen.send_thought(f"问题类型: {TYPE_MAP.get(qtype, '综合')}")

            # 3. 检索知识库
            yield await gen.send_thought("正在检索知识库...")
            search_results = search_vectors(query=request.question, top_k=5)
            sources = []
            for r in search_results:
                sources.append({
                    "index": len(sources),
                    "title": r.get("title", ""),
                    "source": r.get("source", "知识库"),
                    "date": r.get("publish_time", ""),
                    "content": r.get("content", ""),
                    "relevance": r.get("similarity", 0),
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
            )

            # 5. LLM 流式调用（含心跳保活）
            answer_text = ""
            _last_hb = time_module.monotonic()
            async for chunk in generate_answer_stream(
                messages=messages,
                model=None,
            ):
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
                # 每 12s 插入一次心跳
                if time_module.monotonic() - _last_hb >= 12:
                    hb = await gen.send_heartbeat()
                    if hb:
                        yield hb
                    _last_hb = time_module.monotonic()

            # 6. 写 Redis（同步）+ 写 MySQL（异步双写）
            # 使用 CounterFactory.get_counter() 返回的全局计数器
            # group_id = user 的 message_id（同一个 pair 共享 group_id）
            try:
                msg_id = counter.incr("chat_messages")
                group_id = msg_id
                hm.add_message(role="user", content=request.question, group_id=group_id, message_id=msg_id)
                asst_msg_id = counter.incr("chat_messages")
                hm.add_message(role="assistant", content=answer_text, group_id=group_id, message_id=asst_msg_id)

                # MySQL 异步写入（模块级单例）
                from app.services.async_writer import get_global_writer
                writer = get_global_writer()
                for role, content, gid, mid, s in [
                    ("user", request.question, group_id, msg_id, 0),
                    ("assistant", answer_text, group_id, asst_msg_id, 1),
                ]:
                    writer.enqueue({
                        "user_id": user_id, "request_id": "-",
                        "session_id": request.session_id,
                        "client_msg_id": request.client_msg_id,
                        "role": role, "content": content,
                        "knowledge_ids": None,
                        "message_id": mid, "group_id": gid, "seq": s,
                    })
            except Exception as e:
                logger.error("[AI_QA] [ERROR] [counter_incr_failed] user=%s error=%s", user_id, e)

            # 7. 注册异步标题生成（静默30秒后触发）
            try:
                from app.services.session_title import schedule_title_generation
                schedule_title_generation(request.session_id, request.question)
            except Exception as e:
                logger.warning("[AI_QA] [WARN] [title_schedule_failed] session=%s error=%s", request.session_id, e)

            # 8. done 事件 + 幂等键清理
            done_event = await gen.send_done(token_used=0)
            if done_event:
                yield done_event
            _cleanup_idempotent('done')

        except asyncio.CancelledError:
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

    return StreamingResponse(sse_gen(), media_type="text/event-stream")


# ============================================================
# 旧端点（保持不变）
# ============================================================


class ChatRequest(BaseModel):
    question: str = Field(..., min_length=1, max_length=500)
    top_k: Optional[int] = 5
    source_filter: Optional[List[str]] = None


@router.post("/chat")
async def chat(request: ChatRequest):
    # 1. 搜索相关知识
    search_results = search_vectors(
        query=request.question,
        top_k=request.top_k,
        source_filter=None  # TODO: 支持 source_filter
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
    from app.services.llm import build_messages
    from app.services.question_classifier import classify_question
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
    """流式聊天接口，返回SSE格式数据流"""
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
