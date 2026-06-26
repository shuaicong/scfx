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

4. 走标准采集生命周期，执行记录写入 t_task_execution
   
   a. 启动执行
      POST /collector/exec/start  body: {"taskId": ${TASK_ID}}
      ← 返回 execution_id（如粮信网一样）
   
   b. httpx 调粮达网 API 采集数据（品种串行+1s间隔+信号量≤3）
   
   c. 上报日志和进度
      POST /collector/exec/{executionId}/log
      body: {"level": "INFO", "message": "玉米港口22条已采集", "phase": "crawl"}
   
   d. 批量写入 t_price
      POST /api/price/batch
      {
        "execution_id": "${executionId}",
        "source": "liangdawang",
        "total_records": 224,
        "records": [ {...}, ... ]
      }
      → PriceController → PriceService.batchInsertOrUpdate()
      → INSERT ... ON DUPLICATE KEY UPDATE
   
   e. 完成执行
      POST /collector/exec/{executionId}/complete
      body: {"status": "success", "collectedCount": 224, "successCount": 224}

   说明：/api/price/batch 只负责写 t_price（结构化数据），
   不负责知识库条目（知识库不做价格指数条目，见 5.2 节）。
   采集执行记录由标准生命周期管理，前端 TaskList 直接可见。

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

**断点续传设计：** 按 province+area 组合粒度追踪进度，超时中断后重新运行自动跳过已完成组合。

**约束：**
- 日常定时任务只做当日增量采集，不做历史回填
- 预估总量约 **194,000 条**（全品种约 150 个 province+area 组合 × 平均约 1,300 条/组合），全量约 6 分钟
- 超时中断不丢进度——已完成的组合不会重复拉取

**状态追踪方式（选型对比）：**

| 方案 | 实现 | 优劣 |
|------|------|------|
| **A：本地 JSON 进度文件**（推荐） | `liangdawang_backfill_progress.json` 记录已完成的组合列表 | 最简单，无需改 DB 表；缺点是多实例部署需共享存储 |
| B：DB `t_backfill_progress` 表 | 独立的进度追踪表，每组合一条记录 | 适合多实例，但要新建表 |
| C：纯靠 `ON DUPLICATE KEY` | 重新拉取所有 API，已存在的跳过写入 | 最笨但可行——但浪费 API 请求，增加 IP 封禁风险 |

**推荐方案 A**，理由：
- 采集器是单机 cron 触发，不需要多实例协调
- JSON 文件读写简单，不引入额外依赖
- 每个组合完成后立即写盘，即使进程 kill 也不丢进度

**回填流程：**

```
1. 读取进度文件 liangdawang_backfill_progress.json
   {"completed": ["玉米+港口+北港+锦州港", "玉米+港口+北港+鲅鱼圈", ...]}
   
2. 获取所有品种 × 区域类型组合
   GET /varietyNameAndAreaType
   → 遍历每个 variety 的 areaTypeList
   → 仅 isChart=true 的组合才回填
   
3. 获取区域下的省份+港口列表
   GET /getPriceInfo → 从 province+area 提取组合
   
4. 遍历每个组合 {variety, areaType, province, area}：
   a. 生成唯一 key: "{variety}+{areaType}+{province}+{area}"
   b. 检查 key 是否已在 completed 列表中
      → 已在 → 跳过（断点续传） 
      → 不在 → 执行回填
   
5. 回填单个组合
   GET /getPriceChart?varietyName={品种}&areaType={区域}&province={省份}&area={地点}
   → 返回该组合的所有历史时间序列
   
6. 分批写入（每批 500 条，批间间隔 500ms）
   INSERT INTO t_price (date, variety, region, price, change_val, unit, source, area_type)
   VALUES (?, ?, ?, ?, ?, ?, ?, ?)
   ON DUPLICATE KEY UPDATE price=VALUES(price), change_val=VALUES(change_val)
   每批 batch_size=500
   进度日志："回填进度：海口港 1200/8500 条"

7. 单个组合完成后立即更新进度文件
   将 key 追加到 completed 列表，写入 JSON
   日志："回填完成：玉米+港口+南港+海口港（1274条）| 总进度：23/150"

8. 全量完成后记录结束日志＋进度文件保留用于下次检查
   "全量历史回填完成：150个组合，共计194,000条，耗时368秒"
```

**中断恢复场景：**

| 场景 | 表现 | 处理方式 |
|------|------|----------|
| 回填运行到第 30/150 组合时超时（10min） | 进度文件有 30 个 key | 下次运行跳过 30 个，从第 31 个继续 |
| 回填到一半进程被 kill | 正在写入的组合未写入完成，但该组合的 key 未记入文件 | 下一轮会拉取该组合重做，ON DUPLICATE KEY 防重复 |
| 某个组合 getPriceChart 返回空 | 该组合无历史数据，直接记入 completed 跳过 | 避免每次回填都重新尝试空组合 |
| 已完成 150/150，再次运行 | 150 个 key 全部在 completed 中，跳过全部 | 不产生任何 API 请求 |

> **核心保障：** `ON DUPLICATE KEY UPDATE` 唯一键 `(date, variety, region, source)` 确保即使同一个组合被执行多次也不会产生重复行。

### 3.6 部署架构与调度

#### 部署方式：与现有采集器共用同一 SDK 和部署环境

```
python-collector-sdk/
├── collectorsdk/
│   ├── base.py                # BaseCollector（复用）
│   ├── reporter.py            # CollectorReporter（复用）
│   ├── dimensions.py          # 枚举（新增 LIANGDAWANG/PRICE_INDEX）
│   └── collectors/
│       ├── liangxin.py        # 粮信网（已有）
│       ├── mysteel.py         # 我的钢铁（已有）
│       ├── liangdawang.py     # 粮达网 ← 新增
│       └── ...
├── dev/collectors/
│   ├── liangxin.py            # 粮信网入口脚本（已有）
│   ├── liangdawang.py         # 粮达网入口脚本 ← 新增
│   └── ...
```

**理由：**

| 维度 | 共用方案 | 单独部署方案 |
|------|---------|-------------|
| SDK 依赖 | 直接复用 BaseCollector/ReporterConfig/dimensions | 同样要装 SDK，或抽成 pip 包 |
| 上报接口 | 都走 `POST /api/collector/report`，一套配置 | 各自配 API_BASE，重复工作 |
| 部署运维 | 一套 cron、一套日志、一套监控 | 多进程就要多管健康检查 |
| 依赖差异 | 粮达网只需 httpx，与 playwright 不冲突 | pip 依赖相同，分开也省不了 |
| 故障隔离 | 脚本级隔离——各自独立进程 cron 触发 | 进程级隔离，但效果一样 |
| 扩缩容 | 不能单独扩某个采集器 | 可以，但 224 条/日无性能瓶颈 |

#### 调度方式：cron 触发独立脚本

每个采集器有独立的入口脚本，由 cron 定时触发，互不依赖：

```
# crontab 示例
0 9 * * * cd /app && python main.py run liangdawang       # 粮达网 每日 9:00
0 9 * * * cd /app && python main.py run liangxin-morning   # 粮信网晨报 9:00
30 9 * * * cd /app && python main.py run liangxin-daily    # 粮信网日报 9:30
```

#### 并发策略

**结论：逻辑串行 + 信号量限死并发 ≤ 3 作为安全兜底。**

每日增量仅 20 次 API 调用，串行 40s 完成，无并行必要。但 HTTP 客户端层仍用 `asyncio.Semaphore(3)` 兜底，防止意外并发请求触发粮达网 WAF。

**耗时估算：**

| 阶段 | 请求数 | 串行耗时 | 说明 |
|------|--------|---------|------|
| 每日增量（getPriceInfo） | 5 品种 × 4 区域 ≈ 20 次 | ~40s | 串行已足够快 |
| 历史回填（getPriceChart） | ~150 组合 | ~5min | 串行+1s间隔，信号量兜底 |

**规则：**

| 策略 | 规则 | 理由 |
|------|------|------|
| 品种间 | 串行，一个品种全部区域采完再采下一个 | 20 次调用/40s，无并行必要 |
| API 请求间隔 | ≥ 1s，httpx 请求间 `await asyncio.sleep(1)` | 防 IP 封禁 |
| 信号量兜底 | `asyncio.Semaphore(3)` 包裹所有 HTTP 请求 | 即使代码 bug 导致意外并发，也不会超过 3 路 |
| 品种隔离 | 同一 collector 内顺序执行，不同品种共用间隔 | 简化代码，不增加总耗时 |
| 跨采集器 | 粮达网和粮信网互不影响（不同 cron 任务、不同进程） | 天然隔离 |

#### 重试策略

| 场景 | 策略 |
|------|------|
| 网络故障 | 自动重试 3 次，指数退避（1s→3s→9s） |
| 空数据 | 当天跳过，不计入失败 |
| 连续失败 | 3 天以上 ERROR 告警 + 推送通知 |

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

### 4.4 价格数据与知识库的关系

价格数据是结构化数字，不是可阅读的报告，不在 `t_knowledge_base` 中创建条目。
用户通过 AI 问答或执行历史页面查看价格数据。详见 5.2 节。

---

## 5. 展示设计

在 `ai-qa-service` 中新增 3 个工具函数，LLM 可在回答问题时调用：

#### 通用约束（所有工具适用）

| 约束 | 规则 |
|------|------|
| 日期范围上限 | 单次查询最多 180 天，超限自动截断并提示"仅展示近 180 天数据" |
| 返回附带信息 | 每条结果附带 `source=粮达网`、`grain_standard=容重二等以上，水分14%`，避免用户误解报价适用粮质 |
| 空结果处理 | 返回空列表时输出"暂无数据"，不抛异常 |
| 涨跌格式 | 统一转为 `+10` / `-10` / `持平` 字符串，方便 LLM 直接拼接回答 |

#### 工具 1：query_price — 查价格

```python
async def query_price(
    variety: str,
    region: str,
    date: str | None = None
) -> dict:
    """
    查询某个品种在指定区域的价格
    
    variety 取值: 玉米/小麦/进口粮/国产大豆/生猪
    region: 港口名/企业名/省份名/原产国/船期
    date: 可选，日期（yyyy-MM-dd）。不传则返回最近 7 天。
          由 LLM 根据用户问题自动推算：
          - "今天" → date=CURDATE()
          - "昨天" → date=CURDATE() - 1
          - "本月" → 由 LLM 换算为"2026-06-01"传入
          - "今年" → 由 LLM 换算为"2026-01-01"传入
    
    SQL(date有值): SELECT * FROM t_price 
                   WHERE variety=%s AND region=%s AND date=%s
    SQL(date为空): SELECT * FROM t_price 
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
async def query_price_trend(
    variety: str,
    region: str,
    days: int = 30,
    start_date: str | None = None,
    end_date: str | None = None
) -> dict:
    """
    查询某个品种在指定区域的价格趋势
    
    variety 取值: 玉米/小麦/进口粮/国产大豆/生猪
    
    两种调用方式：
    方式 A（相对天数）: days=30 → 近 30 天
      适用："最近一周""近一个月""近三个月"
      LLM 转换: "最近一周" → days=7
               "近三个月" → days=90
    
    方式 B（绝对日期）: start_date + end_date
      适用："今年""本月""6月份""2025年全年"
      LLM 转换: "今年" → start_date="2026-01-01", end_date="2026-06-26"
               "本月" → start_date="2026-06-01", end_date="2026-06-26"
               "6月份" → start_date="2026-06-01", end_date="2026-06-30"
    
    约束：
    - days 取值范围 [1, 180]，超 180 自动截断
    - start_date 与 end_date 间隔最多 365 天
    - 返回按 date ASC 排序
    
    SQL(days): SELECT * FROM t_price
               WHERE variety=%s AND region=%s
                 AND date >= DATE_SUB(CURDATE(), INTERVAL %s DAY)
               ORDER BY date ASC
    SQL(绝对日期): SELECT * FROM t_price
                  WHERE variety=%s AND region=%s
                    AND date BETWEEN %s AND %s
                  ORDER BY date ASC
    """
```

**触发场景：**
- "最近一周海口港玉米走势"
- "湛江港玉米今年价格变化"

#### 工具 3：query_price_comparison — 多港口对比

```python
async def query_price_comparison(
    variety: str,
    regions: list[str] | None = None,
    date: str | None = None
) -> dict:
    """
    对比多个区域的同品种价格
    
    variety 取值: 玉米/小麦/进口粮/国产大豆/生猪
    date: 可选，对比日期。不传则查最新一日。
          由 LLM 根据问题自动推算：
          "今天各港口价格" → date=CURDATE()
          "昨天各港口对比" → date=CURDATE() - 1
    
    特殊行为：
    - regions=None 或空列表时 → 返回当日全部区域价格，按 price ASC 排序
    - 自动标注最高价和最低价区域
    - 附带全市场均价
    
    SQL(date有值): SELECT region, price, change_val, remark, date
                   FROM t_price
                   WHERE variety=%s AND date=%s
                   ORDER BY price ASC
    SQL(date为空): SELECT region, price, change_val, remark, date
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
  url?: string            // 原文链接（可点击跳转）
}

// 新增 SQL sources  
interface SqlSource {
  type: 'sql'
  query_summary: string   // 查询概要："玉米 海口港 近7天 共5条"
  source_name: string     // 数据来源："粮达网"
  date: string            // 数据日期："2026-06-25"
  url: string             // 来源网站链接（可点击跳转粮达网原页面）
}
```

**跳转链接生成规则：**

| 工具 | URL 参数 | 示例 |
|------|---------|------|
| query_price | varietyName+areaType+province+area | `https://www.liangdawang.com/ldw-portal-vue/information/priceIndices?varietyName=玉米&areaType=港口&province=南港&area=海口港` |
| query_price_trend | 同上 | 同上，带省市参数 |
| query_price_comparison | varietyName+areaType | 只到品种+区域类型 |

> 多港口对比时链接到品种+区域类型页面，不带具体港口参数。

**回答格式示例（含来源标签+跳转链接）：**

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
📊 结构化数据 · 粮达网 · 2026-06-25 ｜ 玉米 海口港 近7天 共5条  [查看来源] ↗
```

前端将 `SqlSource.url` 渲染为可点击的 `<a>` 标签，在新标签页打开，图标为 ↗。

**混合场景（同时走两条管道）：**

```
📊 海口港玉米 2510 元/吨，较上周持平

📄 粮信网周报指出，南方港口玉米到货量增加，
近期饲用需求转淡，预计短期价格以稳为主。

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📄 粮信网 · 玉米晨报 2026-06-24  | 相似度 0.85
📊 结构化数据 · 粮达网 · 2026-06-25 ｜ 玉米 海口港 近7天 共5条  [查看来源] ↗
```

### 5.1 AI 回答内联图表

价格走势图、热力图、对比图**直接在 AI 回答消息中渲染**，不跳转独立页面。

#### 架构思路

```
用户："锦州港玉米最近走势"
  ↓
LLM 调用 query_price_trend(variety="玉米", region="锦州港", days=30)
  ↓
工具返回结构化数据 + visualization 配置
  ↓
SSE 流式返回: LLM 文本 + visualization 数据块 (非文本)
  ↓
前端 MessageContent 组件收到:
  - 文本部分 → 正常渲染文字回答
  - visualization → 渲染 ECharts 图表 / el-table
```

**关键：** visualization 数据与 LLM 文本分离，前端并行渲染。图表不是截图，是真正的 ECharts 交互组件。

#### 消息数据结构扩展

```typescript
interface DisplayMessage {
  role: 'user' | 'assistant'
  content: string                    // LLM 回答文本
  sources?: Source[]                 // 来源标签
  visualization?: VisualizationBlock // ← 新增：内嵌可视化
}

interface VisualizationBlock {
  type: 'line' | 'heatmap' | 'comparison' | 'table'
  title: string
  chartType?: 'echarts' | 'table'   // echarts 或 el-table
  data: {
    xAxis?: string[]                // 日期/标签
    series: {                       // 数据系列
      name: string                  // 系列名（港口名）
      data: number[]                // 数值
      type?: 'line' | 'bar'         // 图表类型
    }[]
    unit?: string                   // 单位（元/吨）
    highlight?: string              // 高亮标注文本
  }
  config?: {
    height?: number                 // 图表高度，默认 300px
    showLegend?: boolean            // 是否显示图例
    smooth?: boolean                // 折线是否平滑
    colors?: string[]               // 自定义配色
  }
}
```

#### 渲染效果（AI 消息中内嵌）

```
┌──────────────────────────────────────────────────────┐
│  📈 锦州港玉米近 30 天价格走势                        │
│                                                       │
│  2330 ┤⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⢀⡠⠤⠒⠉  │
│  2320 ┤⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⢀⡠⠤⠒⠉⠁⠀⠀⠀⠀⠀⠀    │
│  2310 ┤⠀⠀⠀⢀⡠⠤⠒⠉⠁⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀    │
│  2300 ┤⡤⠒⠉⠁⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀    │
│       └──────────────────────────────────                │
│       05-26  06-02  06-09  06-16  06-23               │
│                                                       │
│  当前 2330 元/吨 ｜ 30 日涨幅 +1.3%                    │
│  ─────────────────────────────────────────────         │
│  📊 结构化数据 · 粮达网 · 锦州港 · 30天 · 22条  [查看来源] ↗│
└──────────────────────────────────────────────────────┘
```

三种图表形态，LLM 根据场景选择：

| 问题 | LLM 选择的图表 | 数据 |
|------|-------------|------|
| "锦州港玉米最近走势" | 📈 **折线图**（单条） | 一个港口的时间序列 |
| "北港最近价格对比" | 📈 **折线对比图**（多条） | 北港 4 个港口对比 |
| "哪些港口在涨价" | 🔥 **热力图** | 行=港口、列=日期、颜色=价格 |
| "今日各港口价格" | 📊 **表格** | 当前价格+涨跌+等级 |

#### 热力图

用户问"最近各港口价格对比"时触发：

```
┌──────────────────────────────────────────────────────┐
│  🔥 北港玉米近 10 日价格                             │
│                                                       │
│          06-16 06-17 06-18 06-19 06-22 06-23 06-24    │
│  锦州港   ■■■■  ■■■■  ■■■■  ■■■■  ■■■■  ■■■■  ■■■■  │
│  鲅鱼圈   ■■■■  ■■■■  ■■■■  ■■■■  ■■■■  ■■■■  ■■■■  │
│  北良港   ■■■■  ■■■■  ■■■■  ■■■■  ■■■■  ■■■■  ■■■■  │
│  葫芦岛港 ■■■■  ■■■■  ■■■■  ■■■■  ■■■■  ■■■■  ■■■■  │
│                                                       │
│  ▨ 低                      高 ▨                       │
│  2320                    2350                         │
│                                                       │
│  📊 结构化数据 · 粮达网 · 北港 · 10日 · 28条  [查看来源] ↗│
└──────────────────────────────────────────────────────┘
```

色阶规则：每列（每天）独立计算色阶范围，反映当日港口间价差。
颜色映射：绿（低）→ 黄（中）→ 红（高），纯数值映射无倾斜。

#### 折线对比图

用户问"北港哪些港口价格有差异"时触发：

```
┌──────────────────────────────────────────────────────┐
│  📈 北港各港口价格对比（近 30 天）                    │
│                                                       │
│  2350 ┤         ╱╲          ╱  ╲     ── 锦州港       │
│  2340 ┤   ╱╲╱   ╲  ╱╲  ╱╱    ╲    ══ 鲅鱼圈        │
│  2330 ┤ ╱        ╲╱  ╲╱      ╲╱    ══ 北良港        │
│       └────────────────────────────── ══ 葫芦岛港     │
│       05-26  06-02  06-09  06-16  06-23               │
│                                                       │
│  最大价差：15 元/吨（锦州港 vs 北良港）               │
│  ─────────────────────────────────────────────         │
│  📊 结构化数据 · 粮达网 · 北港 · 30日 · 112条  [查看来源] ↗│
└──────────────────────────────────────────────────────┘
```

自动选择 3-6 条最具代表性的折线（最高/最低/中位/用户指定）。

#### 表格

用户问"今天各港口价格多少"时触发，与现有 text 回答同时渲染：

```
📊 今日玉米港口价格（2026-06-25）

┌──────────┬───────┬────────┬──────────┐
│  港口    │ 价格   │ 涨跌   │ 等级     │
├──────────┼───────┼────────┼──────────┤
│  北港    │       │        │          │
│  锦州港  │ 2330  │ 持平   │ 二等散粮 │
│  鲅鱼圈  │ 2335  │ 持平   │ 二等散粮 │
│  北良港  │ 2345  │ 持平   │ 二等散粮 │
│  葫芦岛港│ 2340  │ 持平   │ 二等散粮 │
├──────────┼───────┼────────┼──────────┤
│  南港    │       │        │          │
│  湛江港  │ 2500  │ 🔻-10  │ 一等集装箱│
│  蛇口港  │ 2450  │ 持平   │ 二等散粮 │
│  海口港  │ 2510  │ 持平   │ 二等散粮 │
│  ...     │       │        │          │
└──────────┴───────┴────────┴──────────┘

📊 结构化数据 · 粮达网 · 2026-06-25 · 22条  [查看来源] ↗
```

#### SSE 数据流扩展

现有 SSE 事件格式不变，新增 `visualization` 事件：

```
// 当前 SSE 事件
event: content
data: {"content": "锦州港玉米近30天价格..."}

// 新增 SSE 事件
event: visualization
data: {
  "type": "line",
  "title": "锦州港玉米近30天价格走势",
  "data": {
    "xAxis": ["2026-05-26", ...],
    "series": [{"name": "锦州港", "data": [2300, ...]}],
    "unit": "元/吨"
  }
}
```

前端收到 `visualization` 事件后，将其存入 `currentVisualization` ref，回答渲染完成后在消息底部挂载 ECharts 容器。

#### 前端组件结构

```
MessageContent.vue（现有）
  ├─ 文本渲染（现有，content → marked）
  ├─ sources 标签（现有，扩展 type=sql）
  └─ VisualizationRenderer.vue（新增）
       ├─ EChartsLine.vue        // 折线图/对比图
       ├─ EChartsHeatmap.vue     // 热力图
       └─ PriceTable.vue         // 价格表格
```

#### 美观性要求

| 维度 | 标准 |
|------|------|
| 配色 | 专业金融看板风格，与当前 AI 聊天深色主题一致 |
| 图表容器 | 圆角 8px，背景色 `rgba(255,255,255,0.03)`，微光边框 |
| 字体 | 数字用等宽字体 `tabular-nums`，对齐小数点 |
| 动效 | ECharts 入场动画 800ms，数据更新过渡 300ms |
| 间距 | 图表与上下文本间距 16px |
| 色阶 | 热力图每列独立色阶，颜色映射绿→黄→红 |
| 涨跌色标 | 表格中涨=红 `#d32f2f`、跌=绿 `#2e7d32`、持平=灰 `#888` |
| 空状态 | 无数据时显示 "📭 暂无数据" 插画 |

---

### 5.2 价格数据不进知识库

价格数据是结构化数字，不是可阅读的报告。不在 `t_knowledge_base` 创建条目。

用户通过以下方式查看价格数据：

| 方式 | 入口 | 展示内容 |
|------|------|---------|
| AI 问答 | 对话窗口 | 文本回答 + 内联图表 |
| 执行历史 | `/scripts/{id}/executions` | 采集统计 + 各品种数据量 |
| 执行详情 | 点击某次执行 | 品种级统计 + 采集日志 |

### 5.3 执行页面优化

现有的执行历史页和执行详情页需要区分"知识库采集"和"数据库采集"两种类型，展示不同信息。

#### 执行历史列表页（ExecutionHistory.vue）

增加"类型"列，替换"知识库"列：

```
┌─────┬──────┬──────┬────────┬──────────┬────────┬────────┬──────┬──────────┬──────┐
│ ID  │ 状态 │ 触发 │ 类型   │ 开始时间 │ 结束   │ 耗时   │ 数量 │ 数据统计 │ 操作 │
├─────┼──────┼──────┼────────┼──────────┼────────┼────────┼──────┼──────────┼──────┤
│ a1b │ ✅   │ 定时 │ 知识库  │ 09:30    │ 09:31  │ 65s    │ 1    │ 查看     │ 详情 │
│ a2c │ ✅   │ 定时 │ 数据库  │ 09:00    │ 09:01  │ 38s    │ 224  │ 📊查看   │ 详情 │
└─────┴──────┴──────┴────────┴──────────┴────────┴────────┴──────┴──────────┴──────┘
```

| 改动 | 说明 |
|------|------|
| 新增"类型"列 | 根据 `source` 字段判断：`liangdawang`→"数据库"，其他→"知识库" |
| "知识库"列改为"数据统计" | 数据库类型显示"📊查看"按钮，弹窗展示各品种统计 |
| 列宽调整 | "知识库"→"数据统计"后列宽略增 |

#### 执行详情页（ExecutionDetail.vue）

**数据库类型**展示与知识库类型不同的信息：

```
┌────────────────────────────────────────────┐
│  📊 采集统计                               │
│                                            │
│  总条数: 224                                │
│  ├─ 🌽 玉米       106 条   ✅ 全部成功     │
│  ├─ 🌾 小麦        53 条   ✅ 全部成功     │
│  ├─ 🚢 进口粮      20 条   ✅ 全部成功     │
│  ├─ 🫘 国产大豆    17 条   ✅ 全部成功     │
│  └─ 🐷 生猪        28 条   ✅ 全部成功     │
│                                            │
│  各品种详情:                               │
│  🌽 玉米: 港口22 东北28 华北37 其他19      │
│  🌾 小麦: 港口3 华北37 华东4 华中9         │
└────────────────────────────────────────────┘
```

| 卡片 | 知识库类型 | 数据库类型 |
|------|-----------|-----------|
| 基本信息 | 显示 | 显示（同现有） |
| 采集统计 | 总数/成功/跳过/失败/数据量 | **品种级统计**（总条数+各品种明细） |
| 阶段耗时 | 展示 | 展示（简化：只有 crawl 阶段） |
| 采集日志 | 显示 | 显示（同现有） |

**判断逻辑：** 后端在返回执行详情时携带 `collectTarget` 字段：

```json
{
  "executionId": "...",
  "collectTarget": "database",     // "knowledge_base" 或 "database"
  "stats": {
    "玉米": {"count": 106, "success": 106, "error": 0, "regions": {"港口": 22, "东北": 28, "华北": 37, "其他": 19}},
    "小麦": {"count": 53, "success": 53, "error": 0, "regions": {"港口": 3, "华北": 37, "华东": 4, "华中": 9}},
    ...
  }
}
```

**`collectTarget` 来源：** 从 `t_collection_script` 表的 `collect_object` 字段推断。
`PRICE_INDEX` → `database`，其他 → `knowledge_base`。

```python
# 采集器创建时自动确定
LiangdawangCollector:
    obj=CollectObject.PRICE_INDEX  # → collectTarget = "database"
    
LiangxinCollector:
    obj=CollectObject.DAILY_REPORT  # → collectTarget = "knowledge_base"
```

#### 脚本任务列表页（TaskList.vue）

现有列表页已有足够的信息展示，不需要大的改动。只需要在"任务名称"旁增加类型标记：

```
📊 粮达网价格指数采集      [数据库]       数据源：粮达网
🌾 粮信网玉米晨报采集      [知识库]       数据源：粮信网
```

**不需要的改动：** 数据源列、分类列、触发方式列、执行统计列——这些对两类采集器都有意义，无需区分。

#### 脚本任务详情页（TaskDetail.vue）

| 区域 | 数据库类型 | 知识库类型 |
|------|-----------|-----------|
| 左侧基础信息 | 显示（同现有） | 显示 |
| 右侧脚本内容预览 | **隐藏**，改为显示"接口采集器，无需脚本" | 显示 Python 代码 |
| 执行历史列表 | 显示（新增"数据统计"列） | 显示 |

**右侧区域改造（数据库类型）：**

```
┌─────────────────────────────────────────┐
│  📡 采集配置                            │
│                                         │
│  采集方式：API 接口采集                  │
│  API 端点：ldw-portal-mer/v1/infoCenter │
│  品种：玉米/小麦/进口粮/国产大豆/生猪    │
│  每日数据量：~224 条                     │
│  采集频率：每日 9:00                     │
│  数据存储：t_price（结构化数据库）        │
└─────────────────────────────────────────┘
```

**"执行历史"列表新增"数据统计"列：**

```
┌──────────┬────────┬──────┬──────┬──────────┐
│ 执行时间  │ 状态   │ 数量  │ 数据统计  │ 操作    │
├──────────┼────────┼──────┼──────┼──────────┤
│ 06-25    │ ✅     │ 224  │ 📊查看   │ 详情    │
│ 06-24    │ ✅     │ 224  │ 📊查看   │ 详情    │
│ 06-23    │ ✅     │ 224  │ 📊查看   │ 详情    │
└──────────┴────────┴──────┴──────┴──────────┘
```

点击"📊查看"弹出各品种统计详情（同 5.3 节执行详情页的品种级统计）。

#### 创建/编辑任务（TaskDetail.vue 创建/编辑模式）

**核心原则：表单结构由"采集类型"决定，数据源只是其中一个字段。**

#### 编辑任务字段锁定规则

一旦任务创建完成（采集类型已确定），各字段的可修改性如下：

| 字段 | 数据库采集 | 知识库采集 | 理由 |
|------|-----------|-----------|------|
| 采集类型 | ❌ 不可修改 | ❌ 不可修改 | 改采集类型 = 完全不同的任务，不如新建 |
| 任务名称 | ✅ 可修改 | ✅ 可修改 | 仅展示用，不影响执行 |
| 数据源 | ❌ 不可修改 | ❌ 不可修改 | 改数据源 = 改用另一个采集器，脚本全变 |
| 关联分类 | ✅ 可修改 | ✅ 可修改 | 分类仅用于数据归类，不影响采集逻辑 |
| 仅采集不进知识库 | ❌ 不可修改（强制勾选+禁用） | ✅ 可修改 | 数据库采集的目标就是 t_price，不涉及知识库 |
| 触发方式 | ✅ 可修改 | ✅ 可修改 | 用户可能调整采集频率和时间 |
| Cron 表达式 | ✅ 可修改 | ✅ 可修改 | 同上 |
| 脚本内容 | N/A（无脚本） | ✅ 可新增/编辑/上传 | 知识库采集依赖用户自定义脚本 |

**简化原则：** 影响"采集什么、数据去哪"的字段（采集类型、数据源）不可改；
影响"什么时候采、怎么分类"的字段（触发方式、分类）可改。

#### 删除任务是否删除数据

**不删除。** 任务只是采集配置，已采集的数据独立存在，理由：

| 场景 | 行为 | 理由 |
|------|------|------|
| 删除数据库采集任务 | `t_collection_script` 记录删除，`t_price` 数据保留 | 数据是独立资产，AI 查询和可视化看板仍需使用 |
| 删除知识库采集任务 | `t_collection_script` 记录删除，`t_knowledge_base` 知识条目保留 | 同现有行为，用户仍需阅读已采集的报告 |
| 禁用任务（非删除） | 任务停止调度，数据不受影响 | 用户可随时重新启用 |

如果用户确实想清除某个数据源的全部价格数据，应该通过独立的数据管理功能操作（如"清空粮达网全部价格数据"按钮），而非通过删除任务隐式触发。

用户在创建任务时，先选择采集类型，表单根据类型展示不同字段：

| 采集类型 | 含义 | 数据存储目标 | 适用场景 |
|---------|------|------------|---------|
| 数据库采集 | 结构化数据 | `t_price` | 价格指数、库存数据等 |
| 知识库采集 | 文本报告 | `t_knowledge_base` | 粮信网周报、行业报告等 |

```
创建任务页面
│
├─ 采集类型: [数据库采集 ● / 知识库采集 ○]
│
├─ 数据库采集表单：                    ├─ 知识库采集表单：
│   ├─ 任务名称（自动填入）             │   ├─ 任务名称（手动输入）
│   ├─ 数据源下拉（仅数据库类源）        │   ├─ 数据源下拉（仅知识库类源）
│   ├─ 关联分类（必填）                 │   ├─ 关联分类（必填）
│   ├─ ☑ 仅采集不进知识库              │   ├─ ☐ 仅采集不进知识库
│   │  （自动勾选+禁用）               │   │  （用户可选）
│   ├─ 采集配置卡片（信息展示）          │   ├─ 脚本上传/编辑
│   └─ 触发方式（默认每天9:00）          │   └─ 触发方式（默认单次）
```

**"数据源"下拉的选项过滤：**

| 采集类型 | 数据源选项 |
|---------|-----------|
| 数据库采集 | 粮达网（仅一个，后续可扩展） |
| 知识库采集 | 粮信网、我的钢铁网、中华粮网…… |

**采集类型的判定依据（后端存储）：**

在 `t_collection_script` 表中新增或复用字段记录采集类型：

| 方案 | 实现 |
|------|------|
| 复用 `coll_object` | `coll_object = "price_index"` → 数据库采集，其他 → 知识库采集 |
| 新增 `collect_target` 字段 | 明确标记 `database` / `knowledge_base`，更清晰 |
| 任务名称 | 自动填入"粮达网价格指数采集"，可修改 | 用户自定义 |
| 任务描述 | 自动填入默认描述，可修改 | 用户自定义 |
| 关联分类 | 用户选择（同现有） | 用户选择 |
| 仅采集不进知识库 | **自动勾选+禁用**（价格数据不进知识库） | 用户可选 |
| 脚本内容预览 | **隐藏**，显示"预置采集器，无需脚本"提示 | 显示 Python 代码 |
| 触发方式 | 默认 Cron `0 9 * * *`（工作日 9:00） | 用户配置 |
| 保存 | 同现有逻辑 | 同现有 |

**选择"数据库采集"类型后的表单效果：**

```
┌─────────────────────┬─────────────────────────────┐
│ 📋 任务信息          │ 📡 采集配置                  │
│                      │                             │
│ 任务名称: [粮达网价格]│ 采集方式：API 接口采集        │
│           [指数采集] │ API 端点：ldw-portal...      │
│ 数据源: [粮达网 ▼]  │ 品种：5个（玉米/小麦...）     │
│ 描述: [自动填入]    │ 每日数据量：~224 条           │
│ 分类: [用户选择]    │                             │
│ ☑ 仅采集不进知识库  │                             │
│ （已勾选+禁用）     │                             │
├─────────────────────┤                             │
│ ⏰ 触发配置          │                             │
│ ● 单次触发          │                             │
│ ○ 周期触发          │                             │
│   ┌──────────────┐  │                             │
│   │ Cron: 0 9 * * │  │                             │
│   └──────────────┘  │                             │
└─────────────────────┴─────────────────────────────┘
```

**预置采集器的 source 映射关系：**

选择数据源时，根据 `source` 自动关联 `collectorsdk` 中预置的采集器，不再需要用户上传或编辑脚本。

| 数据源 | source 值 | 采集器类 | 是否预置 |
|--------|----------|---------|---------|
| 粮达网 | `liangdawang` | `LiangdawangCollector` | ✅ 预置，无需脚本 |
| 粮信网 | `liangxin` | `LiangxinCollector` | ✅ 预置，可编辑脚本 |

#### 数据源注册

在 `t_data_source` 表中新增一条粮达网数据源记录，前端下拉列表才能出现"粮达网"选项：

```sql
INSERT INTO t_data_source (code, name, description, enabled, sort_order, config)
VALUES ('liangdawang', '粮达网', '粮达网玉米、小麦、进口粮、国产大豆、生猪价格指数采集', 1, 10, '{}');
```

| 字段 | 值 | 说明 |
|------|-----|------|
| `code` | `liangdawang` | 与 Source.LIANGDAWANG 枚举值一致 |
| `name` | `粮达网` | 前端下拉菜单显示名称 |
| `description` | `粮达网...价格指数采集` | 提示文本 |
| `enabled` | `1` | 启用 |
| `config` | `{}` | 无需登录配置 |

> 在 Phase 1 实施时执行此 INSERT，同时新增 `dev/collectors/liangdawang.py` 入口脚本。

#### 采集器与后端的绑定机制

```
source 值              任务 ID
  │                      │
  ▼                      ▼
python main.py run liangdawang --task_id=17
  │
  ├─ 1. 加载模块: dev/collectors/liangdawang.py
  │     (或从 ~/.cache/collectors/ 加载后端下载的脚本)
  │
  ├─ 2. 查找 LiangdawangCollector 类
  │     (第一个非抽象 BaseCollector 子类)
  │
  ├─ 3. 解析 __init__ 参数签名
  │     config=ReporterConfig(api_base)
  │     task_id=17
  │     execution_id=None (首次运行自动创建)
  │
  ├─ 4. collector.run()
  │     │
  │     ├─ reporter.report_start(task_id=17)
  │     │    POST /collector/exec/start
  │     │    ← { executionId: "xxx", scriptId: 17 }
  │     │
  │     ├─ httpx 调粮达网 API 采集数据
  │     │
  │     ├─ reporter.report_log(level="INFO", message="玉米港口22条已采集")
  │     │    POST /collector/exec/{id}/log
  │     │
  │     ├─ POST /api/price/batch { execution_id, records, stats }
  │     │    → PriceController → t_price
  │     │
  │     └─ reporter.report_complete(collectedCount=224, stats={...})
  │          POST /collector/exec/{id}/complete
  │
  └─ 5. 执行记录写入 t_task_execution（关联 scriptId=17）
```

**关键绑定关系：**

| 概念 | 值 | 说明 |
|------|-----|------|
| 数据源 code | `liangdawang` | `t_data_source.code` → Python 文件名 |
| 任务 ID | `17` | `t_collection_script.id` → 执行记录的 scriptId |
| execution_id | `uuid` | `t_task_execution.execution_id` → 一次执行的生命周期 |
| 采集器类 | `LiangdawangCollector` | `dev/collectors/liangdawang.py` 中定义的类 |

**预置采集器 vs 自定义脚本的区别：**

| 维度 | 预置采集器（粮达网） | 自定义脚本（粮信网） |
|------|-------------------|-------------------|
| 脚本位置 | `collectorsdk/collectors/liangdawang.py`（SDK 内建） | `dev/collectors/` 或后端下载 |
| 用户可编辑 | ❌ 不可编辑（SDK 代码） | ✅ 可编辑 |
| 脚本预览 | 隐藏，显示"预置采集器" | 显示 Python 代码 |
| 加载方式 | `main.py run liangdawang` | `main.py run liangxin` |
| 更新方式 | 随 SDK 版本更新 | 用户手动上传新版本 |
| ... | ... | ... | ... |

**实现改动清单：**

| 文件 | 改动 |
|------|------|
| `TaskDetail.vue` | 选择"数据库采集"类型时：隐藏脚本预览、自动勾选"仅采集"、预设 Cron、右侧显示采集配置信息 |

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

### 7.2 采集入口与调度（新增）

| 文件 | 说明 |
|------|------|
| `python-collector-sdk/dev/collectors/liangdawang.py` | 采集器入口脚本（cron 触发） |
| crontab | 新增一行 `0 9 * * * python main.py run liangdawang` |

### 7.3 Java 后端（修改/新增）

| 文件 | 说明 |
|------|------|
| `backend/src/main/java/com/scfx/controller/PriceController.java`（新增） | `POST /api/price/batch` 批量接收价格记录 |
| `backend/src/main/java/com/scfx/service/PriceService.java`（新增） | `batchInsertOrUpdate()` 批量写入+去重 + `generateDailySummary()` 触发知识库条目 |
| `backend/src/main/java/com/scfx/service/KnowledgeBaseService.java`（修改） | 新增 `createPriceIndexEntry()` 生成每日汇总条目 |

| `backend/src/main/java/com/scfx/entity/Price.java`（修改） | 修正 `change`→`change_val`，新增 `province`/`remark`/`area_type` 字段 |
| `backend/src/main/java/com/scfx/mapper/PriceMapper.java`（修改） | 新增 `batchInsertOrUpdate` 方法（XML 或注解） |
| `backend/src/main/resources/db/migration/V6__alter_t_price_add_fields.sql`（新增） | 新增 province/remark/area_type 列和索引 |

### 7.4 AI 问答服务（修改/新增）

| 文件 | 说明 |
|------|------|
| `ai-qa-service/app/services/tools/__init__.py`（新增） | 工具注册入口，导出所有工具函数和 schemas |
| `ai-qa-service/app/services/tools/price_tools.py`（新增） | 三个价格工具实现：query_price / query_price_trend / query_price_comparison |
| `ai-qa-service/app/services/tools/schema.py`（新增） | 工具 JSON Schema 定义（OpenAI function calling 格式） |
| `ai-qa-service/app/services/tools/db.py`（新增） | t_price 数据库查询封装（MySQL 直连） |
| `ai-qa-service/app/services/llm.py`（修改） | `generate_answer_stream` 新增 tools 参数；调用时注入工具 schema |
| `ai-qa-service/app/api/chat.py`（修改） | 流式响应中处理 tool_call 回执，执行工具→回填 LLM→返回 visualization 事件 |

**工具注册架构：**

```
chat.py (SSE 流)
  │
  ├─ build_messages(question, history, sources)         ← 现有，不改
  │
  ├─ generate_answer_stream(messages, tools=price_tools) ← 新增 tools 参数
  │   │
  │   ├─ 第一轮请求: POST /v1/chat/completions (messages + tools)
  │   │   ↓
  │   ├─ LLM 返回 finish_reason = "tool_calls"
  │   │   ↓
  │   ├─ 解析 tool_call: name + arguments
  │   │   ↓
  │   ├─ execute_tool(name, arguments) → 查询 t_price → 结构化结果 + visualization 数据
  │   │   ↓
  │   ├─ 第二轮请求: POST /v1/chat/completions (messages + tool_result)
  │   │   ↓
  │   └─ LLM 返回文本 → yield 给前端
  │
  └─ yield visualization 事件 + sources 标签
```

**工具函数执行流程（price_tools.py）：**

```python
TOOL_REGISTRY = {
    "query_price": {
        "handler": query_price,          # 执行函数
        "schema": QUERY_PRICE_SCHEMA,    # JSON Schema
    },
    "query_price_trend": {
        "handler": query_price_trend,
        "schema": QUERY_PRICE_TREND_SCHEMA,
    },
    "query_price_comparison": {
        "handler": query_price_comparison,
        "schema": QUERY_PRICE_COMPARISON_SCHEMA,
    },
}

async def query_price(variety: str, region: str, date: str | None = None) -> dict:
    """查 t_price → 返回 {content, sources, visualization}"""
    records = await db.query(variety, region, date)
    return {
        "content": format_text_answer(records),     # LLM 可读的文本
        "sources": [SqlSource(...)],                  # 来源标签
        "visualization": build_visualization(records), # 图表数据
    }
```

**第一次 LLM 调用携带的 tools 参数（OpenAI 兼容格式）：**

```json
[
  {
    "type": "function",
    "function": {
      "name": "query_price",
      "description": "查询某个品种在指定区域的当前价格。"
                     "适合：海口港玉米多少钱、锦州港今天价格",
      "parameters": {
        "type": "object",
        "properties": {
          "variety": {"type": "string", "enum": ["玉米", "小麦", "进口粮", "国产大豆", "生猪"]},
          "region": {"type": "string", "description": "港口名/企业名/省份/船期"},
          "date": {"type": "string", "description": "yyyy-MM-dd，不传查最近"}
        },
        "required": ["variety", "region"]
      }
    }
  },
  {
    "type": "function",
    "function": {
      "name": "query_price_trend",
      "description": "查询某个品种在指定区域的价格趋势。"
                     "适合：最近一周玉米走势、今年海口港价格变化",
      "parameters": {
        "type": "object",
        "properties": {
          "variety": {"type": "string", "enum": ["玉米", "小麦", "进口粮", "国产大豆", "生猪"]},
          "region": {"type": "string"},
          "days": {"type": "integer", "description": "近N天，默认30，最大180"},
          "start_date": {"type": "string", "description": "绝对起始日期 yyyy-MM-dd"},
          "end_date": {"type": "string", "description": "绝对截止日期 yyyy-MM-dd"}
        },
        "required": ["variety", "region"]
      }
    }
  },
  {
    "type": "function",
    "function": {
      "name": "query_price_comparison",
      "description": "对比多个区域的同品种价格。"
                     "适合：哪个港口最便宜、北港南港价差",
      "parameters": {
        "type": "object",
        "properties": {
          "variety": {"type": "string", "enum": ["玉米", "小麦", "进口粮", "国产大豆", "生猪"]},
          "regions": {"type": "array", "items": {"type": "string"}, "description": "区域列表，空=全部"},
          "date": {"type": "string", "description": "对比日期 yyyy-MM-dd"}
        },
        "required": ["variety"]
      }
    }
  }
]
```

### 7.5 前端（新增/修改）

| 文件 | 说明 |
|------|------|
| `frontend/src/views/ai-chat/components/VisualizationRenderer.vue`（新增） | 可视化渲染容器，分发到具体图表组件 |
| `frontend/src/views/ai-chat/components/EChartsLine.vue`（新增） | ECharts 折线图/对比图封装 |
| `frontend/src/views/ai-chat/components/EChartsHeatmap.vue`（新增） | ECharts 热力图封装 |
| `frontend/src/views/ai-chat/components/PriceTable.vue`（新增） | 价格表格组件（el-table） |
| `frontend/src/views/ai-chat/AiChat.vue`（修改） | SSE 增加 visualization 事件处理 |
| `frontend/src/views/ai-chat/components/MessageContent.vue`（修改） | 引入 VisualizationRenderer，渲染 `type=sql` 来源标签 |
| `frontend/src/views/scripts/TaskDetail.vue`（修改） | 数据库类型隐藏脚本预览，显示采集配置 |
| `frontend/src/views/scripts/ExecutionHistory.vue`（修改） | 新增类型列/数据统计列 |
| `frontend/src/views/scripts/ExecutionDetail.vue`（修改） | 数据库类型展示品种级统计 |

> 知识库展示方案 A（后端生成 HTML）无需前端改动，`content_html` 直接 `v-html` 渲染。

---

## 8. 实施步骤

### Phase 1：采集器（1 天）

1. 新增 `collectorsdk/collectors/liangdawang.py` 采集器，实现 `collect()` 方法
2. 新增 `dev/collectors/liangdawang.py` 入口脚本
3. `t_data_source` 插入粮达网数据源记录
4. 配置定时任务，每日 9:00 执行
5. 手动运行一次，验证数据采集成功
6. 首次运行自动回填历史数据

### Phase 2：后端存储（1 天）

1. Flyway 迁移：`t_price` 新增 `province`/`remark`/`area_type` 列和索引
2. 修正 `Price.java` 实体字段（`change`→`change_val`，新增字段）
3. 新增 `PriceService.batchInsertOrUpdate()` 批量写入+去重
4. 新增 `PriceController`，`POST /api/price/batch` 端点
5. 验证数据正确写入 `t_price`

### Phase 3：执行页面适配（1 天）

1. `TaskDetail.vue`：选择"数据库采集"类型时隐藏脚本预览，显示采集配置信息
2. `TaskDetail.vue`：执行历史列表新增"数据统计"列弹窗
3. `ExecutionHistory.vue`：新增"类型"列，替换"知识库"为"数据统计"
4. `ExecutionDetail.vue`：数据库类型展示品种级统计

### Phase 4：AI 回答内联图表（2 天）

1. 新增 SSE `visualization` 事件协议：工具返回数据中携带 `visualization` 块
2. 新增 `VisualizationRenderer.vue` + `EChartsLine.vue` + `EChartsHeatmap.vue` + `PriceTable.vue`
3. `MessageContent.vue` 集成 VisualizationRenderer，消息底部渲染图表
4. SS E 流中 `visualization` 事件处理，存入 `currentVisualization` ref
5. 美观性适配：深色主题、等宽字体、入场动效、涨跌色标

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
| 粮达网 API 故障 | 当日无价格数据 | 降级使用缓存数据 + RAG 粮信网报告补充 + 标注"可能延迟" |

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
| 断点续传 | 使用进度 JSON 文件按 province+area 组合粒度追踪，已完成组合自动跳过 | 超时/中断不丢进度 |
| 只回填有图表的组合 | 仅 `isChart=true` 的品种×区域才调 getPriceChart | 进口粮/大豆/生猪等无图表的不浪费 API |
| 分批写入 | 每批 500 条，批间间隔 500ms | 防止单条大事务锁表，降低主从延迟 |
| API 限流 | 请求间隔 ≥ 1s，信号量限制并发 ≤ 3 | 防 IP 封禁 |
| 进度日志 | 每组合完成后输出："回填完成：玉米+港口+南港+海口港（1274条）\| 总进度：23/150" | 可观测性 |
| 防重复 | `INSERT ... ON DUPLICATE KEY UPDATE` 唯一键 `(date, variety, region, source)` | 重复执行不产生脏数据 |
| 组合级原子性 | 每个组合拉取完成后立即更新进度文件，即使进程 kill 也只丢失当前正在写入的一个组合 | 无需人工清理 |
| 空组合跳过 | getPriceChart 返回空的组合直接记入 completed，避免每次回填都重试 | 节省 API 请求 |

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
| API 降级策略 | 粮达网 API 异常时，按 B+C 组合降级（见下方说明） | 采集故障时用户仍能看到数据 |

**API 降级策略（B + C 组合）：**

```
正常情况：
  SQL 查 t_price → 当日数据 → LLM 回答 + 来源标签

API 异常情况（粮达网挂/采集失败/无当日数据）：
  ① SQL 查 t_price ORDER BY date DESC LIMIT 1（最近缓存数据）
  ② 同时 RAG 搜索知识库是否有提及相关价格的报告
  ③ 合并回答：缓存价格 + 标注延迟 + 粮信网分析参考
```

**回答格式示例（降级时）：**

```
📊 锦州港玉米价格（数据截至 2026-06-24 ⚠️可能延迟）
当前价格：2330 元/吨（持平）

📄 粮信网周报提及：北方港口玉米收购价 2320-2350 元/吨区间震荡。

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📊 结构化数据 · 粮达网 · 2026-06-24 · 数据可能延迟 [查看来源] ↗
📄 粮信网 · 玉米周报 2026-06-22 | 相似度 0.72
```

> 降级触发的判断条件：工具返回空列表 或 t_price 当日无数据 或 采集器连续失败。

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
