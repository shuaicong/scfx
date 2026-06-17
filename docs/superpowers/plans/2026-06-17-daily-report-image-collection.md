# 粮信网日报图片采集实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 采集粮信网日报时，自动下载文章中的图片到 MinIO，替换 HTML 中的图片 URL，支持前端展示和知识库删除时的图片清理。

**Architecture:** Python 采集器端利用 Playwright 浏览器上下文下载图片（复用登录 cookie），通过 boto3 上传到 MinIO，替换 `contentHtml` 中的 URL 后上报。Java 后端新增图片明细表 `t_knowledge_image`，知识库删除时同步清理 MinIO 图片。

**Tech Stack:** Python (Playwright, boto3, python-magic), Java (Spring Boot, MyBatis-Plus, MinIO SDK), MinIO S3-compatible storage

---

### Task 1: Python MinIO 客户端模块

**Files:**
- Create: `python-collector-sdk/collectorsdk/minio_client.py`
- Modify: `python-collector-sdk/collectorsdk/__init__.py`（导出 minio_client）

- [ ] **Step 1: 创建 minio_client.py**

```python
"""MinIO 对象存储客户端封装"""
import json
import logging
from typing import Optional
from botocore.exceptions import ClientError
from mypy_boto3_s3 import S3Client
import boto3
from botocore.config import Config

logger = logging.getLogger(__name__)

# 最小权限公开读策略
PUBLIC_READ_POLICY = json.dumps({
    "Version": "2012-10-17",
    "Statement": [{
        "Effect": "Allow",
        "Principal": "*",
        "Action": "s3:GetObject",
        "Resource": "arn:aws:s3:::{bucket}/*"
    }]
})

# CORS 配置
CORS_CONFIG = {
    "CORSRules": [{
        "AllowedOrigins": ["http://localhost:3000", "http://localhost:9528"],
        "AllowedMethods": ["GET"],
        "AllowedHeaders": ["*"]
    }]
}


class MinioClient:
    """MinIO 操作客户端"""

    def __init__(
        self,
        endpoint: str = "http://localhost:9000",
        access_key: str = "admin",
        secret_key: str = "password",
        bucket: str = "knowledge-img",
        region: str = "us-east-1",
    ):
        self.endpoint = endpoint
        self.bucket = bucket
        self.region = region

        self._client: S3Client = boto3.client(
            "s3",
            endpoint_url=endpoint,
            aws_access_key_id=access_key,
            aws_secret_access_key=secret_key,
            region_name=region,
            config=Config(signature_version="s3v4"),
        )
        self._ensure_bucket()

    def _ensure_bucket(self):
        """桶不存在则自动创建并配置策略"""
        try:
            self._client.head_bucket(Bucket=self.bucket)
            logger.info("MinIO 桶已存在: %s", self.bucket)
        except ClientError:
            logger.info("MinIO 桶不存在，正在创建: %s", self.bucket)
            self._client.create_bucket(Bucket=self.bucket)

            # 设置公开读策略
            policy = PUBLIC_READ_POLICY.replace("{bucket}", self.bucket)
            self._client.put_bucket_policy(Bucket=self.bucket, Policy=policy)
            logger.info("MinIO 桶公开读策略已设置: %s", self.bucket)

            # 设置 CORS
            self._client.put_bucket_cors(
                Bucket=self.bucket, CORSConfiguration=CORS_CONFIG
            )
            logger.info("MinIO 桶 CORS 已配置: %s", self.bucket)

    def upload_fileobj(
        self,
        data: bytes,
        object_path: str,
        metadata: Optional[dict] = None,
        content_type: str = "image/png",
    ) -> str:
        """上传二进制数据到 MinIO，返回可公开访问的 URL"""
        extra_args = {
            "ContentType": content_type,
        }
        if metadata:
            extra_args["Metadata"] = metadata

        self._client.put_object(
            Bucket=self.bucket,
            Key=object_path,
            Body=data,
            **extra_args,
        )

        # 返回公开可访问的 URL
        return f"{self.endpoint}/{self.bucket}/{object_path}"

    def delete_object(self, object_path: str):
        """删除单个对象"""
        self._client.delete_object(Bucket=self.bucket, Key=object_path)

    def delete_objects(self, object_paths: list[str]):
        """批量删除对象"""
        if not object_paths:
            return
        objects = [{"Key": p} for p in object_paths]
        self._client.delete_objects(
            Bucket=self.bucket,
            Delete={"Objects": objects},
        )
        logger.info("MinIO 批量删除 %d 个对象", len(object_paths))
```

- [ ] **Step 2: 更新 `__init__.py` 导出**

在 `python-collector-sdk/collectorsdk/__init__.py` 底部增加：
```python
from collectorsdk.minio_client import MinioClient
```

- [ ] **Step 3: 安装 Python 依赖**

```bash
cd python-collector-sdk && pip install boto3 mypy-boto3-s3 2>&1 | tail -3
```

- [ ] **Step 4: 编写单元测试**

在 `python-collector-sdk/tests/test_minio_client.py`：
```python
"""MinIO 客户端单元测试"""
from collectorsdk.minio_client import MinioClient


def test_minio_client_init():
    """测试初始化不报错（不真正连接）"""
    # 传入一个不存在的 endpoint，确认构造时桶检查异常被捕获
    client = MinioClient(
        endpoint="http://localhost:19000",  # 不存在的端口
        access_key="test",
        secret_key="test",
    )
    assert client.bucket == "knowledge-img"
    assert client.endpoint == "http://localhost:19000"
```

- [ ] **Step 5: 运行测试**

```bash
cd python-collector-sdk && python -m pytest tests/test_minio_client.py -v 2>&1
```

Expected: 1 passed

- [ ] **Step 6: Commit**

```bash
git add python-collector-sdk/collectorsdk/minio_client.py \
       python-collector-sdk/collectorsdk/__init__.py \
       python-collector-sdk/tests/test_minio_client.py
git commit -m "feat: Python MinIO 客户端模块（桶初始化/上传/删除/策略配置）"
```

---

### Task 2: Python 图片下载处理器

**Files:**
- Create: `python-collector-sdk/collectorsdk/image_processor.py`
- Create: `python-collector-sdk/tests/test_image_processor.py`

- [ ] **Step 1: 编写魔数校验工具函数**

```python
"""图片下载、校验、上传处理器"""
import hashlib
import logging
import os
import re
import tempfile
import uuid
from concurrent.futures import ThreadPoolExecutor, as_completed
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

# 全局下载超时（秒）
GLOBAL_TIMEOUT_SECONDS = 120

# 最大并发数
MAX_WORKERS = 5

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
    safe_name = re.sub(r'[^\w.-]', '_', name)
    safe_ext = re.sub(r'[^\w.-]', '_', ext)
    return safe_name + safe_ext


def _is_allowed_domain(url: str) -> bool:
    """检查 URL 是否属于白名单域名"""
    import urllib.parse
    parsed = urllib.parse.urlparse(url)
    hostname = parsed.hostname or ''
    return any(hostname.endswith(d) for d in ALLOWED_DOMAINS)


def _extract_image_urls(html: str) -> list[str]:
    """从 HTML 中提取所有白名单图片 URL（去重）"""
    pattern = re.compile(r'<img[^>]+src\s*=\s*[\'"]?([^\s\'">]+)[\'"]?', re.IGNORECASE)
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
```

- [ ] **Step 2: 编写图片处理主函数**

```python
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

    # 并发下载
    url_to_body: dict[str, Optional[bytes]] = {}

    with ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
        future_map = {
            executor.submit(_download_image, page, url): url
            for url in source_urls
        }
        for future in as_completed(future_map, timeout=GLOBAL_TIMEOUT_SECONDS):
            url = future_map[future]
            try:
                body = future.result()
                url_to_body[url] = body
            except Exception as e:
                url_to_body[url] = None
                result.failed += 1
                result.failures.append({"url": url, "reason": f"download_exception:{str(e)[:50]}"})

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
```

需要在文件头部加上 `import json`。

- [ ] **Step 3: 编写单元测试**

```python
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
    assert _sanitize_filename("中文名.jpg") == "_.jpg"
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
    assert "https://other.com/ad.png" not in urls  # 非白名单
    assert "https://cms.jinnong.cn/lazy.png" not in urls  # data-src 不匹配


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
```

- [ ] **Step 4: 运行测试**

```bash
cd python-collector-sdk && python -m pytest tests/test_image_processor.py -v 2>&1
```

Expected: 7+ passed

- [ ] **Step 5: Commit**

```bash
git add python-collector-sdk/collectorsdk/image_processor.py \
       python-collector-sdk/tests/test_image_processor.py
git commit -m "feat: Python 图片下载处理器（提取/下载/魔数校验/上传/URL替换）"
```

---

### Task 3: 集成到 liangxin 采集器

**Files:**
- Modify: `python-collector-sdk/collectorsdk/collectors/liangxin.py`
- Modify: `python-collector-sdk/collectorsdk/reporter.py`

- [ ] **Step 1: 在 liangxin.py 的 `collect()` 方法中集成图片处理**

找到 `liangxin.py` 中 `_get_report_content` 返回 HTML 后的位置，或者 `collect()` 方法中获取文章内容的地方，添加图片处理逻辑。

定位 `_get_report_content` 方法（约 249 行），在返回 `text` 和 `html` 之前增加图片处理：

```python
# 处理文章中的图片（替换为 MinIO 本地地址）
try:
    from collectorsdk.image_processor import process_images
    from collectorsdk.minio_client import MinioClient

    minio_client = MinioClient()
    processed_html, img_result = process_images(
        html=content_html,
        page=self._page,
        minio_client=minio_client,
        knowledge_id=None,  # 上报后才知道 knowledge_id, 由 reporter 赋值
        execution_id=self.get_execution_id(),
        collect_date=self.target_date or datetime.now().strftime("%Y-%m-%d"),
    )
    if img_result.success > 0:
        content_html = processed_html
        self._image_count = img_result.success
    else:
        self._image_count = 0
except Exception as e:
    logger.warning("图片处理失败（已降级）: %s", str(e))
    self._image_count = 0
```

在 `__init__` 中增加 `self._image_count = 0`。

- [ ] **Step 2: 修改 reporter.py 上报 imageCount**

在 `reporter.py` 的 `submit_data` 方法中，找到上报字段的位置（约 460 行），增加 `imageCount`：

```python
data = {
    "executionId": execution_id,
    "title": title,
    "originalUrl": original_url,
    "content": content,
    "contentHtml": content_html,
    "imageCount": getattr(collector, '_image_count', 0) if collector else 0,
    # ... 其他现有字段
}
```

- [ ] **Step 3: 在 liangxin_daily.py 传递 target_date**

确认 `LiangxinDailyCollector.__init__` 已有 `target_date` 参数（之前已修复），确保 `collect()` 中取 `self.target_date` 传给图片处理的 `collect_date` 参数。

- [ ] **Step 4: 运行现有测试，确认无回归**

```bash
cd python-collector-sdk && python -m pytest tests/ -v 2>&1 | tail -15
```

Expected: 通过，无新增失败

- [ ] **Step 5: Commit**

```bash
git add python-collector-sdk/collectorsdk/collectors/liangxin.py \
       python-collector-sdk/collectorsdk/reporter.py
git commit -m "feat: liangxin 采集器集成图片处理流程，上报 imageCount"
```

---

### Task 4: Java 后端 - 图片明细表 + Flyway 迁移

**Files:**
- Create: `backend/src/main/resources/db/migration/V9__create_knowledge_image_table.sql`
- Create: `backend/src/main/java/com/scfx/entity/KnowledgeImage.java`
- Create: `backend/src/main/java/com/scfx/mapper/KnowledgeImageMapper.java`
- Create: `backend/src/main/resources/mapper/KnowledgeImageMapper.xml`

- [ ] **Step 1: Flyway 迁移脚本 V9**

```sql
-- V9__create_knowledge_image_table.sql
CREATE TABLE IF NOT EXISTS `t_knowledge_image` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `knowledge_id` bigint NOT NULL COMMENT '关联知识库ID',
  `source_url` varchar(2048) NOT NULL COMMENT '原始图片URL',
  `minio_path` varchar(512) NOT NULL COMMENT 'MinIO对象路径（相对桶路径）',
  `minio_bucket` varchar(128) NOT NULL DEFAULT 'knowledge-img' COMMENT 'MinIO桶名称',
  `file_size` int NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
  `file_type` varchar(32) NOT NULL DEFAULT '' COMMENT '文件类型 image/jpeg',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_knowledge_id` (`knowledge_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库图片明细';
```

- [ ] **Step 2: 实体类 KnowledgeImage.java**

```java
package com.scfx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_knowledge_image")
public class KnowledgeImage {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long knowledgeId;

    private String sourceUrl;

    private String minioPath;

    private String minioBucket;

    private Integer fileSize;

    private String fileType;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
```

- [ ] **Step 3: Mapper 接口 KnowledgeImageMapper.java**

```java
package com.scfx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scfx.entity.KnowledgeImage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface KnowledgeImageMapper extends BaseMapper<KnowledgeImage> {
    List<KnowledgeImage> findByKnowledgeId(@Param("knowledgeId") Long knowledgeId);
    void deleteByKnowledgeId(@Param("knowledgeId") Long knowledgeId);
}
```

- [ ] **Step 4: Mapper XML KnowledgeImageMapper.xml**

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
    "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.scfx.mapper.KnowledgeImageMapper">
    <select id="findByKnowledgeId" resultType="com.scfx.entity.KnowledgeImage">
        SELECT * FROM t_knowledge_image WHERE knowledge_id = #{knowledgeId}
    </select>
    <delete id="deleteByKnowledgeId">
        DELETE FROM t_knowledge_image WHERE knowledge_id = #{knowledgeId}
    </delete>
</mapper>
```

- [ ] **Step 5: 编译验证**

```bash
cd backend && mvn compile -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/db/migration/V9__create_knowledge_image_table.sql \
       backend/src/main/java/com/scfx/entity/KnowledgeImage.java \
       backend/src/main/java/com/scfx/mapper/KnowledgeImageMapper.java \
       backend/src/main/resources/mapper/KnowledgeImageMapper.xml
git commit -m "feat: 知识库图片明细表 t_knowledge_image + Flyway V9"
```

---

### Task 5: Java 后端 - 知识库删除时同步清理 MinIO 图片

**Files:**
- Modify: `backend/src/main/java/com/scfx/service/impl/KnowledgeBaseServiceImpl.java`（或类似的文件，需要先确认删除逻辑的位置）
- Create: `backend/src/main/java/com/scfx/service/ImageService.java`（可选，如果不想膨胀 KnowledgeBaseService）
- Create: `backend/src/main/java/com/scfx/service/impl/ImageServiceImpl.java`

- [ ] **Step 1: 创建 ImageService.java**

```java
package com.scfx.service;

import com.scfx.entity.KnowledgeImage;
import java.util.List;

public interface ImageService {
    /** 保存知识库图片记录 */
    void saveImages(Long knowledgeId, List<KnowledgeImage> images);
    /** 删除知识库关联的所有图片（数据库记录 + MinIO 文件） */
    void deleteByKnowledgeId(Long knowledgeId);
}
```

- [ ] **Step 2: 创建 ImageServiceImpl.java（使用 MinIO Java SDK）**

首先添加 MinIO SDK 依赖到 `pom.xml`:

```xml
<dependency>
    <groupId>io.minio</groupId>
    <artifactId>minio</artifactId>
    <version>8.5.10</version>
</dependency>
```

```java
package com.scfx.service.impl;

import com.scfx.entity.KnowledgeImage;
import com.scfx.mapper.KnowledgeImageMapper;
import com.scfx.service.ImageService;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.errors.MinioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageServiceImpl implements ImageService {

    private final KnowledgeImageMapper knowledgeImageMapper;

    @Value("${minio.endpoint:http://localhost:9000}")
    private String minioEndpoint;

    @Value("${minio.access-key:admin}")
    private String minioAccessKey;

    @Value("${minio.secret-key:password}")
    private String minioSecretKey;

    private MinioClient getMinioClient() {
        return MinioClient.builder()
            .endpoint(minioEndpoint)
            .credentials(minioAccessKey, minioSecretKey)
            .build();
    }

    @Override
    @Transactional
    public void saveImages(Long knowledgeId, List<KnowledgeImage> images) {
        if (images == null || images.isEmpty()) return;
        for (KnowledgeImage img : images) {
            img.setKnowledgeId(knowledgeId);
            knowledgeImageMapper.insert(img);
        }
    }

    @Override
    @Transactional
    public void deleteByKnowledgeId(Long knowledgeId) {
        // 1. 查数据库记录
        List<KnowledgeImage> images = knowledgeImageMapper.findByKnowledgeId(knowledgeId);
        if (images.isEmpty()) return;

        // 2. 删除 MinIO 对象
        MinioClient client = getMinioClient();
        for (KnowledgeImage img : images) {
            try {
                client.removeObject(RemoveObjectArgs.builder()
                    .bucket(img.getMinioBucket())
                    .object(img.getMinioPath())
                    .build());
            } catch (Exception e) {
                log.warn("MinIO 删除图片失败: bucket={}, path={}, err={}",
                    img.getMinioBucket(), img.getMinioPath(), e.getMessage());
            }
        }

        // 3. 删除数据库记录
        knowledgeImageMapper.deleteByKnowledgeId(knowledgeId);

        log.info("知识库图片清理完成: knowledgeId={}, count={}", knowledgeId, images.size());
    }
}
```

- [ ] **Step 3: 注入 reporter 保存图片记录**

在 `CollectorController.java`（或其他接收 `submitData` 的 Controller）中，确认 `imageCount` 字段的接收，并调用 `ImageService.saveImages()` 保存图片明细。

**确定图片明细的收集时机：** 图片明细的 `minio_path`、`source_url` 等字段需要在 Python 采集器上报时告知 Java 后端。

有两种方案实现图片明细入库：
- 方案 A：Python 端在 `contentHtml` 替换的同时，额外上报一个 `imageList` JSON 字段，Java 端解析后调用 `ImageService.saveImages()`
- 方案 B：Python 端不上报明细，Java 端从 `contentHtml` 中再次解析 MinIO URL

**推荐方案 A**，因为 Python 端已有完整的 `url_mapping` 信息。

在 `reporter.py` 的 `submit_data` 中增加：

```python
# 图片明细列表（用于 Java 端落库 t_knowledge_image）
image_list = []
for source_url, minio_url in getattr(collector, '_url_mapping', {}).items():
    import urllib.parse
    parsed = urllib.parse.urlparse(minio_url)
    # 从 MinIO URL 解析出 bucket 和 object_path
    # URL 格式: http://localhost:9000/knowledge-img/liangxin/20260616/xxx.png
    path_parts = parsed.path.lstrip('/').split('/', 1)
    bucket = path_parts[0] if len(path_parts) > 0 else 'knowledge-img'
    object_path = path_parts[1] if len(path_parts) > 1 else ''
    image_list.append({
        "sourceUrl": source_url,
        "minioPath": object_path,
        "minioBucket": bucket,
    })
```

在 `submit_data` 的数据字典中增加：
```python
"imageList": json.dumps(image_list) if image_list else None,
```

**Java Controller 端** 接收到 `imageList` JSON 字段后，解析并调用 `ImageService.saveImages()`：

```java
// 在 submitData 中，接收 knowledgeId 后
String imageListJson = (String) data.get("imageList");
if (imageListJson != null && !imageListJson.isEmpty()) {
    try {
        List<KnowledgeImage> images = objectMapper.readValue(imageListJson,
            new TypeReference<List<KnowledgeImage>>() {});
        imageService.saveImages(knowledgeId, images);
    } catch (Exception e) {
        log.warn("保存图片明细失败: knowledgeId={}", knowledgeId, e);
    }
}
```

- [ ] **Step 4: 在知识库删除逻辑中集成图片清理**

找到知识库删除的方法（按 `knowledge_id` 删除），在其执行逻辑中增加：

```java
// 先清理关联图片（MinIO + 数据库记录）
imageService.deleteByKnowledgeId(knowledgeId);

// 再执行知识库本身的删除
knowledgeBaseMapper.deleteById(knowledgeId);
```

- [ ] **Step 5: 编译验证**

```bash
cd backend && mvn compile -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS

- [ ] **Step 6: 运行后端测试**

```bash
cd backend && mvn test -q 2>&1 | tail -10
```

Expected: 无回归

- [ ] **Step 7: 提交**

```bash
git add backend/pom.xml \
       backend/src/main/java/com/scfx/service/ImageService.java \
       backend/src/main/java/com/scfx/service/impl/ImageServiceImpl.java \
       backend/src/main/java/com/scfx/controller/CollectorController.java \  # 或其他实际修改的 Controller
       backend/src/main/resources/application.yml  # 如果新增了 minio 配置
git commit -m "feat: 知识库删除时同步清理 MinIO 图片 + ImageService"
```

---

## 自检清单

- [ ] **设计覆盖度**：每个设计章节都有对应的 Task 实现（图片提取→Task2/3, MinIO 存储→Task1, 上报→Task3/5, 图片明细/删除→Task4/5）
- [ ] **占位符检查**：无 TBD/TODO，所有代码块有完整实现
- [ ] **类型一致性**：`_image_count`、`imageCount`、`image_count` 字段名在各层保持一致
