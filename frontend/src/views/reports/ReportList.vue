<template>
  <div class="report-list-container">
    <!-- Header -->
    <div class="header">
      <div class="header-title">
        <span>智能研报</span>
      </div>
      <div class="header-actions">
        <el-button class="btn-secondary" @click="handleRefresh">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
        <el-button class="btn-primary" @click="showNewReportDialog">
          <el-icon><Plus /></el-icon>
          新建报告
        </el-button>
      </div>
    </div>

    <!-- Page Container -->
    <div class="page-content">
      <!-- Filter Tabs -->
      <div class="filter-tabs">
        <span
          v-for="tab in filterTabs"
          :key="tab.key"
          class="filter-tab"
          :class="{ active: activeTab === tab.key }"
          @click="handleTabClick(tab.key)"
        >{{ tab.label }}</span>
      </div>

      <!-- Search Bar -->
      <div class="search-bar">
        <div class="search-input-wrap">
          <el-icon class="search-icon"><Search /></el-icon>
          <input
            v-model="searchKeyword"
            type="text"
            class="search-input"
            placeholder="搜索报告标题..."
            @keyup.enter="handleSearch"
          />
        </div>
        <button class="search-btn" @click="handleSearch">搜索</button>
      </div>

      <!-- Table -->
      <div class="table-container">
        <table class="data-table" v-loading="loading">
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
              <th>报告标题</th>
              <th>品种</th>
              <th>模板</th>
              <th>版本</th>
              <th>状态</th>
              <th>更新时间</th>
              <th style="width: 140px">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="report in reports"
              :key="report.id"
              @mouseenter="hoveredRow = report.id"
              @mouseleave="hoveredRow = null"
              :class="{ hovered: hoveredRow === report.id }"
            >
              <td class="col-checkbox">
                <input
                  type="checkbox"
                  class="checkbox"
                  :checked="report.id !== undefined && selectedRows.includes(report.id)"
                  @change="handleSelect(report.id)"
                />
              </td>
              <td>
                <div class="report-title" @click="goToEditor(report)">{{ report.title }}</div>
              </td>
              <td>
                <span class="variety-tag" :class="getVarietyClass(report.variety)">{{ getVarietyLabel(report.variety) }}</span>
              </td>
              <td>
                <span class="template-name">{{ report.templateName || getTemplateName(report) }}</span>
              </td>
              <td>
                <span class="version-badge">{{ report.version ? 'v' + report.version : '--' }}</span>
              </td>
              <td>
                <span class="status-tag" :class="getStatusClass(report.status)">{{ getStatusLabel(report.status) }}</span>
              </td>
              <td>
                <span class="time-value">{{ formatTime(report.updatedAt || report.createTime) }}</span>
              </td>
              <td>
                <div class="action-buttons">
                  <button class="action-btn primary" @click="goToEditor(report)">编辑</button>
                  <button class="action-btn" @click="handleExport(report)">导出</button>
                  <el-dropdown trigger="click" @command="(cmd: string) => handleMoreCommand(cmd, report)">
                    <button class="action-btn more-btn">⋯</button>
                    <template #dropdown>
                      <el-dropdown-menu class="dark-dropdown">
                        <el-dropdown-item command="delete">
                          <el-icon><Delete /></el-icon>
                          删除
                        </el-dropdown-item>
                        <el-dropdown-item command="versions">
                          <el-icon><Clock /></el-icon>
                          版本历史
                        </el-dropdown-item>
                        <el-dropdown-item command="regenerate" v-if="report.status !== 'generating'">
                          <el-icon><Refresh /></el-icon>
                          重新生成
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </td>
            </tr>
            <!-- Empty State -->
            <tr v-if="!loading && reports.length === 0">
              <td colspan="8">
                <div class="empty-state">
                  <el-icon class="empty-icon" :size="48"><Document /></el-icon>
                  <p class="empty-text">暂无报告数据</p>
                  <p class="empty-hint">点击「新建报告」创建您的第一份智能研报</p>
                </div>
              </td>
            </tr>
          </tbody>
        </table>

        <!-- Pagination -->
        <div class="pagination" v-if="total > 0">
          <div class="pagination-left">
            <div class="page-info">共 {{ total }} 条</div>
          </div>
          <div class="pagination-right">
            <div class="page-info">第 {{ filters.page }} / {{ totalPages }} 页</div>
            <button class="page-btn" :disabled="filters.page <= 1" @click="prevPage">‹</button>
            <button
              v-for="p in visiblePages"
              :key="p"
              class="page-btn"
              :class="{ active: p === filters.page }"
              @click="goToPage(p)"
            >{{ p }}</button>
            <button class="page-btn" :disabled="filters.page >= totalPages" @click="nextPage">›</button>
          </div>
        </div>
      </div>
    </div>

    <!-- 按模板新建 Dialog -->
    <el-dialog
      v-model="newReportDialog.visible"
      title="按模板新建报告"
      width="520px"
      :close-on-click-modal="false"
      append-to-body
      class="dark-dialog"
    >
      <div class="dialog-body">
        <div class="dialog-field">
          <label>选择模板</label>
          <el-select v-model="newReportDialog.templateId" placeholder="请选择模板" class="dialog-select">
            <el-option
              v-for="tpl in templates"
              :key="tpl.id"
              :label="tpl.name"
              :value="tpl.id"
            >
              <span class="tpl-option">{{ tpl.variety === 'corn' ? '🌽' : '🌾' }} {{ tpl.name }}</span>
            </el-option>
          </el-select>
        </div>
        <div class="dialog-field">
          <label>报告标题</label>
          <el-input v-model="newReportDialog.title" placeholder="输入报告标题" maxlength="200" />
        </div>
        <div class="dialog-field">
          <label>副标题（可选）</label>
          <el-input v-model="newReportDialog.subtitle" placeholder="如：粮源趋紧，玉米价格持续上涨" maxlength="200" />
        </div>
        <div class="dialog-field">
          <label>生成选项</label>
          <div class="radio-group">
            <label class="radio-item">
              <el-radio v-model="newReportDialog.generateNow" :label="false" size="small" />
              <span>创建草稿，稍后生成</span>
            </label>
            <label class="radio-item">
              <el-radio v-model="newReportDialog.generateNow" :label="true" size="small" />
              <span>立即 AI 生成</span>
            </label>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="newReportDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="confirmCreateReport" :loading="creating">创建报告</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style>
@import '@/styles/dark-theme.css';
</style>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Plus, Search, Document, Delete, Clock } from '@element-plus/icons-vue'
import { reportApi } from '@/api/report'
import { useReportList } from './composables/useReportList'

const router = useRouter()

const { reports, loading, total, filters, load, remove } = useReportList()

const hoveredRow = ref<number | null | undefined>(null)
const selectedRows = ref<number[]>([])
const searchKeyword = ref('')
const templates = ref<any[]>([])
const creating = ref(false)

// Filter tabs
const activeTab = ref('all')

const filterTabs = [
  { key: 'all', label: '全部' },
  { key: 'corn', label: '玉米' },
  { key: 'rice', label: '稻米' },
  { key: 'draft', label: '草稿' },
  { key: 'published', label: '已发布' }
]

function handleTabClick(tab: string) {
  activeTab.value = tab
  filters.page = 1
  switch (tab) {
    case 'all':
      filters.variety = ''
      filters.status = ''
      break
    case 'corn':
      filters.variety = 'corn'
      filters.status = ''
      break
    case 'rice':
      filters.variety = 'rice'
      filters.status = ''
      break
    case 'draft':
      filters.variety = ''
      filters.status = 'draft'
      break
    case 'published':
      filters.variety = ''
      filters.status = 'published'
      break
  }
  load()
}

// Search
function handleSearch() {
  filters.keyword = searchKeyword.value.trim()
  filters.page = 1
  load()
}

// Selection
const isAllSelected = computed(() => {
  return reports.value.length > 0 &&
    reports.value.every(r => r.id !== undefined && selectedRows.value.includes(r.id))
})

const isIndeterminate = computed(() => {
  const selected = reports.value.filter(r => r.id !== undefined && selectedRows.value.includes(r.id)).length
  return selected > 0 && selected < reports.value.length
})

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
      !reports.value.some(r => r.id === id)
    )
  } else {
    reports.value.forEach(r => {
      if (r.id !== undefined && !selectedRows.value.includes(r.id)) {
        selectedRows.value.push(r.id)
      }
    })
  }
}

// Pagination
const totalPages = computed(() => Math.ceil(total.value / filters.size) || 1)

const visiblePages = computed(() => {
  const pages: number[] = []
  const total = totalPages.value
  const current = filters.page

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

function goToPage(page: number) {
  if (page < 1 || page > totalPages.value) return
  filters.page = page
  load()
}

function prevPage() {
  if (filters.page > 1) {
    filters.page--
    load()
  }
}

function nextPage() {
  if (filters.page < totalPages.value) {
    filters.page++
    load()
  }
}

// Actions
function handleRefresh() {
  load()
  ElMessage.success('数据已刷新')
}

function goToEditor(report: any) {
  router.push(`/reports/editor/${report.id}`)
}

function getVarietyClass(variety: string | undefined): string {
  if (variety === 'corn') return 'tag-corn'
  if (variety === 'rice') return 'tag-rice'
  return ''
}

function getVarietyLabel(variety: string | undefined): string {
  if (variety === 'corn') return '玉米'
  if (variety === 'rice') return '稻米'
  return variety || '--'
}

function getStatusClass(status: string | undefined): string {
  if (status === 'draft') return 'tag-draft'
  if (status === 'published') return 'tag-published'
  if (status === 'generating') return 'tag-generating'
  return 'tag-draft'
}

function getStatusLabel(status: string | undefined): string {
  if (status === 'draft') return '草稿'
  if (status === 'published') return '已发布'
  if (status === 'generating') return '生成中'
  return status || '--'
}

function getTemplateName(report: any): string {
  return report.templateName || report.template?.name || '--'
}

function formatTime(time?: string): string {
  if (!time) return '--'
  return time.substring(0, 10).replace('T', ' ')
}

async function handleExport(report: any) {
  try {
    await reportApi.export(report.id)
    ElMessage.success('导出请求已提交')
  } catch (e: any) {
    ElMessage.error(e.message || '导出失败')
  }
}

async function handleDelete(report: any) {
  try {
    await ElMessageBox.confirm(
      `确定删除报告"${report.title}"吗？此操作不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await reportApi.delete(report.id)
    ElMessage.success('删除成功')
    load()
  } catch (e: any) {
    if (e !== 'cancel') {
      console.error(e)
    }
  }
}

function handleVersions(report: any) {
  router.push(`/reports/editor/${report.id}?tab=versions`)
}

async function handleRegenerate(report: any) {
  try {
    await reportApi.generate(report.id)
    ElMessage.success('已触发重新生成')
    load()
  } catch (e: any) {
    ElMessage.error(e.message || '重新生成失败')
  }
}

function handleMoreCommand(command: string, report: any) {
  switch (command) {
    case 'delete':
      handleDelete(report)
      break
    case 'versions':
      handleVersions(report)
      break
    case 'regenerate':
      handleRegenerate(report)
      break
  }
}

// 按模板新建 dialog
const newReportDialog = reactive({
  visible: false,
  templateId: null as number | null,
  title: '',
  subtitle: '',
  generateNow: false
})

async function showNewReportDialog() {
  newReportDialog.visible = true
  newReportDialog.title = ''
  newReportDialog.subtitle = ''
  newReportDialog.templateId = null
  newReportDialog.generateNow = false
  // Load templates
  try {
    const res = await reportApi.templateList()
    templates.value = (res as any).data || []
  } catch (e) {
    console.error('加载模板列表失败:', e)
    templates.value = []
  }
}

async function confirmCreateReport() {
  if (!newReportDialog.title.trim()) {
    ElMessage.warning('请输入报告标题')
    return
  }
  if (!newReportDialog.templateId) {
    ElMessage.warning('请选择模板')
    return
  }

  creating.value = true
  try {
    const res = await reportApi.create({
      templateId: newReportDialog.templateId,
      title: newReportDialog.title.trim()
    })
    const created = (res as any).data || {}
    newReportDialog.visible = false

    if (newReportDialog.generateNow && created.id) {
      await reportApi.generate(created.id)
      ElMessage.success('报告已创建，AI 生成已启动')
    } else {
      ElMessage.success('报告草稿已创建')
    }
    load()
  } catch (e: any) {
    ElMessage.error(e.message || '创建报告失败')
  } finally {
    creating.value = false
  }
}
</script>

<style scoped>
.report-list-container {
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

/* Filter Tabs */
.filter-tabs {
  display: flex;
  gap: 0;
  margin-bottom: 12px;
  border-bottom: 1px solid var(--border-color);
}

.filter-tab {
  padding: 8px 18px;
  cursor: pointer;
  color: var(--text-secondary);
  border-bottom: 2px solid transparent;
  transition: all 0.12s;
  font-size: 13px;
  user-select: none;
}

.filter-tab:hover {
  color: var(--text-primary);
}

.filter-tab.active {
  color: var(--accent);
  border-bottom-color: var(--accent);
}

/* Search Bar */
.search-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}

.search-input-wrap {
  flex: 1;
  display: flex;
  align-items: center;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  padding: 0 12px;
  max-width: 360px;
  transition: border-color 0.2s ease;
}

.search-input-wrap:focus-within {
  border-color: var(--accent-blue);
}

.search-icon {
  color: var(--text-muted);
  font-size: 14px;
  margin-right: 6px;
}

.search-input {
  flex: 1;
  padding: 8px 0;
  background: transparent;
  border: none;
  color: var(--text-primary);
  font-size: 13px;
  outline: none;
}

.search-input::placeholder {
  color: var(--text-muted);
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

/* Report Title */
.report-title {
  font-weight: 600;
  color: var(--text-primary);
  cursor: pointer;
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.report-title:hover {
  color: var(--accent-blue);
}

/* Variety Tags */
.variety-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
}

.variety-tag.tag-corn {
  background: rgba(245, 200, 122, 0.15);
  color: var(--accent);
}

.variety-tag.tag-rice {
  background: rgba(76, 175, 80, 0.15);
  color: #4caf50;
}

/* Template Name */
.template-name {
  font-size: 12px;
  color: var(--text-secondary);
}

/* Version Badge */
.version-badge {
  display: inline-block;
  padding: 2px 6px;
  background: rgba(139, 148, 158, 0.1);
  border-radius: 4px;
  font-size: 11px;
  font-family: 'JetBrains Mono', monospace;
  color: var(--text-secondary);
}

/* Status Tags */
.status-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
}

.status-tag.tag-draft {
  background: rgba(139, 148, 158, 0.15);
  color: var(--text-secondary);
}

.status-tag.tag-published {
  background: rgba(63, 185, 80, 0.15);
  color: var(--accent-green);
}

.status-tag.tag-generating {
  background: rgba(100, 181, 246, 0.15);
  color: #64b5f6;
}

/* Time */
.time-value {
  font-size: 12px;
  color: var(--text-secondary);
  font-family: 'JetBrains Mono', monospace;
}

/* Actions */
.action-buttons {
  display: flex;
  gap: 4px;
  flex-wrap: nowrap;
  align-items: center;
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

.more-btn {
  font-size: 16px;
  letter-spacing: 2px;
  padding: 2px 8px;
}

/* Empty State */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 60px 20px;
  color: var(--text-muted);
}

.empty-icon {
  margin-bottom: 12px;
  opacity: 0.4;
}

.empty-text {
  font-size: 15px;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.empty-hint {
  font-size: 12px;
  color: var(--text-muted);
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

/* Dialog */
.dialog-body {
  padding: 4px 0;
}

.dialog-field {
  margin-bottom: 16px;
}

.dialog-field label {
  display: block;
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 6px;
  font-weight: 500;
}

.dialog-select {
  width: 100%;
}

.tpl-option {
  font-size: 13px;
}

.radio-group {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 4px 0;
}

.radio-item {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  font-size: 13px;
  color: var(--text-secondary);
}

.radio-item:hover {
  color: var(--text-primary);
}

/* Global dark dialog overrides */
:global(.dark-dialog .el-dialog) {
  --el-dialog-bg-color: var(--bg-secondary);
  --el-dialog-title-font-size: 16px;
  --el-dialog-padding-primary: 20px;
}

:global(.dark-dialog .el-dialog__header) {
  border-bottom: 1px solid var(--border-color);
  padding: 16px 20px;
  margin: 0;
}

:global(.dark-dialog .el-dialog__title) {
  color: var(--text-primary);
  font-size: 16px;
  font-weight: 600;
}

:global(.dark-dialog .el-dialog__body) {
  padding: 20px;
}

:global(.dark-dialog .el-dialog__footer) {
  border-top: 1px solid var(--border-color);
  padding: 12px 20px;
}

:global(.dark-dialog .el-dialog__close) {
  color: var(--text-muted);
}

:global(.dark-dialog .el-dialog__close:hover) {
  color: var(--text-primary);
}

/* Dark dropdown */
:global(.dark-dropdown) {
  background: var(--bg-secondary) !important;
  border: 1px solid var(--border-color) !important;
}

:global(.dark-dropdown .el-dropdown-menu__item) {
  color: var(--text-secondary) !important;
  font-size: 13px !important;
  display: flex !important;
  align-items: center !important;
  gap: 6px !important;
}

:global(.dark-dropdown .el-dropdown-menu__item:hover) {
  color: var(--text-primary) !important;
  background: var(--bg-hover) !important;
}

:global(.dark-dropdown .el-dropdown-menu__item .el-icon) {
  font-size: 14px;
}

/* El-Select dark overrides */
:global(.dark-dialog .el-select-dropdown) {
  background: var(--bg-secondary) !important;
  border: 1px solid var(--border-color) !important;
}

:global(.dark-dialog .el-select-dropdown .el-select-dropdown__item) {
  color: var(--text-secondary) !important;
}

:global(.dark-dialog .el-select-dropdown .el-select-dropdown__item.hover) {
  background: var(--bg-hover) !important;
  color: var(--text-primary) !important;
}

:global(.dark-dialog .el-select-dropdown .el-select-dropdown__item.selected) {
  color: var(--accent-blue) !important;
}

:global(.dark-dialog .el-input__wrapper) {
  background: var(--bg-card) !important;
  box-shadow: 0 0 0 1px var(--border-color) inset !important;
}

:global(.dark-dialog .el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--accent-blue) inset !important;
}

:global(.dark-dialog .el-input__inner) {
  color: var(--text-primary) !important;
}

:global(.dark-dialog .el-radio__label) {
  color: var(--text-secondary) !important;
}

/* Responsive */
@media (max-width: 768px) {
  .filter-tabs {
    overflow-x: auto;
  }

  .search-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .search-input-wrap {
    max-width: none;
  }
}
</style>
