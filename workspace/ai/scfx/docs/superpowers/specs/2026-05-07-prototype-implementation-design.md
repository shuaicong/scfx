# 原型页面实现设计规格

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task.

**Goal:** 将 4 个 HTML 原型页面完整重新实现为 Vue 3 组件，使用深色主题风格

**Architecture:** Vue 3 + TypeScript + Element Plus + Monaco Editor (CDN) + Mock Data

**Tech Stack:** Vue 3, TypeScript, Element Plus, Monaco Editor, CSS Variables (Dark Theme)

---

## 1. 设计语言

### 1.1 色彩系统 (Dark Theme)

```css
--bg-primary: #0d1117;     /* 主背景 */
--bg-secondary: #161b22;     /* 次级背景 */
--bg-card: #21262d;         /* 卡片背景 */
--bg-hover: #30363d;        /* 悬停状态 */
--border-color: #30363d;    /* 边框 */
--text-primary: #e6edf3;    /* 主文本 */
--text-secondary: #8b949e;  /* 次级文本 */
--text-muted: #6e7681;      /* 辅助文本 */
--accent-blue: #58a6ff;     /* 蓝色强调 */
--accent-green: #3fb950;    /* 绿色强调 */
--accent-orange: #f0883e;    /* 橙色强调 */
--accent-purple: #a371f7;    /* 紫色强调 */
--accent-red: #f85149;      /* 红色强调 */
--accent-yellow: #d29922;    /* 黄色强调 */
```

### 1.2 字体

- 主字体: `Inter, -apple-system, BlinkMacSystemFont, sans-serif`
- 代码字体: `'JetBrains Mono', 'Fira Code', Consolas, monospace`

### 1.3 间距系统

- 页面内边距: 24px
- 卡片内边距: 16-20px
- 元素间距: 8-16px
- 圆角: 6-12px

---

## 2. 页面规格

### 2.1 任务列表页 (TaskList)

**路由:** `/scripts`

**功能:**
- 统计卡片行: 总数/启用/禁用/今日执行/累计成功/累计失败
- 筛选栏: 状态下拉、触发方式下拉、数据源下拉、关键词搜索
- 数据表格: 复选框选择、任务名称、状态标签、数据源标签、触发方式、下次执行、最近执行、执行统计、操作按钮
- 批量操作栏: 批量启用、批量禁用
- 分页组件

**组件结构:**
```
TaskList.vue
├── StatsCards (统计卡片行)
├── FilterBar (筛选栏)
├── TaskTable (数据表格)
│   ├── TableHeader
│   ├── TableRow × N
│   └── BatchActionsBar
└── Pagination (分页)
```

### 2.2 任务详情页 (TaskDetail)

**路由:** `/scripts/:id`

**功能:**
- Header: 返回按钮、任务名称、数据源标签、触发标签、Cron表达式、描述
- 状态切换按钮 (启用/禁用)
- 立即执行按钮
- 保存按钮
- 统计卡片行: 执行次数/成功/失败/成功率/下次执行
- 触发配置面板: 单次/周期/Cron 三种模式
- 左侧边栏: 任务信息表单、执行记录列表
- 右侧主区域: Monaco 脚本编辑器
- 版本历史弹窗 (iframe 嵌入)

**组件结构:**
```
TaskDetail.vue
├── DetailHeader (详情头部)
├── StatsBar (统计卡片)
├── TriggerConfigCard (触发配置)
├── ContentGrid (内容网格)
│   ├── LeftSidebar
│   │   ├── TaskInfoForm
│   │   └── ExecutionRecordList
│   └── MainContent
│       └── ScriptEditorCard
├── VersionHistoryDialog
└── ExecutionDialog
```

### 2.3 创建任务页 (CreateTask)

**路由:** `/scripts/create`

**功能:**
- Header: 返回按钮、页面标题
- 基本信息表单: 任务名称、任务描述
- 脚本配置: 单文件脚本编辑器 (Monaco) / 文件上传
- 触发配置: 单次触发/周期触发/Cron 表达式
- 操作按钮: 取消、保存、保存并启动

**组件结构:**
```
CreateTask.vue
├── PageHeader (页面头部)
├── FormSections
│   ├── BasicInfoSection (基本信息)
│   ├── ScriptConfigSection (脚本配置)
│   │   ├── ScriptTypeSelector
│   │   ├── MonacoEditor
│   │   └── FileUploader
│   └── TriggerConfigSection (触发配置)
│       ├── TriggerTypeSelector
│       ├── OnceTriggerPanel
│       ├── CycleTriggerPanel
│       └── CronTriggerPanel
└── FormActions (操作按钮)
```

### 2.4 版本历史页 (VersionHistory)

**路由:** `/scripts/:id/versions`

**功能:**
- Top Navigation: 返回按钮、页面标题、任务名称
- Tab 切换: 历史版本 / 执行记录
- 左侧时间线: 版本列表（当前版本高亮）
- 右侧详情面板: 版本信息、触发配置、变更说明、脚本内容
- 版本对比功能 (Monaco Diff Editor)
- 回滚弹窗
- 执行记录表格 (分页)

**组件结构:**
```
VersionHistory.vue
├── TopNav (顶部导航)
├── ModuleTabs (模块切换)
├── MainLayout
│   ├── LeftPanel (时间线)
│   │   └── TimelineItem × N
│   └── RightPanel (详情)
│       ├── VersionDetail
│       │   ├── VersionHeader
│       │   ├── TriggerConfigCard
│       │   ├── ChangeDescCard
│       │   └── ScriptContentCard
│       └── DiffPanel (对比面板)
├── ExecutionsPanel (执行记录)
├── VersionSelectorModal (版本选择弹窗)
├── RollbackModal (回滚弹窗)
└── LogModal (日志弹窗)
```

---

## 3. 共享组件

### 3.1 需要创建

| 组件 | 路径 | 说明 |
|------|------|------|
| DarkCard | `components/DarkCard.vue` | 深色主题卡片容器 |
| StatusBadge | `components/StatusBadge.vue` | 状态标签 (enabled/disabled) |
| SourceTag | `components/SourceTag.vue` | 数据源标签 |
| TriggerBadge | `components/TriggerBadge.vue` | 触发方式标签 |
| TimelineItem | `components/TimelineItem.vue` | 时间线项 |
| CronInput | `components/CronInput.vue` | Cron 表达式输入器 |
| TimePicker | `components/TimePicker.vue` | 时间选择器 |
| DatePicker | `components/DatePicker.vue` | 日期选择器 |
| WeekdaySelector | `components/WeekdaySelector.vue` | 星期选择器 |
| DiffViewer | `components/DiffViewer.vue` | Monaco Diff 对比组件 |

### 3.2 已有组件复用

- `ScriptEditor.vue` - Monaco Editor 封装 (已有)
- `ExecutionLogViewer.vue` - 日志查看器 (已有)
- `TriggerConfig.vue` - 触发配置 (已有，需改造为深色主题)

---

## 4. Mock 数据策略

### 4.1 Mock Service

创建 `frontend/src/mock/` 目录:

```
mock/
├── index.ts              # 导出所有 mock 数据
├── scripts.ts            # 任务相关 mock
├── executions.ts          # 执行记录 mock
├── versions.ts            # 版本历史 mock
└── generators.ts         # 数据生成器
```

### 4.2 Mock API 层

在 `frontend/src/api/` 创建 mock 适配器:

```typescript
// mock/adapter.ts
// 拦截 API 请求，返回 mock 数据
```

### 4.3 Mock 数据结构

```typescript
// Script
interface CollectionScript {
  id: number;
  scriptName: string;
  source: string;           // liangxin, mysteel, chinagrain
  status: 'enabled' | 'disabled';
  triggerType: 'once' | 'cycle' | 'cron';
  cronExpression?: string;
  repeatType?: 'daily' | 'weekly' | 'monthly';
  weeklyDays?: string;
  monthlyDay?: number;
  executionCount: number;
  successCount: number;
  failedCount: number;
  nextExecutionTime?: string;
  lastExecutionTime?: string;
}

// Execution
interface Execution {
  id: number;
  scriptId: number;
  status: 'success' | 'failed' | 'running';
  triggerType: 'manual' | 'scheduled';
  version: string;
  duration: number;
  startTime: string;
  endTime?: string;
}

// Version
interface ScriptVersion {
  id: number;
  scriptId: number;
  version: string;           // v1, v2, v3...
  scriptContent: string;
  changeDescription: string;
  author: string;
  createdAt: string;
  isCurrent: boolean;
}
```

---

## 5. 文件结构

```
frontend/src/
├── api/
│   ├── index.ts           # 已有 API 定义
│   └── mock/
│       ├── index.ts
│       ├── scripts.ts
│       ├── executions.ts
│       └── versions.ts
├── components/
│   ├── ScriptEditor.vue   # 已有
│   ├── ExecutionLogViewer.vue  # 已有
│   └── DarkCard.vue       # 新增
│   └── StatusBadge.vue    # 新增
│   └── ...
├── composables/
│   └── useMonaco.ts       # 已有
├── views/
│   └── scripts/
│       ├── TaskList.vue   # 新增/重写
│       ├── TaskDetail.vue # 新增/重写
│       ├── CreateTask.vue # 新增
│       └── VersionHistory.vue  # 新增/重写
├── styles/
│   └── dark-theme.css     # 新增 - 全局深色主题变量
└── router/
    └── index.ts          # 更新路由
```

---

## 6. 实现顺序

### Phase 1: 基础设施
1. 创建深色主题全局样式 (`dark-theme.css`)
2. 创建共享 Dark 主题组件 (DarkCard, StatusBadge 等)
3. 设置 Mock 数据服务
4. 更新 Router 配置

### Phase 2: 页面实现
1. TaskList (任务列表页)
2. CreateTask (创建任务页)
3. TaskDetail (任务详情页)
4. VersionHistory (版本历史页)

### Phase 3: 集成与优化
1. 页面间导航集成
2. Mock 数据调优
3. 响应式适配
4. 动画与过渡效果

---

## 7. 关键实现细节

### 7.1 深色主题实现

使用 CSS Variables 实现深色主题:

```css
/* styles/dark-theme.css */
:root {
  --bg-primary: #0d1117;
  --bg-secondary: #161b22;
  --bg-card: #21262d;
  /* ... 其他变量 */
}

body.dark-theme {
  background: var(--bg-primary);
  color: var(--text-primary);
}
```

### 7.2 Monaco Editor 集成

复用已有的 `ScriptEditor.vue` 组件，保持 vs-dark 主题。

### 7.3 版本对比

使用 Monaco Diff Editor:

```typescript
const diffEditor = monaco.editor.createDiffEditor(container, {
  theme: 'vs-dark',
  renderSideBySide: true
});
diffEditor.setModel({
  original: originalModel,
  modified: modifiedModel
});
```

### 7.4 Cron 表达式解析

使用前端 Cron 解析库或自定义实现计算下次触发时间。

---

## 8. 验证标准

- [ ] 所有 4 个页面可正常访问
- [ ] 深色主题样式正确应用
- [ ] Monaco Editor 正常加载和工作
- [ ] Mock 数据正确显示
- [ ] 页面间导航正常工作
- [ ] 版本对比功能正常
- [ ] 回滚功能正常
- [ ] 无控制台错误

---

**Spec written:** 2026-05-07
**Author:** Claude
