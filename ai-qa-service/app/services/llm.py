"""
LLM 调用 + modular messages 构建 + Token 风控 + 熔断/重试

核心职责：
1. 模块化 Prompt 构建（A-B-D-C-Q 顺序）
2. Token 计数与超限降级
3. LLM 熔断（断路器模式）与指数退避重试
4. 兼容旧接口 generate_answer + 新接口 generate_answer_stream
"""
import os
import json
import logging
import time
import asyncio
from datetime import date
from typing import Optional, AsyncGenerator

import yaml
import tiktoken
import httpx

logger = logging.getLogger(__name__)

# ============================================================
# 基础配置
# ============================================================
API_URL = os.getenv("SILICON_FLOW_URL", "https://api.siliconflow.cn/v1/chat/completions")
API_KEY = os.getenv("SILICON_FLOW_API_KEY", "")
MODEL = os.getenv("SILICON_FLOW_MODEL", "Qwen/Qwen2.5-14B-Instruct")
TOKENIZER_ENCODING = os.getenv("TOKENIZER_ENCODING", "cl100k_base")

# ============================================================
# Token 风控常量
# ============================================================
TOKEN_HARD_LIMIT = 4000          # 消息数组总 token 硬上限
TOKEN_WARN_LIMIT = 3800          # 触发降级的警告线
MODULE_B_SOFT_LIMIT = 1800       # 模块 B（历史）token 软上限
SOURCES_MAX_COUNT = 8            # 模块 D 最大参考资料条数
SOURCES_MAX_TOKENS = 2000        # 模块 D 总 token 上限（提至2000让LLM看到完整数据）
CONTEXT_MAX_TOKENS = 1200        # 旧接口 generate_answer 的 context 截断阈值

# ============================================================
# LLM 熔断 + 重试 + 超时
# ============================================================
CIRCUIT_BREAKER_THRESHOLD = 3      # 连续失败次数 → 熔断打开
CIRCUIT_BREAKER_RECOVERY = 60      # 熔断恢复时间（秒）
CIRCUIT_BREAKER_HALF_OPEN_MAX = 1  # 半开状态最大放行请求数
LLM_CONNECT_TIMEOUT = 15           # 连接超时（秒）
LLM_READ_TIMEOUT = 55              # 读取超时（秒）
LLM_REQUEST_TIMEOUT = 60           # 整体请求超时（秒，兼容旧引用）
LLM_RETRY_MAX = 2                  # 失败后指数退避重试次数（不含首次）
LLM_RETRY_BASE_DELAY = 2.0         # 退避基值（秒）
LLM_RETRY_MAX_DELAY = 10.0         # 退避最大延迟上限（秒）

class CircuitBreakerOpen(Exception):
    """熔断打开异常 — 调用方捕获后立即返回 error 事件，不再尝试。"""


class CircuitBreaker:
    """熔断器 — 独立实例，asyncio.Lock 保护状态，支持并发安全。"""

    def __init__(self, threshold: int, recovery: int, half_open_max: int):
        self._threshold = threshold
        self._recovery = recovery
        self._half_open_max = half_open_max
        self._state = "closed"      # closed / open / half-open
        self._failures = 0
        self._last_open = 0.0
        self._lock = asyncio.Lock()

    async def allow_request(self) -> bool:
        async with self._lock:
            if self._state == "closed":
                return True
            if self._state == "open":
                now = time.monotonic()
                if now - self._last_open >= self._recovery:
                    self._state = "half-open"
                    self._failures = 0
                    logger.info("[AI_QA] [INFO] [circuit_breaker_half_open]")
                    return True
                logger.warning(
                    "[AI_QA] [WARN] [circuit_breaker_open] remaining=%ds",
                    int(self._recovery - (now - self._last_open)),
                )
                return False
            # half-open
            return self._failures < self._half_open_max

    async def record_success(self):
        async with self._lock:
            self._failures = 0
            if self._state == "half-open":
                self._state = "closed"
                logger.info("[AI_QA] [INFO] [circuit_breaker_closed]")

    async def record_failure(self):
        async with self._lock:
            self._failures += 1
            if self._failures >= self._threshold:
                self._state = "open"
                self._last_open = time.monotonic()
                logger.error(
                    "[AI_QA] [ALERT] [circuit_breaker_tripped] failures=%d recovery=%ds",
                    self._failures, self._recovery,
                )


# 全局熔断器实例
_circuit_breaker = CircuitBreaker(
    threshold=CIRCUIT_BREAKER_THRESHOLD,
    recovery=CIRCUIT_BREAKER_RECOVERY,
    half_open_max=CIRCUIT_BREAKER_HALF_OPEN_MAX,
)


# ============================================================
# Prompt 配置加载
# ============================================================
_MODULE_A_CACHE: Optional[str] = None
_TEMPLATES_CACHE: Optional[dict] = None
_CONFIG_MTIME: float = 0


def _load_prompt_config():
    """加载模块 A 和模板 C 配置（带文件 mtime 热更新）。"""
    global _MODULE_A_CACHE, _TEMPLATES_CACHE, _CONFIG_MTIME
    config_path = os.path.join(
        os.path.dirname(__file__), "..", "config", "prompt.yaml"
    )
    try:
        current_mtime = os.path.getmtime(config_path)
        if _MODULE_A_CACHE is not None and current_mtime <= _CONFIG_MTIME:
            return _MODULE_A_CACHE, _TEMPLATES_CACHE
    except OSError:
        if _MODULE_A_CACHE is not None:
            return _MODULE_A_CACHE, _TEMPLATES_CACHE
    try:
        with open(config_path, encoding="utf-8") as f:
            config = yaml.safe_load(f)
        _MODULE_A_CACHE = config["module_a"]
        _TEMPLATES_CACHE = config["templates"]
        _CONFIG_MTIME = current_mtime
        logger.info("[AI_QA] [INFO] [config_reloaded] mtime=%s", current_mtime)
    except Exception as e:
        logger.warning(
            "[AI_QA] [WARN] [config_load_failed] path=%s error=%s",
            config_path, e,
        )
        if _MODULE_A_CACHE is None:
            _MODULE_A_CACHE = "你是专业的粮食价格分析助手。请基于参考材料回答问题。"
        if _TEMPLATES_CACHE is None:
            _TEMPLATES_CACHE = {"general": "请根据提供的参考材料回答用户问题。"}
    return _MODULE_A_CACHE, _TEMPLATES_CACHE


# ============================================================
# Token 计数
# ============================================================


def _count_tokens(text: str) -> int:
    """使用 tiktoken 计算纯文本 token 数。"""
    try:
        enc = tiktoken.get_encoding(TOKENIZER_ENCODING)
        return len(enc.encode(text))
    except Exception:
        logger.warning("[AI_QA] [WARN] [tiktoken_failed] fallback=len_estimate")
        return len(text)  # fallback: 中文字符数 ≈ token 数


def _count_msg_tokens(msg: dict) -> int:
    """计算单条 message 的总 token（含 role/name 结构开销）。"""
    return _count_tokens(json.dumps(msg, ensure_ascii=False))


# ============================================================
# LLM 请求（带熔断 + 重试）
# ============================================================


async def _call_llm_with_retry(messages: list[dict], stream: bool = False) -> httpx.Response:
    """
    带熔断 + 超时 + 指数退避重试的 LLM 请求（同步返回完整响应）。

    流程：
    1. 检查熔断状态（打开 → 立即抛出 CircuitBreakerOpen）
    2. httpx.post(timeout=LLM_REQUEST_TIMEOUT) 发起请求
    3. 失败后 sleep(retry_base * 2^attempt) 重试
    4. 全部重试耗尽 → 记录熔断失败 → 抛出原始异常
    """
    if not await _circuit_breaker.allow_request():
        raise CircuitBreakerOpen("LLM circuit breaker is open")

    last_exc = None
    for attempt in range(LLM_RETRY_MAX + 1):
        try:
            async with httpx.AsyncClient(timeout=httpx.Timeout(LLM_CONNECT_TIMEOUT, read=LLM_READ_TIMEOUT)) as client:
                response = await client.post(
                    API_URL,
                    json={
                        "model": MODEL,
                        "messages": messages,
                        "temperature": 0.7,
                        "max_tokens": 2000,
                        "stream": stream,
                    },
                    headers={
                        "Authorization": f"Bearer {API_KEY}",
                        "Content-Type": "application/json",
                    },
                )
                response.raise_for_status()
                await _circuit_breaker.record_success()
                return response
        except httpx.HTTPStatusError as e:
            if e.response.status_code < 500:
                raise  # 4xx 属于业务错误，不重试
            last_exc = e
            logger.warning(
                "[AI_QA] [WARN] [llm_request_failed] attempt=%d/%d status=%d error=%s",
                attempt + 1, LLM_RETRY_MAX + 1, e.response.status_code, e,
            )
            if attempt < LLM_RETRY_MAX:
                await asyncio.sleep(min(LLM_RETRY_BASE_DELAY * (2 ** attempt), LLM_RETRY_MAX_DELAY))
        except (httpx.TimeoutException, httpx.ConnectError) as e:
            last_exc = e
            logger.warning(
                "[AI_QA] [WARN] [llm_request_failed] attempt=%d/%d error=%s",
                attempt + 1, LLM_RETRY_MAX + 1, e,
            )
            if attempt < LLM_RETRY_MAX:
                await asyncio.sleep(min(LLM_RETRY_BASE_DELAY * (2 ** attempt), LLM_RETRY_MAX_DELAY))

    await _circuit_breaker.record_failure()
    raise last_exc  # type: ignore[misc]


async def _call_llm_stream_with_retry(
    messages: list[dict],
) -> AsyncGenerator[dict, None]:
    """
    流式 LLM 请求 — 异步生成器，支持熔断 + 超时 + 重试。

    每次 yield 一个 dict：
    - 正常内容: {"type": "text", "content": char}
    - 错误:     {"type": "error", "content": str(e)}
    """
    if not await _circuit_breaker.allow_request():
        logger.warning("[AI_QA] [WARN] [circuit_breaker_open] action=stream_reject")
        yield {"type": "error", "content": "[CIRCUIT_OPEN] 服务暂时限流，请稍后重试"}
        return

    last_exc = None
    for attempt in range(LLM_RETRY_MAX + 1):
        try:
            async with httpx.AsyncClient(timeout=httpx.Timeout(LLM_CONNECT_TIMEOUT, read=LLM_READ_TIMEOUT)) as client:
                async with client.stream(
                    "POST",
                    API_URL,
                    json={
                        "model": MODEL,
                        "messages": messages,
                        "temperature": 0.7,
                        "max_tokens": 2000,
                        "stream": True,
                    },
                    headers={
                        "Authorization": f"Bearer {API_KEY}",
                        "Content-Type": "application/json",
                    },
                ) as response:
                    response.raise_for_status()
                    await _circuit_breaker.record_success()
                    async for line in response.aiter_lines():
                        if line.startswith("data: "):
                            data = line[6:]  # Remove "data: " prefix
                            if data == "[DONE]":
                                return
                            try:
                                chunk = json.loads(data)
                                if "choices" in chunk and len(chunk["choices"]) > 0:
                                    delta = chunk["choices"][0].get("delta", {})
                                    content = delta.get("content", "")
                                    if content:
                                        yield {"type": "text", "content": content}
                            except json.JSONDecodeError:
                                continue
                    return  # 正常完成
        except (httpx.TimeoutException, httpx.ConnectError, httpx.HTTPStatusError) as e:
            last_exc = e
            logger.warning(
                "[AI_QA] [WARN] [llm_stream_failed] attempt=%d/%d error=%s",
                attempt + 1, LLM_RETRY_MAX + 1, e,
            )
            if attempt < LLM_RETRY_MAX:
                await asyncio.sleep(min(LLM_RETRY_BASE_DELAY * (2 ** attempt), LLM_RETRY_MAX_DELAY))

    await _circuit_breaker.record_failure()
    yield {"type": "error", "content": str(last_exc)}


# ============================================================
# build_messages — A-B-D-C-Q 顺序构建
# ============================================================


def build_messages(
    question: str,
    history: list[dict],
    sources: list[dict],
    qtype: str = "general",
    request_id: str = "",
    session_id: str = "",
    deep_thinking: bool = False,     # 是否启用深度思考模式
) -> list[dict]:
    """
    构建 A → B → D → C → Q 顺序的 messages 数组。
    自动执行 Token 风控和降级截断。

    参考资料限制（双阈值）：
    - 数量上限: SOURCES_MAX_COUNT = 8 条
    - Token 上限: SOURCES_MAX_TOKENS = 2000（超出后丢弃最不相关的来源）

    参数：
        question: 用户当前问题
        history: 对话历史列表，每项含 role/content/message_id/group_id
        sources: 检索到的参考资料列表，每项含 content/source/similarity 等
        qtype: 问题类型（price/trend/policy/general）
        request_id: 链路追踪 ID
        session_id: 会话 ID
        deep_thinking: 是否启用深度思考模式（CoT）
    """
    _trace = f"req={request_id} session={session_id}"
    module_a, templates = _load_prompt_config()

    # ---- 模块 A: 全局角色（system） ----
    messages = [{"role": "system", "content": module_a}]

    # ---- 模块 B: 对话历史（user/assistant，过滤空内容和无效消息） ----
    for h in history:
        content = h.get("content", "").strip()
        if not content:
            continue
        messages.append({"role": h["role"], "content": content})

    # ---- 模块 D: 外部参考资料（system） ----
    if sources:
        # 阈值 1: 数量上限 — 保留上层传入的排序（chat.py 已按时间倒序排列）
        limited = sources[:SOURCES_MAX_COUNT]

        # 阈值 2: Token 上限 — 从最不相关开始丢弃
        selected = []
        token_budget = SOURCES_MAX_TOKENS
        for s in limited:
            _src_date = (s.get('publish_time') or s.get('date') or '')[:10]  # 归一化为 YYYY-MM-DD
            _src_tag = f"[来源: {s.get('source', '未知')} | {_src_date}]" if _src_date else f"[来源: {s.get('source', '未知')}]"
            snippet = f"{_src_tag}\n{s['content'].strip()}"
            tokens = _count_tokens(snippet)
            if tokens <= token_budget:
                selected.append(snippet)
                token_budget -= tokens
            elif token_budget > 50:
                # 单条超限: 截断到剩余预算的 80%（保留至少 20% 给其他来源）
                truncated = s["content"][:max(50, token_budget * 3)]
                selected.append(f"{_src_tag}\n{truncated}")
                token_budget = 0
                break

        source_text = "\n\n".join(selected) if selected else "（参考资料过多已精简）"
        messages.append({"role": "system", "content": source_text})

        if len(sources) > SOURCES_MAX_COUNT:
            logger.warning(
                "[AI_QA] [WARN] [sources_truncated] count=%d max=%d dropped=%d %s",
                len(sources), SOURCES_MAX_COUNT, len(sources) - SOURCES_MAX_COUNT, _trace,
            )

    # ---- 模块 C: 本次执行指令（system） ----
    template = templates.get(qtype, templates.get("general", ""))
    # 注入当前日期，让 LLM 能判断数据是否过时
    template = f"当前日期：{date.today().isoformat()}\n\n{template}"

    # 深度思考模式：追加 CoT 指令（放在模块 C 尾部，参考资料之后、问题之前）
    cot_instr = templates.get("deep_thinking_instruction", "")
    if deep_thinking and cot_instr:
        template += f"\n\n{cot_instr}"

    messages.append({"role": "system", "content": template})

    # ---- 模块 Q: 当前问题（user） ----
    messages.append({"role": "user", "content": question})

    # ================================================================
    # Token 风控（使用 _count_msg_tokens 纳入 role 结构开销）
    # ================================================================
    total_tokens = sum(_count_msg_tokens(m) for m in messages)
    if total_tokens <= TOKEN_WARN_LIMIT:
        return messages

    logger.warning(
        "[AI_QA] [WARN] [token_overflow] total=%d threshold=%d action=degrade %s",
        total_tokens, TOKEN_WARN_LIMIT, _trace,
    )

    # 降级 1: 精简模块 B（对话历史）— 先保留最近 3 轮
    module_b_tokens = sum(
        _count_msg_tokens(m)
        for m in messages
        if m["role"] in ("user", "assistant")
    )
    if module_b_tokens > MODULE_B_SOFT_LIMIT:
        msg_b = [m for m in messages if m["role"] in ("user", "assistant")]
        non_b = [m for m in messages if m["role"] not in ("user", "assistant")]
        kept = msg_b[-6:]  # 最多 3 组 = 6 条
        messages = non_b + kept
        logger.info(
            "[AI_QA] [INFO] [token_degrade_history] kept_rounds=3 %s", _trace,
        )

    # 降级 1b: 二次校验 — 降级 1 后仍超限则继续裁历史至 2 轮
    total_tokens = sum(_count_msg_tokens(m) for m in messages)
    if total_tokens > TOKEN_WARN_LIMIT:
        msg_b = [m for m in messages if m["role"] in ("user", "assistant")]
        non_b = [m for m in messages if m["role"] not in ("user", "assistant")]
        kept = msg_b[-4:]  # 2 组 = 4 条
        messages = non_b + kept
        logger.info(
            "[AI_QA] [INFO] [token_degrade_history_aggressive] kept_rounds=2 %s", _trace,
        )

    # 降级 2: 替换模块 D 为占位符
    total_tokens = sum(_count_msg_tokens(m) for m in messages)
    if total_tokens > TOKEN_HARD_LIMIT:
        for m in messages:
            if m["role"] == "system" and (
                "[来源:" in m["content"] or "暂无相关资料" in m["content"]
            ):
                m["content"] = "（参考资料已精简）"
                logger.warning(
                    "[AI_QA] [WARN] [token_degrade_sources] total=%d hard_limit=%d",
                    total_tokens, TOKEN_HARD_LIMIT,
                )
                break

    return messages


# ============================================================
# generate_answer — 旧接口（兼容）带 context 截断 + 重试
# ============================================================


async def generate_answer(
    question: str,
    context: str,
    model: str = None,
) -> str:
    """调用 LLM 生成回答（旧接口，兼容用途）。

    改动：
    - 入口处增加 context 截断（CONTEXT_MAX_TOKENS=1200）
    - 替换 httpx.post 直调为 _call_llm_with_retry
    """
    model = model or MODEL

    if not API_KEY:
        logger.info("[AI_QA] [INFO] [DEMO_MODE] API_KEY 未配置，使用演示模式")
        return (
            f"【演示模式】基于以下信息回答您的问题：\n\n{context}\n\n"
            f"问题：{question}\n\n"
            "（请配置 SILICON_FLOW_API_KEY 环境变量以启用真实 AI 回答）"
        )

    # ---- context 长度保护 ----
    total = _count_tokens(context)
    if total > CONTEXT_MAX_TOKENS:
        ratio = CONTEXT_MAX_TOKENS / total
        keep_chars = int(len(context) * ratio * 0.9)  # 留 10% 余量
        context = context[:keep_chars] + "\n\n（参考资料已截断）"
        logger.warning(
            "[AI_QA] [WARN] [context_truncated] total_tokens=%d limit=%d",
            total, CONTEXT_MAX_TOKENS,
        )

    # ---- 构建简单消息（旧接口风格，复用配置中心 module_a） ----
    _module_a, _ = _load_prompt_config()
    messages = [
        {"role": "system", "content": _module_a},
        {
            "role": "user",
            "content": f"参考材料：\n{context}\n\n问题：{question}\n\n"
            f"要求：\n"
            f"1. 仅基于参考材料回答\n"
            f"2. 如资料不足，说明\"根据现有资料无法回答\"\n"
            f"3. 引用时标注来源\n"
            f"4. 回答使用中文\n"
            f"5. 保持专业但易于理解",
        },
    ]

    # ---- 调用 LLM（带熔断 + 重试） ----
    try:
        response = await _call_llm_with_retry(messages, stream=False)
        result = response.json()
        if "choices" in result and len(result["choices"]) > 0:
            return result["choices"][0]["message"]["content"]
        else:
            return f"AI 服务返回异常：{result}"
    except CircuitBreakerOpen:
        logger.warning("[AI_QA] [WARN] [circuit_breaker_open] action=fallback")
        return "[CIRCUIT_OPEN] AI 服务暂时限流，请稍后重试"
    except Exception as e:
        logger.error(
            "[AI_QA] [ERROR] [generate_answer_failed] error=%s", e,
        )
        return f"调用 AI 服务失败：{str(e)}\n\n参考材料：\n{context}"


# ============================================================
# generate_answer_stream — 流式接口（新，接受预构建 messages）
# ============================================================


async def generate_answer_stream(
    messages: list[dict],
    model: str = None,
) -> AsyncGenerator[dict, None]:
    """流式调用 LLM，逐字符 yield 返回。

    参数：
        messages: 由 build_messages() 预构建好的 messages 数组
        model: 模型名称（可选，不传使用默认 MODEL）

    Yields:
        {"type": "text", "content": char} — 正常内容
        {"type": "error", "content": str(e)} — 错误
    """
    model = model or MODEL

    if not API_KEY:
        logger.info("[AI_QA] [INFO] [DEMO_MODE] API_KEY 未配置，使用演示模式")
        yield {"type": "text", "content": "【演示模式】请配置 SILICON_FLOW_API_KEY 环境变量以启用真实 AI 回答。"}
        return

    # 调用熔断保护的流式请求
    async for chunk in _call_llm_stream_with_retry(messages):
        if chunk.get("type") == "error":
            logger.error(
                "[AI_QA] [ERROR] [generate_answer_stream_failed] error=%s",
                chunk.get("content", ""),
            )
        yield chunk
