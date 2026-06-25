# 粮达网价格指数采集与展示设计

## 1. 概述

### 1.1 背景

粮达网（liangdawang.com）是中粮招商局（深圳）粮食电子交易中心运营的大宗农粮交易平台。其"价格指数"板块每日发布玉米、小麦等多品种在各大港口的实际成交价，由各区业务员实地采集，数据可靠、更新及时。

当前系统已采集粮信网图文周报/晨报，但缺乏结构化的价格指数数据。接入粮达网价格数据后，可实现：
- **结构化存储**：港口价格直接存入 `t_price`，支持 SQL 查询和趋势分析
- **知识库展示**：每日生成价格指数知识条目，用户可查阅
- **AI 问答**：LLM 可查询实时价格，回答"海口港玉米多少钱"类问题

### 1.2 目标

1. 自动化采集粮达网价格指数数据
2. 结构化存入 `t_price` 表，每日增量更新
3. 在知识库中以"粮达网价格指数"条目呈现
4. AI 问答支持价格查询工具函数

### 1.3 采集范围（初期）

| 品种 | 区域类型 | 采集点数 | 采集频率 |
|------|----------|----------|----------|
| 玉米 | 港口（23 港） | 23 条/日 | 每日 9:00 |
| 玉米 | 东北 | 待定 | 每日 |
| 玉米 | 华北 | 待定 | 每日 |
| 玉米 | 其他 | 待定 | 每日 |

> 后续可扩展到小麦、进口粮、国产大豆、生猪品种

---

## 2. 数据源分析

### 2.1 完整数据清单

价格指数页面可提取 4 类数据：

#### 2.1.1 基础筛选维度（URL 参数 / 顶部筛选栏）

| 维度 | 数据 | 说明 |
|------|------|------|
| 品种 | 玉米（当前固定） | URL: `varietyName=玉米` |
| 所属大区类型 | 港口 / 东北产区 / 华北产区 | URL: `areaType` |
| 所属省份 | 广东、黑龙江、吉林、山东等 | URL: `province` |
| 具体港口/地市 | 锦州港、蛇口港、湛江港、海口港等 | URL: `area` |
| 价格口径 | 业务员实地采集成交价/收购价 | 页面说明文案 |
| 粮质标准 | 容重二等以上、水分 14% 主流粮源 | 页面说明文案 |

> 筛选维度通过 API `GET /varietyNameAndAreaType` 获取，无需从 URL 硬编码。

#### 2.1.2 核心行情数据（结构化，每条港口一行）

| 字段 | 类型 | 来源 | 示例 |
|------|------|------|------|
| 港口名称 | string | API | 海口港 |
| 省份 | string | API（province 分组） | 南港 |
| 玉米现货报价 | decimal | API `price` | 2510 |
| 涨跌 | decimal | API `priceDif` | 0（持平）/-10/+10 |
| 粮质等级 | string | API `remark` | 二等散粮 / 一等集装箱 |
| 价格日期 | date | API `endDate` | 2026-06-25 |
| 单位 | string | 固定 | 元/吨 |

> 注：当前 API 返回的 `remark` 仅为简要等级（"二等散粮""一等集装箱"），
> 页面未提供更细粒度的水分、容重、霉变、赤霉等指标字段，
> 这些内容仅在页面描述文案中提及粮质标准（容重二等以上、水分 14%），
> 非每条行情的独立数据。**若后续页面细分到字段级粮质，再扩展 `t_price` 表。**

#### 2.1.3 历史走势数据（时间序列）

| 字段 | 类型 | 来源 | 示例 |
|------|------|------|------|
| 日期 | date | API | 2020-06-02 |
| 价格 | decimal | API | 2210 |
| 涨跌 | string | API | 持平 / +10 / -10 |

#### 2.1.4 走势图表（前端 ECharts 渲染，无图片可采集）

走势图和季节性走势图由前端 ECharts 库通过 `getPriceChart` API 数据动态渲染为 `<canvas>`，页面中不存在任何 `<img>` 标签。
采集器无需截图，直接通过 API 获取原始数据即可。展示端如需图表，可复用 `getPriceChart` 数据在前端用 ECharts 重新绘制。

### 2.2 API 清单

粮达网为 SPA（Vue.js），数据通过 REST API 加载，无需登录：

| # | API | 方法 | 参数 | 用途 |
|---|-----|------|------|------|
| 1 | `/ldw-portal-mer/v1/infoCenter/varietyNameAndAreaType` | GET | 无 | 获取品种列表 |
| 2 | `/ldw-portal-mer/v1/infoCenter/getPriceInfo` | GET | `varietyName`, `areaType` | 当前价格表 |
| 3 | `/ldw-portal-mer/v1/infoCenter/getPriceChart` | GET | `varietyName`, `areaType`, `province`, `area` | 历史走势 |

### 2.3 数据模型

**当前价格（getPriceInfo）：**

```json
{
  "code": "200",
  "success": true,
  "data": [
    {
      "province": "北港",
      "priceInfoList": [
        {
          "area": "锦州港",
          "price": "2330",
          "priceDif": "持平",
          "remark": "二等散粮",
          "priceDate": "06/25",
          "endDate": "2026-06-25"
        }
      ]
    },
    {
      "province": "南港",
      "priceInfoList": [
        { "area": "湛江港", "price": "2500", "priceDif": "-10", "remark": "一等集装箱" },
        { "area": "海口港", "price": "2510", "priceDif": "持平", "remark": "二等散粮" },
        { "area": "广州港", "price": "2480", "priceDif": "持平", "remark": "一等集装箱" }
        // 共 19 港
      ]
    }
  ]
}
```

**历史走势（getPriceChart）:**

```json
{
  "data": {
    "priceByArea": {
      "priceIndexBOs": [
        { "priceDate": "2020-06-02", "price": "2210", "priceDiff": "持平" }
        // 多年时间序列
      ]
    }
  }
}
```

### 2.4 数据特征

- **来源可靠**：粮达网业务员实地采集，二等以上主流粮成交价
- **每日更新**：工作日发布当日价格
- **字段固定**：品种、区域、价格、涨跌、等级备注
- **全量返回**：getPriceInfo 一次性返回所有区域的数据，无需翻页
- **历史完整**：getPriceChart 有 2020 年起的历史数据

### 2.5 过滤丢弃内容

以下页面内容不具存储价值，采集时直接丢弃：

| 内容 | 原因 |
|------|------|
| 顶部导航栏（首页/产品服务/政策性粮食专区等） | 导航结构，无行情数据 |
| 侧边悬浮客服（客服热线/反馈/帮助中心） | UI 组件，无数据价值 |
| 页脚（备案号/版权声明） | 固定模板文本 |
| 登录/注册弹窗 | 交互组件，非数据 |
| 智能客服"粮小花" | 聊天窗口 |
| 平台公告/广告 | 与价格指数无关 |
| 免责声明、交易规则 | 固定法律文本 |

---

## 3. 采集设计

### 3.1 技术选型

| 组件 | 选择 | 理由 |
|------|------|------|
| HTTP 客户端 | `httpx` (async) | 纯 API 调用，无需浏览器渲染 |
| 采集器基类 | `BaseCollector` | 复用现有 SDK（上报/维度/日志） |
| 认证 | 无 | 公开 API，无需 cookie/header |

### 3.2 采集器实现

**新增文件：** `python-collector-sdk/collectorsdk/collectors/liangdawang.py`

```python
class LiangdawangCollector(BaseCollector):
    API_BASE = "https://www.liangdawang.com/ldw-portal-mer/v1/infoCenter"
    
    # 5 个核心维度
    Dimensions:
      source=Source.LIANGDAWANG   # liangdawang
      subject=Subject.CORN        # corn（初期）
      coll_type=CollectType.API_CRAWL
      obj=CollectObject.PRICE_INDEX
```

**采集流程：**

```
1. GET /varietyNameAndAreaType
   → 获取品种列表 [{varietyName, areaTypeList}]

2. 遍历品种 × 区域类型
   GET /getPriceInfo?varietyName=玉米&areaType=港口
   → 返回该品种在该区域类型下的所有价格数据

3. 遍历 priceInfoList，每条映射为 t_price 记录
   area        → region
   province    → province（北港/南港分组）
   price       → price
   priceDif    → change_val（"持平"→0, "+10"→10, "-10"→-10）
   endDate     → date
   remark      → remark（"二等散粮""一等集装箱"）
   固定值       → variety=玉米, unit=元/吨, source=liangdawang

4. 上报到 Java 后端
   POST /api/collector/report
   { "items": [ { "type": "price", "data": { ... } }, ... ] }

5. 首次运行：回填历史数据
   GET /getPriceChart?varietyName=玉米&areaType=港口&province=南港&area=海口港
   → 每个港口的历史价格，一次性批量插入 t_price
```

### 3.3 去重策略

- **每日增量**：`INSERT ... ON DUPLICATE KEY UPDATE`
- **唯一键**：`(date, variety, region, source)` 联合唯一
- **历史回填**：启动时检查 `t_price` 中 `source=liangdawang` 的记录数，少于预期则执行回填

### 3.4 调度配置

在现有 APScheduler 中新增定时任务：

| 品种 | 采集时间 | 重试策略 |
|------|----------|----------|
| 玉米 | 每日 9:00 | 9:30、10:00 各重试一次 |
| 小麦（后续） | 每日 9:00 | 同上 |

---

## 4. 存储设计

### 4.1 核心表 `t_price`（扩展字段）

现有 `t_price` 表已有基础字段，需要扩展 `remark` 列存储粮质等级：

```sql
CREATE TABLE `t_price` (
  `id`         bigint NOT NULL AUTO_INCREMENT,
  `report_id`  bigint DEFAULT NULL,
  `date`       date NOT NULL,              -- 价格日期
  `variety`    varchar(50) NOT NULL,       -- 品种（玉米/小麦）
  `region`     varchar(50) DEFAULT NULL,    -- 港口/区域
  `province`   varchar(50) DEFAULT NULL,    -- 省份/大区（如"北港""南港"）
  `contract`   varchar(50) DEFAULT NULL,    -- 合约代码（未来扩展）
  `price`      decimal(12,2) DEFAULT NULL,
  `change_val` decimal(12,2) DEFAULT NULL,  -- 涨跌
  `remark`     varchar(200) DEFAULT NULL,   -- 粮质备注（"二等散粮""一等集装箱"）
  `unit`       varchar(50) DEFAULT NULL,    -- 元/吨
  `source`     varchar(100) DEFAULT NULL,   -- 数据来源（liangdawang）
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
```

> **扩展说明：** 新增 `province` 字段记录省份/大区分组（北港/南港），
> 新增 `remark` 字段记录粮质等级备注。当前 API 暂未提供细粒度粮质指标
>（水分、容重、霉变、赤霉），页面描述中提及的"容重二等以上，水分14%"为
> 所有港口的统一标准，非每条行情独立指标。未来若 API 扩展了粒度字段，
> 再通过 Flyway 迁移新增列。

**数据映射细则：**

| getPriceInfo 字段 | t_price 字段 | 转换逻辑 |
|-------------------|-------------|----------|
| `province` | `province` | 透传（北港/南港） |
| `area` | `region` | 透传（锦州港/蛇口港） |
| `price` | `price` | 转 decimal |
| `priceDif` | `change_val` | "持平"→0, "+10"→10, "-10"→-10, "--"→NULL |
| `remark` | `remark` | 透传（二等散粮/一等集装箱） |
| `endDate` | `date` | 转 date |
| 固定 | `variety` | 从请求参数获取（玉米） |
| 固定 | `unit` | "元/吨" |
| 固定 | `source` | "liangdawang" |

### 4.2 索引优化

为支持 AI 问答和趋势查询，新增索引：

```sql
ALTER TABLE `t_price` ADD INDEX `idx_variety_region_date` (`variety`, `region`, `date`);
ALTER TABLE `t_price` ADD INDEX `idx_source_date` (`source`, `date`);
ALTER TABLE `t_price` ADD INDEX `idx_variety_province` (`variety`, `province`);
```

`idx_variety_region_date` 覆盖了最常见的查询模式：
- `WHERE variety='玉米' AND region='海口港' ORDER BY date DESC`
- `WHERE variety='玉米' AND region IN ('锦州港','蛇口港') AND date='2026-06-25'`
- `WHERE variety='玉米' AND date BETWEEN '2026-06-01' AND '2026-06-25'`

### 4.3 知识库条目

新增 `t_knowledge_base` 记录，每日一条"粮达网价格指数"：

| 字段 | 值 |
|------|-----|
| `title` | `粮达网玉米港口价格指数（2026-06-25）` |
| `source` | `liangdawang` |
| `source_type` | `liangdawang` |
| `content_html` | 动态生成的 HTML 表格（见 5.1 节），含 MinIO 图片链接 |
| `publish_time` | 采集日期 |
| `category` | 价格指数 |

---

## 5. 展示设计

### 5.1 知识库展示

**形式：** 每日生成一条价格指数知识条目，内容为 HTML 表格

**布局：**

```
┌──────────────────────────────────────────────────┐
│  粮达网玉米港口价格指数（2026-06-25）             │
│  来源：粮达网 · 更新于 09:00                      │
├──────────────────────────────────────────────────┤
│  北港                             南港            │
│  ┌───────┬───────┬──────┐  ┌───────┬──────┬──────┐│
│  │ 港口   │ 价格  │ 涨跌 │  │ 港口   │ 价格 │ 涨跌 ││
│  ├───────┼───────┼──────┤  ├───────┼──────┼──────┤│
│  │锦州港  │ 2330  │ 持平 │  │湛江港  │ 2500 │ -10  ││
│  │鲅鱼圈  │ 2335  │ 持平 │  │蛇口港  │ 2450 │ 持平 ││
│  │北良港  │ 2345  │ 持平 │  │海口港  │ 2510 │ 持平 ││
│  │葫芦岛港│ 2340  │ 持平 │  │广州港  │ 2480 │ 持平 ││
│  └───────┴───────┴──────┘  └───────┴──────┴──────┘│
│                                                    │
│  * 涨跌红色=上涨 绿色=下跌 灰色=持平               │
│  * 数据来源：粮达网业务员实地采集                    │
└──────────────────────────────────────────────────┘
```

**实现方案：**

**方案 A（推荐）：后端生成 HTML**

后端 Java 在处理采集上报时，从 `t_price` 读取当日数据，拼接 HTML 表格字符串，存入 `t_knowledge_base.content_html`。前端直接 `v-html` 渲染（复用现有 `sanitizedHtml` 路径）。

优点：零前端改动，数据随采集完成自动展现
缺点：需在后端新增 HTML 拼接逻辑

**方案 B：前端动态查询**

知识详情页加载时，通过 `knowledgeId` 查询关联的 `t_price` 数据，在前端渲染为表格。
优点：数据新，价格可交互排序
缺点：需新增 API、增加前端复杂度

**初期选方案 A**，以最小改动快速上线。

### 5.2 知识库详情页中的走势图

走势图由前端 ECharts 通过 `getPriceChart` API 数据动态渲染，页面无静态图片。

**一期**：在 `content_html` 末尾增加原文图表链接，用户点击跳转粮达网原页面查看交互式图表：

```html
<p class="chart-link">
  <a href="https://www.liangdawang.com/ldw-portal-vue/information/priceIndices?
     varietyName=玉米&areaType=港口&province=南港&area=海口港" target="_blank">
    📈 查看完整走势图（粮达网）
  </a>
</p>
```

**二期（可选）**：在后端新增一个 `GET /api/price/chart?variety=玉米&region=海口港` 接口，
返回 `getPriceChart` 数据，前端在知识详情页中用 ECharts 重新绘制可交互的趋势图。

### 5.3 价格走势增强（远期，二期）

在知识详情页底部，增加一个 ECharts 趋势图区块，根据 `t_price` 历史数据在前端动态绘制（不做截图）。
复用后端 `getPriceChart` API 数据 + 前端 ECharts 渲染，可与静态截图互补。

### 5.4 AI 问答展示

在 `ai-qa-service` 中新增 3 个工具函数，LLM 可在回答问题时调用：

#### 工具 1：query_price — 查当前价格

```python
async def query_price(variety: str, region: str) -> dict:
    """
    查询某个品种在指定区域的最新价格
    
    SQL: SELECT * FROM t_price 
         WHERE variety=%s AND region=%s 
         ORDER BY date DESC LIMIT 7
    """
    返回: { "dates": [...], "prices": [...], "changes": [...], "unit": "元/吨" }
```

**触发场景：**
- "海口港玉米多少钱"
- "锦州港今天玉米价格"

#### 工具 2：query_price_trend — 查趋势

```python
async def query_price_trend(variety: str, region: str, days: int = 30) -> dict:
    """
    查询某个品种在指定区域近 N 天的价格趋势
    """
```

**触发场景：**
- "最近一周海口港玉米走势"
- "湛江港玉米今年价格变化"

#### 工具 3：query_price_comparison — 多港口对比

```python
async def query_price_comparison(variety: str, regions: list[str]) -> dict:
    """
    对比多个区域的同品种价格
    """
```

**触发场景：**
- "哪个港口玉米最便宜"
- "北港和南港玉米价差多少"

#### 工具接入流程

```
用户："海口港玉米今天多少钱"
  ↓ UI 流式请求 → POST /api/chat
  ↓ LLM: 调用 query_price(variety="玉米", region="海口港")
  ↓ SQL: SELECT * FROM t_price WHERE ...
  ↓ LLM: 结构化结果 → 自然语言回答
  ↓ UI 展示：文字回答 + 价格表格
```

**回答格式示例：**

```
📊 海口港玉米价格（2026-06-25）

当前价格：2510 元/吨（二等散粮）
涨跌：持平
去年同期：2470 元/吨（同比 +40 元/吨）

近 5 日走势：
06-25  2510  持平
06-24  2510  持平
06-23  2510  持平
06-22  2510  持平
06-18  2510  持平
```

### 5.5 数据可视化页面（远期）

新增独立页面 `/price-index`，以 ECharts 展示：
- 热力图：品种×区域×日期 的价格矩阵
- 折线图：选定港口的历史走势
- 对比图：多港口价格对比

一期不做，留作扩展。

---

## 6. 系统架构

```
┌──────────────────────────────────────────────────────────────────┐
│                        粮达网 API                                │
│  https://www.liangdawang.com/ldw-portal-mer/v1/infoCenter/      │
│  GET /getPriceInfo / getPriceChart / varietyNameAndAreaType      │
└─────────────────────┬────────────────────────────────────────────┘
                      │ HTTP (public, no auth)
                      ▼
┌──────────────────────────────────────────────────────────────────┐
│  Python 采集器 (liangdawang.py)                                  │
│  ┌────────────┐  ┌──────────────┐  ┌────────────────────────┐  │
│  │ httpx 调用 │  │ 数据映射     │  │ CollectorReporter 上报 │  │
│  │ API 获取   │─▶│ JSON→t_price│─▶│ POST /api/collector     │  │
│  └────────────┘  └──────────────┘  └───────────┬────────────┘  │
└─────────────────────────────────────────────────┼────────────────┘
                                                  │
                    ┌─────────────────────────────┼──────────┐
                    │                             │          │
                    ▼                             ▼          ▼
          ┌──────────────────┐        ┌──────────────────┐
          │  Java 后端       │        │  t_knowledge_base │
          │  解析上报数据     │◀───────│  (价格指数条目)   │
          │  INSERT t_price   │        └──────────────────┘
          │  生成 content_html│
          └────────┬─────────┘
                   │
                   ▼
          ┌──────────────────┐      ┌──────────────────┐
          │  MySQL t_price   │◀─────│  AI 问答服务      │
          │  (结构化存储)    │──────▶│  query_price 等   │
          └──────────────────┘      └──────────────────┘
                   │
                   ▼
          ┌──────────────────┐
          │  知识库详情页     │
          │  (v-html 渲染)   │
          └──────────────────┘
```

---

## 7. 新增/修改文件清单

### 7.1 Python 采集器（新增）

| 文件 | 说明 |
|------|------|
| `python-collector-sdk/collectorsdk/collectors/liangdawang.py` | 采集器主类（httpx API 采集） |
| `python-collector-sdk/collectorsdk/dimensions.py`（修改） | 新增 `LIANGDAWANG` 和 `PRICE_INDEX` 枚举值 |

### 7.2 采集调度（修改）

| 文件 | 说明 |
|------|------|
| `python-collector-sdk/dev/scheduler.py`（或现有调度配置） | 新增每日 9:00 定时任务 |

### 7.3 Java 后端（修改/新增）

| 文件 | 说明 |
|------|------|
| `backend/src/main/java/com/scfx/service/PriceDataService.java`（新增） | 处理 t_price 入库和去重 |
| `backend/src/main/java/com/scfx/service/CollectionScriptService.java`（修改） | 处理 price 类型的上报 |
| `backend/src/main/java/com/scfx/mapper/PriceMapper.java`（新增） | t_price 的 MyBatis 映射 |
| `backend/src/main/resources/db/migration/V6__alter_t_price_add_fields.sql`（新增） | 新增 province/remark 列和索引 |

### 7.4 AI 问答服务（修改）

| 文件 | 说明 |
|------|------|
| `ai-qa-service/app/services/llm.py`（修改） | 新增 query_price 等 3 个工具 |
| `ai-qa-service/app/api/chat.py`（修改） | 工具路由注册 |

### 7.5 前端（不改）

知识库展示方案 A（后端生成 HTML）无需前端改动。

---

## 8. 实施步骤

### Phase 1：采集器（1 天）

1. 新增 `liangdawang.py` 采集器，实现 `collect()` 方法
2. 配置定时任务，每日 9:00 执行
3. 手动运行一次，验证数据采集成功
4. 首次运行自动回填历史数据

### Phase 2：后端存储（1 天）

1. 新增 `PriceDataService`，处理 `type=price` 的上报
2. 实现去重逻辑（`INSERT ... ON DUPLICATE KEY UPDATE`）
3. Flyway 迁移：新增索引
4. 验证数据正确写入 `t_price`

### Phase 3：知识库展示（0.5 天）

1. 后端生成 `content_html` 表格字符串
2. 存入 `t_knowledge_base`
3. 打开前端知识详情页验证显示

### Phase 4：AI 问答工具（1 天）

1. 定义工具函数 `query_price`、`query_price_trend`、`query_price_comparison`
2. 在 LLM 服务中注册工具
3. 测试问答效果："海口港玉米多少钱"

---

## 9. 风险与注意事项

### 9.1 已知风险

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 粮达网 API 鉴权变更 | 采集中断 | 关注响应状态码，告警通知 |
| API 响应格式变更 | 解析失败 | 增加字段校验，采集失败告警 |
| 数据重复采集 | t_price 膨胀 | `upsert` 去重 |
| 粮达网更换域名/路径 | 完全不可用 | 配置化 API_BASE |

### 9.2 注意事项

- **涨跌字段解析**：`priceDif` 可能为"持平""+10""-10""--"，需统一转换为数字（0、10、-10、NULL）
- **节假日无数据**：getPriceInfo 在节假日可能返回空数据或昨日数据，需要去重判断
- **等级备注**：remark 字段（如"二等散粮""一等集装箱"）保留在扩展字段中，可用于 AI 回答
- **province 分组**：getPriceInfo 按 province（北港/南港）分组返回。每条 priceInfoList 中的 area 才能唯一确定港口，注意不要跨组混淆
- **涨跌值对比**：历史数据中的 `priceDif`（getPriceInfo）和 `priceDiff`（getPriceChart）字段名不同，注意区分
