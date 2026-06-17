"""图片下载、校验、上传处理器"""
import json
import logging
import os
import re
import uuid
from typing import Optional
from playwright.sync_api import Page

from collectorsdk.minio_client import MinioClient

logger = logging.getLogger(__name__)

# 魔数字典 {magic_bytes_hex: mime_type}
MAGIC_SIGNATURES = {
    b'\xff\xd8\xff': 'image/jpeg',
    b'\x89\x50\x4e\x47': 'image/png',
    b'\x52\x49\x46\x46': 'image/webp',  # 还需验证第 8 字节起为 WEBP
}

ALLOWED_MIME_TYPES = {'image/jpeg', 'image/png', 'image/webp'}

# 白名单域名（仅下载这些来源的图片）
ALLOWED_DOMAINS = {'cms.jinnong.cn'}

# 单图下载超时（毫秒）
SINGLE_IMAGE_TIMEOUT_MS = 30_000

# 文件大小上限
IMAGE_MAX_BYTES = 10 * 1024 * 1024

# 存储路径前缀
STORE_PREFIX = "liangxin"


def _detect_image_type(data: bytes) -> Optional[str]:
    """通过二进制魔数检测图片类型"""
    for magic, mime in MAGIC_SIGNATURES.items():
        if data.startswith(magic):
            if mime == 'image/webp':
                # WebP 额外校验：偏移 8 字节为 'WEBP'
                if data[8:12] == b'WEBP':
                    return mime
                continue
            return mime
    return None


def _sanitize_filename(filename: str) -> str:
    """过滤文件名中的非法字符"""
    name, ext = os.path.splitext(filename)
    # 只保留字母数字点短横线下划线
    safe_name = re.sub(r'[^a-zA-Z0-9._-]', '_', name)
    safe_ext = re.sub(r'[^a-zA-Z0-9._-]', '_', ext)
    return safe_name + safe_ext


def _is_allowed_domain(url: str) -> bool:
    """检查 URL 是否属于白名单域名"""
    import urllib.parse
    parsed = urllib.parse.urlparse(url)
    hostname = parsed.hostname or ''
    return any(hostname.endswith(d) for d in ALLOWED_DOMAINS)


def _extract_image_urls(html: str) -> list[str]:
    """从 HTML 中提取所有白名单图片 URL（去重）"""
    pattern = re.compile(r'<img[^>]*?\ssrc\s*=\s*[\'"]?([^\s\'">]+)[\'"]?', re.IGNORECASE)
    urls = []
    seen = set()
    for m in pattern.finditer(html):
        url = m.group(1)
        if url not in seen and _is_allowed_domain(url):
            urls.append(url)
            seen.add(url)
    return urls


def _download_image(page: Page, url: str) -> Optional[bytes]:
    """用 Playwright 页面上下文下载单张图片"""
    try:
        resp = page.context.request.get(url, timeout=SINGLE_IMAGE_TIMEOUT_MS)
        if not resp.ok:
            logger.warning("图片下载失败: url=%s, status=%d", url, resp.status)
            return None

        content_type = resp.headers.get('content-type', '')
        # 初筛 Content-Type
        if content_type.split(';')[0].strip() not in ALLOWED_MIME_TYPES:
            # 有些服务器不返回 content-type，不拦截
            logger.warning("图片 Content-Type 不匹配: url=%s, type=%s", url, content_type)

        body = resp.body()
        if len(body) > IMAGE_MAX_BYTES:
            logger.warning("图片超过大小上限: url=%s, size=%d", url, len(body))
            return None

        return body
    except Exception as e:
        logger.warning("图片下载异常: url=%s, err=%s", url, str(e))
        return None


class ImageProcessResult:
    """一批图片的处理结果"""
    def __init__(self):
        self.total = 0
        self.success = 0
        self.failed = 0
        self.failures: list[dict] = []
        self.url_mapping: dict[str, str] = {}  # source_url -> minio_url


def process_images(
    html: str,
    page: Page,
    minio_client: MinioClient,
    knowledge_id: Optional[int] = None,
    execution_id: Optional[str] = None,
    collect_date: Optional[str] = None,
) -> tuple[str, ImageProcessResult]:
    """处理 HTML 中的所有图片

    返回值: (替换后的 HTML, ImageProcessResult)
    """
    result = ImageProcessResult()
    source_urls = _extract_image_urls(html)
    result.total = len(source_urls)

    if not source_urls:
        return html, result

    if collect_date is None:
        from datetime import datetime
        collect_date = datetime.now().strftime("%Y%m%d")

    # 顺序下载（必须与 Playwright 同线程，ThreadPoolExecutor 会触发跨线程错误）
    url_to_body: dict[str, Optional[bytes]] = {}

    for url in source_urls:
        body = _download_image(page, url)
        url_to_body[url] = body
        if body is None:
            result.failed += 1
            result.failures.append({"url": url, "reason": "download_failed"})

    # 校验魔数 + 上传 MinIO
    from datetime import datetime
    today_str = datetime.now().strftime("%Y%m%d")

    for source_url, body in url_to_body.items():
        if body is None:
            continue  # 已经在失败中统计过

        # 魔数校验
        image_type = _detect_image_type(body)
        if image_type is None:
            result.failed += 1
            result.failures.append({"url": source_url, "reason": "invalid_magic_bytes"})
            continue

        # 生成存储路径
        parsed = re.search(r'/([^/]+\.\w+)(?:\?|$)', source_url)
        raw_filename = parsed.group(1) if parsed else f"{uuid.uuid4().hex[:8]}.png"
        safe_name = _sanitize_filename(raw_filename)
        object_path = f"{STORE_PREFIX}/{today_str}/{uuid.uuid4().hex[:8]}_{safe_name}"

        # 元数据
        metadata = {
            "source_url": source_url,
            "collect_date": collect_date,
            "script": "liangxin-daily",
        }
        if execution_id:
            metadata["execution_id"] = execution_id

        # 上传
        try:
            minio_url = minio_client.upload_fileobj(
                data=body,
                object_path=object_path,
                metadata=metadata,
                content_type=image_type,
            )
            result.url_mapping[source_url] = minio_url
            result.success += 1
        except Exception as e:
            result.failed += 1
            result.failures.append({"url": source_url, "reason": f"upload_error:{str(e)[:50]}"})

    # 替换 HTML 中的 URL
    replaced_html = html
    for source_url, minio_url in result.url_mapping.items():
        replaced_html = replaced_html.replace(source_url, minio_url)

    # 日志
    logger.info(
        "图片处理完成: total=%d, success=%d, failed=%d, failures=%s, "
        "knowledgeId=%s, executionId=%s",
        result.total, result.success, result.failed,
        json.dumps(result.failures),
        knowledge_id, execution_id,
    )

    return replaced_html, result
