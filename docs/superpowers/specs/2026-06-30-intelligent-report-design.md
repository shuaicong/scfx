# 智能研报系统设计

## 1. 概述

### 1.1 背景

当前系统已有完整的粮达网价格数据采集能力（t_price 表 14 万条历史数据）和多数据源知识库（粮信网/我的钢铁/中华粮网等）。但价格数据和知识库报告是分离的——用户看价格需要 AI 问答，看报告需要进知识库管理。

智能研报系统将两者打通：以玉米/小麦/稻米等品种为单位，自动拉取 t_price 价格数据 + 知识库分析报告，生成带行情图表的行业周报。支持在线编辑、版本管理、一键导出 Word/PDF 归档。

### 1.2 目标

1. 基于 t_price 和知识库数据，AI 辅助生成研报
2. 浏览器内直接编辑研报（TipTap 富文本编辑器）
3. 支持版本历史管理（保存/回滚）
4. 一键导出 docx + PDF（Gotenberg 转换）
5. 导出文件存 MinIO 归档

### 1.3 适用范围

- **品种：** 一期玉米，后续扩展小麦/进口粮/国产大豆/生猪
- **报告类型：** 周报（weekly）为主，后续支持月报/专题
- **用户：** 内部决策 + 对外客户/会员

---

## 2. 技术选型

### 2.1 前端编辑器：TipTap

| 维度 | 选择 | 理由 |
|------|------|------|
| 编辑器核心 | **TipTap** (ProseMirror) | Vue 3 原生适配 `@tiptap/vue-3`，Composition API 直接支持 |
| 表格 | `@tiptap/extension-table` | colspan/rowspan 合并单元格原生支持，港口价格对比表刚需 |
| 涨跌色 | 内联 `<span style="color:#d32f2f/#2e7d32">` | 避免外部 CSS 导出失效 |
| 图表插入 | `<img>` 标签引用 MinIO URL | 前端 ECharts 渲染 → html2canvas → MinIO → 插入编辑器 |
| 分页符 | `@tiptap/extension-page-break` | Gotenberg 可识别 |
| 预览 | `editable: false` 切换 | 同一组件两用 |
| 存储格式 | **HTML + TipTap JSON** 双存 | HTML 给后端导出，JSON 给前端版本回滚 |
| 导出 | **纯后端 Gotenberg** | 不引入付费 Pro 导出插件 |

### 2.2 后端导出：Gotenberg

| 维度 | 选择 |
|------|------|
| 服务 | `gotenberg/gotenberg:8` Docker 容器 |
| 转换引擎 | LibreOffice（HTML→docx）+ Chromium（HTML→PDF） |
| 字体 | 卷挂载 SimSun.ttf / SimHei.ttf |
| 超时 | 60s，并发上限 5 |

### 2.3 存储

| 存储对象 | 存储位置 |
|----------|----------|
| 报告 HTML 内容 | MySQL `t_report.content_html`（LONGTEXT）|
| 版本快照 HTML+JSON | MySQL `t_report_version` |
| 导出的 docx/PDF | MinIO bucket `reports` |
| 行情图表图片 | MinIO bucket `reports` |

---

## 3. 数据库设计

### 3.1 t_report（报告主表）

```sql
CREATE TABLE t_report (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    title           VARCHAR(500) NOT NULL COMMENT '报告标题',
    variety         VARCHAR(50) COMMENT '品种（玉米/小麦/进口粮/国产大豆/生猪）',
    report_type     VARCHAR(20) DEFAULT 'weekly' COMMENT 'weekly/monthly/special',
    status          VARCHAR(20) DEFAULT 'draft' COMMENT 'draft/published',
    current_version INT DEFAULT 0 COMMENT '当前版本号',
    generation_status VARCHAR(20) DEFAULT 'none' COMMENT 'none/generating/completed/failed',
    rich_content    LONGTEXT COMMENT '当前富文本 HTML 内容',
    author          VARCHAR(100),
    source          VARCHAR(100) DEFAULT '粮达网',
    publish_time    DATETIME,
    export_docx_path VARCHAR(500) COMMENT 'MinIO docx 路径',
    export_pdf_path  VARCHAR(500) COMMENT 'MinIO pdf 路径',
    deleted         INT DEFAULT 0,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_variety (variety),
    INDEX idx_status (status),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 3.2 t_report_version（版本快照）

```sql
CREATE TABLE t_report_version (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_id       BIGINT NOT NULL,
    version_number  INT NOT NULL,
    title           VARCHAR(500),
    rich_content    LONGTEXT COMMENT 'HTML 内容',
    editor_json     LONGTEXT COMMENT 'TipTap JSON 快照',
    editor          VARCHAR(100),
    change_summary  VARCHAR(500),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_report_version (report_id, version_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 3.3 t_report_chart（图表元数据）

```sql
CREATE TABLE t_report_chart (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_id   BIGINT NOT NULL,
    version_id  BIGINT,
    chart_type  VARCHAR(20) COMMENT 'line/table',
    variety     VARCHAR(50),
    region      VARCHAR(100),
    date_start  DATE,
    date_end    DATE,
    minio_path  VARCHAR(500),
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 3.4 t_report_template（模板）

```sql
CREATE TABLE t_report_template (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(200) NOT NULL COMMENT '模板名称（如"玉米分析周报"）',
    variety         VARCHAR(50) COMMENT '关联品种（玉米/稻米/小麦等）',
    report_type     VARCHAR(20) DEFAULT 'weekly',
    current_version INT DEFAULT 0,
    thumbnail       VARCHAR(500) COMMENT '缩略图 MinIO 路径',
    description     TEXT,
    generation_config JSON COMMENT '生成规则配置（品种/数据源/定时等）',
    deleted         INT DEFAULT 0,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_variety (variety)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 3.5 t_report_template_version（模板版本）

```sql
CREATE TABLE t_report_template_version (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_id     BIGINT NOT NULL,
    version_number  INT NOT NULL,
    name            VARCHAR(200),
    rich_content    LONGTEXT COMMENT '含占位符的模板 HTML',
    editor_json     LONGTEXT COMMENT 'TipTap JSON 快照',
    editor          VARCHAR(100),
    change_summary  VARCHAR(500),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_template_version (template_id, version_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 3.6 t_report_generation_log（生成日志）

```sql
CREATE TABLE t_report_generation_log (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_id    BIGINT NOT NULL,
    execution_id VARCHAR(50),
    status       VARCHAR(20),
    step         VARCHAR(50),
    message      TEXT,
    duration_ms  INT,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 4. 数据流

### 4.1 AI 生成流

```
用户点击"AI 生成"
  → 选择模板（玉米分析周报/稻米分析周报）和日期范围
  → POST /api/reports/{id}/generate{?template_id=}
  → ReportGenerationService (@Async):
    1. 加载模板（t_report_template_version.rich_content）
    2. 解析模板中的占位符 {{PRICE_TABLE:玉米,港口}}
    3. 查询 t_price 填充数据
    4. 查询 t_knowledge_base 填充市场分析
    5. 渲染为完整 HTML（含 ECharts 图表 img）
    6. 保存到 t_report_version（version 1）
    7. 更新 t_report.generation_status = completed
  → 前端轮询 GET /reports/{id}/generation-status
  → 完成后跳转 ReportEditor 展示内容
```

### 4.2 编辑保存流

```
编辑器中编辑内容
  → 点击"保存"
  → 前端输出：标准 HTML + TipTap JSON
  → POST /api/reports/{id}/save
    请求体：{ title, rich_content, editor_json, change_summary }
  → 后端：
    1. INSERT t_report_version（version_number + 1）
    2. UPDATE t_report.current_version, rich_content
  → 返回版本号
```

### 4.3 导出流

```
用户点击"导出"
  → POST /api/reports/{id}/export{?version=}
  → ReportExportService:
    1. 读取 t_report_version.rich_content
    2. HTML → POST Gotenberg LibreOffice 端点 → docx
    3. HTML → POST Gotenberg Chromium 端点 → PDF
    4. docx/PDF 上传 MinIO bucket "reports"
    5. UPDATE t_report.export_docx_path/export_pdf_path
  → 返回下载 URL
```

### 4.4 版本回滚流

```
查看版本历史
  → 选择某个版本
  → POST /api/reports/{id}/versions/{v}/restore
  → 后端：
    1. 读取 t_report_version 的 rich_content + editor_json
    2. UPDATE t_report.rich_content = 选中版本的内容
    3. 新增一条 version（版本号递增，change_summary="回滚到版本 v"）
  → 前端重新加载编辑器内容
```

---

## 5. 编辑器功能清单

### 5.1 工具栏

| 功能 | 实现方式 |
|------|----------|
| 标题 H1/H2/H3 | `@tiptap/extension-heading` |
| 字号/字体 | `@tiptap/extension-font-family` + `@tiptap/extension-text-style` |
| 加粗/斜体/下划线 | StarterKit + `@tiptap/extension-underline` |
| **涨跌红色** | 自定义 Mark → `<span style="color:#d32f2f">` |
| **跌绿色** | 自定义 Mark → `<span style="color:#2e7d32">` |
| 表格 | `@tiptap/extension-table` + row/cell/header |
| 合并单元格 | Table extension 原生支持 |
| 插入行情图表 | ChartInsertDialog → ECharts → html2canvas → MinIO → `<img>` |
| 分页符 | `@tiptap/extension-page-break` |
| 预览切换 | 切换 `editor.setEditable(!editable)` |

### 5.2 模板与占位符

#### 模板管理

- 模板用 TipTap 编辑，和普通报告同一套编辑器
- 模板内容含 `{{...}}` 数据占位符，生成时后端替换
- 模板有独立的 **版本管理**（`t_report_template_version`），修改模板后保存新版本
- 新建报告时**先选模板**，基于模板生成草稿，再人工微调

#### 占位符规范

| 占位符 | 说明 | 示例 |
|--------|------|------|
| `{{PRICE_TABLE:品种,地区}}` | 价格数据表格 | `{{PRICE_TABLE:玉米,锦州港}}` |
| `{{PRICE_CHART:品种,地区,天数}}` | 价格走势图 | `{{PRICE_CHART:玉米,锦州港,30}}` |
| `{{PRICE_COMPARISON:品种,区域类型}}` | 多区域对比 | `{{PRICE_COMPARISON:玉米,港口}}` |
| `{{DATE_RANGE}}` | 报告覆盖日期范围 | 自动填充 |
| `{{GENERATE_DATE}}` | 生成日期 | 自动填充 |
| `{{WEEK_NUMBER}}` | 周数 | 如"第26周" |

### 5.3 生成规则配置

每个模板关联一份 **JSON 配置**，定义生成时拉什么数据、搜哪些知识库。存储在 `t_report_template.generation_config` 字段。

```json
{
  "variety": "玉米",
  "default_days": 7,
  "price_data": {
    "tables": [
      {"region": "港口", "area_type": "port"},
      {"region": "东北", "area_type": "enterprise"},
      {"region": "华北", "area_type": "enterprise"}
    ],
    "comparison": {"area_type": "port"},
    "trend_charts": [
      {"region": "锦州港", "days": 30},
      {"region": "蛇口港", "days": 30}
    ]
  },
  "knowledge_search": {
    "categories": ["玉米周报", "玉米晨报"],
    "max_results": 5,
    "days_range": 30
  },
  "schedule": {
    "enabled": false,
    "cron": "0 9 * * MON",
    "variety": "玉米"
  }
}
```

#### 配置 vs 占位符的关系

```
模板 HTML（排版结构）         生成配置（数据规则）
─────────────────           ─────────────────
{{PRICE_TABLE:玉米,港口}}  ←  price_data.tables[0]
{{PRICE_CHART:锦州港,30}} ←  price_data.trend_charts[0]
{{KNOWLEDGE_SUMMARY}}     ←  knowledge_search
{{DATE_RANGE}}            ←  自动计算
```

**关键原则：** 模板管「放在哪」，配置管「取什么」。两者独立修改：
- 编辑模板不改变数据规则
- 修改配置不改变排版结构
- 生成时两者合并：按配置拉数据 → 按模板位置插入

#### 生成配置 UI

在模板编辑器中，右侧增加一个「数据规则」面板（非 TipTap 工具栏）：

```
┌─────────────────────┬──────────────────────┐
│  TipTap 编辑器       │  数据规则             │
│                     │                      │
│  {{PRICE_TABLE}}    │  品种: [玉米    ▼]    │
│  {{PRICE_CHART}}    │  港口对比: [✅]       │
│  {{KNOWLEDGE}}      │  东北产区: [✅]       │
│                     │  华北产区: [✅]       │
│                     │  走势图:             │
│                     │    ┌──────────────┐ │
│                     │    │ 锦州港  30天  │ │
│                     │    │ 蛇口港  30天  │ │
│                     │    └──────────────┘ │
│                     │  知识库: [玉米周报  ▼]│
│                     │  定时: [☐ 每周一9:00]│
│                     │  [保存配置]          │
└─────────────────────┴──────────────────────┘
```

配置保存为 JSON 写入 `t_report_template.generation_config`，生成时 ReportGenerationService 读取。

#### 后端解析流程

1. 读取模板 HTML + generation_config
2. 正则提取 `{{TYPE:params}}` 占位符列表
2. 按类型路由到不同的数据查询器：
   - `PRICE_TABLE` → PriceMapper 查 t_price → 渲染 HTML 表格
   - `PRICE_CHART` → 查 t_price → 生成 ECharts 图片 → 上传 MinIO → `<img>` 标签
   - `PRICE_COMPARISON` → 查 t_price 多区域数据 → HTML 对比表
   - `DATE_RANGE` / `WEEK_NUMBER` → 根据生成日期计算
3. 替换占位符为实际内容
4. 返回完整 HTML

### 5.3 HTML 输出规范

```
1. 表格统一加固定列宽属性（<col width="100">），防止 Word 导出变形
2. 涨跌高亮使用 <span style="color:..."> 内联样式
3. 图片使用完整 MinIO URL（<img src="http://minio:9000/reports/xxx.png">）
4. 分页符使用 <div style="page-break-after: always;"></div>
5. 导出前清理 TipTap 的 data-* 属性和 ProseMirror 类
```

---

## 6. 后端 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/reports` | 分页列表（品种/状态/时间过滤） |
| POST | `/api/reports` | 创建报告 |
| GET | `/api/reports/{id}` | 报告详情 |
| PUT | `/api/reports/{id}` | 更新元数据 |
| DELETE | `/api/reports/{id}` | 软删除 |
| POST | `/api/reports/{id}/generate` | 触发 AI 生成（异步） |
| GET | `/api/reports/{id}/generation-status` | 轮询生成状态 |
| POST | `/api/reports/{id}/save` | 保存版本 |
| GET | `/api/reports/{id}/versions` | 版本列表 |
| POST | `/api/reports/{id}/versions/{v}/restore` | 回滚 |
| POST | `/api/reports/{id}/export` | 导出 docx + PDF |
| GET | `/api/reports/{id}/download?type=docx\|pdf` | 下载文件 |
| POST | `/api/reports/upload-image` | 上传编辑器图片到 MinIO |
| GET | `/api/reports/templates` | 模板列表 |
| POST | `/api/reports/templates` | 创建模板 |
| GET | `/api/reports/templates/{id}` | 模板详情（含当前版本内容） |
| PUT | `/api/reports/templates/{id}` | 更新模板 |
| DELETE | `/api/reports/templates/{id}` | 删除模板 |
| POST | `/api/reports/templates/{id}/save` | 保存模板版本 |
| GET | `/api/reports/templates/{id}/versions` | 模板版本列表 |
| POST | `/api/reports/templates/{id}/versions/{v}/restore` | 回滚模板版本 |

---

## 7. 前端路由

```
路径                    | 组件              | 菜单名称   | 说明
/reports                | ReportList.vue    | 报告模板   | 基于模板生成的报告列表
/reports/editor/:id     | ReportEditor.vue  | —          | 报告编辑器（新建/编辑）
/reports/templates      | TemplateList.vue  | 模板列表   | 模板管理列表
/reports/templates/editor/:id | ReportEditor.vue | —     | 模板编辑器（含占位符工具栏）
/reports/templates/new  | ReportEditor.vue  | —          | 新建模板
```

侧边栏菜单：报告模板 + 模板列表 两个入口平级。

---

## 9. 前端组件

```
src/views/reports/
├── ReportList.vue                    # 列表页（搜索/筛选/新建）
├── ReportEditor.vue                  # 编辑器页（核心）
├── components/
│   ├── ChartInsertDialog.vue         # 插入图表弹窗
│   ├── GenerateDialog.vue            # AI 生成配置弹窗（选模板+品种+日期）
│   ├── VersionHistoryDialog.vue      # 版本历史弹窗（支持模板和报告）
│   └── TemplateManager.vue           # 模板列表/编辑/版本管理
├── composables/
│   └── useReportList.ts              # 列表逻辑
```

---

## 10. 实施阶段

### Phase 1：基础设施 + 后端（约 3 天）

| 步骤 | 内容 | 产出 |
|------|------|------|
| 1 | Gotenberg docker-compose + 字体 + MinIO bucket | 基础设施就绪 |
| 2 | Flyway V13 迁移脚本 | t_report 等 4 张表 |
| 3 | Entity 4 个 + Mapper 3 个 + XML | 数据层 |
| 4 | ReportService + 版本管理 | CRUD + 版本逻辑 |
| 5 | ReportGenerationService | AI 生成编排 |
| 6 | ReportExportService | Gotenberg + MinIO |
| 7 | ReportController | 全部 API 端点 |
| 8 | AsyncConfig + application.yml | 配置 |

### Phase 2：前端（约 3 天）

| 步骤 | 内容 | 产出 |
|------|------|------|
| 9 | 安装 TipTap 依赖 + 配置 | 构建通过 |
| 10 | API 模块 report.ts | 前端 API |
| 11 | 路由 + 侧边栏菜单 | 页面可访问 |
| 12 | ReportEditor.vue + 工具栏 | 核心编辑器 |
| 13 | ChartInsertDialog（ECharts + html2canvas） | 图表插入 |
| 14 | ReportList.vue + GenerateDialog | 列表页 |
| 15 | VersionHistoryDialog | 版本管理 |

### Phase 3：联调验证（约 1 天）

| 步骤 | 内容 |
|------|------|
| 16 | 端到端：创建报告 → AI 生成 → 编辑 → 保存版本 → 导出 docx/PDF → 下载 |
| 17 | 版本：保存多次 → 版本列表 → 回滚 → 内容恢复 |
| 18 | 图表：插入行情图 → 保存 → 导出 → Word 中图片可见 |

---

## 11. 约束与注意事项

| 约束 | 规则 |
|------|------|
| 付费组件 | **禁止引入**任何付费 Pro 编辑器插件，导出全部走 Gotenberg |
| 表格导出 | 加固定列宽 `<col width="...">`，防止 Word 自动变形 |
| 涨跌色 | 统一使用 `style="color:..."` 内联样式，不用 CSS class |
| 图片路径 | HTML 中用完整 MinIO URL，导出时 Gotenberg 可下载 |
| 分页符 | TipTap page-break 扩展 + `<div style="page-break-after:always">` |
| HTML 清洗 | 保存前去掉 ProseMirror 的 data-* 和多余 class |
| 并发导出 | Gotenberg 配置 max-concurrent-requests=5，超时 60s |
| 版本上限 | 单报告/单模板最多保留 50 个版本，超出自动删除最旧版本 |
| 模板使用 | 报告创建时**必须选择模板**，基于模板生成草稿后再编辑 |
| 模板占位符 | 所有数据占位符统一 `{{TYPE:params}}` 格式，生成时服务端替换 |
| 模板编辑 | 模板在 ReportEditor 中编辑，和普通报告同一套工具栏 |
