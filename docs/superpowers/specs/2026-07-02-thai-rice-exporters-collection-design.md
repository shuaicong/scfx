# 泰国大米出口商协会数据采集设计

> **版本:** v1.0 (草案)
> **日期:** 2026-07-02
> **状态:** 设计草案 — 暂停中

---

## 1. 概述

采集泰国大米出口商协会（Thai Rice Exporters Association）官网的泰国大米出口价格、出口统计等数据，补充系统东南亚大米市场数据维度。

**网站:** http://www.thairiceexporters.or.th/default_eng.htm

## 2. 可采集数据

| # | 数据类 | 页面路径 | 覆盖期间 | 更新频率 | 格式 |
|---|--------|---------|---------|---------|------|
| 1 | 周度出口价格 (FOB $/吨) | `price_eng.html` | 当前周 | 每周 | HTML 表格 |
| 2 | 历史月均价格 | `database/AVG prices {year}.html` | 1998~2026 | 年度 | HTML 表格 |
| 3 | 出口量（按品种） | `statistic_{year}.html` | 2002~2026 | 年度 | HTML 表格 |
| 4 | 出口量（按目的地） | `export by country {year}.html` | 2002~2026 | 年度 | HTML 表格 |
| 5 | 汇率 THB/USD | `exchange rate.htm` | 1982~2026 | 低频 | HTML 表格 |
| 6 | 市场报告 | `Rice_reports_eng.htm` | - | - | 仅外部链接，跳过 |

**价格品种：** White Rice 5%/25%/35%/100% Broken, Thai Hom Mali Premium/100%, Parboiled 5%/10%/100%, Jasmine, Glutinous Rice, Pathumthani Aroma 等

## 3. 技术要点

- 与 CONAB 不同，数据为结构化 HTML 表格（非 PDF），需要 HTML 解析
- 存储方案待定：专用业务表 vs 知识库富文本
- Python 采集器复用 BaseCollector
- 历史回填量较大（1998~2026 共 28 年 × 多维度）

## 4. 待定事项

- [ ] 存储方案选择（业务表 / 知识库）
- [ ] 是否纳入采集任务管理（cron 定时）
- [ ] 是否需要知识库分类

---

> **状态：** 设计暂停。待采集任务优先级确认后继续。
