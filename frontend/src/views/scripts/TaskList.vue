<template>
  <div class="task-list-container">
    <!-- Header -->
    <div class="header">
      <div class="header-title">
        <span>采集任务管理</span>
      </div>
      <div class="header-actions">
        <el-button class="btn-secondary" @click="handleRefresh">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
        <el-button class="btn-primary" @click="handleCreate">
          <el-icon><Plus /></el-icon>
          创建任务
        </el-button>
      </div>
    </div>

    <!-- Page Container -->
    <div class="page-content">
      <!-- Stats Cards -->
      <div class="stats-grid">
        <div class="stat-card">
          <div class="stat-icon total">
            <el-icon><Document /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.total }}</div>
            <div class="stat-label">任务总数</div>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon enabled">
            <el-icon><Check /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.enabled }}</div>
            <div class="stat-label">启用中</div>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon disabled">
            <el-icon><CloseBold /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.disabled }}</div>
            <div class="stat-label">已禁用</div>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon running">
            <el-icon><Clock /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.todayExec }}</div>
            <div class="stat-label">今日执行</div>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon success">
            <el-icon><SuccessFilled /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ formatNumber(stats.success) }}</div>
            <div class="stat-label">累计成功</div>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon failed">
            <el-icon><CircleCloseFilled /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ formatNumber(stats.failed) }}</div>
            <div class="stat-label">累计失败</div>
          </div>
        </div>
      </div>

      <!-- Filter Bar -->
      <div class="filter-bar">
        <div class="filter-item">
          <span class="filter-label">状态</span>
          <select v-model="filters.status" class="filter-select" @change="handleFilter">
            <option value="">全部</option>
            <option value="enabled">启用</option>
            <option value="disabled">禁用</option>
          </select>
        </div>
        <div class="filter-item">
          <span class="filter-label">触发方式</span>
          <select v-model="filters.trigger" class="filter-select" @change="handleFilter">
            <option value="">全部</option>
            <option value="cron">Cron表达式</option>
            <option value="cycle">周期触发</option>
            <option value="once">单次触发</option>
          </select>
        </div>
        <div class="filter-item">
          <span class="filter-label">数据源</span>
          <select v-model="filters.source" class="filter-select" @change="handleFilter">
            <option value="">全部</option>
            <option v-for="ds in datasourceList" :key="ds.code" :value="ds.code">{{ ds.name }}</option>
          </select>
        </div>
        <div class="filter-item">
          <span class="filter-label">分类</span>
          <select v-model="filters.categoryId" class="filter-select" @change="handleFilter">
            <option value="">全部</option>
            <option v-for="cat in flatCategories" :key="cat.id" :value="cat.id">
              {{ '　'.repeat(cat.depth) }}{{ cat.icon }} {{ cat.name }}
            </option>
          </select>
        </div>
        <div class="filter-search">
          <input
            v-model="filters.keyword"
            type="text"
            class="search-input"
            placeholder="搜索任务名称..."
            @keyup.enter="handleFilter"
          />
          <button class="search-btn" @click="handleFilter">搜索</button>
        </div>
      </div>

      <!-- Failure Alert -->
      <el-alert
        v-if="failedAlertVisible"
        :title="`有 ${failedCount} 个采集任务上次执行失败，请关注`"
        type="warning"
        show-icon
        :closable="true"
        close-text="知道了"
        @close="dismissFailedAlert"
        class="failure-alert"
      />

      <!-- Table -->
      <div class="table-container">
        <!-- Batch Actions -->
        <div class="batch-actions" :class="{ show: selectedRows.length > 0 }">
          <span class="batch-info">已选择 <span>{{ selectedRows.length }}</span> 项</span>
          <el-button size="small" type="primary" @click="handleBatchExecute">批量执行</el-button>
          <el-button size="small" type="success" @click="handleBatchEnable">批量启用</el-button>
          <el-button size="small" type="warning" @click="handleBatchDisable">批量禁用</el-button>
          <el-button size="small" @click="handleClearSelection">取消选择</el-button>
        </div>

        <table class="data-table">
          <thead>
            <tr>
              <th class="col-checkbox">
                <input
                  type="checkbox"
                  class="checkbox"
                  :checked="isAllSelected"
                  :indeterminate="isIndeterminate"
                  @change="handleSelectAll"
                />
              </th>
              <th>任务名称</th>
              <th>状态</th>
              <th>数据源</th>
              <th>分类</th>
              <th>触发方式</th>
              <th>下次执行</th>
              <th>最近执行</th>
              <th>最近状态</th>
              <th>执行统计</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="script in paginatedScripts"
              :key="script.id"
              @mouseenter="hoveredRow = script.id"
              @mouseleave="hoveredRow = null"
              :class="{ hovered: hoveredRow === script.id }"
            >
              <td class="col-checkbox">
                <input
                  type="checkbox"
                  class="checkbox"
                  :checked="script.id !== undefined && selectedRows.includes(script.id)"
                  @change="handleSelect(script.id)"
                />
              </td>
              <td>
                <div class="task-name" @click="handleDetail(script.id!)">{{ script.scriptName }}</div>
                <div class="task-desc">{{ script.description || '-' }}</div>
              </td>
              <td>
                <StatusBadge :status="script.status" />
              </td>
              <td>
                <SourceTag :source="script.source" />
              </td>
              <td>
                <span class="category-cell">{{ getCategoryName(script.categoryId) }}</span>
              </td>
              <td>
                <TriggerBadge :type="script.triggerType" :executed="script.triggerType === 'once' ? !!script.lastExecutionTime : undefined" />
              </td>
              <td>
                <span class="time-value" :class="{ next: script.status === 'enabled' && script.nextExecutionTime }">
                  {{ formatTime(script.nextExecutionTime) }}
                </span>
              </td>
              <td>
                <span class="time-value">{{ formatTime(script.lastExecutionTime) }}</span>
              </td>
              <td>
                <span class="status-badge" :class="execStatusClass(script.lastExecutionStatus, script.status)">
                  {{ execStatusText(script.lastExecutionStatus, script.status) }}
                </span>
              </td>
              <td>
                <div class="stats-inline">
                  <span class="success">✓ {{ script.successCount }}</span>
                  <span class="failed">✗ {{ script.failedCount }}</span>
                </div>
              </td>
              <td>
                <div class="action-buttons">
                  <button class="action-btn success" @click="handleExecute(script)">执行</button>
                  <button v-if="script.lastExecutionStatus === 'failed'" class="action-btn warning" @click="handleRetry(script)">重试</button>
                  <button class="action-btn primary" @click="handleDetail(script.id!)">详情</button>
                  <button
                    class="action-btn"
                    :class="script.status === 'enabled' ? 'warning' : 'success'"
                    @click="handleToggleStatus(script)"
                  >
                    {{ script.status === 'enabled' ? '禁用' : '启用' }}
                  </button>
                  <button class="action-btn danger" @click="handleDelete(script)">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>

        <!-- Pagination -->
        <div class="pagination">
          <div class="pagination-left">
            <div class="page-size-select">
              <label>每页</label>
              <select v-model="pagination.pageSize" @change="handlePageSizeChange">
                <option :value="10">10 条</option>
                <option :value="20">20 条</option>
                <option :value="50">50 条</option>
              </select>
            </div>
            <div class="page-info">共 {{ filteredScripts.length }} 条</div>
          </div>
          <div class="pagination-right">
            <div class="page-info">第 {{ pagination.page }} / {{ totalPages }} 页</div>
            <button class="page-btn" :disabled="pagination.page <= 1" @click="prevPage">
              ‹
            </button>
            <button
              v-for="p in visiblePages"
              :key="p"
              class="page-btn"
              :class="{ active: p === pagination.page }"
              @click="goToPage(p)"
            >
              {{ p }}
            </button>
            <button class="page-btn" :disabled="pagination.page >= totalPages" @click="nextPage">
              ›
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 进度抽屉 -->
    <CollectionProgress ref="progressDrawer" />

    <!-- 详情弹窗 -->
    <ScriptDetailDialog
      v-model:visible="detailDialogVisible"
      :script-id="detailScriptId"
      @edit="(script: any) => { detailDialogVisible = false; router.push(`/scripts/${script.id}`) }"
    />
  </div>

    <!-- 日期选择弹窗 -->
    <el-dialog v-model="executeDialogVisible" title="选择采集日期" width="420px" :close-on-click-modal="false" append-to-body>
      <div class="date-picker-container">
        <p class="date-picker-hint">可选范围：{{ minExecuteDate }} ~ 今天；当日日报通常18:00后发布，白天采集今日大概率无数据</p>
        <el-date-picker
          v-model="executeDate"
          type="date"
          value-format="yyyy-MM-dd"
          placeholder="选择采集日期"
          :disabled-date="disabledDate"
          :editable="false"
          :clearable="false"
          style="width: 100%"
        />
      </div>
      <template #footer>
        <el-button @click="executeDialogVisible = false" :disabled="executing">取消</el-button>
        <el-button type="primary" @click="confirmExecute" :loading="executing">确认执行</el-button>
      </template>
    </el-dialog>
</template>

<style>
@import '@/styles/dark-theme.css';
</style>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Plus, Document, Check, CloseBold, Clock, SuccessFilled, CircleCloseFilled } from '@element-plus/icons-vue'
import StatusBadge from '@/components/StatusBadge.vue'
import SourceTag from '@/components/SourceTag.vue'
import TriggerBadge from '@/components/TriggerBadge.vue'
import CollectionProgress from '@/components/CollectionProgress.vue'
import ScriptDetailDialog from './components/ScriptDetailDialog.vue'
import { scriptApi, executionApi } from '@/api'
import { datasourceApi } from '@/api/datasource'
import { categoryApi, type Category } from '@/api/category'
import type { CollectionScript } from '@/api'

const router = useRouter()

const loading = ref(false)
const scripts = ref<CollectionScript[]>([])
const hoveredRow = ref<number | null | undefined>(null)
const selectedRows = ref<number[]>([])

// 详情弹窗
const detailDialogVisible = ref(false)
const detailScriptId = ref<number>(0)

// 进度抽屉
const progressDrawer = ref<any>(null)

// 日期选择弹窗
const executeDialogVisible = ref(false)
const executeDate = ref('')
const minExecuteDate = ref('2020-01-01')
const currentScript = ref<CollectionScript | null>(null)
const executing = ref(false)

const isTodaySelected = computed(() => {
  const today = new Date()
  const y = today.getFullYear()
  const m = String(today.getMonth() + 1).padStart(2, '0')
  const d = String(today.getDate()).padStart(2, '0')
  return executeDate.value === `${y}-${m}-${d}`
})

function disabledDate(time: Date) {
  const minDate = new Date(minExecuteDate.value)
  const today = new Date()
  today.setHours(23, 59, 59, 999)
  return time.getTime() < minDate.getTime() || time.getTime() > today.getTime()
}

// 失败通知
const failedAlertVisible = ref(sessionStorage.getItem('dismissFailedAlert') !== 'true')
const failedCount = computed(() =>
  scripts.value.filter(s => s.lastExecutionStatus === 'failed').length
)
function dismissFailedAlert() {
  failedAlertVisible.value = false
  sessionStorage.setItem('dismissFailedAlert', 'true')
}

const filters = reactive({
  status: '',
  trigger: '',
  source: '',
  keyword: '',
  categoryId: ''
})

const pagination = reactive({
  page: 1,
  pageSize: 10
})

const stats = reactive({
  total: 0,
  enabled: 0,
  disabled: 0,
  todayExec: 0,
  success: 0,
  failed: 0
})

// Filtered scripts
const filteredScripts = computed(() => {
  return scripts.value.filter(script => {
    const matchStatus = !filters.status || script.status === filters.status
    const matchTrigger = !filters.trigger || script.triggerType === filters.trigger
    const matchSource = !filters.source || script.source === filters.source
    const matchKeyword = !filters.keyword ||
      script.scriptName.toLowerCase().includes(filters.keyword.toLowerCase()) ||
      (script.description && script.description.toLowerCase().includes(filters.keyword.toLowerCase()))
    const matchCategory = !filters.categoryId || String(script.categoryId) === filters.categoryId
    return matchStatus && matchTrigger && matchSource && matchKeyword && matchCategory
  })
})


// Paginated scripts
const totalPages = computed(() => Math.ceil(filteredScripts.value.length / pagination.pageSize) || 1)

const paginatedScripts = computed(() => {
  const start = (pagination.page - 1) * pagination.pageSize
  const end = start + pagination.pageSize
  return filteredScripts.value.slice(start, end)
})

const datasourceList = ref<{ code: string; name: string }[]>([])

const flatCategories = ref<Array<Category & { depth: number }>>([])

function flattenTree(cats: Category[], depth = 0): Array<Category & { depth: number }> {
  const result: Array<Category & { depth: number }> = []
  for (const cat of cats) {
    result.push({ ...cat, depth })
    if (cat.children && cat.children.length > 0) {
      result.push(...flattenTree(cat.children, depth + 1))
    }
  }
  return result
}

async function loadCategories() {
  try {
    const res: any = await categoryApi.tree()
    const tree = res.data?.data || []
    flatCategories.value = flattenTree(tree)
  } catch (e) {
    console.error('加载分类列表失败', e)
  }
}

async function loadDatasources() {
  try {
    const res: any = await datasourceApi.list()
    datasourceList.value = res.data || []
  } catch (e) {
    console.error('加载数据源列表失败', e)
  }
}

// Page numbers to display
const visiblePages = computed(() => {
  const pages: number[] = []
  const total = totalPages.value
  const current = pagination.page

  if (total <= 5) {
    for (let i = 1; i <= total; i++) pages.push(i)
  } else {
    if (current <= 3) {
      pages.push(1, 2, 3, 4, 5)
    } else if (current >= total - 2) {
      for (let i = total - 4; i <= total; i++) pages.push(i)
    } else {
      for (let i = current - 2; i <= current + 2; i++) pages.push(i)
    }
  }
  return pages
})

// Selection
const isAllSelected = computed(() => {
  return paginatedScripts.value.length > 0 &&
    paginatedScripts.value.every(s => s.id !== undefined && selectedRows.value.includes(s.id))
})

const isIndeterminate = computed(() => {
  const selected = paginatedScripts.value.filter(s => s.id !== undefined && selectedRows.value.includes(s.id)).length
  return selected > 0 && selected < paginatedScripts.value.length
})

// Methods
function formatTime(time?: string): string {
  if (!time) return '--'
  return time.substring(0, 16).replace('T', ' ')
}

function getCategoryName(categoryId: number | undefined | null): string {
  if (!categoryId) return '--'
  const cat = flatCategories.value.find(c => c.id === categoryId)
  return cat ? `${cat.icon} ${cat.name}` : '--'
}

function formatNumber(num: number): string {
  if (num >= 1000) {
    return (num / 1000).toFixed(1) + 'k'
  }
  return num.toString()
}

function handleSelect(id: number | undefined) {
  if (id === undefined) return
  const index = selectedRows.value.indexOf(id)
  if (index === -1) {
    selectedRows.value.push(id)
  } else {
    selectedRows.value.splice(index, 1)
  }
}

function handleSelectAll() {
  if (isAllSelected.value) {
    selectedRows.value = selectedRows.value.filter(id =>
      !paginatedScripts.value.some(s => s.id === id)
    )
  } else {
    paginatedScripts.value.forEach(s => {
      if (s.id !== undefined && !selectedRows.value.includes(s.id)) {
        selectedRows.value.push(s.id)
      }
    })
  }
}

function handleClearSelection() {
  selectedRows.value = []
}

function handleFilter() {
  pagination.page = 1
}

function handlePageSizeChange() {
  pagination.page = 1
}

function goToPage(page: number) {
  if (page < 1 || page > totalPages.value) return
  pagination.page = page
}

function prevPage() {
  if (pagination.page > 1) pagination.page--
}

function nextPage() {
  if (pagination.page < totalPages.value) pagination.page++
}

function handleRefresh() {
  loadData()
  ElMessage.success('数据已刷新')
}

function handleCreate() {
  router.push('/scripts/create')
}

function handleDetail(id: number) {
  router.push(`/scripts/${id}`)
}

async function handleDelete(script: CollectionScript) {
  try {
    await ElMessageBox.confirm(
      `确定删除任务"${script.scriptName}"吗？\n将同时清除该任务的执行记录、日志等关联数据。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await scriptApi.delete(script.id!)
    ElMessage.success('删除成功')
    handleRefresh()
  } catch (e: any) {
    if (e !== 'cancel') {
      console.error(e)
    }
  }
}

function handleRecord(id: number) {
  router.push(`/scripts/${id}?tab=records`)
}

async function handleExecute(script: CollectionScript) {
  currentScript.value = script
  // 设置默认值
  const today = new Date()
  const y = today.getFullYear()
  const m = String(today.getMonth() + 1).padStart(2, '0')
  const d = String(today.getDate()).padStart(2, '0')
  const todayStr = `${y}-${m}-${d}`
  // 默认选中: 如果最小日期<今天则选昨天，否则选今天
  if (minExecuteDate.value < todayStr) {
    const yesterday = new Date(today)
    yesterday.setDate(yesterday.getDate() - 1)
    executeDate.value = `${yesterday.getFullYear()}-${String(yesterday.getMonth() + 1).padStart(2, '0')}-${String(yesterday.getDate()).padStart(2, '0')}`
  } else {
    executeDate.value = todayStr
  }
  executeDialogVisible.value = true
}

async function confirmExecute() {
  if (!currentScript.value) return
  // 选中今天的二次确认
  if (isTodaySelected.value) {
    try {
      await ElMessageBox.confirm(
        '当前选择采集今日日报，平台一般18点后才会更新当日数据，确定立即执行吗？',
        '确认执行',
        { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
      )
    } catch {
      return
    }
  }
  const script = currentScript.value
  executing.value = true
  try {
    const params = executeDate.value ? { date: executeDate.value } : undefined
    const res: any = await scriptApi.execute(script.id!, params)
    executeDialogVisible.value = false
    if (res.data?.executionId) {
      progressDrawer.value?.open(res.data.executionId)
    }
    ElMessage.success('脚本已触发执行')
  } catch (e: any) {
    if (e !== 'cancel') {
      console.error(e)
      ElMessage.error(e.message || '执行失败，请检查脚本配置')
    }
  } finally {
    executing.value = false
  }
}

function handleToggleStatus(script: CollectionScript) {
  const newStatus = script.status === 'enabled' ? 'disabled' : 'enabled'

  // 调用 API
  const action = newStatus === 'enabled' ? scriptApi.enable(script.id!) : scriptApi.disable(script.id!)
  action.then(() => {
    script.status = newStatus
    if (newStatus === 'enabled') {
      stats.enabled++
      stats.disabled--
    } else {
      stats.enabled--
      stats.disabled++
    }
    ElMessage.success(`任务已${newStatus === 'enabled' ? '启用' : '禁用'}`)
  }).catch(console.error)
}

async function handleBatchEnable() {
  try {
    const idsWithDef = selectedRows.value.filter(id => id !== undefined) as number[]
    await Promise.all(idsWithDef.map(id => scriptApi.enable(id)))
    scripts.value.forEach(s => {
      if (s.id !== undefined && selectedRows.value.includes(s.id) && s.status === 'disabled') {
        s.status = 'enabled'
      }
    })
    stats.enabled = scripts.value.filter(s => s.status === 'enabled').length
    stats.disabled = scripts.value.filter(s => s.status === 'disabled').length
    ElMessage.success('批量启用成功')
    handleClearSelection()
  } catch (e) {
    console.error(e)
  }
}

async function handleBatchDisable() {
  try {
    const idsWithDef = selectedRows.value.filter(id => id !== undefined) as number[]
    await Promise.all(idsWithDef.map(id => scriptApi.disable(id)))
    scripts.value.forEach(s => {
      if (s.id !== undefined && selectedRows.value.includes(s.id) && s.status === 'enabled') {
        s.status = 'disabled'
      }
    })
    stats.enabled = scripts.value.filter(s => s.status === 'enabled').length
    stats.disabled = scripts.value.filter(s => s.status === 'disabled').length
    ElMessage.success('批量禁用成功')
    handleClearSelection()
  } catch (e) {
    console.error(e)
  }
}

async function handleBatchExecute() {
  try {
    await ElMessageBox.confirm(`确定立即执行已选择的 ${selectedRows.value.length} 个脚本吗？`, '批量执行确认', { type: 'info' })
    const idsWithDef = selectedRows.value.filter(id => id !== undefined) as number[]
    const results = await Promise.allSettled(idsWithDef.map(id => scriptApi.execute(id)))
    const rejected = results.filter(r => r.status === 'rejected')
    if (rejected.length === 0) {
      ElMessage.success(`批量执行请求已提交 (${idsWithDef.length} 个)`)
    } else {
      ElMessage.error(`${rejected.length} / ${idsWithDef.length} 个脚本执行失败`)
    }
    handleClearSelection()
    loadData()
  } catch (e: any) {
    if (e !== 'cancel') {
      console.error(e)
    }
  }
}

async function loadData() {
  loading.value = true
  try {
    // 获取所有脚本（客户端分页 + 过滤），确保搜索和过滤在全量数据上生效
    const params: any = { page: 1, size: 9999 }
    if (filters.status) params.status = filters.status
    if (filters.source) params.source = filters.source
    const res: any = await scriptApi.list(params)
    scripts.value = (res.data?.records || []).map((s: any) => {
      // 扩展字段 lastExecutionStatus，由后端填充
      return s
    })
    // 真实统计数据
    try {
      const statsRes: any = await scriptApi.stats()
      const s = statsRes.data || {}
      stats.total = s.total || 0
      stats.enabled = s.enabled || 0
      stats.disabled = s.disabled || 0
    } catch (_) { /* ignore */ }
    stats.success = scripts.value.reduce((sum: number, s: any) => sum + (s.successCount || 0), 0)
    stats.failed = scripts.value.reduce((sum: number, s: any) => sum + (s.failedCount || 0), 0)
  } catch (e) {
    console.error('Failed to load scripts', e)
  } finally {
    loading.value = false
  }
}

async function handleRetry(script: CollectionScript) {
  try {
    await ElMessageBox.confirm(`确定重新执行"${script.scriptName}"吗？`, '重试确认', { type: 'info' })
    const res: any = await scriptApi.execute(script.id!)
    if (res.data?.executionId) {
      progressDrawer.value?.open(res.data.executionId)
    }
    ElMessage.success('脚本已触发执行')
  } catch (e: any) {
    if (e !== 'cancel') console.error(e)
  }
}

function execStatusClass(lastStatus: string | undefined, scriptStatus: string | undefined): string {
  if (lastStatus === 'success') return 'success'
  if (lastStatus === 'failed') return 'failed'
  if (lastStatus === 'running' || lastStatus === 'pending') return 'running'
  return 'none'
}

function execStatusText(lastStatus: string | undefined, scriptStatus: string | undefined): string {
  if (lastStatus === 'success') return '成功'
  if (lastStatus === 'failed') return '失败'
  if (lastStatus === 'running') return '运行中'
  if (lastStatus === 'pending') return '等待中'
  return '-'
}

onMounted(() => {
  loadData()
  loadDatasources()
  loadCategories()
})

onUnmounted(() => {
})
</script>

<style scoped>
.task-list-container {
  min-height: 100vh;
  background: var(--bg-primary);
  color: var(--text-primary);
}

/* Header */
.header {
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border-color);
  padding: 14px 24px;
  display: flex;
  align-items: center;
  gap: 16px;
}

.header-title {
  font-size: 18px;
  font-weight: 600;
}

.header-actions {
  margin-left: auto;
  display: flex;
  gap: 8px;
}

.btn-primary {
  padding: 8px 16px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: linear-gradient(135deg, var(--accent-blue), var(--accent-purple));
  color: #fff;
}

.btn-primary:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(88, 166, 255, 0.3);
}

.btn-secondary {
  padding: 8px 16px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--border-color);
  background: var(--bg-card);
  color: var(--text-primary);
}

.btn-secondary:hover {
  background: var(--bg-hover);
  border-color: var(--accent-blue);
}

/* Page Content */
.page-content {
  padding: 20px 24px;
  min-height: calc(100vh - 80px - 48px);
}

/* Stats Cards */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 12px;
  margin-bottom: 20px;
}

.stat-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 16px 20px;
  display: flex;
  align-items: center;
  gap: 14px;
  transition: all 0.2s ease;
}

.stat-card:hover {
  border-color: var(--accent-blue);
}

.stat-icon {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
}

.stat-icon.total { background: linear-gradient(135deg, var(--accent-blue), var(--accent-purple)); }
.stat-icon.enabled { background: linear-gradient(135deg, var(--accent-green), #2d9a5a); }
.stat-icon.disabled { background: rgba(110, 118, 129, 0.2); }
.stat-icon.running { background: linear-gradient(135deg, var(--accent-orange), #d97706); }
.stat-icon.success { background: linear-gradient(135deg, var(--accent-green), #2d9a5a); }
.stat-icon.failed { background: linear-gradient(135deg, var(--accent-red), #c53030); }

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
}

.stat-label {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 2px;
}

/* Filter Bar */
.filter-bar {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 16px 20px;
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.filter-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-label {
  font-size: 12px;
  color: var(--text-muted);
}

.filter-select {
  padding: 8px 32px 8px 12px;
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-primary);
  font-size: 13px;
  cursor: pointer;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' fill='%238b949e' viewBox='0 0 16 16'%3E%3Cpath d='M8 11L3 6h10z'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 10px center;
}

.filter-select:focus {
  outline: none;
  border-color: var(--accent-blue);
}

.filter-search {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
  min-width: 200px;
}

.search-input {
  flex: 1;
  padding: 8px 12px;
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-primary);
  font-size: 13px;
}

.search-input:focus {
  outline: none;
  border-color: var(--accent-blue);
}

.search-btn {
  padding: 8px 16px;
  background: var(--accent-blue);
  border: none;
  border-radius: 6px;
  color: #fff;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.search-btn:hover {
  background: #4a9aff;
}

/* Table */
.table-container {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  overflow: hidden;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
}

.data-table th {
  text-align: left;
  padding: 12px 16px;
  font-size: 11px;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border-color);
}

.col-checkbox {
  width: 40px;
  text-align: center;
}

.data-table td {
  padding: 14px 16px;
  font-size: 13px;
  border-bottom: 1px solid var(--border-color);
  vertical-align: middle;
}

.data-table tr:last-child td {
  border-bottom: none;
}

.data-table tr.hovered td {
  background: rgba(88, 166, 255, 0.05);
}

/* Checkbox */
.checkbox {
  width: 18px;
  height: 18px;
  cursor: pointer;
  appearance: none;
  -webkit-appearance: none;
  background: var(--bg-primary);
  border: 2px solid var(--border-color);
  border-radius: 4px;
  position: relative;
  transition: all 0.2s ease;
}

.checkbox:hover {
  border-color: var(--accent-blue);
}

.checkbox:checked {
  background: var(--accent-blue);
  border-color: var(--accent-blue);
}

.checkbox:checked::after {
  content: '✓';
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  color: #fff;
  font-size: 12px;
  font-weight: bold;
}

.checkbox:indeterminate {
  background: var(--accent-blue);
  border-color: var(--accent-blue);
}

.checkbox:indeterminate::after {
  content: '-';
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  color: #fff;
  font-size: 14px;
  font-weight: bold;
}

/* Task Name */
.task-name {
  font-weight: 600;
  color: var(--text-primary);
  cursor: pointer;
}

.task-name:hover {
  color: var(--accent-blue);
}

.task-desc {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 4px;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* Time */
.time-value {
  font-size: 12px;
  color: var(--text-secondary);
  font-family: 'JetBrains Mono', monospace;
}

.time-value.next {
  color: var(--accent-blue);
  font-weight: 500;
}

/* Stats */
.stats-inline {
  display: flex;
  gap: 10px;
  font-size: 12px;
}

.stats-inline .success {
  color: var(--accent-green);
}

.stats-inline .failed {
  color: var(--accent-red);
}

/* Status Badge */
.status-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
}
.status-badge.success {
  background: rgba(63, 185, 80, 0.15);
  color: var(--accent-green);
}
.status-badge.failed {
  background: rgba(248, 81, 73, 0.15);
  color: var(--accent-red);
}
.status-badge.running {
  background: rgba(240, 136, 62, 0.15);
  color: var(--accent-orange);
}
.status-badge.none {
  background: rgba(110, 118, 129, 0.1);
  color: var(--text-muted);
}

/* Category */
.category-cell {
  font-size: 12px;
  color: var(--text-secondary);
}

/* Actions */
.action-buttons {
  display: flex;
  gap: 6px;
  flex-wrap: nowrap;
}

.action-btn {
  padding: 5px 10px;
  border-radius: 5px;
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
  border: none;
  background: transparent;
  color: var(--text-secondary);
}

.action-btn:hover {
  color: var(--text-primary);
  background: var(--bg-hover);
}

.action-btn.primary {
  background: rgba(88, 166, 255, 0.1);
  color: var(--accent-blue);
}

.action-btn.primary:hover {
  background: rgba(88, 166, 255, 0.2);
}

.action-btn.success {
  background: rgba(63, 185, 80, 0.1);
  color: var(--accent-green);
}

.action-btn.success:hover {
  background: rgba(63, 185, 80, 0.2);
}

.action-btn.warning {
  background: rgba(240, 136, 62, 0.1);
  color: var(--accent-orange);
}

.action-btn.warning:hover {
  background: rgba(240, 136, 62, 0.2);
}

.action-btn.danger {
  background: rgba(220, 38, 38, 0.1);
  color: var(--accent-red);
}

.action-btn.danger:hover {
  background: rgba(220, 38, 38, 0.2);
}

/* Running indicator */
.running-indicator {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--accent-orange);
  animation: pulse 1.5s infinite;
  margin-right: 6px;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

/* Batch Actions */
.batch-actions {
  display: none;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: rgba(88, 166, 255, 0.1);
  border-bottom: 1px solid var(--border-color);
}

.batch-actions.show {
  display: flex;
}

.batch-info {
  font-size: 13px;
  color: var(--accent-blue);
  font-weight: 500;
}

/* Pagination */
.pagination {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: var(--bg-secondary);
  border-top: 1px solid var(--border-color);
}

.pagination-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.page-info {
  font-size: 12px;
  color: var(--text-secondary);
}

.page-size-select {
  display: flex;
  align-items: center;
  gap: 6px;
}

.page-size-select label {
  font-size: 12px;
  color: var(--text-secondary);
}

.page-size-select select {
  padding: 4px 24px 4px 8px;
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: 4px;
  color: var(--text-primary);
  font-size: 12px;
  cursor: pointer;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='10' height='10' fill='%238b949e' viewBox='0 0 16 16'%3E%3Cpath d='M8 11L3 6h10z'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 6px center;
}

.pagination-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.page-btn {
  width: 28px;
  height: 28px;
  border-radius: 4px;
  border: 1px solid var(--border-color);
  background: var(--bg-card);
  color: var(--text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s ease;
  font-size: 12px;
}

.page-btn:hover:not(:disabled) {
  border-color: var(--accent-blue);
  color: var(--accent-blue);
}

.page-btn.active {
  background: var(--accent-blue);
  border-color: var(--accent-blue);
  color: #fff;
}

.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

/* Failure Alert */
.failure-alert {
  margin-bottom: 12px;
}

/* Responsive */
@media (max-width: 1200px) {
  .stats-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .filter-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .filter-search {
    margin-left: 0;
    min-width: auto;
  }
}
</style>
