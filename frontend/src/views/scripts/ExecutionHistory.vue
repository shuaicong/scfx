<template>
  <div class="execution-history">
    <!-- Header -->
    <div class="header">
      <div class="header-left">
        <button class="back-btn" @click="goBack">
          <span>←</span>
        </button>
        <div class="header-info">
          <div class="header-title">
            <span class="task-icon">{{ script?.scriptName ? '📊' : '📊' }}</span>
            <span>{{ script?.scriptName || '执行历史' }}</span>
          </div>
          <div class="header-meta" v-if="script">
            <span class="meta-tag">数据源: {{ script.source || '-' }}</span>
            <span class="meta-tag">触发: {{ triggerTypeText }}</span>
            <span class="meta-tag" v-if="script.cronExpression">{{ script.cronExpression }}</span>
          </div>
        </div>
      </div>
      <div class="header-actions">
        <el-button class="btn-secondary" @click="handleRefresh">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
        <el-button class="btn-primary" @click="goToDetail">
          <el-icon><Back /></el-icon>
          返回任务详情
        </el-button>
      </div>
    </div>

    <!-- Page Content -->
    <div class="page-content">
      <!-- Filter Bar -->
      <div class="filter-bar">
        <div class="filter-item">
          <span class="filter-label">状态</span>
          <select v-model="filters.status" class="filter-select" @change="loadExecutions">
            <option value="">全部</option>
            <option value="success">成功</option>
            <option value="failed">失败</option>
            <option value="running">运行中</option>
            <option value="pending">等待中</option>
            <option value="cancelled">已取消</option>
          </select>
        </div>
        <div class="filter-item">
          <span class="filter-label">触发方式</span>
          <select v-model="filters.triggerType" class="filter-select" @change="loadExecutions">
            <option value="">全部</option>
            <option value="manual">手动</option>
            <option value="scheduled">定时</option>
          </select>
        </div>
        <div class="filter-item">
          <span class="filter-label">每页</span>
          <select v-model="pagination.pageSize" class="filter-select" @change="loadExecutions">
            <option :value="10">10 条</option>
            <option :value="20">20 条</option>
            <option :value="50">50 条</option>
          </select>
        </div>
        <div class="filter-info">
          共 {{ totalRecords }} 条记录
        </div>
      </div>

      <!-- Table -->
      <div class="table-container">
        <table class="data-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>状态</th>
              <th>触发方式</th>
              <th>版本</th>
              <th>开始时间</th>
              <th>结束时间</th>
              <th>耗时</th>
              <th>采集数量</th>
              <th>知识库</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="exec in executions"
              :key="exec.executionId"
              @mouseenter="hoveredRow = exec.executionId"
              @mouseleave="hoveredRow = null"
              :class="{ hovered: hoveredRow === exec.executionId }"
            >
              <td>
                <span class="mono">{{ exec.executionId?.substring(0, 8) }}...</span>
              </td>
              <td>
                <span class="status-badge" :class="exec.status">
                  {{ statusText(exec.status) }}
                </span>
              </td>
              <td>
                <span class="trigger-badge" :class="exec.triggerType">
                  {{ exec.triggerType === 'manual' ? '手动' : '定时' }}
                </span>
              </td>
              <td>
                <span class="version-num">v{{ exec.versionNum ?? exec.versionId ?? '-' }}</span>
              </td>
              <td>
                <span class="time-value">{{ formatTime(exec.startTime) }}</span>
              </td>
              <td>
                <span class="time-value">{{ formatTime(exec.endTime) }}</span>
              </td>
              <td>
                <span class="mono">{{ exec.durationMs ? (exec.durationMs / 1000).toFixed(1) + 's' : '-' }}</span>
              </td>
              <td>
                <span>{{ exec.collectedCount ?? '-' }}</span>
              </td>
              <td>
                <button class="action-btn primary" @click="viewKnowledge(exec.executionId)">查看</button>
              </td>
              <td>
                <div class="action-buttons">
                  <button class="action-btn primary" @click="viewDetail(exec.executionId)">详情</button>
                </div>
              </td>
            </tr>
            <tr v-if="executions.length === 0">
              <td colspan="10" class="empty-cell">暂无执行记录</td>
            </tr>
          </tbody>
        </table>

        <!-- Pagination -->
        <div class="pagination">
          <div class="pagination-left">
            <div class="page-info">第 {{ pagination.page }} / {{ totalPages }} 页</div>
          </div>
          <div class="pagination-right">
            <button class="page-btn" :disabled="pagination.page <= 1" @click="prevPage">‹</button>
            <button
              v-for="p in visiblePages"
              :key="p"
              class="page-btn"
              :class="{ active: p === pagination.page }"
              @click="goToPage(p)"
            >{{ p }}</button>
            <button class="page-btn" :disabled="pagination.page >= totalPages" @click="nextPage">›</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style>
@import '@/styles/dark-theme.css';
</style>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, Back } from '@element-plus/icons-vue'
import { scriptApi, executionApi } from '@/api'
import type { CollectionScript } from '@/api'

const route = useRoute()
const router = useRouter()
const scriptId = Number(route.params.scriptId)

const script = ref<CollectionScript | null>(null)
const executions = ref<any[]>([])
const loading = ref(false)
const hoveredRow = ref<string | null>(null)
const totalRecords = ref(0)

const filters = reactive({
  status: '',
  triggerType: ''
})

const pagination = reactive({
  page: 1,
  pageSize: 10
})

const totalPages = computed(() => Math.ceil(totalRecords.value / pagination.pageSize) || 1)

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

const triggerTypeText = computed(() => {
  if (!script.value?.triggerType) return '-'
  const labels: Record<string, string> = { cron: 'Cron', cycle: '周期', once: '单次', manual: '手动' }
  return labels[script.value.triggerType] || script.value.triggerType
})

function formatTime(time?: string): string {
  if (!time) return '--'
  return time.substring(0, 16).replace('T', ' ')
}

function statusText(status: string): string {
  const labels: Record<string, string> = {
    success: '成功', failed: '失败', running: '运行中',
    pending: '等待中', cancelled: '已取消'
  }
  return labels[status] || status
}

async function loadScript() {
  try {
    const res: any = await scriptApi.getById(scriptId)
    script.value = res.data
  } catch (e) {
    console.error('加载脚本信息失败', e)
  }
}

async function loadExecutions() {
  loading.value = true
  try {
    const params: any = {
      page: pagination.page,
      size: pagination.pageSize
    }
    if (filters.status) params.status = filters.status
    if (filters.triggerType) params.triggerType = filters.triggerType
    const res: any = await executionApi.list(scriptId, params)
    executions.value = res.data?.records || []
    totalRecords.value = res.data?.total || 0
  } catch (e) {
    console.error('加载执行记录失败', e)
  } finally {
    loading.value = false
  }
}

function goToPage(page: number) {
  if (page < 1 || page > totalPages.value) return
  pagination.page = page
  loadExecutions()
}

function prevPage() {
  if (pagination.page > 1) {
    pagination.page--
    loadExecutions()
  }
}

function nextPage() {
  if (pagination.page < totalPages.value) {
    pagination.page++
    loadExecutions()
  }
}

function handleRefresh() {
  loadExecutions()
  ElMessage.success('数据已刷新')
}

function viewDetail(executionId: string) {
  router.push(`/scripts/${scriptId}/executions/${executionId}`)
}

function goBack() {
  router.back()
}

function goToDetail() {
  router.push(`/scripts/${scriptId}`)
}

function viewKnowledge(executionId: string) {
  router.push(`/knowledge?executionId=${executionId}`)
}

onMounted(() => {
  loadScript()
  loadExecutions()
})
</script>

<style scoped>
.execution-history {
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

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
}

.back-btn {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  border: 1px solid var(--border-color);
  background: var(--bg-card);
  color: var(--text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  transition: all 0.15s ease;
}

.back-btn:hover {
  border-color: var(--accent-blue);
  color: var(--accent-blue);
}

.header-info {
  flex: 1;
}

.header-title {
  font-size: 18px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
}

.task-icon {
  font-size: 20px;
}

.header-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
  flex-wrap: wrap;
}

.meta-tag {
  font-size: 11px;
  color: var(--text-muted);
  background: var(--bg-primary);
  padding: 2px 8px;
  border-radius: 4px;
  border: 1px solid var(--border-color);
}

.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
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

.filter-info {
  font-size: 12px;
  color: var(--text-muted);
  margin-left: auto;
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

.empty-cell {
  text-align: center;
  color: var(--text-muted);
  padding: 40px 16px !important;
  font-size: 14px !important;
}

.mono {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  color: var(--text-secondary);
}

.time-value {
  font-size: 12px;
  color: var(--text-secondary);
  font-family: 'JetBrains Mono', monospace;
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

.status-badge.pending {
  background: rgba(110, 118, 129, 0.1);
  color: var(--text-muted);
}

.status-badge.cancelled {
  background: rgba(110, 118, 129, 0.1);
  color: var(--text-muted);
}

/* Trigger Badge */
.trigger-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
}

.trigger-badge.manual {
  background: rgba(88, 166, 255, 0.1);
  color: var(--accent-blue);
}

.trigger-badge.scheduled {
  background: rgba(240, 136, 62, 0.1);
  color: var(--accent-orange);
}

.version-num {
  font-size: 12px;
  font-family: 'JetBrains Mono', monospace;
  color: var(--text-secondary);
}

/* Action Buttons */
.action-buttons {
  display: flex;
  gap: 6px;
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
</style>
