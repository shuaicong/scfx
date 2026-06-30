# USDA WASDE 报告采集与解析设计

> **版本:** v1.0
> **日期:** 2026-06-30
> **状态:** 设计待评审

---

## 1. 概述

### 1.1 背景

当前系统已接入粮信网（图文报告）、粮达网（价格指数）、中央气象台（农业气象）等多数据源，具备完整的采集 SDK 和异步上报管道。USDA 世界农业供需预测（WASDE）作为全球最具影响力的农产品供需平衡表之一，是机构交易决策和产业分析的核心参考数据。

接入 WASDE 报告可实现：

- **PDF 报告入库知识库**：每月完整报告文档可检索、可 AI 问答
- **结构化供需数据**：产量/出口/进口/期末库存等关键指标落地 `t_wasde_data`，支持研报占位符 `{{WASDE:玉米,全球产量}}`
- **多数据源格局**：USDA + 粮信网 + 粮达网 + 气象 + 未来 CONAB，完善全球农产品数据拼图

### 1.2 目标

1. 自动化采集 WASDE 月度报告的 PDF 和 XML 文件
2. PDF 存入 MinIO 同时写入知识库（复用现有 submit_report 管道 → 切片 → 向量化）
3. XML 经后端解析引擎提取结构化供需数据，写入 `t_wasde_data`
4. 纳入采集任务管理体系（CollectionScript），支持定时触发与手动回填
5. 设计预留 CONAB（巴西）等同类数据源的扩展能力

### 1.3 采集范围

| 维度 | 内容 |
|------|------|
| **数据源** | USDA WASDE 主报告（World Agricultural Supply and Demand Estimates） |
| **文件格式** | PDF（完整报告）+ XML（结构化数据） |
| **采集频率** | 每月 USDA 发布日触发（每月第 8-12 日，美国东部时间 12:00 PM） |
| **增量策略** | 采集器判断当月发布日已过才触发，未到发布日自动跳过 |
| **历史回填** | 2026 年 1-6 月已发布报告一次性回填 |
| **数据去向** | PDF → MinIO + t_knowledge_base；XML 解析 → t_wasde_data |

### 1.4 发布日历参考（2026）

| 月份 | 日期 |
|------|------|
| 1 月 | Jan 12 |
| 2 月 | Feb 10 |
| 3 月 | Mar 10 |
| 4 月 | Apr 9 |
| 5 月 | May 12 |
| 6 月 | Jun 11 |
| 7 月 | Jul 10 |
| 8 月 | Aug 12 |
| 9 月 | Sep 11 |
| 10 月 | Oct 9 |
| 11 月 | Nov 10 |
| 12 月 | Dec 10 |

---

## 2. 技术架构

```
                    USDA WASDE 网站
                          │
               ┌──────────┴──────────┐
               ▼                     ▼
        wasde0626v2.pdf     wasde0626v2.xml
               │                     │
               ▼                     ▼
    ┌──────────────────── UsdaWasdeCollector ────────────────────┐
    │  ① PDF 下载 → 上传 MinIO（reports/wasde/{year}/）          │
    │  ② XML 下载 → 上传 MinIO（同一目录）                       │
    │  ③ 从官网页面提取真实发布日期                                │
    │  ④ Mint IO 文件存在判断（前置去重）                            │
    │  ⑤ submit_report(PDF) → t_knowledge_base                   │
    │  ⑥ POST /api/parse/trigger → 触发后端解析                   │
    └──────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
              ┌──────────────────────┐
              │  ReportParseService   │
              │  ① 幂等校验 + Redis 锁 │
              │  ② MinIO 读取 XML     │
              │  ③ WasdeXmlParser 解析 │
              │  ④ 数据合法性校验      │
              │  ⑤ 批量写入 t_wasde_data│
              │  ⑥ 更新 t_parse_record │
              └──────────────────────┘
                           │
                    ┌──────┴──────┐
                    ▼             ▼
           t_knowledge_base  t_wasde_data
           (已有管道)         (结构化供需)
                                   │
                                   ▼
                         智能研报占位符
                    {{WASDE:玉米,全球产量}}
```

### 2.1 文件存储结构

```
MinIO bucket "reports/" （独立于知识库文件的知识产权桶）
├── wasde/
│   ├── 2026/
│   │   ├── wasde0126v2.pdf
│   │   ├── wasde0126v2.xml
│   │   ├── wasde0226v2.pdf
│   │   ├── wasde0226v2.xml
│   │   ├── ... (1-6 月已发布)
│   │   ├── wasde0626v2.pdf
│   │   └── wasde0626v2.xml
│   └── 2027/
│       └── ...
└── conab/                    （预留）
    └── 2026/
        └── ...
```

### 2.2 数据流向

```
采集器                        后端                          消费端
───────                      ──────                       ──────
PDF → MinIO ──────────────→ submit_report() → t_knowledge_base → AI问答/搜索
XML → MinIO ─→ POST /trigger → WasdeXmlParser → t_wasde_data → 智能研报占位符
                                  ↑
                              (幂等锁/重试/告警)
```

---

## 3. Python 采集器设计

### 3.1 类结构

```python
class UsdaWasdeCollector(BaseCollector):
    source = "usda"
    subject = "report"
    coll_type = "file_download"
    coll_object = "monthly_report"
```

### 3.2 采集主流程

```
collect()
  │
  ├── _determine_report_months()
  │     ├── 从 CollectionScript 配置获取回填起始月份（首次部署：2026-01）
  │     ├── 获取 USDA 当月发布日（_fetch_publish_calendar()）
  │     ├── 判断今天是否已过发布日
  │     │     ├── 已过 → 采集当月报告
  │     │     └── 未到 → 跳过当月，只采历史月份
  │     └── 返回待采集月份列表 [(2026,1), (2026,2), ..., (2026,6)]
  │
  └── for year, month in months:
        _collect_single(year, month)
              │
              ├── report_key = f"wasde_{year}{month:02d}"    // 固话生成
              │
              ├── pdf_url  = build_url(year, month, "pdf")
              ├── xml_url  = build_url(year, month, "xml")
              │
              ├── pdf_path = f"reports/wasde/{year}/wasde{month:02d}{year[-2:]}v2.pdf"
              ├── xml_path = f"reports/wasde/{year}/wasde{month:02d}{year[-2:]}v2.xml"
              │
              ├── minio_client.stat_object(pdf_path) 且 stat_object(xml_path)
              │     ├── 都存在 → 记录跳过日志，continue（前置去重）
              │     └── 任一缺失 → 重新完整采集
              │
              ├── pdf_data  = _download_file(pdf_url)   // 3次重试/2s间隔/15s超时
              ├── xml_data  = _download_file(xml_url)   // 同上
              │
              ├── minio_client.put(pdf_path, pdf_data)  // 上传 PDF
              ├── minio_client.put(xml_path, xml_data)  // 上传 XML
              │
              ├── _fetch_report_date()                  // 从官网页面提取发布日期
              │
              ├── submit_report(                         // PDF 入知识库（复用）
              │     title=f"USDA WASDE 报告 {year}年{month}月",
              │     source="usda",
              │     url=pdf_url,
              │     content="",                          // 知识库无纯文本内容
              │     content_html="",                     // 无 HTML 版本
              │     file_path=pdf_path,                  // MinIO PDF 路径
              │     publish_time=report_date,
              │   )
              │
              ├── _trigger_parse({                       // 触发后端解析
              │     source_type: "wasde",
              │     report_key: report_key,
              │     minio_path: xml_path,
              │     report_date: report_date            // 从官网提取的真实日期 → 非硬编码
              │   })
              │
              └── 日志输出：年月、PDF/XML URL、MinIO 路径、submit_report 结果、解析 HTTP 状态码
```

### 3.3 发布日期自动解析

采集器在 `_collect_single()` 中先 GET WASDE 主站页面，从页面的文件列表中提取当前期报告的真实发布日期，填入 `_trigger_parse()` 的 `report_date` 参数：

```python
def _fetch_report_date(self, year, month):
    """从 USDA WASDE 页面提取该期报告的真实发布日期，而非默认当月1日"""
    page_url = "https://www.usda.gov/about-usda/general-information/staff-offices/office-chief-economist/commodity-markets/wasde-report"
    # 解析页面，定位当前月份的发布条目
    # 提取发布日期文本（如 "June 11, 2026"）
    # 返回 YYYY-MM-DD 格式
```

### 3.4 下载健壮性

```python
def _download_file(self, url, max_retries=3):
    for attempt in range(1, max_retries + 1):
        try:
            resp = requests.get(
                url,
                headers={
                    "User-Agent": self._random_ua(),       # 随机模拟浏览器
                    "Accept": "application/pdf,application/xml,*/*",
                },
                timeout=15,
            )
            if resp.status_code == 404:
                self.log_error(f"文件不存在: {url}")
                return None                               # 不重试，直接跳过
            resp.raise_for_status()
            return resp.content
        except requests.Timeout:
            self.log_warn(f"下载超时(第{attempt}次): {url}")
            time.sleep(2 * attempt)                       # 阶梯间隔 2/4/6s
        except requests.RequestException as e:
            self.log_warn(f"下载异常(第{attempt}次): {url} - {e}")
            time.sleep(2)
    self.log_error(f"下载失败(已达最大重试): {url}")
    return None
```

### 3.5 异常熔断

- **单月熔断**：`_download_file` 返回 None 标记该月失败 → `self._failed_months.append(ym)` → 写入告警日志 → **不阻断整体循环**
- **连续失败阈值**：连续 3 个月失败 → 告警升级，记录到 `t_collection_log`
- **不影响后续月份**：单月失败不阻塞后续月份的回填

### 3.6 report_key 生成规则

在 `_collect_single` 内部统一生成，杜绝客户端/接口自定义拼接：

```python
report_key = f"wasde_{year}{month:02d}"
# 示例: wasde_202606
```

### 3.7 触发解析接口

```python
POST /api/parse/trigger
Content-Type: application/json

{
  "source_type": "wasde",         // source_type 枚举，预留 conab
  "report_key": "wasde_202606",   // 唯一标识
  "minio_path": "reports/wasde/2026/wasde0626v2.xml",
  "report_date": "2026-06-11"     // 从官网提取的真实发布日期
}
```

### 3.8 多品种扩展预留

当前 `_download_file` 支持参数化子报告 URL 模板。后续 WASDE 棉花/油料分表文件上线时，只需在 `collect()` 中新增 URL 模板和下载分支，类结构不需要改动：

```python
GRAINS_URL  = "https://www.usda.gov/oce/commodity/wasde/wasde{MMYY}v2"
# 预留:
# COTTON_URL = "https://www.usda.gov/oce/commodity/wasde/wasde-cotton{MMYY}v2"
# OILSEEDS_URL = "..."
```

---

## 4. Java 后端解析模块

### 4.1 新增表结构

#### `t_wasde_data` — WASDE 结构化供需数据

```sql
CREATE TABLE t_wasde_data (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    report_key      VARCHAR(32)     NOT NULL COMMENT '报告唯一标识, wasde_202606',
    source_type     VARCHAR(16)     NOT NULL COMMENT '数据源, wasde/conab',
    commodity       VARCHAR(64)     NOT NULL COMMENT '品种, CORN/WHEAT/SOYBEANS',
    country         VARCHAR(64)              COMMENT '国家/地区',
    attribute       VARCHAR(32)              COMMENT '指标, PRODUCTION/IMPORTS/EXPORTS/ENDING_STOCK',
    year_marketing  VARCHAR(16)              COMMENT '市场年度, 如 2025/26',
    value           DECIMAL(20, 2)           COMMENT '数值',
    unit            VARCHAR(16)              COMMENT '单位, 百万蒲式耳/百万吨',
    report_date     DATE            NOT NULL COMMENT '报告发布日期',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_report_com_country_attr_my (report_key, commodity, country, attribute, year_marketing),
    KEY idx_report_key (report_key),
    KEY idx_commodity (commodity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='WASDE 供需数据';
```

**索引说明**：`(report_key, commodity, country, attribute, year_marketing)` 联合唯一索引，同时承担业务去重（`INSERT ... ON DUPLICATE KEY UPDATE`）和日常按 `report_key`+`commodity` 查询的索引加速。

#### `t_parse_record` — 解析记录（幂等 + 失败追踪）

```sql
CREATE TABLE t_parse_record (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    source_type     VARCHAR(16)     NOT NULL COMMENT '数据源, wasde/conab',
    report_key      VARCHAR(32)     NOT NULL COMMENT '报告唯一标识',
    status          VARCHAR(16)     NOT NULL DEFAULT 'pending' COMMENT '解析状态, pending/success/failed',
    minio_path      VARCHAR(512)             COMMENT 'XML 文件 MinIO 路径',
    error_message   TEXT                     COMMENT '失败异常堆栈',
    retry_count     INT             NOT NULL DEFAULT 0 COMMENT '已重试次数',
    report_date     DATE                     COMMENT '报告发布日期',
    parse_at        DATETIME                 COMMENT '最后解析时间',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uk_source_report (source_type, report_key),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报告解析记录';
```

#### `t_report_generation_log` — 统一业务日志（复用现有表）

```sql
-- 如果当前无此表，则新增；否则复用
CREATE TABLE t_report_generation_log (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    source_type     VARCHAR(32)              COMMENT '数据源',
    report_key      VARCHAR(32)              COMMENT '报告标识',
    level           VARCHAR(8)      NOT NULL DEFAULT 'INFO' COMMENT '日志级别, INFO/WARN/ERROR',
    message         TEXT                     COMMENT '日志消息',
    stack_trace     TEXT                     COMMENT '异常堆栈（ERROR 时填写）',
    minio_path      VARCHAR(512)             COMMENT '关联文件路径',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    KEY idx_source_report (source_type, report_key),
    KEY idx_level (level),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报告生成/解析业务日志';
```

### 4.2 Java 枚举类与参数校验约束

```java
public enum SourceTypeEnum {
    WASDE("wasde"),
    CONAB("conab");  // 预留

    private final String code;
    // getter / fromCode(code) throws IllegalArgumentException / isValid()
}

public enum ParseStatusEnum {
    PENDING("pending"),
    SUCCESS("success"),
    FAILED("failed");
}

public enum CommodityEnum {
    CORN("CORN", "玉米"),
    WHEAT("WHEAT", "小麦"),
    SOYBEANS("SOYBEANS", "大豆"),
    RICE("RICE", "稻米"),
    COTTON("COTTON", "棉花");  // 预留

    private final String code;
    private final String label;
    // getter / fromCode(code) throws IllegalArgumentException / isValid()
}

public enum AttributeEnum {
    PRODUCTION("PRODUCTION", "产量"),
    IMPORTS("IMPORTS", "进口"),
    EXPORTS("EXPORTS", "出口"),
    ENDING_STOCK("ENDING_STOCK", "期末库存"),
    STOCK_USE_RATIO("STOCK_USE_RATIO", "库存消费比");

    private final String code;
    private final String label;
    // getter / fromCode(code) throws IllegalArgumentException / isValid()
}
```

**枚举校验约束**：
- 所有接口入参 `source_type` 必须在 `SourceTypeEnum` 范围内，非法值直接返回 `{code: 400, msg: "不支持的source_type: xxx"}`
- 解析阶段的 `commodity`、`attribute` 必须在对应枚举范围内
- WASDE XML 中未预定义的小众指标（如非核心的细分指标）统一过滤丢弃，不写入 `t_wasde_data`，同时记录告警日志到 `t_report_generation_log`
- `fromCode()` 方法对非法值抛出 `IllegalArgumentException`，由全局异常处理器统一拦截返回 400
```

### 4.3 解析流程

```
POST /api/parse/trigger
  │
  ▼
ReportParseController
  │  validate request: source_type, report_key, minio_path 非空
  │        source_type 在 SourceTypeEnum 范围内
  ▼
ReportParseService
  │
  ├── ① 幂等校验
  │      SELECT status FROM t_parse_record WHERE source_type=？ AND report_key=？
  │      └─ status=success → 直接返回 {code: 0, msg: "已解析完成"}
  │
  ├── ② Redis 分布式锁
  │      key = "lock:parse:{source_type}:{report_key}"
  │      ├─ 获取成功 → TTL=300s，继续
  │      ├─ 获取失败 → 返回 {code: 1, msg: "该报告正在解析中"}
  │      └─ finally → 解锁（兜底释放）
  │
  ├── ③ MinIO 读取 XML
  │      for i in [1, 2, 3]:
  │          try:
  │              xml_bytes = minioClient.getObject(minio_path)
  │              break
  │          except MinioException as e:
  │              区分日志：文件不存在 / 权限拒绝 / 连接超时
  │              Thread.sleep(阶梯间隔 1000*i ms)
  │      └─ 3 次失败 → 标记 failed → 告警 → return
  │
  ├── ④ 路由解析器（策略模式）
  │      parser = ParserFactory.getParser(source_type)
  │      └─ wasde → WasdeXmlParser
  │      └─ conab → ConabHtmlParser（预留，当前抛出 UnsupportedOperationException）
  │
  ├── ⑤ XML 解析
  │      WasdeXmlParser.parse(xml_bytes)
  │      ├── 使用 JAXB / DOM4J / XPath 逐表格提取
  │      ├── 映射 CommodityEnum / AttributeEnum
  │      └── 返回 List<WasdeData>（解析后的结构化数据集合）
  │
  ├── ⑥ 数据合法性校验
  │      for each record:
  │          ├── value != null && value >= 0
  │          ├── commodity in CommodityEnum
  │          ├── attribute in AttributeEnum（或为空时跳过非核心指标）
  │          └── unit 非空
  │      └─ 校验不通过 → 设置 error_message → 标记 failed → 告警 → return
  │
  ├── ⑦ 批量入库
  │      INSERT INTO t_wasde_data
  │      VALUES (...) ON DUPLICATE KEY UPDATE
  │      value=VALUES(value), updated_at=NOW()
  │      └─ 数据库异常 → 写入 t_report_generation_log → 标记 failed → 告警
  │
  ├── ⑧ 更新解析记录
  │      INSERT INTO t_parse_record (source_type, report_key, status=success, ...)
  │      ON DUPLICATE KEY UPDATE status=success, retry_count=递增, ...
  │
  └── ⑨ 返回
        {code: 0, msg: "解析完成", data: {report_key, parse_count}}
```

### 4.4 失败处理与告警

- **重试机制**：解析服务内置 3 次重试（间隔 1s→2s→3s），重试也失败后进入终态 `failed`
- **状态流转**：无论哪个环节异常（文件拉取 / XML 格式 / 字段校验 / DB 写入）：
  - `retry_count += 1`
  - `status = failed`
  - `error_message` 写入完整异常堆栈
  - 写入 `t_report_generation_log`（含 MinIO 路径 + 异常堆栈）
  - **钉钉告警**：推送内容含报告周期、异常原因、MinIO 路径
- **文件保留**：失败文件保留在 MinIO，不删除。人工修复后可重新通过管理端触发解析

### 4.5 数据变更追踪

`t_wasde_data` 设有 `updated_at` 字段。USDA 发布后可能因数据错误发布 v2/v3 修正版，此时：

1. 采集器重新采集时因 MinIO 文件已存在而跳过下载
2. 人工通过管理端 `POST /api/parse/manual` 重新触发解析
3. 解析器重新读取 XML，`ON DUPLICATE KEY UPDATE` 自动覆盖旧值
4. `updated_at` 记录最新更新时间，方便追踪数据修正历史

---

## 5. 管理端点设计

| 端点 | 方法 | 用途 | 请求参数 |
|------|------|------|---------|
| `/api/parse/trigger` | POST | 采集器自动触发（正常流程） | `{source_type, report_key, minio_path, report_date}` |
| `/api/parse/manual` | POST | 手工重试指定报告 | `{source_type, report_key, minio_path}` — 仅允许 `status=failed` 状态 |
| `/api/parse/batch` | POST | 批量补数（时间段） | `{source_type, start_date, end_date}` — 遍历 MinIO 文件异步解析 |
| `/api/parse/records` | GET | 解析记录列表（管理后台） | 分页参数 + source_type/report_key/status 筛选 |

**接口规范**：

- 所有接口统一返回标准业务结果封装：

```json
{
  "code": 0,
  "msg": "success",
  "data": { ... },
  "traceId": "req-xxxxx"
}
```

- **批量补数限流**：`POST /api/parse/batch` 增加 Redis 分布式任务锁 `lock:parse:batch:{source_type}`，防止大批量时间段并发导致服务过载
- **批量补数时间跨度限制**：单次请求 `start_date` 与 `end_date` 最长时间跨度不超过 **90 天**，防止一次请求触发上千份报告解析压垮 MinIO / 数据库 / 服务线程池
- **手动重试约束**：后端校验 `t_parse_record.status`，仅 `failed` 状态允许重新触发；`success`/`pending` 状态返回 `{code: 1, msg: "该报告已达终态，不允许重新解析"}`

---

## 6. 历史回填策略

### 6.1 范围

2026 年 1 月（WASDE Jan 12）至 2026 年 6 月（WASDE Jun 11），共 6 期报告。

### 6.2 触发方式

- **首次部署**：`CollectionScript` 配置 `trigger_type=manual`，手动触发一次回填
- **回填流程**：采集器 `_determine_report_months()` 返回 `[1, 2, 3, 4, 5, 6]`，循环调用 `_collect_single(ym)`
- **去重保障**：MinIO 文件前置检查 + 后端 `t_parse_record` 幂等 + Redis 锁，三重防重复
- **非阻塞**：单月失败不影响后续月份

### 6.3 回填完成后

回填完成后，采集任务由 `manual` 更新为 `cron` 模式，每月自动触发：

```yaml
cron: "0 18 10 * *"   # 每月 10 日 18:00 UTC（北京时间次日 02:00，保证 ET 12:00 PM 已过）
```

实际 cron 表达式应根据 USDA 发布日历动态确认，建议每月配置在发布日次日（北京时间凌晨）执行。

### 6.4 时区约束（重要）

**强制统一 UTC 时区**，原因如下：
- USDA 发布时间为美国东部时间每月 8-12 日 12:00 PM（ET）
- 北京时间（UTC+8）与 ET 有 12-13 小时时差，若采集器北京时区 10 日 00:00 触发 = ET 9 日 11:00（尚未发布）
- 采集器时区偏差会导致提前采集，URL 指向下期报告从而 404

**部署要求**：

| 组件 | 时区配置 |
|------|---------|
| Java 应用容器（Docker） | `TZ=UTC` 环境变量 |
| Python 采集器容器 | `TZ=UTC` 环境变量 |
| APScheduler / 定时任务 | cron 表达式按 UTC 时间解释 |
| CollectionScript.cron_expression | 存储 UTC 时间的 cron |
| 钉钉告警时间戳 | 统一 UTC 输出，前端展示时转换 |

**示例（2026 年 7 月）**：
- USDA 发布日：2026-07-10 ET 12:00 PM = 2026-07-10 16:00 UTC
- 采集器触发：2026-07-11 02:00 UTC（发布次日的 UTC 凌晨，保证文件已稳定）
- Cron：`0 2 11 7 *`（每年需结合 USDA 发布日历微调日期字段）

---

## 7. 系统集成

### 7.1 数据源 — 补充配置（无需新增）

`t_data_source` 表中已有 `usda` 记录（code=usda, name=USDA），无需新增数据源注册，但需要补充配置：

| 操作 | 内容 |
|------|------|
| **补充 config** | 在数据源管理页面的配置编辑中，设置采集器所需的参数（如请求间隔、重试策略） |
| **关联分类** | 在知识库分类管理中创建 USDA WASDE 报告的分类，在数据源详情页中关联 |

### 7.2 采集脚本 — 新建文件并注册

#### 7.2.1 Python 采集器文件

新建 `python-collector-sdk/collectorsdk/collectors/usda.py`（替换现有占位文件），在采集器类上声明 `META` 注册字典：

```python
class UsdaWasdeCollector(BaseCollector):
    META = {
        "code": "usda",          # 与 datasource code 一致
        "name": "USDA WASDE",
        "description": "USDA 世界农业供需预测报告采集",
    }
    source = "usda"
    subject = "report"
    coll_type = "file_download"
    coll_object = "monthly_report"
```

> **文件名约定**：必须命名为 `usda.py`（而非 `usda_wasde.py`），因为 `main.py run usda` 会按 `import collectorsdk.collectors.usda` 路径加载。`META['code']='usda'` 确保 `discover_collectors()` 能正确发现。

#### 7.2.2 脚本版本注册

通过管理端「数据源 > USDA > 上传脚本」或 `POST /datasource/usda/upload-collector` 上传 `usda.py`：

| 字段 | 值 |
|------|------|
| `datasource_code` | `usda` |
| `version` | `1`（自动递增） |
| `file_path` | 上传后生成，写入 `data/collectors/usda.py` |
| `is_current` | `true` |

注册后，`python main.py run usda` 即可从 SDK 内置模块或后端下载执行。

### 7.3 采集任务 — 新建 CollectionScript

在采集任务管理页面新建一条记录：

| 字段 | 值 |
|------|------|
| `script_name` | `usda_wasde` |
| `source` | `usda` |
| `subject` | `report` |
| `coll_type` | `file_download` |
| `coll_object` | `monthly_report` |
| `trigger_type` | `manual`（首次回填，完成后改为 `cron`） |
| `cron_expression` | `0 2 11 * *`（UTC 时区，每月 11 日 02:00） |
| `sync_to_knowledge_base` | `true` |
| `category_id` | 知识库中 USDA WASDE 报告分类 ID |

创建后，采集任务会出现在「采集任务管理」列表中，支持查看执行记录、手动触发、暂停启用等操作。

### 7.4 执行流程

```
CollectionScriptService.checkRepeatTriggerScripts()
  │  每分钟轮询，匹配 cron 表达式
  │
  ├── POST /collector/exec/start → 创建 TaskExecution
  │
  ├── Python SDK 调用 main.py run usda
  │     └── import collectorsdk.collectors.usda → UsdaWasdeCollector
  │     └── collector.run()
  │           ├── start → collect() → complete
  │           └── 自动上报 start/progress/log/data/error/complete
  │
  └── submit_report() → t_knowledge_base（知识库管道自动触发）
        └── 切片 → 向量化（复用现有管道，无需单独开发）
```

```
CollectionScriptService.checkRepeatTriggerScripts()
  │  每分钟轮询，匹配 cron 表达式
  │
  ├── POST /collector/exec/start → 创建 TaskExecution
  │
  ├── Python 采集器 UsdaWasdeCollector.run()
  │     ├── start → collect() → complete
  │     └── 自动上报 start/progress/log/data/error/complete
  │
  └── submit_report() → t_knowledge_base（知识库管道自动触发）
        └── 切片 → 向量化（复用现有管道，无需单独开发）
```

---

## 8. 设计补充与优化（讨论确认汇总）

以下为设计过程中讨论确认的补充要点，全部纳入最终设计：

### 8.1 月份计算逻辑

- `_determine_report_months()` 增加发布日期判断
- 定时任务执行日 > USDA 当月发布日 → 采集当月报告
- 执行日 < 发布日 → 暂不采集，等待下月
- 避免提前抓取不存在的报告导致 404

### 8.2 单期采集幂等（采集器层前置去重）

- `_collect_single` 上传前先 `minio_client.stat_object()` 检查 PDF + XML 是否已存在
- 文件全部存在 → 跳过下载/上传/上报/触发解析，直接返回
- 任一缺失 → 重新完整采集
- 双重保障：采集器前置去重 + 后端解析接口幂等

### 8.3 下载健壮性

- 最大重试 3 次、阶梯间隔 2s/4s/6s
- 模拟浏览器 User-Agent
- 请求超时 15s
- 404/403/500 异常分类日志
- 失败跳过该月份，不阻断整体循环

### 8.4 日志标准化

- 每一步打印：待采集年月、PDF/XML 下载链接、MinIO 存储路径
- `submit_report()` 调用返回结果、解析接口 HTTP 状态码
- 文件已存在跳过、下载失败、网络异常分级日志

### 8.5 report_key 生成固化

- 在 `_collect_single` 内统一生成 `f"wasde_{year}{month:02d}"`
- 前端/接口不允许自定义拼接

### 8.6 发布日期自动解析

- 从官网页面提取该期报告的真实发布日期
- 真实日期填入 `_trigger_parse()` 的 `report_date` 参数
- 非默认当月 1 日硬编码

### 8.7 异常熔断

- 单月下载/上传连续失败 3 次 → 标记该月失败 → 写入告警日志 → `continue`
- 防止单期报错阻塞整个回填流程

### 8.8 Redis 锁异常释放兜底

- 锁过期 TTL=300s
- `finally` 代码块主动释放分布式锁
- 防止服务异常宕机导致死锁

### 8.9 MinIO 文件读取重试细化

- 3 次重试搭配阶梯间隔 1s→2s→3s
- 每次重试打印详细异常日志
- 区分文件不存在、权限拒绝、连接超时等错误类型

### 8.10 数据前置校验

- XML 解析完成后、批量入库前校验：
  - 数值非空、不能为负数
  - 品种/指标在枚举范围内
- 校验不通过 → 标记 failed → 拒绝写入 → 钉钉告警

### 8.11 批量补数限流

- `POST /api/parse/batch` 增加 Redis 分布式任务锁
- 防止大批量时间段触发导致服务/数据库过载

---

## 9. 扩展兼容性（CONAB 预留）

当前设计为多数据源做了以下预留：

| 维度 | 预留机制 |
|------|---------|
| **SourceTypeEnum** | 已有 `CONAB("conab")` 枚举 |
| **解析路由** | `ParserFactory.getParser(sourceType)` 策略模式，新增 CONAB 只需注册解析实现类 |
| **存储结构** | MinIO 路径分桶 `reports/{source_type}/`；数据库分表 `t_conab_data`/`t_wasde_data` |
| **接口参数** | 所有端点 `source_type` 参数化，上下游无适配偏差 |
| **研报联调** | 结构化数据通过 `report_key` + `commodity` 可供智能研报 `{{WASDE:品种,指标}}` 占位符统一读取 |

---

## 10. 影响范围

| 模块 | 变更内容 |
|------|---------|
| `python-collector-sdk/collectorsdk/collectors/usda.py` | 替换现有占位文件，实现 `UsdaWasdeCollector`（含 `META` 注册字典） |
| `python-collector-sdk/collectors/__init__.py` | 无需改动（`discover_collectors()` 通过 `META` 自动发现） |
| `python-collector-sdk/collectorsdk/minio_client.py` | 确认已有 `stat_object()`、`put()` 方法可用 |
| `backend/.../entity/WasdeData.java` | 新增 Entity |
| `backend/.../entity/ParseRecord.java` | 新增 Entity |
| `backend/.../entity/ReportGenerationLog.java` | 新增 Entity（或复用同类日志表） |
| `backend/.../mapper/WasdeDataMapper.java` | 新增 Mapper |
| `backend/.../mapper/ParseRecordMapper.java` | 新增 Mapper |
| `backend/.../mapper/ReportGenerationLogMapper.java` | 新增 Mapper |
| `backend/.../enums/SourceTypeEnum.java` | 新增枚举 |
| `backend/.../enums/ParseStatusEnum.java` | 新增枚举 |
| `backend/.../enums/CommodityEnum.java` | 新增枚举 |
| `backend/.../enums/AttributeEnum.java` | 新增枚举 |
| `backend/.../service/ReportParseService.java` | 新增 Service（解析调度 + 幂等 + Redis 锁） |
| `backend/.../controller/ParseController.java` | 新增 Controller（4 个端点） |
| `backend/.../resources/db/migration/V14__create_wasde_tables.sql` | 新增 Flyway V14（3 张新表） |
| 钉钉告警配置 | WASDE 解析失败告警通道 |
| **数据源 `usda`** | 补充 `config` 配置（管理后台编辑，无需 DDL） |
| **采集任务** | 管理后台新建 `CollectionScript` 一条（`script_name=usda_wasde`） |
| **脚本版本** | 通过 `POST /datasource/usda/upload-collector` 上传 `usda.py` |

---

## 11. 后续规划

| 阶段 | 内容 | 优先级 |
|------|------|--------|
| **V1** | WASDE 月度采集 + PDF 知识库 + XML 结构化解析 | P0 |
| **V1.1** | CONAB 巴西周报采集 | P1 |
| **V2** | WASDE 棉花/油料子报告采集 | P2 |
| **V2.1** | 多数据源结构化数据统一查询 API（返回标准化供需数据，供研报占位符读取） | P2 |

---

## 12. 上线验收核心校验要点

### 12.1 历史回填

- **6 期全覆盖**：2026 年 1-6 月共 6 期报告可一次性完整采集，PDF 全部入库知识库可检索，XML 正常解析入库
- **数据干净**：无重复数据、无缺失数据
- **三重防重**：MinIO 文件前置检查 + 后端 `t_parse_record` 幂等 + Redis 锁，任一维度均可拦截

### 12.2 增量定时

- **触发精确**：UTC 定时任务可在 USDA 发布日后稳定触发
- **智能跳过**：未到发布日自动跳过，不会产生 404 无效采集
- **时区对齐**：所有组件统一 UTC 时区，无时区偏差导致的提前/延迟触发

### 12.3 幂等校验

- **重复执行采集**：重复执行 `main.py run usda` 不会重复下载、重复上报知识库
- **重复调用解析**：多次 `POST /api/parse/trigger` 同一 `report_key` 不会重复写入 `t_wasde_data`
- **知识库去重**：`submit_report()` 对已存在的报告不重复写入

### 12.4 异常容错

- **网络超时**：`_download_file` 3 次阶梯重试，超时不影响整体循环
- **XML 格式异常**：后端解析失败 → `status=failed` → 写入 `t_report_generation_log`
- **字段非法**：枚举校验不通过 → 标记 `failed` → 拒绝写入业务表
- **告警推送**：钉钉正常推送告警，含报告周期、异常原因、MinIO 路径
- **手动修复**：失败文件保留在 MinIO，可后台 `POST /api/parse/manual` 重试修复

### 12.5 扩展兼容

- **架构预留**：架构可无缝对接后续 CONAB 巴西周报采集
- **接口通用**：上下游接口（`source_type` 参数化）、文件存储（`reports/{source_type}/` 分桶）、任务配置无需大规模重构
- **语义统一**：结构化数据通过 `report_key` + `commodity` 接入智能研报 `{{WASDE:品种,指标}}` 占位符
