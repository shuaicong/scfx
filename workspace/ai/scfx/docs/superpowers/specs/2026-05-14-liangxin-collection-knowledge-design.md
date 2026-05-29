# 粮信网采集与知识库集成设计

## 1. 系统概述

### 1.1 背景

粮信网是中国领先的粮油行业门户网站，提供玉米、小麦、稻谷等品种的市场晨报、日报等报告。为实现自动化采集并统一管理采集数据，需设计与知识库系统深度集成的采集模块。

### 1.2 目标

- 实现粮信网玉米晨报/日报的自动化采集
- 采集数据直接存入知识库，统一管理
- 提供透明化的采集状态展示
- 为AI问答提供知识支撑

### 1.3 采集范围

| 品种 | 报告类型 | 每日数量 | 采集时间 |
|------|----------|----------|----------|
| 玉米 | 晨报 | 1条 | 09:30（重试10:00、10:30） |
| 玉米 | 日报 | 1条 | 18:30（重试19:00、19:30） |

> 后续可扩展到其他品种（小麦、大豆、稻谷等）

---

## 2. 技术架构

### 2.1 系统架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           采集调度层 (:5001)                                │
│  ┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐   │
│  │   Cron定时任务   │ ──▶ │  粮信网采集器   │ ──▶ │  知识库API      │   │
│  │  (APScheduler)   │     │  (Playwright)    │     │  (:5002)        │   │
│  └─────────────────┘     └─────────────────┘     └─────────────────┘   │
│                                    │                                        │
│                                    ▼                                        │
│                           ┌─────────────────┐                             │
│                           │   采集日志库     │                             │
│                           │   (MySQL)       │                             │
│                           └─────────────────┘                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 技术选型

| 组件 | 技术 | 说明 |
|------|------|------|
| 采集器 | Python + Playwright | 模拟浏览器登录，支持动态内容 |
| 定时调度 | APScheduler | Python定时任务框架 |
| 知识库API | FastAPI (:5002) | 已有系统 |
| 向量化 | BGE + Qdrant | 已有系统 |

### 2.3 目录结构

```
python-collector-sdk/
├── collectorsdk/
│   ├── __init__.py
│   ├── config.py              # 配置管理
│   ├── dimensions.py          # 维度枚举
│   ├── reporter.py            # 上报器
│   ├── collectors/
│   │   ├── __init__.py
│   │   └── liangxin.py       # 粮信网采集器（新增）
│   └── utils.py
├── scheduler/
│   ├── __init__.py
│   └── corn_scheduler.py      # 玉米采集调度器（新增）
├── main.py
└── requirements.txt
```

---

## 3. 数据模型

### 3.1 采集日志表 (t_collection_log)

```sql
CREATE TABLE IF NOT EXISTS t_collection_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    execution_id VARCHAR(64) NOT NULL UNIQUE,  -- 执行ID
    source VARCHAR(50) NOT NULL,                -- liangxin
    subject VARCHAR(50) NOT NULL,              -- corn
    coll_type VARCHAR(50) NOT NULL,             -- login_crawl
    report_type VARCHAR(20),                   -- 晨报/日报
    variety VARCHAR(20),                        -- 玉米
    status VARCHAR(20) NOT NULL,               -- running/success/failed
    collected_count INT DEFAULT 0,            -- 采集数量
    error_message TEXT,                        -- 错误信息
    start_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    end_time TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_source_subject (source, subject),
    INDEX idx_status (status),
    INDEX idx_start_time (start_time)
);
```

### 3.2 知识库表扩展 (t_knowledge_base)

```sql
-- 已有字段使用说明
source_type = 'collection'              -- 固定值
source_name = '粮信网'                   -- 来源名称
original_url = '报告原文URL'             -- 原文链接
author = '粮信网编辑'                    -- 作者
publish_time = '报告发布时间'            -- 发布时间
content = '报告正文'                     -- 全文内容

-- 新增字段（建议扩展）
variety VARCHAR(20),                    -- 品种: 玉米/小麦/大豆
report_type VARCHAR(20),                -- 报告类型: 晨报/日报/周报
collected_at TIMESTAMP,                  -- 采集入库时间
collection_execution_id VARCHAR(64),    -- 关联采集执行ID
```

---

## 4. API设计

### 4.1 采集调度API

```
POST /collector/schedule/corn
Description: 手动触发玉米采集任务

Request:
{
  "reportTypes": ["morning", "evening"],  // 可选，默认全部
  "retryCount": 3                           // 可选，默认3
}

Response:
{
  "code": 200,
  "data": {
    "executionId": "corn-20260514-001",
    "message": "采集任务已启动"
  }
}
```

### 4.2 采集日志API

```
GET /collector/logs
Description: 获取采集日志列表

Query:
  - source: 来源筛选 (liangxin)
  - subject: 品种筛选 (corn)
  - status: 状态筛选 (success/failed/running)
  - startDate: 开始日期
  - endDate: 结束日期
  - page: 页码
  - size: 每页数量

Response:
{
  "code": 200,
  "data": {
    "records": [
      {
        "executionId": "corn-20260514-001",
        "source": "liangxin",
        "subject": "corn",
        "reportType": "morning",
        "variety": "玉米",
        "status": "success",
        "collectedCount": 1,
        "startTime": "2026-05-14T09:35:00",
        "endTime": "2026-05-14T09:35:30",
        "errorMessage": null
      }
    ],
    "total": 100,
    "pages": 10,
    "current": 1
  }
}
```

### 4.3 采集状态查询API

```
GET /collector/status
Description: 获取各品种最后采集状态

Response:
{
  "code": 200,
  "data": {
    "liangxin-corn-morning": {
      "source": "liangxin",
      "subject": "corn",
      "reportType": "morning",
      "lastCollectedAt": "2026-05-14T09:35:00",
      "lastStatus": "success",
      "lastExecutionId": "corn-20260514-001"
    },
    "liangxin-corn-evening": {
      "source": "liangxin",
      "subject": "corn",
      "reportType": "evening",
      "lastCollectedAt": "2026-05-13T18:35:00",
      "lastStatus": "success",
      "lastExecutionId": "corn-20260513-001"
    }
  }
}
```

---

## 5. 采集流程

### 5.1 采集流程图

```
┌────────────────────────────────────────────────────────────────────────────┐
│                           采集主流程                                      │
└────────────────────────────────────────────────────────────────────────────┘

1. 定时触发（09:30 / 18:30）
        │
        ▼
2. 启动Playwright浏览器
        │
        ▼
3. 访问登录页面 → 填充账号密码 → 提交登录
        │
        ▼
4. 检查登录状态 ──失败──▶ 记录错误日志 ──▶ 重试(最多3次)
        │成功
        ▼
5. 访问报告列表页
   URL: https://my.chinagrain.cn/jinnong/liangyou_news.htm?type=7008&producttype=1061
        │
        ▼
6. 解析报告列表，筛选今日报告
        │
        ▼
7. 遍历每个报告URL ──▶ 访问详情页 ──▶ 解析正文
        │
        ▼
8. 调用知识库API存入
   POST /api/knowledge/ingest
        │
        ▼
9. 记录采集日志
        │
        ▼
10. 完成
```

### 5.2 去重机制

```python
def is_duplicate(title: str, publish_time: str) -> bool:
    """
    按 标题+发布时间 去重
    """
    # 计算内容hash
    content_hash = hashlib.sha256(f"{title}:{publish_time}".encode()).hexdigest()

    # 查询知识库是否已存在
    existing = knowledge_base_service.count(
        LambdaQueryWrapper<KnowledgeBase>()
            .eq(KnowledgeBase::getTitle, title)
            .eq(KnowledgeBase::getPublishTime, publish_time)
            .eq(KnowledgeBase::getDeleted, 0)
    )
    return existing > 0
```

### 5.3 边界情况处理

| 情况 | 处理方式 |
|------|----------|
| 报告未发布 | 多次重试（09:30→10:00→10:30） |
| 当日无报告 | 跳过，记录日志"当日无新报告" |
| 节假日无报告 | 跳过，不报错 |
| 登录失败 | 重试3次，失败记录错误 |
| 网络超时 | 重试3次，失败记录错误 |
| 解析失败 | 跳过该条，记录错误，继续下一条 |

---

## 6. 前端展示优化

### 6.1 Knowledge.vue 增强

#### 6.1.1 采集状态提示

在知识库页面顶部添加采集状态栏：

```vue
<!-- 新增：采集状态栏 -->
<div class="collection-status-bar" v-if="showCollectionStatus">
  <div class="status-item success">
    <span class="status-icon">✓</span>
    <span class="status-text">粮信网-玉米晨报 最后采集: 2026-05-14 09:35</span>
  </div>
  <div class="status-item success">
    <span class="status-icon">✓</span>
    <span class="status-text">粮信网-玉米日报 最后采集: 2026-05-13 18:35</span>
  </div>
  <div class="status-item failed">
    <span class="status-icon">✕</span>
    <span class="status-text">粮信网-小麦晨报 采集失败</span>
    <button class="retry-btn" @click="retryCollection('liangxin-wheat-morning')">重试</button>
  </div>
</div>
```

#### 6.1.2 时间范围筛选

在筛选栏增加时间选择器：

```vue
<!-- 新增：时间范围筛选 -->
<div class="filter-group">
  <span class="filter-label">时间:</span>
  <div class="filter-tags">
    <button class="filter-tag" :class="{ active: timeRange === 'today' }" @click="setTimeRange('today')">今天</button>
    <button class="filter-tag" :class="{ active: timeRange === 'week' }" @click="setTimeRange('week')">本周</button>
    <button class="filter-tag" :class="{ active: timeRange === 'month' }" @click="setTimeRange('month')">本月</button>
    <button class="filter-tag" :class="{ active: timeRange === 'custom' }" @click="showCustomDatePicker">
      {{ customDateLabel }}
    </button>
  </div>
</div>
```

#### 6.1.3 内容搜索

在搜索框增加内容搜索功能：

```vue
<!-- 增强：搜索框 -->
<div class="search-box">
  <select v-model="searchScope" class="search-scope-select">
    <option value="title">标题</option>
    <option value="content">内容</option>
    <option value="all">全部</option>
  </select>
  <input
    v-model="searchKeyword"
    type="text"
    placeholder="搜索知识..."
    @keyup.enter="doSearch"
  />
</div>
```

### 6.2 AI问答集成

#### 6.2.1 预览面板增强

在知识预览面板添加AI问答入口：

```vue
<!-- 预览面板中的AI按钮 -->
<div class="preview-actions">
  <button class="btn btn-primary" @click="askAI">
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
      <circle cx="12" cy="12" r="10"/>
      <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
      <line x1="12" y1="17" x2="12.01" y2="17"/>
    </svg>
    问问AI
  </button>
</div>
```

#### 6.2.2 周报汇总功能

在知识列表页面添加AI汇总入口：

```vue
<!-- 新增：AI汇总按钮 -->
<div class="ai-summary-bar" v-if="selectedItems.length > 0 && isSameVariety">
  <button class="btn btn-secondary" @click="summarizeWithAI">
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
      <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
    </svg>
    AI总结本周{{ currentVariety }}
  </button>
</div>
```

### 6.3 移动端适配

#### 6.3.1 响应式布局

```css
/* 移动端适配 */
@media (max-width: 768px) {
  .sidebar {
    display: none;  /* 隐藏侧边栏 */
  }

  .list-area {
    width: 100%;
  }

  .preview-panel {
    position: fixed;
    bottom: 0;
    left: 0;
    right: 0;
    height: 60vh;
    z-index: 100;
  }
}
```

### 6.4 回收站功能

#### 6.4.1 回收站页面

```vue
<!-- 回收站页面 -->
<template>
  <div class="trash-container">
    <div class="trash-header">
      <h2>回收站</h2>
      <button class="btn" @click="emptyTrash" v-if="trashItems.length > 0">
        清空回收站
      </button>
    </div>

    <div class="trash-list">
      <div v-for="item in trashItems" :key="item.id" class="trash-item">
        <div class="item-info">
          <span class="item-title">{{ item.title }}</span>
          <span class="item-meta">删除于 {{ item.deletedAt }}</span>
        </div>
        <div class="item-actions">
          <button class="btn" @click="restoreItem(item)">恢复</button>
          <button class="btn danger" @click="permanentDelete(item)">永久删除</button>
        </div>
      </div>
    </div>
  </div>
</template>
```

---

## 7. 实施计划

### 7.1 第一阶段：采集核心（本周）

| 任务 | 说明 |
|------|------|
| 粮信网采集器开发 | Playwright登录+解析 |
| 采集调度器开发 | APScheduler定时任务 |
| 知识库API对接 | /api/knowledge/ingest |
| 去重机制实现 | 标题+发布时间唯一 |
| 本地测试验证 | 手动触发采集 |

### 7.2 第二阶段：状态透明（第二周）

| 任务 | 说明 |
|------|------|
| 采集日志API | /collector/logs |
| 采集状态API | /collector/status |
| 状态栏UI | Knowledge.vue顶部 |
| 失败重试UI | 点击重试按钮 |

### 7.3 第三阶段：体验优化（第三周）

| 任务 | 说明 |
|------|------|
| 时间范围筛选 | 今天/本周/本月/自定义 |
| 内容搜索增强 | 支持搜索正文 |
| AI问答集成 | 问问AI按钮 |
| AI周报汇总 | 总结本周功能 |

### 7.4 第四阶段：安全增强（第四周）

| 任务 | 说明 |
|------|------|
| 回收站功能 | 删除→回收站 |
| 移动端适配 | 响应式布局 |
| 性能优化 | 分页+索引 |

---

## 8. 风险与对策

| 风险 | 影响 | 对策 |
|------|------|------|
| 粮信网改版 | 采集器失效 | 及时发现，快速修复 |
| 账号密码变更 | 采集中断 | 通知机制 + 配置中心 |
| VIP过期 | 无法采集 | 提前预警通知 |
| 反爬封锁 | IP被封 | 降低请求频率 + 代理池 |
