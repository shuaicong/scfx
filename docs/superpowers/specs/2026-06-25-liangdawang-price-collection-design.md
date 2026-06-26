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

### 1.3 采集范围

**品种：** 全品种同步采集（玉米、小麦、进口粮、国产大豆、生猪）

**全量数据总览：**

| 品种 | 区域类型 | 采集地点数 | 日均记录 | 单位 | 说明 |
|------|----------|-----------|---------|------|------|
| **玉米** | 港口/东北/华北/其他 | 106 | 106 | 元/吨 | 港口平仓价+深加工收购价 |
| **小麦** | 港口/华北/华东/华中 | 53 | 53 | 元/吨 | 面企收购价+港口到货价 |
| **进口粮** | 玉米/高粱/大麦 | 20 | 20 | 元/吨 | 进口 CNF 船期价+国内港口价 |
| **国产大豆** | 东北/华中/华东/其他 | 17 | 17 | 元/吨 | 蛋白含量分级价 |
| **生猪** | 7 大区 | 28 | 28 | 元/斤 | 外三元生猪出栏价 |
| **全部合计** | **~15 个区域** | **224** | **224 条/日** | — | — |

**各品种明细：**

**玉米（106 条/日）**
| 区域 | 省份 | 地点数 | 典型样本 |
|------|------|--------|---------|
| 港口 | 北港（4 港） | 4 | 锦州港 2330、鲅鱼圈 2335 |
| 港口 | 南港（18 港） | 18 | 湛江港 2500、蛇口港 2450、海口港 2510 |
| 东北 | 内蒙古 7 + 黑龙江 13 + 吉林 6 + 辽宁 2 | 28 | 青冈龙凤 2245、中粮榆树 2250 |
| 华北 | 山东 26 + 河北 5 + 河南 6 | 37 | 滨州金汇 2400、玉锋淀粉 2380 |
| 其他 | 陕西/安徽/四川等 8 省 19 点 | 19 | 成都 2600、全国均价 2366 |

**小麦（53 条/日）**
| 区域 | 省份 | 地点数 | 典型样本 |
|------|------|--------|---------|
| 港口 | 上海/福建/广东 | 3 | 广州 2560 到港自提价 |
| 华北 | 河北 9 + 河南 10 + 山东 18 | 37 | 邯郸五得利 2460、新乡五得利 2470 |
| 华东 | 江苏 | 4 | 宿迁五得利 2460 |
| 华中 | 安徽 4 + 陕西 5 | 9 | 渭南五得利 2480 |

**进口粮（20 条/日）** - 备注含"1%关税""CNF300"等关税/运费信息
| 品类 | 原产国 | 船期货期数 | 典型样本 |
|------|--------|-----------|---------|
| 进口玉米 | 美湾 | 5 | 7月船期 2088 1%关税 |
| 高粱 | 美国/澳大利亚/阿根廷 | 4 | 7月船期 2168 3%关税 |
| 大麦 | 阿根廷/法国/乌克兰/澳大利亚 | 11 | 7月船期 2248 CNF298 |

**国产大豆（17 条/日）** - 备注含蛋白含量（39%/41%）
| 区域 | 省份 | 地点数 | 典型样本 |
|------|------|--------|---------|
| 东北 | 黑龙江 11 + 吉林 1 | 12 | 海伦 4760 41%蛋白 |
| 华中 | 河南 | 2 | 商丘 5000 净粮市场价 |
| 华东 | 安徽 | 3 | 涡阳 5100 净粮市场价 |
| 其他 | 四川 | 1 | 眉山 5200 |

**生猪（28 条/日）** - 外三元生猪出栏价（元/斤），与粮食单位不同
| 大区 | 覆盖省份 | 典型样本 |
|------|---------|---------|
| 东北/华北/华东/华中/华南/西南/西北 | 28 省 | 吉林 9.54、四川 8.91、广东 10.44 |

### 1.4 数据量估算

| 维度 | 玉米 | 小麦 | 进口粮 | 国产大豆 | 生猪 | **全部合计** |
|------|------|------|--------|---------|------|------------|
| 每日增量 | 106 条 | 53 条 | 20 条 | 17 条 | 28 条 | **224 条/日** |
| 年增量 | 26,500 条 | 13,250 条 | 5,000 条 | 4,250 条 | 7,000 条 | **56,000 条/年** |
| 首次回填 | ~136,000 | ~30,000 | ~10,000 | ~15,000 | ~3,000 | **~194,000 条** |
| 单条存储 | ~200 B | ~200 B | ~200 B | ~200 B | ~200 B | ~200 B |
| 全量存储 | ~27 MB | ~6 MB | ~2 MB | ~3 MB | ~0.6 MB | **~39 MB** |
| 年增存储 | ~5 MB | ~2.5 MB | ~0.5 MB | ~0.5 MB | ~0.5 MB | **~11 MB** |

> 数据量总体很小。首次回填约 194,000 条、~39 MB，每日增量 224 条、年增 56,000 条。

> 数据量较小，无需额外扩容或分库分表。

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
   → 获取品种列表 [{varietyName, areaTypeList, isChart}]

2. 遍历品种 × 区域类型
   GET /getPriceInfo?varietyName={品种}&areaType={区域类型}
   → 返回该品种在该区域类型下的所有价格数据

3. 遍历 priceInfoList，每条映射为 t_price 记录
   area        → region（港口名/企业名/省份/船期）
   province    → province（北港/黑龙江/美湾/大区名等）
   price       → price（统一转 decimal）
   priceDif    → change_val（全量枚举映射表）
   endDate     → date
   remark      → remark（等级/蛋白含量/关税信息/品种说明）
   varietyName → variety（由请求参数透传）
   unit        → 按品种固定：粮食品种"元/吨"，生猪"元/斤"
   source      → "liangdawang"

4. 上报到 Java 后端
   POST /api/collector/report
   { "items": [ { "type": "price", "data": { ... } }, ... ] }

5. 首次运行：回填历史数据（仅 isChart=true 的组合）
   GET /getPriceChart?varietyName=玉米&areaType=港口&province=北港&area=锦州港
   → 每个组合的历史价格，一次性批量插入 t_price
```

### 3.3 去重策略

- **每日增量**：`INSERT ... ON DUPLICATE KEY UPDATE`
- **唯一键**：`(date, variety, region, source)` 联合唯一
- **价格覆盖**：同一 date+variety+region+source 的已有记录仅更新 price/change_val/remark，不新增行

### 3.4 节假日空数据兜底

**现象：** getPriceInfo 在周末/节假日可能返回空列表或前一日数据。

**处理策略：**

| 场景 | 行为 |
|------|------|
| 返回空列表 `data: []` | 不写入 t_price，不生成知识库条目 |
| 返回数据但日期与当日不符 | 写入数据库（价格数据本身有效），但知识库条目 title 使用 API 返回的日期 |
| 连续 N 天无新数据 | 第 3 天起输出 ERROR 告警日志，推送通知 |

**AI 问答侧：**
用户查询节假日价格时，SQL 查询自动 `ORDER BY date DESC LIMIT 1`，返回最近一个交易日数据，并在回答中追加提示：

> "当前日期（2026-06-25）为节假日无新数据，以上为最近交易日（2026-06-24）价格。"

### 3.5 历史数据回填（首次运行）

**约束：**
- **仅首次运行执行一次**，通过标志位控制（检查 `t_price` 中 `source=liangdawang` 记录数是否为 0）
- 日常定时任务只做当日增量采集，不做历史回填
- 预估总量约 **136,000 条**（106 个 province+area 组合 × 平均约 1,200 条/组合），耗材约 5-6 分钟

**回填流程：**

```
1. 检查 t_price WHERE source='liangdawang' COUNT(*)
   → 若 > 0，跳过回填（已有历史数据）

2. 获取所有品种 × 区域组合
   GET /varietyNameAndAreaType
   → 遍历每个 variety 的 areaTypeList

3. 获取区域下的省份+港口列表
   GET /getPriceInfo → 从 province+area 提取组合
   
4. 遍历每个 province+area，回填历史
   GET /getPriceChart?varietyName=玉米&areaType=港口&province=南港&area=海口港
   → 返回该港口的所有历史时间序列

5. 分批写入（每批 500 条，批间间隔 500ms）
   INSERT INTO t_price (date, variety, region, price, change_val, unit, source)
   VALUES (?, ?, ?, ?, ?, '元/吨', 'liangdawang')
   ON DUPLICATE KEY UPDATE price=VALUES(price), change_val=VALUES(change_val)
   每批 batch_size=500，批量提交
   记录分批进度日志："回填进度：海口港 1200/8500 条"

6. 回填完成后设置标志位
   → 后续定时任务不再进入回填逻辑
```

> 防重复：使用唯一键 `ON DUPLICATE KEY UPDATE`，确保重复执行不会产生脏数据。

### 3.6 调度配置

在现有 APScheduler 中新增定时任务：

| 品种 | 采集时间 | 重试策略 |
|------|----------|----------|
| 玉米 | 每日 9:00 | 9:30、10:00 各重试一次 |
| 小麦（后续） | 每日 9:00 | 同上 |

---

## 4. 存储设计

### 4.1 表结构设计决策：统一表 vs 每数据源一表

**决策：统一存储在 `t_price` 一张表中。**

| 维度 | 统一表（选此方案） | 每数据源一表 |
|------|-----------------|-------------|
| AI 查询 | ✅ `SELECT WHERE variety=玉米 AND region=海口港` 一条 SQL | ❌ 需 UNION ALL 或应用层聚合 |
| 跨源对比 | ✅ 同一 SQL 拿到全部来源报价 | ❌ 需跨表 JOIN |
| 维护成本 | ✅ 一套 Mapper/迁移/索引 | ❌ 每个源一套 |
| 扩展新源 | ✅ 加 `source` 枚举值即可 | ❌ 新建表+Mapper+代码 |
| 数据隔离 | ✅ `WHERE source=liangdawang` 过滤 | ✅ 天然隔离 |
| 字段适配 | ⚠️ 需品种特殊处理表覆盖差异 | ✅ 每表自由定义 |

**关键理由：**
1. **AI 查询是核心场景** — 统一表让 SQL 最简洁，`"海口港玉米多少钱"` 无需跨表
2. **数据量极小** — 年增 ~56K 条/~11MB，一张表完全撑得住
3. **`source` 字段天然隔离** — 查某个源加 `WHERE source='liangdawang'` 即可
4. **品种字段差异已在设计文档中覆盖**（见"品种特殊处理"表），不会混淆

> 如果未来某数据源字段与 `t_price` 结构完全无法兼容（概率极低），届时再单独建表。

### 4.2 核心表 `t_price`（扩展字段）

现有 `t_price` 表已有基础字段，需要扩展 `remark` 列存储粮质等级：

```sql
CREATE TABLE `t_price` (
  `id`         bigint NOT NULL AUTO_INCREMENT,
  `report_id`  bigint DEFAULT NULL,
  `date`       date NOT NULL,              -- 价格日期
  `variety`    varchar(50) NOT NULL,       -- 品种（玉米/小麦/进口粮/国产大豆/生猪）
  `region`     varchar(50) DEFAULT NULL,    -- 地域/地点（港口名/企业名/省份/船期）
  `province`   varchar(50) DEFAULT NULL,    -- 省份/大区（北港/黑龙江/美湾/东北等）
  `area_type`  varchar(20) DEFAULT NULL,    -- region 分类：port/enterprise/region/origin/shipping
  `contract`   varchar(50) DEFAULT NULL,    -- 合约代码（未来扩展）
  `price`      decimal(12,2) DEFAULT NULL,
  `change_val` decimal(12,2) DEFAULT NULL,  -- 涨跌
  `remark`     varchar(200) DEFAULT NULL,   -- 备注（粮质/蛋白含量/关税/品种说明）
  `unit`       varchar(50) DEFAULT NULL,    -- 单位（元/吨/元/斤）
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
| `price` | `price` | 转 decimal，异常捕获见下方说明 |
| `priceDif` / `priceDiff` | `change_val` | 见下方转换规则表；优先取 `priceDif`，不存在时回退 `priceDiff` |
| `remark` | `remark` | 透传（二等散粮/一等集装箱） |
| `endDate` | `date` | 转 date |
| `varietyName` | `variety` | 从 API 响应透传（玉米/小麦/进口粮/国产大豆/生猪） |
| 固定 | `area_type` | 按品种+区域类型设定：港口→port，东北/华北/其他→enterprise，进口粮→shipping，国产大豆/生猪→region |
| 固定 | `unit` | 按品种映射：玉米/小麦/进口粮/国产大豆→"元/吨"，生猪→"元/斤" |
| 固定 | `source` | "liangdawang" |

**品种特殊处理：**

| 品种 | area_type | unit | region 取值 | remark 说明 | 特点 |
|------|-----------|------|------------|------------|------|
| 玉米（港口） | `port` | 元/吨 | 港口名（锦州港） | 二等散粮/一等集装箱 | 标准映射 |
| 玉米（东北/华北/其他） | `enterprise` | 元/吨 | 企业名（青冈龙凤） | 干粮价/折干价 | 深加工收购价 |
| 小麦 | `enterprise` | 元/吨 | 面企名（邯郸五得利） | 到港自提价/空 | remark 部分为空 |
| 进口粮 | `shipping` | 元/吨 | 船期（"7月船期"） | 关税/CNF 信息 | province 存原产国 |
| 国产大豆 | `region` | 元/吨 | 市县名（海伦） | 蛋白含量/市场价类型 | 产地区域 |
| 生猪 | `region` | 元/斤 | 省份名（吉林） | 外三元 | province 存大区名，region 存省份名 |

**priceDif → change_val 转换规则（全量枚举）：**

| 原始值 | 转换后 | 说明 |
|--------|--------|------|
| `"持平"` | `0` | 常见 |
| `"+10"` 格式（如 +5/+20） | `10` / `5` / `20` | 正涨 |
| `"10"`（无 + 号） | `10` | 带符号优先，无符号后备 |
| `"-10"` 格式（如 -5/-20） | `-10` / `-5` / `-20` | 下跌 |
| `"--"` | `NULL` | 无去年同期对比 |
| `""`（空字符串） | `NULL` | 缺失 |
| 其他不可识别值 | `NULL` + 日志告警 | 异常值追踪 |

> **字段兼容**：getPriceInfo 使用 `priceDif`，getPriceChart 使用 `priceDiff`，采集器统一先读 `priceDif`，不存在则回退 `priceDiff`。
> JSON key 严格区分大小写，按 API 实际返回的驼峰命名读取，不做大小写不敏感匹配。

> 转换日志：每次转换记录 `原始priceDif → 转换后change_val`，
> 遇到不可识别值时输出 WARN 级别日志，便于追溯 API 格式变更。

### 4.3 索引优化

为支持 AI 问答和趋势查询，新增索引：

```sql
ALTER TABLE `t_price` ADD INDEX `idx_variety_region_date` (`variety`, `region`, `date`);
ALTER TABLE `t_price` ADD INDEX `idx_source_date` (`source`, `date`);
ALTER TABLE `t_price` ADD INDEX `idx_variety_province` (`variety`, `province`);
```

`idx_variety_region_date` 覆盖了最常见的查询模式（`area_type` 字段配合 `region` 一起使用可区分同名不同类的地点）：
- `WHERE variety='玉米' AND region='海口港' ORDER BY date DESC`
- `WHERE variety='玉米' AND region IN ('锦州港','蛇口港') AND date='2026-06-25'`
- `WHERE variety='玉米' AND date BETWEEN '2026-06-01' AND '2026-06-25'`

### 4.4 知识库条目

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

**初期选方案 A**，以最小改动快速上线。

#### HTML 生成规范

**1. `content_html` 结构模板：**

```html
<div class="ldw-price-index">
  <div class="ldw-header">
    <h2>粮达网玉米港口价格指数</h2>
    <span class="ldw-date">2026-06-25</span>
    <span class="ldw-source">数据来源：粮达网业务员实地采集</span>
  </div>

  <table class="ldw-table">
    <thead>
      <tr>
        <th>省份</th><th>港口</th><th>价格（元/吨）</th><th>涨跌</th><th>粮质等级</th>
      </tr>
    </thead>
    <tbody>
      <tr class="group-header"><td colspan="5">北港</td></tr>
      <tr>
        <td>北港</td><td>锦州港</td><td>2330</td>
        <td class="change-flat">持平</td><td>二等散粮</td>
      </tr>
      <tr>
        <td>北港</td><td>鲅鱼圈</td><td>2335</td>
        <td class="change-flat">持平</td><td>二等散粮</td>
      </tr>
      <tr class="group-header"><td colspan="5">南港</td></tr>
      <tr>
        <td>南港</td><td>湛江港</td><td>2500</td>
        <td class="change-down">-10</td><td>一等集装箱</td>
      </tr>
      <tr>
        <td>南港</td><td>海口港</td><td>2510</td>
        <td class="change-flat">持平</td><td>二等散粮</td>
      </tr>
    </tbody>
  </table>

  <div class="ldw-charts">
    <p><a href="..." target="_blank">📈 查看完整走势图（粮达网）</a></p>
  </div>

  <div class="ldw-footer">
    <p>涨跌：<span class="change-up">红涨</span> <span class="change-down">绿跌</span> <span class="change-flat">灰持平</span></p>
    <p>* 价格指数为粮达网各区业务员实地采集成交价/收购价，粮质容重二等以上，水分14%</p>
  </div>
</div>
```

**2. 内置 CSS 样式：**

```html
<style>
.ldw-price-index {
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  max-width: 100%; overflow-x: auto;
}
.ldw-header { margin-bottom: 16px; }
.ldw-header h2 { font-size: 18px; font-weight: 700; margin: 0 0 4px; }
.ldw-date { font-size: 13px; color: #888; margin-right: 12px; }
.ldw-source { font-size: 12px; color: #aaa; }
.ldw-table {
  width: 100%; border-collapse: collapse; font-size: 13px;
  margin: 12px 0; min-width: 600px;
}
.ldw-table th, .ldw-table td {
  border: 1px solid #d0d5dd; padding: 8px 12px;
  text-align: center; white-space: nowrap;
}
.ldw-table th { background: #f5f0e0; font-weight: 600; color: #222; }
.ldw-table tbody tr:nth-child(even) td { background: #fafbfc; }
.change-up   { color: #d32f2f; font-weight: 600; }
.change-down { color: #2e7d32; font-weight: 600; }
.change-flat { color: #888; }
.group-header td { background: #f0f2f5 !important; font-weight: 700; text-align: left; padding: 6px 12px; color: #333; }
@media (max-width: 768px) {
  .ldw-table { font-size: 11px; }
  .ldw-table th, .ldw-table td { padding: 4px 6px; }
}
.ldw-charts img { max-width: 100%; height: auto; display: block; margin: 12px auto; border-radius: 4px; }
.ldw-footer { margin-top: 16px; padding-top: 12px; border-top: 1px solid #e8eaed; font-size: 12px; color: #888; line-height: 1.6; }
</style>
```

**3. 涨跌色标规则：**

| CSS class | 条件 | 颜色 | 示例 |
|-----------|------|------|------|
| `change-up` | `change_val > 0` | 红色 `#d32f2f` | +10、+20 |
| `change-down` | `change_val < 0` | 绿色 `#2e7d32` | -10、-20 |
| `change-flat` | `change_val = 0` 或 NULL | 灰色 `#888` | 持平、-- |

**4. 知识库标题规范：**

每日条目命名规则：`{品种}{区域类型}{数据源}价格指数（{日期}）`
示例：`玉米港口粮达网价格指数（2026-06-25）`

结构：品种从 varietyName 取值，区域类型从 areaType 取值，数据源固定`粮达网`，日期 `yyyy-MM-dd`。

**5. 标签字段：**

| 字段 | 值 | 说明 |
|------|-----|------|
| `source_type` | `liangdawang` | 与粮信网等区分 |
| `category` | `价格指数` | 区别于行业报告 |

后期可在 `t_knowledge_base` 扩展 `extra_tags` JSON 字段：

```json
{"variety": "玉米", "area_type": "港口", "data_type": "price_index"}
```

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

#### 通用约束（所有工具适用）

| 约束 | 规则 |
|------|------|
| 日期范围上限 | 单次查询最多 180 天，超限自动截断并提示"仅展示近 180 天数据" |
| 返回附带信息 | 每条结果附带 `source=粮达网`、`grain_standard=容重二等以上，水分14%`，避免用户误解报价适用粮质 |
| 空结果处理 | 返回空列表时输出"暂无数据"，不抛异常 |
| 涨跌格式 | 统一转为 `+10` / `-10` / `持平` 字符串，方便 LLM 直接拼接回答 |

#### 工具 1：query_price — 查当前价格

```python
async def query_price(variety: str, region: str) -> dict:
    """
    查询某个品种在指定区域的最新价格
    
    variety 取值: 玉米/小麦/进口粮/国产大豆/生猪
    region: 港口名/企业名/省份名/原产国/船期
    
    SQL: SELECT * FROM t_price 
         WHERE variety=%s AND region=%s 
         ORDER BY date DESC LIMIT 7
    """
    返回: {
        "variety": "玉米",
        "region": "海口港",
        "prices": [
            {"date": "2026-06-25", "price": 2510, "change": "持平", "remark": "二等散粮"},
            ...
        ],
        "unit": "元/吨",
        "source": "粮达网",
        "grain_standard": "容重二等以上，水分14%"
    }
```

**触发场景：**
- "海口港玉米多少钱"
- "锦州港今天玉米价格"

#### 工具 2：query_price_trend — 查趋势

```python
async def query_price_trend(variety: str, region: str, days: int = 30) -> dict:
    """
    查询某个品种在指定区域近 N 天的价格趋势
    
    variety 取值: 玉米/小麦/进口粮/国产大豆/生猪
    约束：
    - days 取值范围 [1, 180]，超 180 自动截断
    - 返回按 date ASC 排序
    
    SQL: SELECT * FROM t_price
         WHERE variety=%s AND region=%s
           AND date >= DATE_SUB(CURDATE(), INTERVAL %s DAY)
         ORDER BY date ASC
    """
```

**触发场景：**
- "最近一周海口港玉米走势"
- "湛江港玉米今年价格变化"

#### 工具 3：query_price_comparison — 多港口对比

```python
async def query_price_comparison(variety: str, regions: list[str] | None = None) -> dict:
    """
    对比多个区域的同品种价格
    
    variety 取值: 玉米/小麦/进口粮/国产大豆/生猪
    
    特殊行为：
    - regions=None 或空列表时 → 返回当日全部区域价格，按 price ASC 排序
    - 自动标注最高价和最低价区域
    - 附带全市场均价
    
    SQL: SELECT region, price, change_val, remark, date
         FROM t_price
         WHERE variety=%s AND date=CURDATE()
         ORDER BY price ASC
    """
```

**触发场景：**
- "哪个港口玉米最便宜"
- "哪个省大豆价格最低"
- "北港和南港玉米价差多少"

#### 路由决策：RAG 路径 vs SQL 路径

LLM 根据问题语义和工具描述，**自主决定**走哪条管道：

```
用户："锦州港玉米多少钱"
  │
  ▼
LLM 收到：问题 + [query_price, query_price_trend, query_price_comparison] 工具列表
  │
  ├─ LLM 判断"需要精确价格"
  │   → 调用 query_price(variety="玉米", region="锦州港")
  │   → SQL 路径：查 t_price → 结构化 JSON →
  │   → LLM 拼接回答 + 来源标签 📊 结构化数据
  │
  ├─ LLM 判断"不需要工具"（市场分析/行情解读类）
  │   → RAG 路径：向量搜索 Qdrant → 文本片段 →
  │   → LLM 归纳回答 + 来源标签 📄 知识库
  │
  └─ LLM 判断"既要价格又要分析"（如"海口港玉米为什么涨价"）
      → 调用 query_price 获取价格 + 同时 RAG 搜索行情分析
      → 两条管道并行 → LLM 融合回答 + 两种来源标签
```

**判断依据（tool description 中写明）：**

| 工具 | description 关键词 | LLM 匹配场景 |
|------|-------------------|-------------|
| query_price | "精确价格""当前价格" | "多少钱""什么价""价格多少" |
| query_price_trend | "走势趋势""近 N 天" | "走势""趋势""变化""最近" |
| query_price_comparison | "对比""最低""价差" | "最便宜""对比""价差""哪个贵" |
| 无匹配 | — | "行情怎么样""后市分析""市场解读"→ 走 RAG |

#### 用户感知设计

用户通过 **来源标签（sources）** 感知哪条管道提供了答案，不需要暴露 SQL 或内部路由细节：

**来源类型扩展：**

```typescript
// 现有 RAG sources
interface RagSource {
  type: 'rag'
  title: string           // 报告标题
  publish_time: string    // 发布时间
  similarity: number      // 相似度
  url?: string            // 原文链接
}

// 新增 SQL sources  
interface SqlSource {
  type: 'sql'
  query_summary: string   // 查询概要："玉米 海口港 近7天 共5条"
  source_name: string     // 数据来源："粮达网"
  date: string            // 数据日期："2026-06-25"
}

type Source = RagSource | SqlSource
```

**UI 展示效果：**

```
RAG 路径 → 📄 粮信网 · 玉米晨报 2026-06-24  | 相似度 0.85
SQL 路径 → 📊 结构化数据 · 粮达网 · 2026-06-25 · 玉米海口港近7天 · 5条
混合路径 → 同时显示两类标签
```

**回答格式示例（含来源标签）：**

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

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📊 结构化数据 · 粮达网 · 2026-06-25 ｜ 玉米 海口港 近7天 共5条
```

**混合场景（同时走两条管道）：**

```
📊 海口港玉米 2510 元/吨，较上周持平

📄 粮信网周报指出，南方港口玉米到货量增加，
近期饲用需求转淡，预计短期价格以稳为主。

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📄 粮信网 · 玉米晨报 2026-06-24  | 相似度 0.85
📊 结构化数据 · 粮达网 · 2026-06-25 ｜ 玉米 海口港 近7天 共5条
```

### 5.5 价格可视化看板

独立的行情可视化页面，嵌入 AI 问答界面中，用户问价格问题时可直接展示图表。

#### 页面入口

| 入口 | 方式 | 说明 |
|------|------|------|
| 路由 | `/price-index` | 独立页面，URL 直接访问 |
| AI 问答 | 用户问"看价格"→ LLM 返回 Markdown 图片链接或 iframe | 无缝切换聊天和看盘 |

#### 布局设计

```
┌──────────────────────────────────────────────────────┐
│  🌽 玉米价格指数           📅 2026-06-25             │
│  品种：[玉米 ▼]   区域：[港口 ▼]   时间：[30天 ▼]    │
├──────────────────────────────────────────────────────┤
│  ┌──────────────────┐  ┌──────────────────────────┐  │
│  │  🔥 价格热力图   │  │  📈 价格走势对比          │  │
│  │  品种×区域×日期  │  │  选定的 3-5 个港口折线    │  │
│  │  颜色深浅=价格   │  │  交互式筛选/缩放          │  │
│  └──────────────────┘  └──────────────────────────┘  │
├──────────────────────────────────────────────────────┤
│  ┌──────────────────────────────────────────────┐   │
│  │  📊 价格明细表                                │   │
│  │  港口  | 价格 | 涨跌 | 等级 | 日期            │   │
│  │  锦州港 | 2330 | 持平 | 二等  | 06-25         │   │
│  │  鲅鱼圈 | 2335 | 持平 | 二等  | 06-25         │   │
│  │  ... 可排序、可筛选、可导出 CSV               │   │
│  └──────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────┘
```

#### 技术选型

| 组件 | 选择 | 理由 |
|------|------|------|
| 图表库 | `ECharts` 5.x | 成熟、中文优化、热力图/折线图原生支持 |
| 数据获取 | `GET /api/price/chart?variety=玉米&areaType=港口&days=30` | 后端新接口，聚合 t_price 返回 |
| 表格 | Element Plus `el-table` | 复用现有组件，排序筛选原生支持 |
| CSS 方案 | scoped styles + CSS variables | 与现有前端风格一致 |

#### 热力图设计

**X 轴：** 日期（按时间递增）
**Y 轴：** 区域/港口（按价格排序）
**单元格颜色：** 从低到高渐变（绿→黄→红），自动计算当日色阶范围

```
      06-01  06-02  06-03  06-04  06-05  ...
锦州港  ██2300 ██2310 ██2320 ██2325 ██2330
鲅鱼圈  ██2310 ██2315 ██2320 ██2330 ██2335
北良港  ██2320 ██2330 ██2335 ██2340 ██2345
蛇口港  ██2430 ██2435 ██2440 ██2445 ██2450
海口港  ██2500 ██2505 ██2505 ██2510 ██2510
```

> 鼠标悬停显示精确数值，点击单元格联动折线图高亮对应港口

#### 折线对比图设计

默认展示当前品种在所选区域类型下**价格最高、最低、中位数**的 3 条折线：

```typescript
// 自动选择规则
const selectedPorts = [
  { name: "最高价港口", data: [...], color: "#d32f2f" },  // 红
  { name: "最低价港口", data: [...], color: "#2e7d32" },  // 绿
  { name: "中位价港口", data: [...], color: "#1976d2" },  // 蓝
  { name: "全国均价",   data: [...], color: "#888" },      // 灰
]
```

交互：点击图例显示/隐藏对应折线，鼠标滚轮缩放时间轴，拖拽平移。

#### 表格设计

| 功能 | 实现 |
|------|------|
| 默认排序 | 按省份分组，组内按价格降序 |
| 点击排序 | 支持按列排序（价格/涨跌） |
| 涨跌色标 | 涨→红色字体，跌→绿色字体，持平→灰色 |
| 搜索过滤 | 顶部搜索框，按港口名模糊过滤 |
| 行悬停 | 悬停行高亮，联动热力图对应端口高亮 |

#### 美观性要求

| 维度 | 标准 |
|------|------|
| 配色 | 专业金融看板风格，深色/浅色双主题适配 |
| 字体 | 等宽字体显示数字（tabular-nums），便于对比 |
| 间距 | 图表区 margin 24px，无冗余边框 |
| 动效 | ECharts 入场动画 800ms，数据更新过渡 300ms |
| 响应式 | 热力图在 <768px 自动改为滚动表格 |
| 空状态 | 无数据时展示 "📭 暂无数据" 插画，不带空白图表 |

#### AI 问答联动

AI 回答价格问题时，在消息底部嵌入可视化缩略图或链接：

```
用户："锦州港玉米最近价格走势"
  ↓
LLM 答：
📈 锦州港玉米近 30 天价格：2300 → 2330 元/吨，上涨 1.3%

[📊 查看完整走势图] ← 点击跳转 /price-index?variety=玉米&region=锦州港
```

> 缩略图方案：后端生成 chart 截图存入 MinIO，AI 回答中以 Markdown 图片嵌入
> 一期先用文字链接跳转，二期支持缩略图

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
| `python-collector-sdk/collectorsdk/dimensions.py`（修改） | 新增枚举值（见下方枚举规范） |

#### 枚举扩展规范

新增数据源和采集类型枚举时，与现有粮信网枚举风格统一：

**原有枚举（粮信网参考）：**

```python
# dimensions.py
class Source(str, Enum):
    LIANGXIN = "liangxin"          # 粮信网
    MYSTEEL = "mysteel"            # 我的钢铁网
    CHINAGRAIN = "chinagrain"      # 中华粮网
    USDA = "usda"                  # USDA
    UPLOAD = "upload"              # 人工录入

class Subject(str, Enum):
    CORN = "corn"                  # 玉米
    WHEAT = "wheat"                # 小麦（新增）
    IMPORTED_GRAIN = "imported_grain"  # 进口粮（新增）
    SOYBEAN = "soybean"            # 国产大豆（新增）
    LIVE_PIG = "live_pig"          # 生猪（新增）

class CollectType(str, Enum):
    LOGIN_CRAWL = "login_crawl"    # 登录后爬虫
    API_CRAWL = "api_crawl"        # API 调用

class CollectObject(str, Enum):
    DAILY_REPORT = "daily_report"  # 日报
    WEEKLY_REPORT = "weekly_report" # 周报
    PRICE_INDEX = "price_index"    # 价格指数（新增）
```

**新增枚举值：**

```python
class Source(str, Enum):
    # ... 保留所有已有枚举值 ...
    LIANGDAWANG = "liangdawang"    # 粮达网 ← 新增，注释写明数据源全称

class CollectType(str, Enum):
    # ... 保留所有已有枚举值 ...
    API_CRAWL = "api_crawl"        # 已有枚举值，粮达网复用此类型

class CollectObject(str, Enum):
    # ... 保留所有已有枚举值 ...
    PRICE_INDEX = "price_index"    # 价格指数 ← 新增
```

**约束：**
- 枚举值使用**全小写蛇形**（`liangdawang`、`price_index`），与现有枚举风格一致
- 每个枚举项必须有**中文注释**说明含义
- 不在现有枚举中间插入新值，统一追加到末尾，避免打乱已有序列化顺序
- 不修改已有枚举值的名称和值，确保历史数据统计不受影响

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
| `backend/src/main/resources/db/migration/V6__alter_t_price_add_fields.sql`（新增） | 新增 province/remark/area_type 列和索引 |

### 7.4 AI 问答服务（修改）

| 文件 | 说明 |
|------|------|
| `ai-qa-service/app/services/llm.py`（修改） | 新增 query_price 等 3 个工具 |
| `ai-qa-service/app/api/chat.py`（修改） | 工具路由注册 |

### 7.5 前端（新增/修改）

| 文件 | 说明 |
|------|------|
| `frontend/src/views/price/PriceIndex.vue`（新增） | 价格可视化看板主页面（热力图/折线图/表格） |
| `frontend/src/router/index.ts`（修改） | 新增路由 `/price-index` |
| `frontend/src/views/ai-chat/AiChat.vue`（修改） | MessageContent 组件扩展 SQL 来源标签展示 |
| `frontend/src/views/ai-chat/components/MessageContent.vue`（修改） | 渲染 `type=sql` 的来源标签 |

> 知识库展示方案 A（后端生成 HTML）无需前端改动，`content_html` 直接 `v-html` 渲染。

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

### Phase 4：价格可视化看板（1.5 天）

1. 新增 `PriceIndex.vue` 页面，实现热力图、折线对比图、表格三个模块
2. 后端新增 `GET /api/price/chart` 接口，聚合 t_price 返回时序数据
3. 路由注册 `/price-index`
4. 实现品种/区域/时间筛选联动
5. 涨跌色标、深色/浅色主题适配
6. AI 问答联动：LLM 回答中嵌入查看图表链接

### Phase 5：AI 问答工具（1 天）

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

- **Decimal 安全解析**：`price` 字段从字符串转 Decimal 时使用 `try/except` 包裹，解析失败（如空字符串、非法字符、科学记数法等）直接丢弃本条记录并输出 ERROR 告警日志，标记 `collect_error_count++`，不阻塞整体采集流程
- **涨跌字段解析**：`priceDif` 可能为"持平""+10""-10""--"，需统一转换为数字（0、10、-10、NULL）
- **节假日无数据**：getPriceInfo 在节假日可能返回空数据或昨日数据，需要去重判断
- **等级备注**：remark 字段（如"二等散粮""一等集装箱"）保留在扩展字段中，可用于 AI 回答
- **province 分组**：getPriceInfo 按 province（北港/南港）分组返回。每条 priceInfoList 中的 area 才能唯一确定港口，注意不要跨组混淆
- **涨跌值对比**：历史数据中的 `priceDif`（getPriceInfo）和 `priceDiff`（getPriceChart）字段名不同，注意区分

---

## 10. 约束规范

### 10.1 数据解析强约束

| 约束 | 规则 | 违反后果 |
|------|------|----------|
| Decimal 安全解析 | `price` 转 decimal 用 `try/except` 包裹，失败丢弃本条+ERROR 日志+`error_count++` | 单条数据丢失，不阻塞整体流程 |
| 字段名兼容 | 优先读 `priceDif`，不存在则回退 `priceDiff` | 二者字段名不同（getPriceInfo vs getPriceChart），不兼容会丢数据 |
| 涨跌枚举转换 | 见 4.2 节全量枚举表，不可识别值→NULL+WARN 日志 | 异常值追踪，API 格式变更可追溯 |
| 大小写严格 | JSON key 严格区分大小写，按 API 实际驼峰命名读取 | 不做 `lower()/upper()` 不区分匹配，避免误读同名字段 |
| area_type 必填 | 每条记录必须有 `area_type` 分类值，由品种+区域类型映射 | region 字段语义歧义，AI 查询结果不准 |
| unit 按品种固定 | 粮食品种→"元/吨"，生猪→"元/斤"，不允许空值 | AI 回答单位错误 |
| 空数据不回退 | getPriceInfo 返回空列表 `data: []` → 不写入 t_price，不生成知识条目 | 节假日脏数据污染 |
| 连续 N 天空数据 | 第 3 天起 ERROR 告警+推送通知 | 静默故障不被发现 |

### 10.2 历史回填风控约束

| 约束 | 规则 | 目的 |
|------|------|------|
| 仅执行一次 | 检查 `source=liangdawang` COUNT(*)=0 时才执行 | 避免重复回填 |
| 只回填有图表的组合 | 仅 `isChart=true` 的品种×区域类型才调 getPriceChart | 进口粮/大豆/生猪等无图表的品种跳过 |
| 分批写入 | 每批 500 条，批间间隔 500ms | 防止单条大事务锁表，降低主从延迟 |
| API 限流 | 请求间隔 ≥ 1s，信号量限制并发 ≤ 3 | 防 IP 封禁 |
| 进度日志 | 每批输出日志："回填进度：海口港 1200/8500 条" | 可观测性 |
| 防重复 | `INSERT ... ON DUPLICATE KEY UPDATE` 唯一键 `(date, variety, region, source)` | 重复执行不产生脏数据 |
| 可中断恢复 | 中断后重新运行，COUNT(*)>0 自动跳过回填 | 无需人工清理 |

### 10.3 AI 工具调用边界约束

| 约束 | 规则 | 示例 |
|------|------|------|
| 品种范围 | `variety` 参数限定：玉米/小麦/进口粮/国产大豆/生猪 | 传入"大米"返回报错 |
| 日期上限 | 单次查询最多 180 天，超限自动截断 | `days=365` → 仅返回 180 天+提示 |
| 节假日兜底 | 无当日数据时返回 `ORDER BY date DESC LIMIT 1` 最近交易日 | 回答末尾追加"当前日期为节假日，以上为最近交易日" |
| 结果附带元信息 | 每条结果包含 `source=粮达网`、`grain_standard=容重二等以上，水分14%` | 避免用户误解报价适用粮质 |
| 空结果处理 | 返回空列表时输出"暂无数据"，不抛异常，不编造数据 | 用户看到"暂无数据"而非幻觉报价 |
| 涨跌格式 | 统一转为 `+10` / `-10` / `持平` 字符串 | 方便 LLM 直接拼接回答 |
| 生猪单位换算 | 生猪 price 为元/斤，回答中需显式标注单位 | LLM 默认理解为元/吨时数据差了 2000 倍 |

### 10.4 知识库生成规范

| 约束 | 规则 | 违反后果 |
|------|------|----------|
| 标题格式 | `{品种}{区域类型}粮达网价格指数（{yyyy-MM-dd}）` | 知识库列表混乱，无法按日期筛选 |
| `source_type` | 固定 `liangdawang` | 与粮信网/其他源区分 |
| `category` | 固定 `价格指数` | 区别于行业报告/政策解读 |
| 涨跌色标 | `change_up`(红 `#d32f2f`) / `change_down`(绿 `#2e7d32`) / `change_flat`(灰 `#888`) | 用户无法直观分辨涨跌 |
| 节假日不生成 | getPriceInfo 空列表时不创建知识条目 | 空壳知识条目浪费存储 |
| 价格日期标注意 | 数据日期与当日不符时，标题使用 API 返回日期 | 用户看到"今日"但实际是昨日数据

### 10.5 安全与运维约束

| 类别 | 约束 | 规则 | 说明 |
|------|------|------|------|
| **安全** | API_BASE 配置化 | 粮达网 API 地址写入配置文件，不硬编码在代码中 | 域名/IP 变更时只改配置不改代码 |
| | 请求间隔与并发 | 请求间隔 ≥ 1s，并发 ≤ 3 | 公开 API 无鉴权，高频请求可能触发粮达网 WAF 封禁 IP |
| | 敏感信息不落库 | 请求 URL 和响应 body 不写入 INFO 级别日志 | 避免 URL 中的查询参数被日志系统采集泄露 |
| | 输入校验 | varietyName 和 areaType 从 API 返回的枚举列表中取值，不拼接用户输入 | 防注入攻击 |
| **监控** | 采集指标暴露 | 暴露指标：采集成功/失败计数、耗时、记录数 | Grafana 可观测 |
| | 数据一致性校验 | 每日采集完成后 COUNT 对比：当日记录数 vs 预期数（玉米≥106） | 差值超过 20% 触发告警 |
| | 涨跌异常告警 | 单品种日涨跌绝对值超过 5% 时输出 WARN 日志 | 可能为数据源错误或行情异动 |
| | 节假日空数据告警 | 连续 3 天空数据 → ERROR 告警 + 推送通知 | 区分节假日无数据和采集故障 |
| **运维** | 重试机制 | 请求失败自动重试 3 次，指数退避（1s→3s→9s） | transient 网络故障不影响采集 |
| | 幂等执行 | 同一 execution_id 重复上报不产生重复数据 | ON DUPLICATE KEY UPDATE 保障 |
| | 慢查询告警 | SQL 执行超过 500ms 记录慢查询日志 | 索引失效或数据量膨胀时及时发现 |
| | 数据归档 | t_price 数据保留 5 年，超期自动归档到历史表 | 5 年后约 300K 条/60MB，暂不需归档 |
| | 采集超时 | 单次采集总超时 10 分钟，超时自动终止并标记 failed | 防止 API hang 住堵塞采集进程 |
| | 字段变更兼容 | API 新增字段不解析不报错；删除字段默认 NULL 不阻塞采集 | 粮达网 API 升级时采集器不崩溃 |
