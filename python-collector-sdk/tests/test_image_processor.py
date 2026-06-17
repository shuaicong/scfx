"""图片处理器单元测试"""
from collectorsdk.image_processor import (
    _detect_image_type, _sanitize_filename, _is_allowed_domain, _extract_image_urls
)


def test_detect_image_type_jpeg():
    data = b'\xff\xd8\xff\x00\x01\x02'
    assert _detect_image_type(data) == 'image/jpeg'


def test_detect_image_type_png():
    data = b'\x89\x50\x4e\x47\x0d\x0a\x1a\x0a'
    assert _detect_image_type(data) == 'image/png'


def test_detect_image_type_webp():
    data = b'RIFF\x00\x00\x00\x00WEBP\x00'
    assert _detect_image_type(data) == 'image/webp'


def test_detect_image_type_unknown():
    assert _detect_image_type(b'\x00\x01\x02') is None


def test_sanitize_filename():
    assert _sanitize_filename("hello#world&test.png") == "hello_world_test.png"
    assert _sanitize_filename("中文名.jpg") == "___.jpg"
    assert _sanitize_filename("simple-file_v2.0.jpg") == "simple-file_v2.0.jpg"


def test_is_allowed_domain():
    assert _is_allowed_domain("https://cms.jinnong.cn/attached/image/1.png") is True
    assert _is_allowed_domain("https://other.com/image.png") is False


def test_extract_image_urls():
    html = '''
    <img src="https://cms.jinnong.cn/a.png">
    <img src='https://cms.jinnong.cn/b.jpg'>
    <img src=https://cms.jinnong.cn/c.webp>
    <img src="https://other.com/ad.png">
    <img data-src="https://cms.jinnong.cn/lazy.png">
    '''
    urls = _extract_image_urls(html)
    assert "https://cms.jinnong.cn/a.png" in urls
    assert "https://cms.jinnong.cn/b.jpg" in urls
    assert "https://cms.jinnong.cn/c.webp" in urls
    assert "https://other.com/ad.png" not in urls
    assert "https://cms.jinnong.cn/lazy.png" not in urls


def test_extract_image_urls_dedup():
    html = '''
    <img src="https://cms.jinnong.cn/a.png">
    <img src="https://cms.jinnong.cn/a.png">
    '''
    urls = _extract_image_urls(html)
    assert len(urls) == 1


def test_extract_image_urls_empty():
    html = '<p>无图片</p>'
    assert _extract_image_urls(html) == []
