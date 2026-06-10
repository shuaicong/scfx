"""SSE 管理器单元测试"""
import pytest
from app.services.sse_manager import (
    SSEStateMachine, SSEStateError,
    TerminalEventGuard, SSEResponseGenerator,
)


class TestSSEStateMachine:
    def test_normal_transitions(self):
        sm = SSEStateMachine()
        sm.transition('THOUGHT')
        assert sm.state == 'THOUGHT'
        sm.transition('SOURCE')
        assert sm.state == 'SOURCE'
        sm.transition('CONTENT')
        assert sm.state == 'CONTENT'
        sm.transition('DONE')
        assert sm.state == 'DONE'

    def test_invalid_transition_raises(self):
        sm = SSEStateMachine()
        sm.transition('CONTENT')
        sm.transition('DONE')
        with pytest.raises(SSEStateError):
            sm.transition('CONTENT')

    def test_source_after_content_forbidden(self):
        sm = SSEStateMachine()
        sm.transition('THOUGHT')
        sm.transition('CONTENT')
        with pytest.raises(SSEStateError):
            sm.transition('SOURCE')


class TestTerminalEventGuard:
    @pytest.mark.asyncio
    async def test_first_event_wins(self):
        guard = TerminalEventGuard()
        assert await guard.try_send('done') is True
        assert await guard.try_send('done') is False

    @pytest.mark.asyncio
    async def test_abort_overrides_error(self):
        guard = TerminalEventGuard()
        assert await guard.try_send('error') is True
        assert await guard.try_send('abort') is True
        assert guard.sent_event == 'abort'

    @pytest.mark.asyncio
    async def test_lower_priority_rejected_after_higher(self):
        guard = TerminalEventGuard()
        assert await guard.try_send('abort') is True
        assert await guard.try_send('error') is False
        assert await guard.try_send('done') is False


class TestSSEResponseGenerator:
    @pytest.mark.asyncio
    async def test_normal_flow(self):
        gen = SSEResponseGenerator("req1", "sess1")
        thought = await gen.send_thought("检索中")
        assert 'event: thought' in thought
        source = await gen.send_source([])
        assert 'event: source' in source
        done = await gen.send_done(100)
        assert done is not None
        assert 'event: done' in done

    @pytest.mark.asyncio
    async def test_abort_overrides_error(self):
        gen = SSEResponseGenerator("req1", "sess1")
        abort_result = await gen.send_abort(code='TIMEOUT')
        error_result = await gen.send_error(code='LLM_FAILED', message='err')
        assert abort_result is not None
        assert error_result is None

    @pytest.mark.asyncio
    async def test_content_accumulation(self):
        gen = SSEResponseGenerator("req1", "sess1")
        r1 = await gen.send_content("你好")
        assert r1 is None
        r2 = await gen.send_content("。根据检索到的数据，山东玉米今天价格是1.12元每斤。")
        assert r2 is not None
        assert 'event: content' in r2


class TestHeartbeat:
    @pytest.mark.asyncio
    async def test_heartbeat_before_done(self):
        gen = SSEResponseGenerator("r1", "s1")
        hb = await gen.send_heartbeat()
        assert hb is not None
        assert "heartbeat" in hb
        assert "timestamp" in hb

    @pytest.mark.asyncio
    async def test_heartbeat_after_abort_returns_none(self):
        gen = SSEResponseGenerator("r1", "s1")
        await gen.send_abort("test")
        hb = await gen.send_heartbeat()
        assert hb is None

    @pytest.mark.asyncio
    async def test_heartbeat_no_state_transition(self):
        gen = SSEResponseGenerator("r1", "s1")
        pre = gen._state_machine.current_state
        await gen.send_heartbeat()
        assert gen._state_machine.current_state == pre
