# 任务详情页 (TaskDetail) 详细设计

## 1. 概述

### 1.1 页面目的
任务详情页用于展示和管理单个采集任务的完整信息，包括脚本编辑、触发配置、执行记录等核心功能。

### 1.2 设计风格
- **主题**: 深色科技风格 (GitHub Dark)
- **色系**: 蓝色主强调色，绿色成功，红色失败，紫色版本标签
- **字体**: Inter (正文) + JetBrains Mono (代码/时间)
- **布局**: 左侧边栏 + 右侧主内容区

---

## 2. 页面布局

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  ← 返回  📰 粮信网玉米晨报采集                        [版本历史] [状态] [执行] [保存] │
│          数据源: 粮信网  触发: Cron 表达式  0 0 8 * * *  每日定时采集粮信网玉米价格数据  │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  📊 156    ✓ 153    ✗ 3    % 98%              下次执行: 明天 08:00  │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  ⏰ 触发配置                                                         │   │
│  │  ┌──────────┬──────────┬──────────────┐                              │   │
│  │  │ 单次触发 │ 周期触发 │ Cron 表达式  │                              │   │
│  │  └──────────┴──────────┴──────────────┘                              │   │
│  │                                                                      │   │
│  │  [触发配置面板内容根据选择的类型显示]                                 │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌─────────────────┐  ┌───────────────────────────────────────────────┐   │
│  │ 📋 任务信息      │  │ 📝 脚本配置                                    │   │
│  │                 │  │ [格式化] [自动换行] [跳转到行]     Ctrl+S 保存  │   │
│  │  任务名称:       │  │ ┌─────────────────────────────────────────┐  │   │
│  │  [粮信网玉米...] │  │ │  # Monaco Editor (Python)                │  │   │
│  │                 │  │ │  1  import requests                      │  │   │
│  │  数据源: 粮信网  │  │ │  2  from bs4 import BeautifulSoup        │  │   │
│  │                 │  │ │  ...                                      │  │   │
│  │  任务描述:       │  │ └─────────────────────────────────────────┘  │   │
│  │  [每日定时...]   │  │                           行 45, 列 12  [已修改] │   │
│  └─────────────────┘  └───────────────────────────────────────────────┘   │
│                                                                              │
│  ┌─────────────────┐                                                        │
│  │ 📊 执行记录      │                                                        │
│  │                 │                                                        │
│  │ ✓ 成功 08:00    │                                                        │
│  │   耗时 12.5s v3 [日志] │                                                │
│  │ ✓ 成功 08:00    │                                                        │
│  │   耗时 11.2s v3 [日志] │                                                │
│  │ ✗ 失败 08:00    │                                                        │
│  │   耗时 3.2s  v2 [日志] │                                                │
│  │                 │                                                        │
│  │  查看全部记录 →  │                                                        │
│  └─────────────────┘                                                        │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│ 全屏弹窗: 版本历史 / 执行记录                                                 │
│ ┌─────────────────────────────────────────────────────────────────────────┐ │
│ │ [×]                                                                     │ │
│ │                                                                         │ │
│ │              version-history-prototype.html 内容                        │ │
│ │              (通过 iframe 加载)                                         │ │
│ │                                                                         │ │
│ └─────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. 组件设计

### 3.1 顶部操作栏 (Header)
- **返回按钮**: 圆形按钮，左上角
- **任务标题区**:
  - 标题 + emoji 图标
  - meta-tag 显示数据源、触发类型、描述
- **操作按钮组**:
  - 版本历史 (btn-secondary)
  - 启用/禁用状态切换 (status-toggle)
  - 立即执行 (btn-primary)
  - 保存创建新版本 (btn-primary, disabled时灰色)

### 3.2 统计栏 (Stats Bar)
- 执行次数、成功次数、失败次数、成功率
- 下次执行时间
- 渐变色图标背景

### 3.3 触发配置卡片
- **单次触发**: 日期选择器 + 时间选择器（时:分:秒）
- **周期触发**:
  - 每天/每周/每月 选项卡
  - 每周: 周一至周日按钮选择
  - 每月: 日期选择 + 时间选择
  - 结束条件: 永不结束 / 指定日结束 / 重复次数结束
- **Cron 表达式**:
  - 输入框 + 计算按钮
  - 显示表达式解析结果
  - 未来5次触发时间列表

### 3.4 左侧边栏

#### 任务信息卡片
- 任务名称 (可编辑)
- 数据源 (只读)
- 任务描述 (可编辑)

#### 执行记录卡片
- 卡片式列表布局
- 每条记录显示:
  - 状态图标 (绿色✓成功 / 红色✗失败，带发光效果)
  - 执行时间 (等宽字体)
  - 耗时
  - 版本标签 (紫色背景)
  - 日志按钮 (悬停变蓝色)
- 悬停时整行右移效果
- 底部"查看全部记录 →"链接

### 3.5 右侧脚本配置区
- **工具栏**: 格式化、自动换行、跳转到行
- **Monaco Editor**:
  - vs-dark 主题
  - Python 语法高亮
  - 行号、最小化地图
  - 自动布局
- **底部状态栏**: 光标位置、修改指示器

---

## 4. 弹窗设计

### 4.1 版本历史弹窗
- 全屏覆盖，右上角关闭按钮
- 通过 iframe 加载 `version-history-prototype.html`
- URL 参数: 无 (默认显示历史版本标签页)

### 4.2 执行记录弹窗
- 全屏覆盖，右上角关闭按钮
- 通过 iframe 加载 `version-history-prototype.html?module=executions`
- 自动切换到执行记录标签页

---

## 5. 样式规范

### 5.1 颜色变量
```css
--bg-primary: #0d1117;      /* 主背景 */
--bg-secondary: #161b22;    /* 次级背景 */
--bg-card: #21262d;         /* 卡片背景 */
--bg-hover: #30363d;        /* 悬停背景 */
--border-color: #30363d;    /* 边框色 */
--text-primary: #e6edf3;    /* 主文字 */
--text-secondary: #8b949e;  /* 次级文字 */
--text-muted: #6e7681;      /* 弱化文字 */
--accent-blue: #58a6ff;      /* 蓝色强调 */
--accent-green: #3fb950;     /* 绿色成功 */
--accent-red: #f85149;       /* 红色失败 */
--accent-orange: #f0883e;    /* 橙色警告 */
--accent-purple: #a371f7;   /* 紫色版本 */
```

### 5.2 字体
- 正文: Inter, 13-14px
- 代码/时间: JetBrains Mono, 12-13px
- 标题: Inter, 16-20px, 600-700 weight

### 5.3 间距
- 页面边距: 20-24px
- 卡片内边距: 14-18px
- 元素间距: 8-16px
- 圆角: 6-12px

---

## 6. 交互设计

### 6.1 键盘快捷键
| 快捷键 | 功能 |
|--------|------|
| Ctrl+S | 保存脚本 |
| Ctrl+Enter | 立即执行 |
| Escape | 关闭弹窗 |

### 6.2 状态切换
- 启用中: 绿色胶囊按钮，带呼吸动画圆点
- 已禁用: 灰色胶囊按钮
- 切换后更新"下次执行时间"显示

### 6.3 脚本编辑
- Monaco Editor 实时编辑
- 内容变更时显示"已修改"指示器
- 保存后创建新版本

---

## 7. API 接口

### 7.1 任务详情
```typescript
GET /api/tasks/{id}
Response: {
  id: number
  name: string
  source: string
  description: string
  scriptContent: string
  triggerType: 'once' | 'cycle' | 'cron'
  triggerConfig: object
  status: 'enabled' | 'disabled'
  currentVersion: string
  executionCount: number
  successCount: number
  failedCount: number
  successRate: number
  nextExecutionTime: string
}
```

### 7.2 保存脚本
```typescript
PUT /api/tasks/{id}/script
Body: { scriptContent: string, changeDescription: string }
```

### 7.3 更新任务信息
```typescript
PUT /api/tasks/{id}
Body: { name: string, description: string }
```

### 7.4 启用/禁用
```typescript
POST /api/tasks/{id}/enable
POST /api/tasks/{id}/disable
```

### 7.5 立即执行
```typescript
POST /api/tasks/{id}/execute
```

### 7.6 执行记录
```typescript
GET /api/tasks/{id}/executions
Response: {
  items: [{
    id: string
    status: 'success' | 'failed'
    startTime: string
    duration: number
    version: string
    errorMessage?: string
  }]
}
```

### 7.7 版本历史
```typescript
GET /api/tasks/{id}/versions
Response: {
  items: [{
    version: string
    createdAt: string
    createdBy: string
    changeDescription: string
    triggerType: string
  }]
}
```

---

## 8. 文件结构

```
docs/superpowers/specs/
├── task-detail-prototype.html      # 任务详情页原型
├── version-history-prototype.html  # 版本历史原型 (复用)
└── 2026-04-30-TaskDetail详细设计.md # 本文档
```

---

## 9. 实现检查清单

| 功能 | 状态 | 说明 |
|------|------|------|
| 页面布局框架 | ✅ | Header + Stats Bar + Trigger + Sidebar + Main |
| Monaco Editor 集成 | ✅ | Python 语法高亮，工具栏 |
| 触发配置 | ✅ | 单次/周期/Cron 三种模式 |
| 任务信息编辑 | ✅ | 名称、描述可编辑 |
| 执行记录列表 | ✅ | 状态/时间/耗时/版本/日志 |
| 版本历史弹窗 | ✅ | iframe 加载 version-history-prototype.html |
| 执行记录弹窗 | ✅ | iframe 加载 version-history-prototype.html?module=executions |
| 启用/禁用切换 | ✅ | 美化的状态切换按钮 |
| 键盘快捷键 | ✅ | Ctrl+S 保存, Ctrl+Enter 执行, Escape 关闭 |
