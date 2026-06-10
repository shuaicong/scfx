"""AI 问答服务入口

注意：dotenv 加载必须在所有模块 import 之前执行，
否则子模块在 import 时读取 os.environ 会拿到空值。
"""
import os
from pathlib import Path
from dotenv import load_dotenv

_env_path = Path(__file__).resolve().parent.parent / "ai-qa-service.env"
if _env_path.exists():
    load_dotenv(_env_path, override=False)
    _key = os.environ.get("SILICON_FLOW_API_KEY", "")
    if _key:
        print(f"[main] 已加载 SILICON_FLOW_API_KEY (前缀={_key[:8]}..., len={len(_key)})")
    else:
        print("[main] 警告: SILICON_FLOW_API_KEY 未设置，AI 问答将使用演示模式")
del _env_path, _key, load_dotenv

import json
import logging
import traceback
import uuid
import time as time_module
from collections import defaultdict
from fastapi import FastAPI, Request, HTTPException
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, StreamingResponse
from app.constants.sensitive_patterns import SensitiveDataFilter
from app.api.knowledge import router as knowledge_router
from app.api.chat import router as chat_router

app = FastAPI(title="AI QA Service", version="1.0.0")

# ---- 日志脱敏过滤器（全局生效，所有日志输出自动脱敏） ----
logging.getLogger().addFilter(SensitiveDataFilter())
logging.getLogger("uvicorn.access").addFilter(SensitiveDataFilter())

logger = logging.getLogger(__name__)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ---- request_id 中间件（全链路透传） ----
@app.middleware("http")
async def request_id_middleware(request: Request, call_next):
    """从 X-Request-Id 请求头读取 request_id，不存在则生成新的 UUID"""
    rid = request.headers.get("X-Request-Id", "")
    if not rid or len(rid) != 36 or rid[14] != '4' or rid[8] != '-':
        rid = str(uuid.uuid4())
    request.state.request_id = rid

    # 在日志中注入 request_id
    old_factory = logging.getLogRecordFactory()
    def record_factory(*args, **kwargs):
        record = old_factory(*args, **kwargs)
        record.request_id = rid
        return record
    logging.setLogRecordFactory(record_factory)

    response = await call_next(request)
    response.headers["X-Request-Id"] = rid
    return response


# ---- QPS 限流中间件（单用户 10 次/分钟，固定窗口） ----
# 使用进程内内存字典计数（P1 简化实现，多实例不共享）
RATE_LIMIT_PER_USER = 10       # 单用户每分钟最大请求数
RATE_LIMIT_WINDOW = 60         # 窗口大小（秒）
RATE_LIMIT_PATHS = {"/api/chat/v2/stream", "/api/chat/session/close"}

_rate_limit_buckets: dict[str, tuple[int, int]] = {}  # user_id -> (window_start_ts, count)


@app.middleware("http")
async def rate_limit_middleware(request: Request, call_next):
    path = request.url.path
    if path not in RATE_LIMIT_PATHS:
        return await call_next(request)

    user_id = getattr(request.state, "user_id", None) or request.headers.get("X-User-Id", "")
    if not user_id:
        return await call_next(request)

    now_ts = int(time_module.time())
    window_start = now_ts - (now_ts % RATE_LIMIT_WINDOW)  # 对齐自然分钟边界

    entry = _rate_limit_buckets.get(user_id)
    if entry and entry[0] == window_start:
        if entry[1] >= RATE_LIMIT_PER_USER:
            rid = getattr(request.state, "request_id", "-")
            logger.warning("[AI_QA] [WARN] [rate_limited] user=%s path=%s request_id=%s",
                           user_id[:8], path, rid)
            if "/chat/v2/stream" in path:
                sse_error_data = {"type":"error","code":"RATE_LIMITED","message":"请求过于频繁，请稍后再试","retry_after":RATE_LIMIT_WINDOW}
                sse_error = f"event: error\ndata: {json.dumps(sse_error_data)}\n\n"
                return StreamingResponse(iter([sse_error]), media_type="text/event-stream",
                                         headers={"X-Request-Id": rid})
            return JSONResponse(
                status_code=429,
                content={"code": "RATE_LIMITED", "message": "请求过于频繁，请稍后再试",
                         "retry_after": RATE_LIMIT_WINDOW},
                headers={"X-Request-Id": rid},
            )
        _rate_limit_buckets[user_id] = (window_start, entry[1] + 1)
    else:
        _rate_limit_buckets[user_id] = (window_start, 1)

    response = await call_next(request)
    return response


app.include_router(knowledge_router)
app.include_router(chat_router)


# ---- 健康检查 ----
@app.get("/health")
async def health():
    return {"status": "ok"}


# ---- 全局关闭钩子 ----
@app.on_event("shutdown")
def shutdown_event():
    """服务关闭时：等待 MySQL 队列消化 + 关闭 Redis 连接池"""
    from app.services.async_writer import _global_writer
    if _global_writer:
        _global_writer.stop(timeout=5.0)
    from app.services.redis_client import close_redis_pool
    close_redis_pool()
    logger.info("[AI_QA] [INFO] [shutdown_complete] resources_released=true")


# ---- 全局异常处理器 ----
@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    """请求体验证失败 → 422 + 结构化错误（不暴露字段细节）"""
    rid = getattr(request.state, "request_id", "-")
    logger.warning("[AI_QA] [WARN] [validation_error] request_id=%s path=%s method=%s",
                   rid, request.url.path, request.method)
    return JSONResponse(
        status_code=422,
        content={"code": "VALIDATION_ERROR", "message": "请求参数格式错误"},
    )


@app.exception_handler(HTTPException)
async def http_exception_handler(request: Request, exc: HTTPException):
    """已知 HTTP 异常（401/403/404 等）"""
    rid = getattr(request.state, "request_id", "-")
    logger.info("[AI_QA] [INFO] [http_error] request_id=%s path=%s status=%s",
                rid, request.url.path, exc.status_code)
    return JSONResponse(
        status_code=exc.status_code,
        content={"code": f"HTTP_{exc.status_code}", "message": exc.detail},
    )


@app.exception_handler(Exception)
async def unhandled_exception_handler(request: Request, exc: Exception):
    """未捕获异常兜底 → 500 + 日志保留堆栈（不暴露给客户端）"""
    rid = getattr(request.state, "request_id", "-")
    logger.error(
        "[AI_QA] [ERROR] [unhandled_exception] request_id=%s path=%s\n%s",
        rid, request.url.path, traceback.format_exc(),
    )
    return JSONResponse(
        status_code=500,
        content={"code": "INTERNAL_ERROR", "message": "服务异常，请稍后重试"},
    )


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=5002)
