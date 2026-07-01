# CONAB 巴西玉米周报采集 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**目标:** 实现 CONAB 巴西玉米周报 PDF 自动采集，纳入采集任务管理体系，支持历史回填 + 每周定时增量。

**架构:** Python 采集器抓取 CONAB 页面 HTML → 解析 PDF 链接 → MinIO 去重 → 下载 → 上传 MinIO → 知识库。纯采集器实现，无需后端 Java 变更。

**技术栈:** Python requests + BeautifulSoup + unicodedata, MinIO (boto3 S3 API), MySQL 8, DingTalk alert channel

---

## 文件结构

| 文件 | 操作 | 职责 |
|------|------|------|
| `python-collector-sdk/collectorsdk/collectors/conab.py` | **新建** | ConabCornCollector 采集器（核心实现） |
| `data/collectors/conab.py` | **新建** | 采集器脚本版本副本（软链接或复制到脚本目录） |
| `t_category` | **INSERT** | 知识库分类「CONAB 玉米周报」 |
| `t_data_source` | **INSERT** | 数据源「conab」 |
| `t_collector_script_version` | **INSERT** | 脚本版本注册 |
| `t_collection_script` | **INSERT + UPDATE** | 采集任务创建与定时切换 |

---

## 任务分解

### Task 1: 数据库初始化 — 知识库分类 + 数据源

**文件:** MySQL（grain_platform 库）

- [ ] **Step 1: 新建知识库分类**

```sql
INSERT INTO t_category (name, icon, color, sort_order)
VALUES ('CONAB 玉米周报', '🌽', '#D4A574', 6);
```

知识库分类用于标识 CONAB 玉米报告的所属类别，用户可在知识库页面按分类筛选。

- [ ] **Step 2: 新建数据源**

```sql
INSERT INTO t_data_source (code, name, description, sort_order)
VALUES ('conab', 'CONAB', '巴西国家商品供应公司（Companhia Nacional de Abastecimento）', 7);
```

数据源 `code=conab` 是采集脚本发现、采集任务关联的 key，需与 ConabCornCollector.META['code'] 一致。

---

### Task 2: Python 采集器实现 — ConabCornCollector

**文件:**
- Create: `python-collector-sdk/collectorsdk/collectors/conab.py`
- 脚本版本副本: `data/collectors/conab.py`

- [ ] **Step 1: 创建 conab.py 采集器文件**

```python
"""CONAB 巴西玉米周报采集器

采集巴西国家商品供应公司（CONAB）每周玉米市场分析报告 PDF，
存入 MinIO 并提交到知识库。仅 PDF 格式，无 XML 结构化数据。
"""

import logging
import os
import random
import re
import time
import unicodedata
from datetime import datetime
from typing import Optional
from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup

from collectorsdk.base import BaseCollector
from collectorsdk.dimensions import Source, Subject, CollectType, CollectObject

logger = logging.getLogger(__name__)

# 浏览器 UA 池
USER_AGENTS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Safari/605.1.15",
]


class ConabCornCollector(BaseCollector):
    """CONAB 巴西玉米周报采集器"""

    META = {
        "code": "conab",
        "name": "CONAB Corn Weekly",
        "description": "CONAB 巴西玉米周报采集",
    }

    source = Source.GOV_BR.value
    subject = Subject.CORN.value
    coll_type = CollectType.FILE_DOWNLOAD.value
    coll_object = CollectObject.WEEKLY_REPORT.value

    # CONAB 玉米周报页面 URL 模板
    CONAB_BASE_URL = (
        "https://www.gov.br/conab/pt-br/atuacao/informacoes-agropecuarias/"
        "analises-do-mercado-agropecuario-e-extrativista/analises-de-mercado/"
        "historico-semanal/copy4_of_historico-semanal-do-algodao"
    )
    CONAB_YEAR_URL = CONAB_BASE_URL + "/milho-conjuntura-semanal-{year}"

    MINIO_BUCKET = "reports"
    DOWNLOAD_TIMEOUT = 15
    MAX_RETRIES = 3
    RETRY_BASE_DELAY = 2  # 秒
    DOWNLOAD_INTERVAL = 0.5  # 单文件下载间隔（防止限流）

    def __init__(self, config=None, task_id=None, execution_id=None):
        super().__init__(
            config=config,
            task_id=task_id or 0,
            execution_id=execution_id,
            source=self.source,
            subject=self.subject,
            coll_type=self.coll_type,
            obj=self.coll_object,
            remark='CONAB 巴西玉米周报采集',
        )
        self._minio_client = None
        # 从 config 注入后端 API 地址
        raw = self.config.api_base.rstrip('/')
        self._api_base = raw + '/api' if not raw.endswith('/api') else raw

    def _get_minio_client(self):
        """延迟初始化 MinIO 客户端"""
        if self._minio_client is None:
            from collectorsdk.minio_client import MinioClient
            self._minio_client = MinioClient(bucket=self.MINIO_BUCKET)
        return self._minio_client

    @staticmethod
    def _random_ua() -> str:
        return random.choice(USER_AGENTS)

    @staticmethod
    def _extract_date_range(filename: str) -> Optional[str]:
        """从 PDF 文件名中提取日期范围

        CONAB 文件名格式: "02-milho-conjuntura-semanal-05-01-a-09-01-2026.pdf"
        返回: "2026/01/05-01/09"
        """
        pattern = r'(\d{2})-(\d{2})-a-(\d{2})-(\d{2})-(\d{4})'
        match = re.search(pattern, filename)
        if match:
            start_day, start_mon, end_day, end_mon, year = match.groups()
            return f"{year}/{start_mon}/{start_day}-{end_mon}/{end_day}"
        return None

    @staticmethod
    def _sanitize_filename(filename: str) -> str:
        """文件名安全转义：保留字母数字、连字符、下划线、点号"""
        name = unicodedata.normalize('NFKD', filename)
        name = name.encode('ascii', 'ignore').decode('ascii')
        name = name.replace(' ', '-')
        name = re.sub(r'[^a-zA-Z0-9\-_\.]', '', name)
        return name

    def _download_file(self, url: str, label: str = "") -> Optional[bytes]:
        """下载文件，带重试"""
        for attempt in range(1, self.MAX_RETRIES + 1):
            try:
                resp = requests.get(url, headers={
                    "User-Agent": self._random_ua(),
                    "Accept-Language": "pt-BR,zh-CN;q=0.9",
                }, timeout=self.DOWNLOAD_TIMEOUT)
                resp.encoding = 'ISO-8859-1'
                if resp.status_code == 404:
                    self.log_error(f"[{label}] 文件不存在(404): {url}",
                                   phase="crawl", category="error")
                    return None
                resp.raise_for_status()
                self.log_info(f"[{label}] 下载成功: {url} ({len(resp.content)} bytes)",
                              phase="crawl", category="success")
                return resp.content
            except requests.Timeout:
                delay = self.RETRY_BASE_DELAY * attempt
                self.log_warn(f"[{label}] 下载超时(第{attempt}次, {delay}s后重试): {url}",
                              phase="crawl", category="retry")
                time.sleep(delay)
            except requests.RequestException as e:
                self.log_warn(f"[{label}] 下载异常(第{attempt}次): {url} - {e}",
                              phase="crawl", category="retry")
                time.sleep(self.RETRY_BASE_DELAY)
        self.log_error(f"[{label}] 下载失败(已达最大重试): {url}",
                       phase="crawl", category="error")
        return None

    def collect(self) -> int:
        """主采集入口

        Returns:
            成功采集的报告数量
        """
        self.log_info("CONAB 玉米周报采集器启动", phase="crawl", category="start")

        year = datetime.now().year
        page_url = self.CONAB_YEAR_URL.format(year=year)
        self.log_info(f"目标页面: {page_url}", phase="crawl", category="url")

        # 抓取页面
        try:
            resp = requests.get(page_url, headers={
                "User-Agent": self._random_ua(),
                "Accept-Language": "pt-BR,zh-CN;q=0.9",
            }, timeout=15)
            resp.encoding = 'ISO-8859-1'
            resp.raise_for_status()
        except requests.RequestException as e:
            self.log_error(f"页面抓取失败: {page_url} - {e}",
                           phase="crawl", category="error")
            # 页面整体失败 → 钉钉告警（通过 log_error 触发告警通道）
            return 0

        # 解析 PDF 链接
        soup = BeautifulSoup(resp.text, 'html.parser')
        pdf_links = []
        for a_tag in soup.find_all('a', href=re.compile(r'\.pdf$', re.I)):
            href = a_tag.get('href', '')
            text = a_tag.get_text(strip=True)
            if 'milho' in href.lower() or 'milho' in text.lower():
                pdf_url = urljoin(page_url, href)
                pdf_links.append((text, pdf_url))

        if not pdf_links:
            self.log_warn(f"页面未解析到 PDF 链接: {page_url}",
                          phase="crawl", category="parse")
            return 0

        self.log_info(f"解析到 {len(pdf_links)} 份 PDF", phase="crawl", category="parse")

        # 逐个采集
        mc = self._get_minio_client()
        new_count = 0
        skip_count = 0
        fail_count = 0

        for text, pdf_url in pdf_links:
            filename = pdf_url.rstrip('/').split('/')[-1]
            safe_name = self._sanitize_filename(filename)
            minio_path = f"conab/{year}/{safe_name}"

            # MinIO 去重
            if mc.stat_object(minio_path):
                skip_count += 1
                self.log_info(f"文件已存在，跳过: {minio_path}",
                              phase="crawl", category="skip")
                continue

            # 下载
            time.sleep(self.DOWNLOAD_INTERVAL)
            data = self._download_file(pdf_url, label=safe_name)
            if data is None:
                fail_count += 1
                continue

            # 上传 MinIO
            try:
                mc.put_object(minio_path, data, content_type="application/pdf")
                self.log_info(f"PDF 上传成功: {minio_path}",
                              phase="report", category="minio")
            except Exception as e:
                self.log_error(f"MinIO 上传失败: {minio_path} - {e}",
                               phase="report", category="error")
                fail_count += 1
                continue

            # 提取日期范围用于标题
            date_range = self._extract_date_range(filename)
            if not date_range:
                date_range = text  # 正则失败时使用页面显示文本兜底
            title = f"CONAB 玉米周报 {date_range}"
            content_text = (
                f"CONAB 玉米周报\n"
                f"覆盖日期: {date_range}\n"
                f"MinIO: {self.MINIO_BUCKET}/{minio_path}\n"
                f"原始文件: {filename}"
            )

            # 提交知识库（设定 publish_time 为年份前缀方便排序）
            self.submit_report(
                title=title,
                source="conab",
                url=pdf_url,
                variety="corn",
                report_type="weekly_report",
                content=content_text,
                content_html=f"<p>{content_text.replace(chr(10), '<br>')}</p>",
                publish_time=f"{year}",
            )
            self.log_info(f"知识库提交完成: {title}",
                          phase="report", category="knowledge")
            new_count += 1

        # 统计日志
        summary = (f"CONAB 玉米周报采集完毕: "
                   f"页面={page_url}, "
                   f"新增={new_count}, 跳过={skip_count}, 失败={fail_count}")
        self.log_info(summary, phase="crawl", category="summary")

        # 连续多文件失败告警（通过 log_error 触发钉钉通道）
        if fail_count >= 3:
            self.log_error(
                f"CONAB 玉米周报采集异常: 连续 {fail_count} 份 PDF 下载失败",
                phase="crawl", category="alert"
            )

        return new_count
```

- [ ] **Step 2: 将采集器复制到脚本目录**

```bash
cp python-collector-sdk/collectorsdk/collectors/conab.py data/collectors/conab.py
```

脚本目录 `data/collectors/` 是系统运行时加载采集脚本的路径，与 SDK 内版本同步。

- [ ] **Step 3: 验证采集器可被自动发现**

```bash
cd python-collector-sdk && python -c "
from collectorsdk.collectors import discover_collectors
collectors = discover_collectors()
print('Discovered:', list(collectors.keys()))
assert 'conab' in collectors, 'conab not discovered!'
print('OK: conab collector discovered')
"
```

预期输出: `Discovered: ['...', 'conab', ...]` 且包含 `conab`。

- [ ] **Step 4: 测试页面解析逻辑（快速验证）**

```bash
cd python-collector-sdk && python -c "
import requests
from bs4 import BeautifulSoup
from urllib.parse import urljoin

url = 'https://www.gov.br/conab/pt-br/atuacao/informacoes-agropecuarias/analises-do-mercado-agropecuario-e-extrativista/analises-de-mercado/historico-semanal/copy4_of_historico-semanal-do-algodao/milho-conjuntura-semanal-2026'
resp = requests.get(url, headers={
    'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36',
    'Accept-Language': 'pt-BR,zh-CN;q=0.9',
})
resp.encoding = 'ISO-8859-1'
soup = BeautifulSoup(resp.text, 'html.parser')
links = []
for a in soup.find_all('a', href=lambda h: h and h.endswith('.pdf')):
    text = a.get_text(strip=True)
    href = a.get('href', '')
    if 'milho' in href.lower() or 'milho' in text.lower():
        pdf_url = urljoin(url, href)
        links.append((text, pdf_url))
print(f'Found {len(links)} corn PDF links')
for t, u in links[:3]:
    print(f'  {t[:50]} -> {u.split(\"/\")[-1]}')
"
```

预期输出: `Found 25 corn PDF links` 并列出前 3 个链接。

---

### Task 3: 脚本版本注册 + 采集任务创建

- [ ] **Step 1: 注册采集器脚本版本**

```bash
# 计算 conab.py 的 MD5 和文件大小
MD5=$(md5 -q data/collectors/conab.py)
SIZE=$(stat -f%z data/collectors/conab.py)
echo "MD5=$MD5 SIZE=$SIZE"
```

```sql
INSERT INTO t_collector_script_version (
    datasource_code, version, file_path, file_md5, file_size, is_current, created_by
) VALUES (
    'conab', 1,
    'data/collectors/conab.py',
    '${MD5}', ${SIZE},
    1, 'admin'
);
```

脚本版本注册后，采集任务执行时通过 `datasource_code=conab` 定位到 `is_current=1` 的版本。

- [ ] **Step 2: 创建采集任务（manual 模式，用于历史回填）**

```sql
INSERT INTO t_collection_script (
    script_name, script_path, source, subject, coll_type, coll_object,
    trigger_type, sync_to_knowledge_base, category_id,
    current_version, created_by
)
SELECT
    'conab_corn', 'data/collectors/conab.py', 'conab', 'report', 'file_download', 'weekly_report',
    'manual', 1, id,
    1, 'system'
FROM t_category WHERE name = 'CONAB 玉米周报';
```

采集任务创建后出现在「采集任务管理」列表中，`trigger_type=manual` 表示需手动触发首次回填。

---

### Task 4: 历史回填 — 手动触发 + 验证

- [ ] **Step 1: 手动触发采集**

通过采集任务管理页面或调用后端接口手动触发 `conab_corn` 任务执行。

触发方式：
```bash
# 直接通过 Python SDK 运行
cd python-collector-sdk && python run.py run conab
```

或通过管理端「采集任务」→「手动执行」按钮触发。

- [ ] **Step 2: 验证 MinIO 文件**

```bash
# 检查 MinIO 中 PDF 数量
python -c "
from collectorsdk.minio_client import MinioClient
mc = MinioClient(bucket='reports')
# 列出 conab/2026/ 目录
import boto3
client = boto3.client('s3', endpoint_url='http://localhost:9000',
    aws_access_key_id='admin', aws_secret_access_key='password')
response = client.list_objects_v2(Bucket='reports', Prefix='conab/2026/')
files = [obj['Key'] for obj in response.get('Contents', [])]
print(f'MinIO conab/2026/ files: {len(files)}')
for f in sorted(files):
    print(f'  {f}')
"
```

预期输出: 25 份 PDF 文件。

- [ ] **Step 3: 验证知识库记录**

```sql
SELECT kb.id, kb.title, c.name as category
FROM t_knowledge_base kb
LEFT JOIN t_category c ON kb.category_id = c.id
WHERE kb.source = 'conab'
ORDER BY kb.id
LIMIT 10;
```

预期结果:
- 25 条知识库记录，source=conab
- title 格式: `CONAB 玉米周报 2026/01/05-01/09`
- category: `CONAB 玉米周报`

- [ ] **Step 4: 验证 PDF 在线预览**

```bash
# 随机选取一个 PDF 确认 MinIO URL 可访问
MINIO_URL="http://localhost:9000/reports/conab/2026/02-milho-conjuntura-semanal-05-01-a-09-01-2026.pdf"
curl -sI "$MINIO_URL" | head -5
```

预期: HTTP/200 且 `Content-Type: application/pdf`。

---

### Task 5: 切换定时模式（回填完成后执行）

- [ ] **Step 1: 将采集任务从 manual 改为 cron**

```sql
UPDATE t_collection_script
SET trigger_type = 'cron',
    cron_expression = '0 6 * * 5'  -- 每周五 UTC 06:00
WHERE script_name = 'conab_corn';
```

- [ ] **Step 2: 验证定时配置生效**

```sql
SELECT script_name, trigger_type, cron_expression, status
FROM t_collection_script
WHERE script_name = 'conab_corn';
```

预期:
| script_name | trigger_type | cron_expression | status |
|------------|-------------|----------------|--------|
| conab_corn | cron | 0 6 * * 5 | enabled |

定时任务开启后，系统每分钟轮询 `t_collection_script`，匹配 cron 表达式后自动触发采集。

---

## 执行顺序

```
Task 1 (数据库初始化)
    ↓
Task 2 (Python 采集器)
    ↓
Task 3 (脚本注册 + 采集任务)
    ↓
Task 4 (手工回填 + 验证) ← 回填完成后确认
    ↓
Task 5 (切换定时模式)
```

**关键检查点:** Task 4 验证通过后再执行 Task 5。若 Task 4 采集异常（如页面结构变化导致解析失败），先修复代码再继续。
