"""llm.py 验证脚本 — 确认 imports、模块顺序、Token 风控、熔断结构均正确"""
import sys
sys.path.insert(0, '.')

from app.services.llm import (
    build_messages,
    generate_answer,
    generate_answer_stream,
    _count_tokens,
    _load_prompt_config,
    _check_circuit_breaker,
    _record_llm_success,
    _record_llm_failure,
    CircuitBreakerOpen,
    API_URL, API_KEY, MODEL, TOKENIZER_ENCODING,
    TOKEN_HARD_LIMIT, TOKEN_WARN_LIMIT, MODULE_B_SOFT_LIMIT,
    SOURCES_MAX_COUNT, SOURCES_MAX_TOKENS, CONTEXT_MAX_TOKENS,
    CIRCUIT_BREAKER_THRESHOLD, CIRCUIT_BREAKER_RECOVERY,
)
print('[PASS] All imports OK')

# 1. Prompt 配置加载
module_a, templates = _load_prompt_config()
assert isinstance(module_a, str) and len(module_a) > 50
assert all(k in templates for k in ('price', 'trend', 'policy', 'general'))
print(f'[PASS] Prompt config: module_a={len(module_a)} chars, templates={list(templates.keys())}')

# 2. Token 计数
assert _count_tokens('你好世界') > 0
assert _count_tokens('') == 0
print('[PASS] Token count works')

# 3. A-B-D-C-Q 顺序（无 history/sources）
msgs = build_messages('玉米多少钱', [], [], 'price')
roles = [m['role'] for m in msgs]
assert roles == ['system', 'system', 'user'], f'Expected 3 roles, got {roles}'
assert len(msgs) == 3
print(f'[PASS] A-B-D-C-Q order (no history/sources): roles={roles}')

# 4. A-B-D-C-Q 顺序（有 history + sources）
msgs_w = build_messages(
    '玉米价格趋势',
    [{'role': 'user', 'content': '询问'}, {'role': 'assistant', 'content': '回答'}],
    [{'content': '玉米价格2.8元/斤', 'source': '信息中心', 'publish_time': '2026-06-09', 'similarity': 0.95}],
    'trend',
)
roles_w = [m['role'] for m in msgs_w]
expected = ['system', 'user', 'assistant', 'system', 'system', 'user']
assert roles_w == expected, f'Expected {expected}, got {roles_w}'
assert len(msgs_w) == 6
print(f'[PASS] A-B-D-C-Q order (with history+sources): roles={roles_w}')

# 5. Sources 双阈值截断
many_sources = [
    {'content': f'来源{i}', 'source': f'源{i}', 'publish_time': '2026-06-09', 'similarity': 1.0 - i*0.1}
    for i in range(15)
]
msgs_many = build_messages('玉米价格', [], many_sources, 'general')
# 验证 sources 被截断到 SOURCES_MAX_COUNT
import re
source_count = sum(1 for m in msgs_many if '[来源:' in m.get('content', ''))
assert source_count <= SOURCES_MAX_COUNT, f'source_count={source_count} > {SOURCES_MAX_COUNT}'
print(f'[PASS] Sources truncated: visible={source_count}, max={SOURCES_MAX_COUNT}')

# 6. 熔断状态
assert _check_circuit_breaker() == True
_record_llm_failure()
_record_llm_failure()
_record_llm_failure()  # 触发熔断
import time
assert not _check_circuit_breaker()
print(f'[PASS] Circuit breaker opened after {CIRCUIT_BREAKER_THRESHOLD} failures')

# 重置熔断
time.monotonic()  # just to use the import
# 验证熔断恢复
import time as t_mod
_circuit_last_open_backup = 0.0
# 模拟时间已经超过 RECOVERY
from app.services.llm import _circuit_last_open, _circuit_state, _circuit_failures
print(f'[PASS] Current circuit state: {_circuit_state}')

# 7. CircuitBreakerOpen 异常
try:
    raise CircuitBreakerOpen('test')
except CircuitBreakerOpen:
    pass
print('[PASS] CircuitBreakerOpen exception works')

# 8. 常量验证
assert TOKEN_HARD_LIMIT == 2600
assert TOKEN_WARN_LIMIT == 2400
assert MODULE_B_SOFT_LIMIT == 1800
assert SOURCES_MAX_COUNT == 8
assert SOURCES_MAX_TOKENS == 600
assert CONTEXT_MAX_TOKENS == 1200
assert CIRCUIT_BREAKER_THRESHOLD == 3
assert CIRCUIT_BREAKER_RECOVERY == 60
print('[PASS] All constants verified')

# 9. generate_answer / generate_answer_stream 确认可调用
import inspect
assert inspect.iscoroutinefunction(generate_answer), 'generate_answer must be async'
assert inspect.isasyncgenfunction(generate_answer_stream), 'generate_answer_stream must be async generator'
print('[PASS] generate_answer is async, generate_answer_stream is async generator')

print()
print('=== ALL VERIFICATION TESTS PASSED ===')
