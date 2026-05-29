<template>
  <div class="alert-records-page">
    <div class="page-header">
      <h2 class="page-title">告警记录</h2>
      <div class="header-actions">
        <el-button @click="loadRecords">刷新</el-button>
      </div>
    </div>

    <!-- Filter Bar -->
    <div class="filter-bar">
      <div class="filter-item">
        <span class="filter-label">级别</span>
        <select v-model="filters.level" class="filter-select" @change="loadRecords">
          <option value="">全部</option>
          <option value="critical">严重</option>
          <option value="error">错误</option>
          <option value="warning">警告</option>
          <option value="info">信息</option>
        </select>
      </div>
      <div class="filter-item">
        <span class="filter-label">状态</span>
        <select v-model="filters.status" class="filter-select" @change="loadRecords">
          <option value="">全部</option>
          <option value="pending">待处理</option>
          <option value="sent">已发送</option>
          <option value="resolved">已解决</option>
        </select>
      </div>
      <div class="filter-info">
        共 {{ total }} 条
      </div>
    </div>

    <!-- Table -->
    <div class="table-container">
      <table class="data-table">
        <thead>
          <tr>
            <th>类型</th>
            <th>级别</th>
            <th>标题</th>
            <th>状态</th>
            <th>时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="alert in records" :key="alert.id" class="table-row">
            <td>
              <span class="type-badge">{{ alertTypeLabel(alert.alertType) }}</span>
            </td>
            <td>
              <span class="level-badge" :class="alert.alertLevel">
                {{ levelLabel(alert.alertLevel) }}
              </span>
            </td>
            <td class="title-cell">
              <div class="alert-title">{{ alert.alertTitle }}</div>
              <div class="alert-content" v-if="alert.alertContent">{{ alert.alertContent }}</div>
            </td>
            <td>
              <span class="status-badge" :class="alert.status">
                {{ statusLabel(alert.status) }}
              </span>
            </td>
            <td class="time-cell">{{ formatTime(alert.createdAt) }}</td>
            <td>
              <button
                v-if="alert.status !== 'resolved'"
                class="action-btn primary"
                @click="resolveAlert(alert)"
              >
                解决
              </button>
            </td>
          </tr>
          <tr v-if="!loading && records.length === 0">
            <td colspan="6" class="empty-cell">暂无告警记录</td>
          </tr>
        </tbody>
      </table>

      <!-- Pagination -->
      <div class="pagination">
        <div class="pagination-left">
          <div class="page-info">第 {{ page }} / {{ totalPages }} 页</div>
        </div>
        <div class="pagination-right">
          <button class="page-btn" :disabled="page <= 1" @click="changePage(page - 1)">‹</button>
          <button
            v-for="p in visiblePages"
            :key="p"
            class="page-btn"
            :class="{ active: p === page }"
            @click="changePage(p)"
          >{{ p }}</button>
          <button class="page-btn" :disabled="page >= totalPages" @click="changePage(page + 1)">›</button>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { alertApi } from '@/api/alert'
import type { AlertRecord } from '@/api/alert'

const records = ref<AlertRecord[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)

const filters = reactive({
  level: '',
  status: '',
})

const totalPages = computed(() => Math.ceil(total.value / pageSize.value) || 1)

const visiblePages = computed(() => {
  const pages: number[] = []
  const total = totalPages.value
  const current = page.value
  if (total <= 5) {
    for (let i = 1; i <= total; i++) pages.push(i)
  } else if (current <= 3) {
    pages.push(1, 2, 3, 4, 5)
  } else if (current >= total - 2) {
    for (let i = total - 4; i <= total; i++) pages.push(i)
  } else {
    for (let i = current - 2; i <= current + 2; i++) pages.push(i)
  }
  return pages
})

function alertTypeLabel(type: string): string {
  const labels: Record<string, string> = {
    CONTINUOUS_FAIL: '连续失败',
    SERVICE_OFFLINE: '离线检测',
  }
  return labels[type] || type
}

function levelLabel(level: string): string {
  const labels: Record<string, string> = {
    critical: '严重',
    error: '错误',
    warning: '警告',
    info: '信息',
  }
  return labels[level] || level
}

function statusLabel(status: string): string {
  const labels: Record<string, string> = {
    pending: '待处理',
    sent: '已发送',
    failed: '发送失败',
    resolved: '已解决',
  }
  return labels[status] || status
}

function formatTime(time?: string): string {
  if (!time) return '--'
  return time.substring(0, 16).replace('T', ' ')
}

async function loadRecords() {
  loading.value = true
  try {
    const params: any = { page: page.value, size: pageSize.value }
    if (filters.level) params.level = filters.level
    if (filters.status) params.status = filters.status
    const res: any = await alertApi.list(params)
    records.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) {
    console.error('加载告警记录失败', e)
  } finally {
    loading.value = false
  }
}

function changePage(p: number) {
  if (p < 1 || p > totalPages.value) return
  page.value = p
  loadRecords()
}

async function resolveAlert(alert: AlertRecord) {
  try {
    await ElMessageBox.confirm(`确定解决此告警吗？`, '确认', { type: 'info' })
    await alertApi.resolve(alert.id)
    alert.status = 'resolved'
    ElMessage.success('告警已解决')
  } catch (e: any) {
    if (e !== 'cancel') console.error(e)
  }
}

onMounted(() => {
  loadRecords()
})
</script>

<style scoped>
.alert-records-page {
  padding: 24px;
  min-height: 100vh;
  background: var(--bg-primary);
  color: var(--text-primary);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  margin: 0;
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
}

.data-table tr:last-child td {
  border-bottom: none;
}

.title-cell {
  max-width: 300px;
}

.alert-title {
  font-weight: 500;
}

.alert-content {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.time-cell {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  color: var(--text-secondary);
  white-space: nowrap;
}

.type-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  background: rgba(88, 166, 255, 0.1);
  color: var(--accent-blue);
}

.level-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
}

.level-badge.critical {
  background: rgba(229, 62, 62, 0.15);
  color: #e53e3e;
}

.level-badge.error {
  background: rgba(237, 137, 54, 0.15);
  color: #ed8936;
}

.level-badge.warning {
  background: rgba(236, 201, 75, 0.15);
  color: #d69e2e;
}

.level-badge.info {
  background: rgba(66, 153, 225, 0.15);
  color: #4299e1;
}

.status-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
}

.status-badge.pending {
  background: rgba(240, 136, 62, 0.15);
  color: var(--accent-orange);
}

.status-badge.sent {
  background: rgba(88, 166, 255, 0.1);
  color: var(--accent-blue);
}

.status-badge.failed {
  background: rgba(248, 81, 73, 0.15);
  color: var(--accent-red);
}

.status-badge.resolved {
  background: rgba(63, 185, 80, 0.15);
  color: var(--accent-green);
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

.empty-cell {
  text-align: center;
  color: var(--text-muted);
  padding: 40px 16px !important;
  font-size: 14px !important;
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
