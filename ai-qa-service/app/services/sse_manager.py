"""
SSE 流管理器 — 状态机 + 终止事件互斥 + 分段发送 + 心跳保活
"""
import json
import time
import logging
import asyncio
from typing import AsyncGenerator, Optional

logger = logging.getLogger(__name__)

HEARTBEAT_INTERVAL = 12  # 秒


class SSEStateError(Exception):
    """SSE 状态转换非法"""


class SSETerminalConflictError(Exception):
    """终止事件冲突"""


class SSEStateMachine:
    """SSE 事件顺序状态机"""
    VALID_TRANSITIONS = {
        'INIT': {'THOUGHT', 'REASONING', 'CONTENT', 'ERROR', 'ABORT', 'HEARTBEAT'},
        'THOUGHT': {'THOUGHT', 'SOURCE', 'REASONING', 'CONTENT', 'ERROR', 'ABORT', 'HEARTBEAT'},
        'SOURCE': {'REASONING', 'CONTENT', 'ERROR', 'ABORT', 'HEARTBEAT'},
        'REASONING': {'REASONING', 'CONTENT', 'DONE', 'ERROR', 'ABORT', 'HEARTBEAT'},
        'CONTENT': {'CONTENT', 'DONE', 'ERROR', 'ABORT', 'HEARTBEAT'},
        'DONE': set(),
        'ERROR': set(),
        'ABORT': set(),
    }

    def __init__(self):
        self.state = 'INIT'

    @property
    def current_state(self) -> str:
        return self.state

    def transition(self, target: str):
        if target not in self.VALID_TRANSITIONS[self.state]:
            raise SSEStateError(f"Invalid transition: {self.state} -> {target}")
        self.state = target


class TerminalEventGuard:
    """终止事件互斥锁 — 优先级：abort > error > done"""
    PRIORITY = {'abort': 3, 'error': 2, 'done': 1}

    def __init__(self):
        self._sent: Optional[str] = None
        self._lock = asyncio.Lock()

    async def try_send(self, event_type: str) -> bool:
        async with self._lock:
            if self._sent is None:
                self._sent = event_type
                return True
            current_priority = self.PRIORITY.get(event_type, 0)
            sent_priority = self.PRIORITY.get(self._sent, 0)
            if current_priority > sent_priority:
                self._sent = event_type
                return True
            return False

    @property
    def sent_event(self) -> Optional[str]:
        return self._sent


def build_sse_event(event_type: str, data: dict) -> str:
    return f"event: {event_type}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"


def build_reasoning_event(content: str, seq: int = 0) -> str:
    """构建 reasoning SSE 事件"""
    return build_sse_event('reasoning', {
        'type': 'reasoning', 'content': content, 'seq': seq,
    })


class SSEResponseGenerator:
    """SSE 响应生成器 — 状态机校验 + 终止事件互斥 + 内容分段 + 心跳"""

    def __init__(self, request_id: str, session_id: str):
        self.request_id = request_id
        self.session_id = session_id
        self._state_machine = SSEStateMachine()
        self._terminal_guard = TerminalEventGuard()
        self._accumulator = ""
        self._content_seq = 0

    async def send_thought(self, content: str) -> str:
        self._state_machine.transition('THOUGHT')
        return build_sse_event('thought', {'type': 'thought', 'content': content})

    async def send_source(self, sources: list) -> str:
        self._state_machine.transition('SOURCE')
        return build_sse_event('source', {'type': 'source', 'sources': sources})

    async def send_content(self, chunk: str) -> Optional[str]:
        self._state_machine.transition('CONTENT')
        self._accumulator += chunk
        if len(self._accumulator) >= 150:
            return self._flush_content()
        for boundary in ['。', '！', '？', '\n']:
            if boundary in self._accumulator:
                last_idx = self._accumulator.rfind(boundary)
                if last_idx > 0 and len(self._accumulator[:last_idx + 1]) >= 10:
                    return self._flush_content(up_to=last_idx + 1)
        return None

    async def send_done(self, token_used: int, compressed: bool = False) -> Optional[str]:
        if not await self._terminal_guard.try_send('done'):
            return None
        remaining = await self._drain_accumulator()
        self._state_machine.transition('DONE')
        data: dict = {'type': 'done', 'token_used': token_used, 'compressed': compressed}
        if remaining:
            data['partial_content'] = remaining
        return build_sse_event('done', data)

    async def send_error(self, code: str, message: str, retry_after: int = 0) -> Optional[str]:
        if not await self._terminal_guard.try_send('error'):
            return None
        data = {'type': 'error', 'code': code, 'message': message}
        if retry_after:
            data['retry_after'] = retry_after
        data['request_id'] = self.request_id
        self._state_machine.state = 'ERROR'
        return build_sse_event('error', data)

    async def send_abort(self, code: str, partial_content: str = "") -> Optional[str]:
        if not await self._terminal_guard.try_send('abort'):
            return None
        self._state_machine.state = 'ABORT'
        return build_sse_event('abort', {
            'type': 'abort', 'code': code, 'message': '回答已中断',
            'partial_content': partial_content or self._accumulator,
            'request_id': self.request_id,
        })

    async def send_heartbeat(self) -> Optional[str]:
        if self._terminal_guard.sent_event:
            return None
        return build_sse_event('heartbeat', {
            'type': 'heartbeat',
            'timestamp': __import__('datetime').datetime.utcnow().isoformat() + 'Z',
        })

    def _flush_content(self, up_to: Optional[int] = None) -> str:
        if up_to is None:
            up_to = len(self._accumulator)
        segment = self._accumulator[:up_to]
        self._accumulator = self._accumulator[up_to:]
        seq = self._content_seq
        self._content_seq += 1
        return build_sse_event('content', {
            'type': 'content', 'content': segment, 'seq': seq,
        })

    async def _drain_accumulator(self):
        if not self._accumulator:
            return ""
        result = self._accumulator
        self._accumulator = ""
        return result

    async def send_terminal_by_priority(self, events: dict) -> Optional[str]:
        priority_order = ['abort', 'error', 'done']
        for event_type in priority_order:
            if event_type in events:
                sender = getattr(self, f'send_{event_type}')
                result = await sender(**events[event_type])
                if result is not None:
                    return result
        return None


async def heartbeat_wrapper(gen: AsyncGenerator[str, None],
                            gen_obj: SSEResponseGenerator,
                            interval: int = HEARTBEAT_INTERVAL) -> AsyncGenerator[str, None]:
    """心跳包装器 — 在 SSE 事件流中间隔插入心跳事件。纯 asyncio，无 threading.Event。"""
    last_heartbeat = time.monotonic()
    async for event in gen:
        yield event
        now = time.monotonic()
        if now - last_heartbeat >= interval:
            hb = await gen_obj.send_heartbeat()
            if hb:
                yield hb
            last_heartbeat = now
