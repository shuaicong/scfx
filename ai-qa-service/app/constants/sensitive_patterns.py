"""敏感信息过滤正则常量模块 — 摘要过滤 + LLM 输出过滤共用"""
import os
import re
import yaml
import logging

logger = logging.getLogger(__name__)

# 编译正则，全局复用
PHONE_PATTERN = re.compile(r'1[3-9]\d{9}|1[3-9]\d{2}[\s-]\d{4}[\s-]\d{4}')
ID_CARD_PATTERN = re.compile(r'\d{17}[\dXx]')
BANK_CARD_PATTERN = re.compile(r'\d{16,19}')
EMAIL_PATTERN = re.compile(r'[\w.]+@[\w.]+')
LANDLINE_PATTERN = re.compile(r'0\d{2,3}[-]?\d{7,8}')
QQ_PATTERN = re.compile(r'(?<!\d)[1-9]\d{4,10}(?!\d)')

# 注入检测正则（与 §3.1.2 一致）
INJECTION_PATTERNS = [
    re.compile(r'忽略.*指令'),
    re.compile(r'无视.*规则'),
    re.compile(r'作为.*角色'),
    re.compile(r'输出.*Prompt'),
    re.compile(r'泄露.*指令'),
    re.compile(r'你是.*模型'),
    re.compile(r'充当.*'),
    re.compile(r'system.*ignore', re.IGNORECASE),
    re.compile(r'role.*play', re.IGNORECASE),
]


def desensitize(text: str) -> str:
    """统一脱敏函数 — 所有日志输出前调用"""
    text = PHONE_PATTERN.sub('[手机号]', text)
    text = ID_CARD_PATTERN.sub('[身份证号]', text)
    text = BANK_CARD_PATTERN.sub('[账号]', text)
    text = EMAIL_PATTERN.sub('[邮箱]', text)
    text = LANDLINE_PATTERN.sub('[电话]', text)
    text = QQ_PATTERN.sub('[QQ号]', text)
    return text


_INJECTION_WHITELIST_PATH = os.path.join(
    os.path.dirname(__file__), "..", "config", "injection_whitelist.yaml"
)
_DEFAULT_WHITELIST = {"作为参考", "相当于", "输出格式", "按照规则"}


def _load_injection_whitelist() -> set[str]:
    """从 YAML 加载注入过滤白名单，含自校验"""
    try:
        with open(_INJECTION_WHITELIST_PATH, encoding="utf-8") as f:
            config = yaml.safe_load(f)
        raw = set(config.get("whitelist", []))
    except Exception:
        logger.warning("[AI_QA] [WARN] [whitelist_load_failed] fallback=default")
        return _DEFAULT_WHITELIST

    valid: set[str] = set()
    rejected_count = 0
    for entry in raw:
        if not isinstance(entry, str):
            rejected_count += 1
            continue
        matched = [p.pattern for p in INJECTION_PATTERNS if p.search(entry)]
        if matched:
            rejected_count += 1
            logger.error(
                "[AI_QA] [ERROR] [whitelist_self_check_failed] entry=%s "
                "match_pattern=%s action=rejected",
                entry, matched[0],
            )
        else:
            valid.add(entry)

    if raw and rejected_count / len(raw) > 0.5:
        logger.error(
            "[AI_QA] [ALERT] [whitelist_suspected_tamper] "
            "rejected_ratio=%.2f action=fallback_to_default",
            rejected_count / len(raw),
        )
        return _DEFAULT_WHITELIST

    return valid or _DEFAULT_WHITELIST


def check_injection(text: str) -> list[str]:
    """检测文本是否命中注入正则，返回命中的模式列表"""
    whitelist = _load_injection_whitelist()
    for wl in whitelist:
        if wl in text:
            return []
    return [p.pattern for p in INJECTION_PATTERNS if p.search(text)]


class SensitiveDataFilter(logging.Filter):
    """日志过滤器 — 自动对所有日志消息执行脱敏"""
    def filter(self, record: logging.LogRecord) -> bool:
        if isinstance(record.msg, str):
            record.msg = desensitize(record.msg)
        if record.args:
            cleaned = []
            for arg in record.args:
                if isinstance(arg, str):
                    cleaned.append(desensitize(arg))
                else:
                    cleaned.append(arg)
            record.args = tuple(cleaned)
        return True
