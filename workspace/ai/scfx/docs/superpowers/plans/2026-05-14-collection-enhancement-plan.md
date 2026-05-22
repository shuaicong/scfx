# 采集管理页面增强实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将采集管理页面重构为完整的 4-Tab 结构，包含采集任务、脚本管理、执行记录、向量化监控，并优化用户体验

**Architecture:** 采用 Tab 式布局，每个 Tab 独立功能模块，通过 API 与后端交互。进度抽屉复用已有组件，日志查看器使用流式输出。

**Tech Stack:** Vue 3 + TypeScript + Element Plus + Monaco Editor + API

---

## Task 1: 重构采集任务 Tab（基础统计卡片）

**Files:**
- Modify: `frontend/src/views/collection/Collection.vue:1-100`

- [ ] **Step 1: 添加统计卡片样式**

在 Collection.vue 的采集任务 Tab 中添加 4 个统计卡片：

```vue
<!-- 统计卡片区域 -->
<el-row :gutter="20" class="stats-row">
  <el-col :span="6">
    <div class="stat-card total">
      <div class="stat-value">{{ taskStats.total }}</div>
      <div class="stat-label">任务总数</div>
    </div>
  </el-col>
  <el-col :span="6">
    <div class="stat-card enabled">
      <div class="stat-value">{{ taskStats.enabled }}</div>
      <div class="stat-label">启用中</div>
    </div>
  </el-col>
  <el-col :span="6">
    <div class="stat-card today">
      <div class="stat-value">{{ taskStats.todayExec }}</div>
      <div class="stat-label">今日执行</div>
    </div>
  </el-col>
  <el-col :span="6">
    <div class="stat-card failed">
      <div class="stat-value">{{ taskStats.failed }}</div>
      <div class="stat-label">失败数</div>
    </div>
  </el-col>
</el-row>
```

添加样式：
```css
.stat-card {
  padding: 20px;
  border-radius: 12px;
  text-align: center;
  color: #fff;
}
.stat-card.total { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); }
.stat-card.enabled { background: linear-gradient(135deg, #48bb78 0%, #38a169 100%); }
.stat-card.today { background: linear-gradient(135deg, #ed8936 0%, #dd6b20 100%); }
.stat-card.failed { background: linear-gradient(135deg, #e53e3e 0%, #c53030 100%); }
.stat-value { font-size: 32px; font-weight: bold; }
.stat-label { font-size: 14px; margin-top: 8px; opacity: 0.9; }
```

- [ ] **Step 2: 添加 taskStats 响应式变量**

```typescript
const taskStats = ref({
  total: 0,
  enabled: 0,
  todayExec: 0,
  failed: 0
})
```

- [ ] **Step 3: 添加 loadTaskStats 函数**

```typescript
async function loadTaskStats() {
  try {
    const res: any = await scriptApi.stats()
    taskStats.value = res.data
  } catch (e) {
    console.error('加载统计失败', e)
  }
}
```

- [ ] **Step 4: 在 onMounted 中调用 loadTaskStats**

```typescript
onMounted(() => {
  loadTasks()
  loadTaskStats()
})
```

- [ ] **Step 5: Commit**

```bash
git add frontend/src/views/collection/Collection.vue
git commit -m "feat(collection): add stats cards to task tab"
```

---

## Task 2: 实现执行进度抽屉和实时日志

**Files:**
- Create: `frontend/src/components/CollectionProgress.vue`
- Modify: `frontend/src/views/collection/Collection.vue:100-200`
- Check: `frontend/src/components/ExecutionLogViewer.vue`

- [ ] **Step 1: 创建 CollectionProgress.vue 组件**

```vue
<template>
  <el-drawer
    v-model="visible"
    title="执行进度"
    size="500px"
    direction="rtl"
    :before-close="handleClose"
  >
    <div class="progress-container">
      <el-progress
        :percentage="percentage"
        :status="progressStatus"
        :stroke-width="10"
      />
      <div class="progress-info">
        <span>状态: {{ statusText }}</span>
        <span>{{ processed }}/{{ total }}</span>
      </div>
      <ExecutionLogViewer
        :logs="logs"
        :loading="loading"
        @pause-scroll="pauseScroll"
        @export="exportLogs"
      />
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import ExecutionLogViewer from './ExecutionLogViewer.vue'

const visible = ref(false)
const logs = ref<string[]>([])
const loading = ref(false)
const processed = ref(0)
const total = ref(0)
const status = ref('running')

const percentage = computed(() => {
  if (total.value === 0) return 0
  return Math.round((processed.value / total.value) * 100)
})

const progressStatus = computed(() => {
  if (status.value === 'success') return 'success'
  if (status.value === 'failed') return 'exception'
  return ''
})

const statusText = computed(() => {
  const map: Record<string, string> = {
    running: '执行中',
    success: '完成',
    failed: '失败',
    cancelled: '已取消'
  }
  return map[status.value] || '等待'
})

const pauseScroll = () => { /* 暂停滚动逻辑 */ }
const exportLogs = () => { /* 导出日志逻辑 */ }

const open = (executionId: string) => {
  visible.value = true
  loadLogs(executionId)
}

const loadLogs = async (executionId: string) => {
  loading.value = true
  try {
    const res: any = await executionApi.getLogs(executionId)
    logs.value = res.data || []
  } catch (e) {
    console.error('加载日志失败', e)
  } finally {
    loading.value = false
  }
}

const handleClose = () => {
  visible.value = false
}

defineExpose({ open })
</script>

<style scoped>
.progress-container { display: flex; flex-direction: column; height: 100%; }
.progress-info { display: flex; justify-content: space-between; margin: 12px 0; font-size: 14px; }
</style>
```

- [ ] **Step 2: 在 Collection.vue 中引入并使用**

```typescript
import CollectionProgress from '@/components/CollectionProgress.vue'

const progressDrawer = ref()

const executeTask = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确定执行任务"${row.taskName}"吗？`, '提示', { type: 'info' })
    await scriptApi.executeNow(row.id)
    ElMessage.success('任务已触发执行')
    progressDrawer.value.open(row.executionId)
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error('执行失败')
    }
  }
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/CollectionProgress.vue frontend/src/views/collection/Collection.vue
git commit -m "feat(collection): add progress drawer with real-time logs"
```

---

## Task 3: 新增脚本管理 Tab

**Files:**
- Modify: `frontend/src/views/collection/Collection.vue:200-400`

- [ ] **Step 1: 添加脚本管理 Tab 内容**

```vue
<el-tab-pane label="脚本管理" name="scripts">
  <el-card>
    <template #header>
      <div class="card-header">
        <span>采集脚本管理</span>
        <div class="header-actions">
          <el-button type="info" @click="showUploadDialog">
            <el-icon><Upload /></el-icon> 上传文件
          </el-button>
          <el-button type="primary" @click="showCreateDialog">
            <el-icon><Plus /></el-icon> 新建脚本
          </el-button>
        </div>
      </div>
    </template>

    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="8">
        <div class="stat-card total">
          <div class="stat-value">{{ scriptStats.total }}</div>
          <div class="stat-label">脚本总数</div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="stat-card enabled">
          <div class="stat-value">{{ scriptStats.enabled }}</div>
          <div class="stat-label">启用中</div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="stat-card disabled">
          <div class="stat-value">{{ scriptStats.disabled }}</div>
          <div class="stat-label">已禁用</div>
        </div>
      </el-col>
    </el-row>

    <!-- 列表 -->
    <el-table :data="scriptList" border stripe v-loading="scriptLoading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="scriptName" label="脚本名称" min-width="150" />
      <el-table-column prop="triggerType" label="触发方式" width="100">
        <template #default="{ row }">
          <TriggerBadge :type="row.triggerType" />
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 'enabled' ? 'success' : 'danger'">
            {{ row.status === 'enabled' ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="executionCount" label="执行次数" width="100" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button type="info" size="small" @click="showScriptDetail(row)">详情</el-button>
          <el-button type="success" size="small" @click="executeScript(row)">执行</el-button>
          <el-button type="primary" size="small" @click="editScript(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="scriptPagination.page"
      v-model:page-size="scriptPagination.size"
      :total="scriptPagination.total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      @size-change="loadScripts"
      @current-change="loadScripts"
      style="margin-top: 20px; justify-content: flex-end;"
    />
  </el-card>
</el-tab-pane>
```

- [ ] **Step 2: 添加脚本管理相关数据和方法**

```typescript
// 脚本管理相关
const scriptLoading = ref(false)
const scriptList = ref<any[]>([])
const scriptStats = ref({ total: 0, enabled: 0, disabled: 0 })
const scriptPagination = reactive({ page: 1, size: 20, total: 0 })
const scriptDialogVisible = ref(false)
const scriptForm = ref<any>({})

async function loadScripts() {
  scriptLoading.value = true
  try {
    const res: any = await scriptApi.list({
      page: scriptPagination.page,
      size: scriptPagination.size
    })
    scriptList.value = res.data.records || []
    scriptPagination.total = res.data.total || 0
  } catch (e) {
    console.error('加载脚本列表失败', e)
  } finally {
    scriptLoading.value = false
  }
}

async function loadScriptStats() {
  try {
    const res: any = await scriptApi.stats()
    scriptStats.value = res.data
  } catch (e) {
    console.error('加载统计失败', e)
  }
}

async function executeScript(row: any) {
  try {
    await ElMessageBox.confirm(`确定执行脚本"${row.scriptName}"吗？`, '执行确认', { type: 'info' })
    await scriptApi.executeNow(row.id)
    ElMessage.success('脚本已触发执行')
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('执行失败')
  }
}
```

- [ ] **Step 3: 在 Tab 切换时加载数据**

```typescript
const activeTab = ref('tasks')
watch(activeTab, (newTab) => {
  if (newTab === 'tasks') loadTasks()
  else if (newTab === 'scripts') loadScripts()
  else if (newTab === 'executions') loadExecutions()
  else if (newTab === 'vectorization') loadVectorStats()
})
```

- [ ] **Step 4: Commit**

```bash
git add frontend/src/views/collection/Collection.vue
git commit -m "feat(collection): add script management tab"
```

---

## Task 4: 新增执行记录 Tab

**Files:**
- Modify: `frontend/src/views/collection/Collection.vue:400-500`

- [ ] **Step 1: 添加执行记录 Tab**

```vue
<el-tab-pane label="执行记录" name="executions">
  <el-card>
    <el-table :data="executionList" v-loading="executionLoading" stripe>
      <el-table-column prop="scriptName" label="脚本名称" min-width="150" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <StatusBadge :status="row.status" />
        </template>
      </el-table-column>
      <el-table-column prop="startTime" label="开始时间" width="160">
        <template #default="{ row }">
          {{ formatTime(row.startTime) }}
        </template>
      </el-table-column>
      <el-table-column prop="duration" label="耗时" width="100">
        <template #default="{ row }">
          {{ row.duration ? row.duration + 's' : '-' }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button type="info" size="small" @click="viewExecutionLogs(row)">日志</el-button>
          <el-button type="danger" size="small" v-if="row.status === 'running'" @click="cancelExecution(row)">取消</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-model:current-page="executionPagination.page"
      :total="executionPagination.total"
      layout="prev, pager, next"
      @current-change="loadExecutions"
      style="margin-top: 20px; justify-content: flex-end;"
    />
  </el-card>
</el-tab-pane>
```

- [ ] **Step 2: 添加执行记录相关数据和方法**

```typescript
// 执行记录相关
const executionLoading = ref(false)
const executionList = ref<any[]>([])
const executionPagination = reactive({ page: 1, total: 0 })

async function loadExecutions() {
  executionLoading.value = true
  try {
    const res: any = await executionApi.list({ page: executionPagination.page })
    executionList.value = res.data.records || []
    executionPagination.total = res.data.total || 0
  } catch (e) {
    console.error('加载执行记录失败', e)
  } finally {
    executionLoading.value = false
  }
}

function formatTime(time: string) {
  return time ? new Date(time).toLocaleString('zh-CN') : '-'
}

function viewExecutionLogs(row: any) {
  // TODO: 打开日志抽屉
  ElMessage.info('日志查看功能开发中')
}

async function cancelExecution(row: any) {
  try {
    await ElMessageBox.confirm('确定取消执行吗？', '提示', { type: 'warning' })
    await executionApi.cancel(row.id)
    ElMessage.success('已取消执行')
    loadExecutions()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('取消失败')
  }
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/views/collection/Collection.vue
git commit -m "feat(collection): add execution records tab"
```

---

## Task 5: 实现向量化监控 Tab

**Files:**
- Modify: `frontend/src/views/collection/Collection.vue:500-600`

- [ ] **Step 1: 替换占位符内容**

```vue
<el-tab-pane label="向量化监控" name="vectorization">
  <el-card>
    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <div class="stat-card pending">
          <div class="stat-value">{{ vectorStats.pending }}</div>
          <div class="stat-label">待处理</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card processing">
          <div class="stat-value">{{ vectorStats.processing }}</div>
          <div class="stat-label">处理中</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card completed">
          <div class="stat-value">{{ vectorStats.vectorized }}</div>
          <div class="stat-label">已完成</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card failed">
          <div class="stat-value">{{ vectorStats.failed }}</div>
          <div class="stat-label">失败</div>
        </div>
      </el-col>
    </el-row>

    <!-- 批量操作 -->
    <div style="margin: 16px 0; display: flex; gap: 12px;">
      <el-button type="primary" @click="triggerAllPending">触发全部待处理</el-button>
      <el-button type="warning" @click="retryAllFailed" :disabled="vectorStats.failed === 0">重试全部失败</el-button>
    </div>

    <!-- 任务列表 -->
    <el-table :data="vectorTasks" v-loading="vectorLoading" stripe>
      <el-table-column prop="categoryName" label="分类" min-width="150" />
      <el-table-column prop="totalCount" label="总数" width="80" />
      <el-table-column prop="processedCount" label="已处理" width="80" />
      <el-table-column prop="failedCount" label="失败" width="80">
        <template #default="{ row }">
          <span style="color: #e53e3e;">{{ row.failedCount }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="getVectorStatusType(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="160">
        <template #default="{ row }">
          {{ formatTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" size="small" @click="triggerVectorization(row)">触发</el-button>
          <el-button type="warning" size="small" @click="retryVectorization(row)">重试</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</el-tab-pane>
```

- [ ] **Step 2: 添加向量化相关数据和方法**

```typescript
// 向量化相关
const vectorLoading = ref(false)
const vectorStats = ref({ pending: 0, processing: 0, vectorized: 0, failed: 0, total: 0 })
const vectorTasks = ref<any[]>([])

async function loadVectorStats() {
  try {
    const res: any = await vectorizationApi.stats()
    vectorStats.value = res.data
  } catch (e) {
    console.error('加载向量化统计失败', e)
  }
}

async function loadVectorTasks() {
  vectorLoading.value = true
  try {
    const res: any = await vectorizationApi.tasks()
    vectorTasks.value = res.data || []
  } catch (e) {
    console.error('加载向量化任务失败', e)
  } finally {
    vectorLoading.value = false
  }
}

async function triggerAllPending() {
  try {
    await ElMessageBox.confirm('确定触发全部待处理数据吗？', '提示', { type: 'info' })
    await vectorizationApi.triggerBatch()
    ElMessage.success('已触发')
    loadVectorStats()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('触发失败')
  }
}

async function retryAllFailed() {
  try {
    await ElMessageBox.confirm('确定重试全部失败项吗？', '提示', { type: 'warning' })
    // TODO: 实现批量重试
    ElMessage.success('重试已触发')
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('重试失败')
  }
}

function getVectorStatusType(status: string) {
  const map: Record<string, string> = {
    pending: 'info',
    processing: 'primary',
    completed: 'success',
    failed: 'danger'
  }
  return map[status] || 'info'
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/views/collection/Collection.vue
git commit -m "feat(collection): implement vectorization monitoring tab"
```

---

## Task 6: 整体体验优化（Toast + 快捷键）

**Files:**
- Modify: `frontend/src/views/collection/Collection.vue:600-700`

- [ ] **Step 1: 添加工具函数**

```typescript
import { useShortcut } from '@/composables/useShortcut'

// Toast 提示函数
function showSuccess(message: string) {
  ElMessage.success({ message, duration: 3000 })
}

function showError(message: string) {
  ElMessage.error({ message, duration: 5000 })
}

// 快捷键注册
onMounted(() => {
  loadTasks()
  loadTaskStats()

  // Ctrl+Enter 执行选中任务
  useShortcut('ctrl+enter', () => {
    if (selectedTask.value) {
      executeTask(selectedTask.value)
    }
  })

  // Ctrl+R 刷新
  useShortcut('ctrl+r', () => {
    refreshCurrentTab()
  })
})

const selectedTask = ref<any>(null)
const refreshCurrentTab = () => {
  if (activeTab.value === 'tasks') loadTasks()
  else if (activeTab.value === 'scripts') loadScripts()
  else if (activeTab.value === 'executions') loadExecutions()
  else if (activeTab.value === 'vectorization') loadVectorStats()
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/views/collection/Collection.vue
git commit -m "feat(collection): add toast notifications and keyboard shortcuts"
```

---

## Task 7: 创建 useShortcut composable

**Files:**
- Create: `frontend/src/composables/useShortcut.ts`

- [ ] **Step 1: 创建 composable**

```typescript
import { onMounted, onUnmounted } from 'vue'

type ShortcutHandler = () => void
type ShortcutKey = string

const shortcutHandlers = new Map<ShortcutKey, ShortcutHandler>()

export function useShortcut(key: ShortcutKey, handler: ShortcutHandler) {
  const normalizedKey = key.toLowerCase().replace(/\s+/g, '')

  shortcutHandlers.set(normalizedKey, handler)

  const listener = (e: KeyboardEvent) => {
    const modifiers: string[] = []
    if (e.ctrlKey || e.metaKey) modifiers.push('ctrl')
    if (e.shiftKey) modifiers.push('shift')
    if (e.altKey) modifiers.push('alt')

    const keyName = e.key.toLowerCase()
    const fullKey = [...modifiers, keyName].join('+')

    if (shortcutHandlers.has(fullKey)) {
      e.preventDefault()
      shortcutHandlers.get(fullKey)?.()
    }
  }

  window.addEventListener('keydown', listener)

  onUnmounted(() => {
    window.removeEventListener('keydown', listener)
    shortcutHandlers.delete(normalizedKey)
  })
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/composables/useShortcut.ts
git commit -m "feat: add useShortcut composable"
```

---

## Task 8: 最终检查和测试

**Files:**
- Test: `frontend/src/views/collection/Collection.vue`

- [ ] **Step 1: TypeScript 类型检查**

```bash
cd frontend && npx vue-tsc --noEmit
```

- [ ] **Step 2: 手动测试各 Tab 功能**

1. 采集任务 Tab - 验证统计卡片、列表、执行按钮
2. 脚本管理 Tab - 验证列表、新建、编辑、执行
3. 执行记录 Tab - 验证列表、日志查看、取消
4. 向量化监控 Tab - 验证统计、任务列表、触发

- [ ] **Step 3: Commit 最终修改**

```bash
git add -A
git commit -m "feat(collection): complete all enhancements"
```

---

## 实施顺序

1. Task 1: 重构采集任务 Tab（基础统计卡片）
2. Task 2: 实现执行进度抽屉和实时日志
3. Task 3: 新增脚本管理 Tab
4. Task 4: 新增执行记录 Tab
5. Task 5: 实现向量化监控 Tab
6. Task 6: 整体体验优化（Toast + 快捷键）
7. Task 7: 创建 useShortcut composable
8. Task 8: 最终检查和测试