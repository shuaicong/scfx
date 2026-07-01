# CONAB 巴西玉米周报采集设计

> **版本:** v1.0
> **日期:** 2026-07-01
> **状态:** 设计待评审

---

## 1. 概述

### 1.1 背景

系统已完成 USDA WASDE 月度报告采集（V1），架构已预留 CONAB（巴西国家商品供应公司）扩展能力。CONAB 每周发布的「Conjuntura Semanal」（市场周报）覆盖巴西主要农产品价格、贸易流、库存等短期市场动态，是南半球农产品市场的重要参考数据源。

接入 CONAB 玉米周报可实现：

- **PDF 报告入库知识库**：每周玉米市场分析报告可检索、可 AI 问答
- **补充南半球数据**：与 USDA（全球供需）形成南北半球互补
- **扩展至其他品种**：架构预留，后续可接入大豆、棉花、小麦等品种周报

### 1.2 目标

1. 自动化采集 CONAB 玉米周报（PDF）文件
2. PDF 存入 MinIO 同时写入知识库（复用现有 submit_report 管道）
3. 纳入采集任务管理体系（CollectionScript），支持定时触发与手动回填
4. 不支持结构化数据解析（CONAB 不提供 XML，仅有 PDF）

### 1.3 采集范围

| 维度 | 内容 |
|------|------|
| **数据源** | CONAB 玉米（Milho）周报 — Conjuntura Semanal |
| **文件格式** | PDF 仅（无 XML 结构化数据） |
| **采集频率** | 每周五 UTC 06:00 触发（巴西周三~周四发布上周周报） |
| **增量策略** | 每次抓取页面 HTML，与 MinIO 文件列表比对去重 |
| **历史回填** | 2026 年现有 25 份周报一次性回填 |
| **数据去向** | PDF → MinIO + t_knowledge_base |
| **知识库分类** | 新建 `CONAB 玉米周报` 分类 |

### 1.4 与 WASDE 对比

| 维度 | WASDE | CONAB 玉米周报 |
|------|-------|---------------|
| 地区 | 美国（USDA） | 巴西（CONAB） |
| 频率 | 月度 | 每周 |
| 文件 | PDF + XML | PDF 仅 |
| 结构化数据 | 解析 XML 入 t_wasde_data | 无 |
| 后端变更 | 新增 4 枚举 + 3 Entity + 3 Mapper + Service + Controller | 无需后端变更 |
| MinIO 桶 | reports/wasde/{year}/ | reports/conab/{year}/ |

---

## 2. 技术架构

```
CONAB 玉米周报页面 (gov.br/conab/.../milho-conjuntura-semanal-{year})
                          │
                          ▼
                    ConabCornCollector
                          │
      ┌───────────────────┴─────────────────────┐
      │  ① GET 页面 HTML                        │
      │  ② BeautifulSoup 解析 → 提取所有 PDF 链接 │
      │  ③ 提取文件名中的日期范围作为标题          │
      │                                          │
      │  for each PDF link in page:              │
      │    ├── minio.stat_object(path)           │
      │    │   ├── 已存在 → log skip，continue   │
      │    │   └── 不存在 → 继续                 │
      │    ├── _download_file(url, retry=3)      │
      │    ├── minio.put_object(path, data)      │
      │    └── submit_report(                    │
      │          title="CONAB 玉米周报 YYYY/MM/DD-MM/DD", │
      │          source="conab",                  │
      │          content="...",                   │
      │          category_id=新建分类ID,          │
      │        )                                  │
      └──────────────────────────────────────────┘
                          │
                          ▼
              MinIO: reports/conab/{year}/
              t_knowledge_base（复用现有管道）
```

### 2.1 文件存储结构

```
MinIO bucket "reports/"
└── conab/
    └── 2026/
        ├── 01-milho-conjuntura-semanal-29-12-a-02-01-2026.pdf
        ├── 02-milho-conjuntura-semanal-05-01-a-09-01-2026.pdf
        ├── 03-milho-conjuntura-semanal-12-01-a-16-01-2026.pdf
        ├── ...
        └── 25-milho-conjuntura-semanal-15-06-a-19-06-2026.pdf
```

PDF 保留 CONAB 原始文件名，不做重命名，便于溯源。

### 2.2 数据流向

```
采集器                        后端                          消费端
───────                      ──────                       ──────
ConabCornCollector           (无变更)                     
  PDF → MinIO ───────────→ submit_report() → t_knowledge_base → AI问答/搜索
```

CONAB 采集比 WASDE 简单，原因：
- 仅 PDF 文件，无需 XML 解析流程
- 无需新增数据库表、Mapper、Service、Controller
- 无需 Flyway 迁移
- 完全复用知识库管道

---

## 3. Python 采集器设计

### 3.1 类结构

```python
class ConabCornCollector(BaseCollector):
    META = {
        "code": "conab",
        "name": "CONAB Corn Weekly",
        "description": "CONAB 巴西玉米周报采集",
    }
    source = "conab"
    subject = "report"
    coll_type = "file_download"
    coll_object = "weekly_report"
```

新建文件 `python-collector-sdk/collectorsdk/collectors/conab.py`。

### 3.2 采集主流程

```
collect()
  │
  ├── ① 确定目标年份
  │     当前年份作为目标年（2026→2027 自动过渡）
  │
  ├── ② 抓取页面
  │     url = f"https://www.gov.br/conab/pt-br/atuacao/informacoes-agropecuarias/
  │               analises-do-mercado-agropecuario-e-extrativista/analises-de-mercado/
  │               historico-semanal/copy4_of_historico-semanal-do-algodao/
  │               milho-conjuntura-semanal-{year}"
  │     headers = {"User-Agent": self._random_ua(),
  │                "Accept-Language": "pt-BR,zh-CN;q=0.9"}  # 巴西站点优先 pt-BR
  │     resp = requests.get(url, headers=headers, timeout=15)
  │     resp.encoding = 'ISO-8859-1'  # 巴西政府站点使用 ISO-8859-1，避免葡萄牙语字符乱码
  │
  ├── ③ BeautifulSoup 解析
  │     soup = BeautifulSoup(resp.text, 'html.parser')
  │     # 提取所有 PDF 链接（class 或 a[href$=".pdf"]）
  │     pdf_links = [(title, url), ...]
  │     # 相对路径补全为绝对 URL
  │     pdf_url = urljoin(page_url, href)  # 使用 urllib.parse.urljoin
  │     # 从 title 中提取日期范围，用于知识库标题
  │
  └── ④ for each (title, url, text) in pdf_links:
        │
        ├── 原始文件名 = url 最后一段
        ├── 安全文件名 = _sanitize_filename(原始文件名)
        │   # 移除/替换葡萄牙语重音符号、空格等不安全字符
        │   # 原始文件名保留在知识库 content 中溯源
        ├── minio_path = f"conab/{year}/{安全文件名}"
        │
        ├── stat_object(minio_path)
        │   ├── 已存在 → log skip, new_count不动, skip_count++，continue
        │   └── 不存在 → 下载
        │
        ├── data = _download_file(url)
        │   ├── 3 次重试，阶梯间隔
        │   ├── 404 → 跳过（日志记录）
        │   └── 全部失败 → 标记 failed, fail_count++，continue
        │
        ├── minio.put_object(minio_path, data, content_type="application/pdf")
        │
        ├── 提取日期范围
        │     # 从原始文件名中提取 "05-01-a-09-01-2026"
        │     date_range = _extract_date_range(原始文件名)
        │     if not date_range:
        │         date_range = text  # 正则失败时使用页面显示文本兜底
        │     title = f"CONAB 玉米周报 {date_range}"
        │
        ├── submit_report(
        │     title=title,
        │     source="conab",
        │     url=pdf_url,
        │     content=f"CONAB 玉米周报\n覆盖日期: {date_range}\nMinIO: {minio_path}\n原始文件: {原始文件名}",
        │     content_html=f"<p>CONAB 玉米周报<br>覆盖日期: {date_range}</p>",
        │     publish_time=...,
        │   )
        │
        └── new_count++
        └── time.sleep(0.5)  # 下载间隔 500ms，防止高频请求被巴西政府站点限流
```

### 3.3 HTML 解析策略

CONAB 页面使用 gov.br 统一 CMS，PDF 链接格式示例：

```html
<a href=".../02-milho-conjuntura-semanal-05-01-a-09-01-2026.pdf">
  Milho - Conjuntura Semanal - 05/01/2026
</a>
```

解析策略：
1. 查找所有 `a[href$=".pdf"]` 元素
2. 过滤出包含 `milho-conjuntura-semanal` 的链接
3. 提取每个链接的 `href`（可能为相对路径）和 `text`（显示文本）
4. 使用 `urllib.parse.urljoin` 将相对路径补全为完整绝对 URL

```python
from urllib.parse import urljoin

pdf_url = urljoin(page_url, href)  # 相对 → 绝对
```

5. 从文件名中正则提取日期范围

### 3.4 日期提取

PDF 文件名格式固定：
```
{序号}-milho-conjuntura-semanal-{DD}-{MM}-a-{DD}-{MM}-{YYYY}.pdf
```

正则提取：
```python
pattern = r'(\d{2})-(\d{2})-a-(\d{2})-(\d{2})-(\d{4})'
# 返回: start_day, start_month, end_day, end_month, year
```

### 3.5 下载健壮性

```python
def _download_file(self, url, max_retries=3):
    for attempt in range(1, max_retries + 1):
        try:
            resp = requests.get(url, headers={
                "User-Agent": self._random_ua(),
                "Accept-Language": "pt-BR,zh-CN;q=0.9",
            }, timeout=15)
            resp.encoding = 'ISO-8859-1'  # 巴西站点编码
            if resp.status_code == 404:
                return None  # 不重试
            resp.raise_for_status()
            return resp.content
        except requests.Timeout:
            time.sleep(2 * attempt)  # 2/4/6s
        except requests.RequestException as e:
            time.sleep(2)
    return None
```

### 3.6 文件名安全转义

原始 PDF 文件名可能包含葡萄牙语重音符号（á, ç, ã, ê）、空格等不兼容 MinIO 对象存储的字符。上传前做安全转义：

```python
import re
import unicodedata

def _sanitize_filename(filename: str) -> str:
    """文件名安全转义：保留字母数字、连字符、下划线、点号"""
    # 将重音字符转为 ASCII 等效
    name = unicodedata.normalize('NFKD', filename).encode('ascii', 'ignore').decode('ascii')
    # 空格转连字符
    name = name.replace(' ', '-')
    # 仅保留安全字符
    name = re.sub(r'[^a-zA-Z0-9\-_\.]', '', name)
    return name
```

转义后的文件名用于 MinIO 存储路径，原始文件名保留在 `submit_report` 的 `content` 字段中用于溯源。

### 3.7 异常处理与告警

```python
# 采集完成后输出增量统计日志
self.log_info(
    f"CONAB 玉米周报采集完毕: "
    f"新增={new_count}, 跳过={skip_count}, 失败={fail_count}",
    phase="crawl", category="summary"
)
```

| 场景 | 处理方式 | 告警级别 |
|------|---------|---------|
| 页面抓取失败（HTTP 异常/超时） | 记录异常，返回失败计数 | **钉钉告警**：携带数据源(conab)、年份、异常摘要 |
| 单 PDF 下载失败 | 记日志后 `continue`，不阻断整体 | 日志 ERROR |
| 连续 ≥3 份 PDF 下载失败 | 累计统计，采集结束汇总 | **钉钉告警**：携带失败数量和文件列表 |
| MinIO 上传异常 | 捕获异常，记日志，`continue` | 日志 ERROR |
| submit_report 异常 | 不影响采集流程，仅记日志 | 日志 WARN |

钉钉告警复用系统现有 `AlertService` 通道（与 WASDE 解析失败共用），无需新建告警配置。

---

## 4. 系统配置（4 项前置准备）

### 4.1 知识库分类

```sql
INSERT INTO t_category (name, icon, color, sort_order)
VALUES ('CONAB 玉米周报', '🌽', '#D4A574', 6);
```

| 字段 | 值 |
|------|------|
| name | CONAB 玉米周报 |
| icon | 🌽 |
| color | #D4A574 |
| sort_order | 6（USDA WASDE 之后） |
| parent_id | NULL（顶级分类） |

### 4.2 数据源

```sql
INSERT INTO t_data_source (code, name, description, sort_order)
VALUES ('conab', 'CONAB', '巴西国家商品供应公司', 7);
```

| 字段 | 值 |
|------|------|
| code | conab |
| name | CONAB |
| description | 巴西国家商品供应公司 |
| sort_order | 7 |

### 4.3 采集器脚本

新建 `data/collectors/conab.py`，通过 `POST /datasource/conab/upload-collector` 上传注册：

| 字段 | 值 |
|------|------|
| datasource_code | conab |
| version | 1 |
| is_current | true |

### 4.4 采集任务

**阶段 1 — 历史回填（manual 模式）：**

```sql
INSERT INTO t_collection_script (
    script_name, source, subject, coll_type, coll_object,
    trigger_type, sync_to_knowledge_base, category_id,
    script_path
) VALUES (
    'conab_corn', 'conab', 'report', 'file_download', 'weekly_report',
    'manual', 1, (SELECT id FROM t_category WHERE name = 'CONAB 玉米周报'),
    'data/collectors/conab.py'
);
```

**阶段 2 — 回填完成后改为定时（cron 模式）：**

```sql
UPDATE t_collection_script
SET trigger_type = 'cron',
    cron_expression = '0 6 * * 5'  -- 每周五 UTC 06:00
WHERE script_name = 'conab_corn';
```

---

## 5. 历史回填策略

### 5.1 范围

2026 年现有 25 份周报（覆盖 2025/12/29 ~ 2026/06/19）。

### 5.2 触发方式

- 采集任务配置 `trigger_type=manual`
- 手动触发一次回填
- 采集器自动抓取页面 → 逐个 MinIO 去重 → 下载 → 上传 → 知识库

### 5.3 去重保障

- MinIO 文件前置检查（`stat_object`），已存在的 PDF 跳过下载
- 知识库 `submit_report()` 内置去重逻辑
- 双向防重复

### 5.4 完成后转换

回填完成后，通过管理端将采集任务从 `manual` 更新为 `cron`，后续每周自动触发。

---

## 6. 采集时间配置

### 6.1 时区分析

| 维度 | 数据 |
|------|------|
| CONAB 发布时间 | 巴西时间每周三~周四 |
| BRT 与 UTC 偏移 | UTC-3（巴西利亚时间） |
| 建议采集时间 | 每周五 UTC 06:00 |
| 北京时间对应 | 14:00（周五下午） |

### 6.2 时间窗说明

- CONAB 在巴西时间周三~周四发布上周周报（覆盖周一~周五）
- 周五 UTC 06:00 = BRT 03:00（巴西凌晨），文件已稳定发布 12h+
- 比 WASDE 更宽裕的时间窗，不存在时区截止风险

### 6.3 Cron 表达式

```
0 6 * * 5
```

含义：每周五，UTC 06:00。

---

## 7. 影响范围

| 模块 | 变更内容 | 备注 |
|------|---------|------|
| `python-collector-sdk/collectorsdk/collectors/conab.py` | **新建** | ConabCornCollector 采集器实现 |
| `data/collectors/conab.py` | **新建** | 脚本版本注册（软链接/上传） |
| `t_category` | **INSERT** | 新建「CONAB 玉米周报」分类 |
| `t_data_source` | **INSERT** | 新建 code=conab 数据源 |
| `t_collection_script` | **INSERT + UPDATE** | 新建采集任务，回填后改 cron |
| `t_collector_script_version` | **INSERT** | 注册 conab.py 版本 |
| 后端 Java 代码 | **无需改动** | 无 XML 无新表无新接口 |
| Flyway 迁移 | **无需改动** | 不涉及 DDL |
| MinIO 配置 | **无需改动** | 复用 reports 桶 |

### 7.1 与 WASDE 架构对比

| 组件 | WASDE | CONAB 玉米周报 |
|------|-------|---------------|
| SourceTypeEnum | 新增 WASDE + CONAB（已存在） | 无需变更 |
| 数据库表 | V14 迁移，3 张新表 | 无需新表 |
| Java Entity/Mapper/XML | 6 文件 | 无需变更 |
| ReportParseService | 351 行 | 无需变更 |
| Parser | WasdeXmlParser 434 行 | 无需变更 |
| Controller | 4 个端点 | 无需变更 |
| 知识库管道 | 复用 submit_report | 复用 submit_report |
| Py 采集器 | usda.py 409 行 | conab.py ~300 行 |

---

## 8. 后续规划

| 阶段 | 内容 | 优先级 |
|------|------|--------|
| **V1** | CONAB 玉米周报采集（本期） | P0 |
| **V1.1** | 扩展至 CONAB 大豆/棉花/小麦周报 | P1 |
| **V2** | WASDE 棉花/油料子报告采集 | P2 |
| **V2.1** | 多数据源统一查询 API（供研报占位符） | P2 |

---

## 9. 上线验收要点

### 9.1 历史回填

- 25 份周报一次性完整采集，PDF 全部入库知识库可检索
- 无重复数据
- MinIO 前置去重有效

### 9.2 增量定时

- 每周五 UTC 06:00 准时触发
- 新报告发布后页面更新，采集器自动发现
- 已采集文件跳过，不重复

### 9.3 异常容错

- 单 PDF 下载失败不影响其他文件
- 页面抓取失败有告警
- 失败文件保留在 MinIO，不删除

### 9.4 分类与检索

- 知识库中「CONAB 玉米周报」分类正确
- PDF 可在线预览
- AI 问答可检索到 CONAB 玉米内容

---

## 10. 风险点规避

### 10.1 页面结构变更风险

设计基于 `a[href$=".pdf"]` CSS 选择器 + 文件名关键词过滤。若 CONAB 后续改版 gov.br CMS 页面样式：
- **影响范围**：仅需微调 CSS 选择器（如定位新的 PDF 链接容器 class）
- **业务主流程**：下载、MinIO 上传、知识库提交完全不受影响
- **监控手段**：新增页面上次可解析 PDF 数量缓存，若连续 2 次采集解析到 0 份 PDF，触发告警

### 10.2 文件命名格式变更风险

正则 `(\d{2})-(\d{2})-a-(\d{2})-(\d{2})-(\d{4})` 依赖当前文件名格式：
- **兜底策略**：正则匹配失败时，使用页面上 `<a>` 标签的显示文本（如 "Milho - Conjuntura Semanal - 05/01/2026"）作为知识库标题
- **文件存储**：即使日期解析失败，PDF 仍正常上传 MinIO 和知识库，不中断

### 10.3 流量限流风险

单次回填 25 份文件，短时间高频请求可能被巴西政府站点封禁 IP：
- **下载间隔**：每份 PDF 下载后 `time.sleep(0.5)`，25 份约 12s 完成
- **请求头优化**：携带 `pt-BR` 语言偏好和合理 User-Agent，降低触发 WAF 概率
- **重试策略**：3 次阶梯重试，非 4xx 错误自动恢复
