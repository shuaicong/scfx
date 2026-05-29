# 原型页面实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 实现 4 个深色主题 Vue 页面 (TaskList, CreateTask, TaskDetail, VersionHistory)

**Architecture:** Vue 3 + TypeScript + Element Plus + Monaco Editor + Mock Data

---

## 实现顺序

### Phase 1: 基础设施

#### Task 1: 深色主题样式和共享组件

**Files:**
- Create: `frontend/src/styles/dark-theme.css`
- Create: `frontend/src/components/DarkCard.vue`
- Create: `frontend/src/components/StatusBadge.vue`
- Create: `frontend/src/components/SourceTag.vue`
- Create: `frontend/src/components/TriggerBadge.vue`
- Create: `frontend/src/components/TimelineItem.vue`
- Create: `frontend/src/components/WeekdaySelector.vue`
- Create: `frontend/src/components/CronInput.vue`
- Create: `frontend/src/components/DiffViewer.vue`

- [ ] **Step 1: 创建深色主题全局样式**

```css
/* frontend/src/styles/dark-theme.css */
:root {
  --bg-primary: #0d1117;
  --bg-secondary: #161b22;
  --bg-card: #21262d;
  --bg-hover: #30363d;
  --border-color: #30363d;
  --text-primary: #e6edf3;
  --text-secondary: #8b949e;
  --text-muted: #6e7681;
  --accent-blue: #58a6ff;
  --accent-green: #3fb950;
  --accent-orange: #f0883e;
  --accent-purple: #a371f7;
  --accent-red: #f85149;
  --accent-yellow: #d29922;
}
```

- [ ] **Step 2: 创建 StatusBadge 组件**

```vue
<!-- frontend/src/components/StatusBadge.vue -->
<template>
  <span class="status-badge" :class="status">
    <span class="dot"></span>
    <span>{{ label }}</span>
  </span>
</template>
```

- [ ] **Step 3: 创建其他共享组件**

(略 - 详见各组件规范)

- [ ] **Step 4: 提交代码**

```bash
git add src/styles/dark-theme.css src/components/DarkCard.vue src/components/StatusBadge.vue ...
git commit -m "feat(frontend): add dark theme styles and shared components"
```

---

#### Task 2: Mock 数据服务

**Files:**
- Create: `frontend/src/mock/index.ts`
- Create: `frontend/src/mock/scripts.ts`
- Create: `frontend/src/mock/executions.ts`
- Create: `frontend/src/mock/versions.ts`
- Create: `frontend/src/mock/generators.ts`

- [ ] **Step 1: 创建 Mock 数据生成器**

```typescript
// frontend/src/mock/generators.ts
export function generateMockScripts(count: number = 10): CollectionScript[] {
  // 生成模拟任务数据
}

export function generateMockExecutions(scriptId: number): Execution[] {
  // 生成模拟执行记录
}

export function generateMockVersions(scriptId: number): ScriptVersion[] {
  // 生成模拟版本历史
}
```

- [ ] **Step 2: 创建 Mock API 服务**

```typescript
// frontend/src/mock/index.ts
import { generateMockScripts, generateMockExecutions, generateMockVersions } from './generators'

export const mockApi = {
  getScripts: () => Promise.resolve(generateMockScripts()),
  getScriptById: (id: number) => Promise.resolve(...),
  // ...
}
```

- [ ] **Step 3: 提交代码**

```bash
git add src/mock/
git commit -m "feat(frontend): add mock data service"
```

---

#### Task 3: 更新路由配置

**Files:**
- Modify: `frontend/src/router/index.ts`

- [ ] **Step 1: 更新路由配置**

添加 VersionHistory 路由:

```typescript
{
  path: 'scripts/:id/versions',
  name: 'VersionHistory',
  component: () => import('../views/scripts/VersionHistory.vue'),
  meta: { hideSidebar: true }
}
```

- [ ] **Step 2: 提交代码**

```bash
git add src/router/index.ts
git commit -m "feat(frontend): add VersionHistory route"
```

---

### Phase 2: 页面实现

#### Task 4: 任务列表页 (TaskList)

**Files:**
- Create: `frontend/src/views/scripts/TaskList.vue`

**功能:**
- 6个统计卡片: 总数/启用/禁用/今日执行/累计成功/累计失败
- 筛选栏: 状态、触发方式、数据源、搜索
- 表格: 复选框、任务名称、状态、数据源、触发方式、下次执行、最近执行、统计、操作
- 批量操作: 批量启用、批量禁用
- 分页

- [ ] **Step 1: 创建 TaskList.vue 基本结构**

```vue
<template>
  <div class="task-list">
    <!-- Stats Cards Row -->
    <div class="stats-grid">
      <div class="stat-card">...</div>
      <!-- 6 cards total -->
    </div>

    <!-- Filter Bar -->
    <div class="filter-bar">
      <select class="filter-select">状态</select>
      <select class="filter-select">触发方式</select>
      <select class="filter-select">数据源</select>
      <div class="filter-search">
        <input type="text" placeholder="搜索任务名称...">
        <button>搜索</button>
      </div>
    </div>

    <!-- Table -->
    <div class="table-container">
      <table class="data-table">
        <!-- header -->
        <!-- body -->
      </table>
      <!-- Pagination -->
    </div>
  </div>
</template>
```

- [ ] **Step 2: 实现统计数据和状态管理**

```typescript
const stats = ref({
  total: 24,
  enabled: 18,
  disabled: 6,
  todayExec: 3,
  successCount: 1234,
  failedCount: 56
})
```

- [ ] **Step 3: 实现筛选和搜索逻辑**

```typescript
function applyFilters() {
  // 根据筛选条件过滤数据
}

function search() {
  // 关键词搜索
}
```

- [ ] **Step 4: 实现批量操作**

```typescript
function batchEnable() { /* 批量启用 */ }
function batchDisable() { /* 批量禁用 */ }
```

- [ ] **Step 5: 添加深色主题样式**

```vue
<style scoped>
.task-list {
  background: var(--bg-primary);
  color: var(--text-primary);
  /* ... */
}
</style>
```

- [ ] **Step 6: 提交代码**

```bash
git add src/views/scripts/TaskList.vue
git commit -m "feat(frontend): implement TaskList page with dark theme"
```

---

#### Task 5: 创建任务页 (CreateTask)

**Files:**
- Create: `frontend/src/views/scripts/CreateTask.vue`

**功能:**
- 基本信息表单: 任务名称、描述
- 脚本配置: Monaco 编辑器 + 文件上传切换
- 触发配置: 单次/周期/Cron
- 操作按钮: 取消、保存、保存并启动
- 表单验证

- [ ] **Step 1: 创建 CreateTask.vue 基本结构**

```vue
<template>
  <div class="create-task">
    <div class="header">
      <button class="back-btn">←</button>
      <span>采集任务管理</span>
      <span>创建新任务</span>
    </div>

    <div class="page-container">
      <!-- Basic Info Section -->
      <div class="form-section">
        <div class="section-header">
          <span>📋</span>
          <span>基本信息</span>
        </div>
        <div class="form-grid">
          <input type="text" placeholder="任务名称" />
          <textarea placeholder="任务描述"></textarea>
        </div>
      </div>

      <!-- Script Config Section -->
      <div class="form-section">
        <!-- Monaco Editor -->
      </div>

      <!-- Trigger Config Section -->
      <div class="form-section">
        <!-- Trigger panels -->
      </div>

      <!-- Actions -->
      <div class="page-actions">
        <button>取消</button>
        <button>保存</button>
        <button>保存并启动</button>
      </div>
    </div>
  </div>
</template>
```

- [ ] **Step 2: 实现 Monaco Editor 集成**

```typescript
// 使用已有的 ScriptEditor 组件
import ScriptEditor from '@/components/ScriptEditor.vue'
```

- [ ] **Step 3: 实现触发配置面板**

```typescript
const triggerType = ref('once') // once | cycle | cron

function onTriggerChange() {
  // 切换显示对应的触发面板
}
```

- [ ] **Step 4: 实现 Cron 表达式计算**

```typescript
function calculateCronNext5(cron: string) {
  // 计算接下来5次触发时间
}
```

- [ ] **Step 5: 实现表单验证和提交**

```typescript
function validateForm() {
  // 验证任务名称、脚本内容等
}

function saveOnly() {
  // 保存任务
}

function saveAndExecute() {
  // 保存并执行
}
```

- [ ] **Step 6: 提交代码**

```bash
git add src/views/scripts/CreateTask.vue
git commit -m "feat(frontend): implement CreateTask page with dark theme"
```

---

#### Task 6: 任务详情页 (TaskDetail)

**Files:**
- Create: `frontend/src/views/scripts/TaskDetail.vue` (重写现有文件)

**功能:**
- Header: 返回、任务信息、状态切换、执行、保存
- 统计卡片: 执行次数/成功/失败/成功率/下次执行
- 触发配置面板
- 左侧: 任务信息表单、执行记录
- 右侧: Monaco 脚本编辑器
- 版本历史弹窗

- [ ] **Step 1: 重写 TaskDetail.vue 基本结构**

```vue
<template>
  <div class="task-detail">
    <!-- Header -->
    <div class="header">
      <button class="back-btn">←</button>
      <div class="header-info">
        <span class="header-title">任务名称</span>
        <div class="header-meta">
          <span class="meta-tag">数据源</span>
          <span class="meta-tag">触发方式</span>
        </div>
      </div>
      <div class="header-actions">
        <button>版本历史</button>
        <button class="status-toggle">启用/禁用</button>
        <button>立即执行</button>
        <button>保存</button>
      </div>
    </div>

    <!-- Stats Bar -->
    <div class="stats-bar">
      <!-- 5 stat chips -->
    </div>

    <!-- Trigger Config -->
    <div class="section-card trigger-section">
      <!-- Trigger type selector -->
      <!-- Trigger panels -->
    </div>

    <!-- Content Grid -->
    <div class="content-grid">
      <!-- Left Sidebar -->
      <div class="sidebar">
        <div class="section-card">任务信息表单</div>
        <div class="section-card">执行记录列表</div>
      </div>

      <!-- Main Content -->
      <div class="main-content">
        <div class="section-card">
          <ScriptEditor />
        </div>
      </div>
    </div>

    <!-- Dialogs -->
    <VersionHistoryDialog />
    <ExecutionDialog />
  </div>
</template>
```

- [ ] **Step 2: 实现状态切换**

```typescript
const isEnabled = ref(true)

function toggleStatus() {
  isEnabled.value = !isEnabled.value
  // 调用 API
}
```

- [ ] **Step 3: 实现脚本编辑和保存**

```typescript
const scriptContent = ref('')
const originalContent = ref('')
const hasChanges = computed(() => scriptContent.value !== originalContent.value)

async function saveScript() {
  // 保存脚本内容
}
```

- [ ] **Step 4: 实现立即执行功能**

```typescript
async function executeTask() {
  // 调用执行 API
  // 显示执行进度弹窗
}
```

- [ ] **Step 5: 添加深色主题样式**

(复用原型中的样式)

- [ ] **Step 6: 提交代码**

```bash
git add src/views/scripts/TaskDetail.vue
git commit -m "feat(frontend): rewrite TaskDetail page with dark theme"
```

---

#### Task 7: 版本历史页 (VersionHistory)

**Files:**
- Create: `frontend/src/views/scripts/VersionHistory.vue`

**功能:**
- Top Navigation
- Tab 切换: 历史版本 / 执行记录
- 左侧时间线: 版本列表
- 右侧详情: 版本信息、触发配置、变更说明、脚本内容
- 版本对比 (Monaco Diff)
- 回滚功能
- 执行记录表格

- [ ] **Step 1: 创建 VersionHistory.vue 基本结构**

```vue
<template>
  <div class="version-history">
    <!-- Top Nav -->
    <div class="top-nav">
      <button class="nav-back-btn">← 返回</button>
      <span>📜 历史版本 & 执行记录</span>
      <span>任务名称</span>
    </div>

    <!-- Tabs -->
    <div class="tabs">
      <button class="tab active">📜 历史版本</button>
      <button class="tab">▶ 执行记录</button>
    </div>

    <!-- Main Layout -->
    <div class="main-layout">
      <!-- Left: Timeline -->
      <div class="left-panel">
        <div class="timeline">
          <div class="timeline-item" v-for="version in versions" :key="version.id">
            <div class="item-header">
              <span class="item-version">v{{ version.version }}</span>
              <span v-if="version.isCurrent">当前</span>
              <span class="item-time">{{ version.createdAt }}</span>
            </div>
            <div class="item-desc">{{ version.changeDescription }}</div>
            <div class="item-actions">
              <button>对比</button>
              <button v-if="!version.isCurrent">回滚</button>
            </div>
          </div>
        </div>
      </div>

      <!-- Right: Detail -->
      <div class="right-panel">
        <div class="detail-header">
          <span class="detail-version">v{{ currentVersion.version }}</span>
          <div class="detail-meta">
            <span>触发类型</span>
            <span>作者</span>
            <span>时间</span>
          </div>
        </div>
        <!-- Trigger Config -->
        <!-- Change Description -->
        <!-- Script Content -->
      </div>

      <!-- Diff Panel (hidden) -->
      <div class="diff-panel">
        <DiffViewer />
      </div>
    </div>

    <!-- Modals -->
    <RollbackModal />
    <LogModal />
  </div>
</template>
```

- [ ] **Step 2: 实现时间线交互**

```typescript
function showVersionDetail(version) {
  currentVersion.value = version
  // 加载版本详情
}

function compareWithCurrent(version) {
  // 显示对比面板
}
```

- [ ] **Step 3: 实现版本对比 (Monaco Diff)**

```typescript
import * as monaco from 'monaco-editor'

function createDiffEditor(oldCode: string, newCode: string) {
  const diffEditor = monaco.editor.createDiffEditor(container, {
    theme: 'vs-dark',
    readOnly: true
  })
  diffEditor.setModel({
    original: monaco.editor.createModel(oldCode, 'python'),
    modified: monaco.editor.createModel(newCode, 'python')
  })
}
```

- [ ] **Step 4: 实现回滚功能**

```typescript
function showRollbackModal(version) {
  // 显示回滚确认弹窗
}

function confirmRollback() {
  // 调用回滚 API
}
```

- [ ] **Step 5: 实现执行记录 Tab**

```typescript
function switchModule(module: 'versions' | 'executions') {
  // 切换显示版本或执行记录
}
```

- [ ] **Step 6: 添加深色主题样式**

- [ ] **Step 7: 提交代码**

```bash
git add src/views/scripts/VersionHistory.vue
git commit -m "feat(frontend): implement VersionHistory page with dark theme"
```

---

### Phase 3: 集成与优化

#### Task 8: 页面间导航集成

- [ ] **Step 1: 确保 TaskList 可跳转详情**

```typescript
// TaskList.vue
function viewDetail(id) {
  router.push(`/scripts/${id}`)
}
```

- [ ] **Step 2: 确保 TaskDetail 可跳转版本历史**

```typescript
// TaskDetail.vue
function openVersionHistory() {
  router.push(`/scripts/${scriptId.value}/versions`)
}
```

- [ ] **Step 3: 确保 CreateTask 保存后跳转**

```typescript
// CreateTask.vue
function saveOnly() {
  // 保存成功后跳转
  router.push('/scripts')
}
```

#### Task 9: 响应式适配

- [ ] **Step 1: 添加响应式断点**

```css
@media (max-width: 1100px) {
  .content-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
```

---

## 验证标准

1. [ ] TaskList 页面显示 6 个统计卡片
2. [ ] 筛选和搜索功能正常
3. [ ] 批量选择和操作正常
4. [ ] 分页功能正常
5. [ ] CreateTask 表单验证正常
6. [ ] Monaco Editor 正常加载和编辑
7. [ ] Cron 表达式计算正确
8. [ ] TaskDetail 状态切换正常
9. [ ] 立即执行功能正常
10. [ ] VersionHistory 时间线正常显示
11. [ ] 版本对比功能正常
12. [ ] 回滚功能正常
13. [ ] Tab 切换正常
14. [ ] 所有页面深色主题正确应用
15. [ ] 页面间导航正常
16. [ ] Mock 数据正常显示

---

## 实现顺序

1. ✅ Task 1: 深色主题样式和共享组件
2. ✅ Task 2: Mock 数据服务
3. ✅ Task 3: 路由配置更新
4. ⬜ Task 4: TaskList 任务列表页
5. ⬜ Task 5: CreateTask 创建任务页
6. ⬜ Task 6: TaskDetail 任务详情页
7. ⬜ Task 7: VersionHistory 版本历史页
8. ⬜ Task 8: 页面间导航集成
9. ⬜ Task 9: 响应式适配

---

**Plan complete and saved to `docs/superpowers/plans/2026-05-07-prototype-implementation-plan.md`**
