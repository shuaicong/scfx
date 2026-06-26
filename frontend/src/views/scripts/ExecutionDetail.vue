<template>
  <div class="execution-detail">
    <!-- Header -->
    <div class="detail-header">
      <div class="header-left">
        <el-button @click="goBack" :icon="ArrowLeft">返回</el-button>
        <h2 class="header-title">执行详情</h2>
      </div>
      <div class="header-time" v-if="execution?.startTime">
        执行时间: {{ formatDateTime(execution.startTime) }}
      </div>
    </div>

    <!-- Basic Info Card -->
    <el-card class="info-card" shadow="never">
      <template #header>
        <span class="card-title">基本信息</span>
      </template>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="任务名称">{{ execution?.scriptName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="数据源">{{ execution?.source || '-' }}</el-descriptions-item>
        <el-descriptions-item label="触发方式">{{ getTriggerText(execution?.triggerType) }}</el-descriptions-item>
        <el-descriptions-item label="脚本版本">v{{ execution?.versionNum ?? execution?.versionId ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusType(execution?.status)">
            {{ getStatusText(execution?.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="执行时长">{{ formatDuration(execution?.durationMs) }}</el-descriptions-item>
        <el-descriptions-item label="错误信息" v-if="execution?.errorMessage" :span="3">
          <span class="error-text">{{ execution.errorMessage }}</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- Variety-Level Stats Card -->
    <el-card v-if="execution?.collectTarget === 'database'" class="stats-card" shadow="never">
      <template #header>
        <span class="card-title">📊 品种级采集统计</span>
      </template>
      <div class="variety-stats">
        <div v-for="(stats, variety) in varietyStats" :key="variety" class="variety-item">
          <span class="variety-icon">{{ varietyIcon(variety) }}</span>
          <span class="variety-name">{{ variety }}</span>
          <span class="variety-count">{{ stats.count }}条</span>
          <span :class="stats.error > 0 ? 'variety-error' : 'variety-success'">
            {{ stats.error > 0 ? `❌ ${stats.error}条失败` : '✅ 全部成功' }}
          </span>
        </div>
      </div>
    </el-card>

    <!-- Stats Cards -->
    <el-card v-if="hasStats" class="stats-card" shadow="never">
      <template #header>
        <span class="card-title">采集统计</span>
      </template>
      <div class="stats-grid">
        <div class="stat-box total">
          <div class="stat-value">{{ execution?.totalCount ?? '-' }}</div>
          <div class="stat-label">总处理</div>
        </div>
        <div class="stat-box success">
          <div class="stat-value">{{ execution?.successCount ?? '-' }}</div>
          <div class="stat-label">成功</div>
        </div>
        <div class="stat-box skip">
          <div class="stat-value">{{ execution?.skipCount ?? '-' }}</div>
          <div class="stat-label">去重跳过</div>
        </div>
        <div class="stat-box error">
          <div class="stat-value">{{ execution?.errorCount ?? '-' }}</div>
          <div class="stat-label">失败</div>
        </div>
        <div class="stat-box data">
          <div class="stat-value">{{ execution?.dataSizeMb ?? '-' }}</div>
          <div class="stat-label">数据量 (MB)</div>
        </div>
      </div>
    </el-card>

    <!-- Phase Timing Bar -->
    <el-card v-if="hasPhaseTimes" class="phase-card" shadow="never">
      <template #header>
        <span class="card-title">阶段耗时</span>
      </template>
      <div class="phase-bars">
        <div class="phase-row" v-for="phase in phases" :key="phase.key">
          <span class="phase-label">{{ phase.label }}</span>
          <div class="phase-bar-track">
            <div
              class="phase-bar-fill"
              :style="{ width: phase.percent + '%', background: phase.color }"
            ></div>
          </div>
          <span class="phase-time">{{ formatDuration(phase.ms) }}</span>
        </div>
      </div>
    </el-card>

    <!-- Data List Card -->
    <el-card v-if="items.length > 0" class="items-card" shadow="never">
      <template #header>
        <div class="items-header">
          <span class="card-title">采集数据清单 ({{ items.length }})</span>
          <el-button size="small" type="primary" @click="viewInKnowledgeBase" :disabled="items.length === 0">
            查看知识库
          </el-button>
        </div>
      </template>
      <el-table :data="items" stripe size="small" style="width: 100%">
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column label="内容" width="130" align="center">
          <template #default="{ row }">
            <div class="content-stats">
              <span v-if="row.contentLength != null">{{ row.contentLength }} 字</span>
              <span v-if="row.contentLength != null && row.imageCount != null"> | </span>
              <span v-if="row.imageCount != null">{{ row.imageCount }} 图</span>
              <span v-if="row.contentLength == null && row.imageCount == null" class="no-data">-</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="url" label="来源URL" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <a v-if="row.url" :href="row.url" target="_blank" class="item-url">{{ row.url }}</a>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="action" label="状态" width="140" align="center">
          <template #default="{ row }">
            <el-tag :type="itemActionType(row.action)" size="small" effect="plain">
              {{ itemActionText(row.action) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="errorMessage" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="130" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.knowledgeId" link type="primary" size="small" @click="openPreview(row)">
              预览
            </el-button>
            <el-button v-if="row.knowledgeId" link type="primary" size="small" @click="viewKnowledge(row.knowledgeId!)">
              查看
            </el-button>
            <span v-if="!row.knowledgeId && !row.contentPreview" class="no-action">-</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Preview Dialog -->
    <el-dialog v-model="previewVisible" :title="previewTitle" width="800px" top="5vh" destroy-on-close>
      <div v-loading="previewLoading" class="preview-dialog-body">
        <template v-if="previewItem">
          <div class="preview-dialog-meta">
            <span>字数 {{ previewItem.contentLength ?? '-' }}</span>
            <span v-if="previewItem.imageCount != null"> | 图片 {{ previewItem.imageCount }} 张</span>
            <span v-if="previewItem.url"> | <a :href="previewItem.url" target="_blank" class="preview-url">原文链接</a></span>
          </div>
          <div class="preview-dialog-content" v-if="previewContentHtml" v-html="previewContentHtml"></div>
          <div class="preview-dialog-content" v-else-if="previewContentText">
            <pre class="preview-text">{{ previewContentText }}</pre>
          </div>
          <div v-else class="preview-empty">暂无内容</div>
        </template>
      </div>
      <template #footer>
        <div class="preview-dialog-footer">
          <el-button size="small" @click="previewPrev" :disabled="!hasPrev">上一个</el-button>
          <span class="preview-position">{{ previewIndex + 1 }} / {{ items.length }}</span>
          <el-button size="small" @click="previewNext" :disabled="!hasNext">下一个</el-button>
          <el-button type="primary" size="small" @click="previewVisible = false">关闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- Execution Log Card -->
    <el-card class="log-card" shadow="never">
      <template #header>
        <div class="log-header">
          <span class="card-title">执行日志</span>
          <div class="log-actions">
            <el-select v-model="logFilterPhase" placeholder="全部阶段" size="small" clearable style="width: 110px;">
              <el-option label="全部阶段" value="" />
              <el-option label="登录" value="login" />
              <el-option label="抓取" value="crawl" />
              <el-option label="解析" value="parse" />
              <el-option label="上报" value="report" />
              <el-option label="系统" value="system" />
            </el-select>
            <el-select v-model="logFilterLevel" placeholder="全部级别" size="small" clearable style="width: 110px;">
              <el-option label="全部级别" value="" />
              <el-option label="INFO" value="INFO" />
              <el-option label="WARN" value="WARN" />
              <el-option label="ERROR" value="ERROR" />
              <el-option label="DEBUG" value="DEBUG" />
            </el-select>
            <el-button size="small" @click="toggleAutoScroll">
              {{ autoScroll ? '暂停滚动' : '继续滚动' }}
            </el-button>
            <el-button size="small" @click="downloadLogs">下载</el-button>
          </div>
        </div>
      </template>
      <div ref="logContainer" class="log-container">
        <div v-if="filteredLogs.length === 0" class="log-empty">
          <el-icon :size="32"><Loading /></el-icon>
          <span>等待日志输出...</span>
        </div>
        <div
          v-for="(log, idx) in filteredLogs"
          :key="idx"
          :class="['log-item', `log-${log.level.toLowerCase()}`, { 'log-has-phase': log.phase }]"
        >
          <span class="log-time">{{ formatLogTime(log.timestamp) }}</span>
          <span v-if="log.phase" class="log-phase" :style="{ color: phaseColor(log.phase) }">{{ log.phase }}</span>
          <span :class="['log-level', `level-${log.level.toLowerCase()}`]">[{{ log.level }}]</span>
          <span class="log-message">{{ log.message }}</span>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Loading } from '@element-plus/icons-vue'
import { executionApi } from '@/api/execution'
import { knowledgeApi } from '@/api/knowledge'
import type { ExecutionDetail, ExecutionItem, LogEntry } from '@/api'

const route = useRoute()
const router = useRouter()

const scriptId = ref<number>(0)
const executionId = ref<string>('')
const execution = ref<ExecutionDetail>()
const logs = ref<LogEntry[]>([])
const items = ref<ExecutionItem[]>([])
const logContainer = ref<HTMLElement | null>(null)

// 品种识别关键词
const varietyKeywords: Record<string, string[]> = {
  '玉米': ['玉米', 'corn', 'maize', '玉'],
  '小麦': ['小麦', 'wheat', '麦'],
  '进口粮': ['进口', '大麦', '高粱', 'import', 'barley', 'sorghum'],
  '国产大豆': ['大豆', '黄豆', 'soybean', 'soy'],
  '生猪': ['生猪', '猪', 'pig', 'hog', '猪肉'],
}
const varietyNames = Object.keys(varietyKeywords)

function determineVariety(title: string): string | null {
  for (const variety of varietyNames) {
    if (varietyKeywords[variety].some(kw => title.includes(kw))) return variety
  }
  return null
}

const varietyStats = computed<Record<string, { count: number; error: number }>>(() => {
  const stats: Record<string, { count: number; error: number }> = {}
  for (const v of varietyNames) {
    stats[v] = { count: 0, error: 0 }
  }
  if (items.value.length > 0) {
    for (const item of items.value) {
      const variety = determineVariety(item.title || '')
      if (variety && stats[variety]) {
        stats[variety].count++
        if (item.action === 'error') stats[variety].error++
      }
    }
  }
  return stats
})

function varietyIcon(variety: string): string {
  const icons: Record<string, string> = { '玉米': '🌽', '小麦': '🌾', '进口粮': '🚢', '国产大豆': '🫘', '生猪': '🐷' }
  return icons[variety] || '📊'
}
const autoScroll = ref(true)
const logFilterPhase = ref('')
const logFilterLevel = ref('')

let pollingTimer: number | null = null

const hasStats = computed(() =>
  execution.value?.totalCount != null || execution.value?.successCount != null ||
  execution.value?.skipCount != null || execution.value?.errorCount != null
)

const hasPhaseTimes = computed(() =>
  execution.value?.phaseLoginMs != null || execution.value?.phaseCrawlMs != null ||
  execution.value?.phaseParseMs != null || execution.value?.phaseReportMs != null
)

const filteredLogs = computed(() => {
  let list = logs.value
  if (logFilterPhase.value) {
    list = list.filter(l => l.phase === logFilterPhase.value)
  }
  if (logFilterLevel.value) {
    list = list.filter(l => l.level === logFilterLevel.value)
  }
  return list
})

const totalPhaseMs = computed(() => {
  const e = execution.value
  return (e?.phaseLoginMs || 0) + (e?.phaseCrawlMs || 0) + (e?.phaseParseMs || 0) + (e?.phaseReportMs || 0)
})

interface PhaseItem { key: string; label: string; ms: number; color: string; percent: number }
const phases = computed<PhaseItem[]>(() => {
  const total = totalPhaseMs.value || 1
  const e = execution.value
  return [
    { key: 'login', label: '登录', ms: e?.phaseLoginMs || 0, color: '#58a6ff', percent: ((e?.phaseLoginMs || 0) / total) * 100 },
    { key: 'crawl', label: '抓取', ms: e?.phaseCrawlMs || 0, color: '#3fb950', percent: ((e?.phaseCrawlMs || 0) / total) * 100 },
    { key: 'parse', label: '解析', ms: e?.phaseParseMs || 0, color: '#d29922', percent: ((e?.phaseParseMs || 0) / total) * 100 },
    { key: 'report', label: '上报', ms: e?.phaseReportMs || 0, color: '#a371f7', percent: ((e?.phaseReportMs || 0) / total) * 100 },
  ]
})

function phaseColor(phase: string): string {
  const map: Record<string, string> = { login: '#58a6ff', crawl: '#3fb950', parse: '#d29922', report: '#a371f7', system: '#8b949e' }
  return map[phase] || '#8b949e'
}

function itemActionType(action: string) {
  const map: Record<string, string> = { created: 'success', skipped_duplicate: 'warning', skipped_existing: 'info', error: 'danger' }
  return map[action] || 'info'
}

function itemActionText(action: string) {
  const map: Record<string, string> = { created: '已创建', skipped_duplicate: '内容重复', skipped_existing: '已存在', error: '失败' }
  return map[action] || action
}

onMounted(async () => {
  scriptId.value = Number(route.params.scriptId)
  executionId.value = String(route.params.executionId)
  await loadExecution()
  loadItems()
  startLogPolling()
})

onUnmounted(() => {
  if (pollingTimer) clearInterval(pollingTimer)
})

async function loadExecution() {
  try {
    const res: any = await executionApi.getById(executionId.value)
    execution.value = res?.data
  } catch (e) {
    ElMessage.error('加载执行详情失败')
  }
}

async function loadItems() {
  try {
    const res: any = await executionApi.items(executionId.value)
    items.value = res?.data || []
  } catch (e) {
    // ignore items loading errors
  }
}

function startLogPolling() {
  let lastLogId = 0
  pollingTimer = window.setInterval(async () => {
    try {
      const res: any = await executionApi.logs(executionId.value)
      const newLogs: LogEntry[] = res?.data || []
      if (newLogs.length > 0) {
        const latestLogs = newLogs.filter((l: LogEntry) => (l.id || 0) > lastLogId)
        if (latestLogs.length > 0) {
          logs.value = [...logs.value, ...latestLogs]
          lastLogId = Math.max(...latestLogs.map((l: LogEntry) => l.id || 0))
          if (autoScroll.value) scrollToBottom()
        }
      }
      // Check if finished
      const status = execution.value?.status
      if (status === 'success' || status === 'failed' || status === 'cancelled') {
        stopPolling()
      }
    } catch (e) { /* ignore polling errors */ }
  }, 2000)
}

function stopPolling() {
  if (pollingTimer) { clearInterval(pollingTimer); pollingTimer = null }
}

function scrollToBottom() {
  nextTick(() => { if (logContainer.value) logContainer.value.scrollTop = logContainer.value.scrollHeight })
}

function toggleAutoScroll() { autoScroll.value = !autoScroll.value }

function downloadLogs() {
  const content = filteredLogs.value.map(l =>
    `[${l.timestamp}] [${l.level}]${l.phase ? ' [' + l.phase + ']' : ''} ${l.message}`
  ).join('\n')
  const blob = new Blob([content], { type: 'text/plain' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url; a.download = `execution-${executionId.value}.log`; a.click()
  URL.revokeObjectURL(url)
}

function goBack() { router.back() }

function viewInKnowledgeBase() {
  const url = router.resolve({ path: '/knowledge', query: { executionId: executionId.value } })
  window.open(url.href, '_blank')
}

function viewKnowledge(knowledgeId: number) {
  const url = router.resolve({ path: '/knowledge', query: { id: String(knowledgeId) } })
  window.open(url.href, '_blank')
}

// Preview dialog state
const previewVisible = ref(false)
const previewLoading = ref(false)
const previewIndex = ref(0)
const previewContentHtml = ref('')
const previewContentText = ref('')
const previewItem = ref<ExecutionItem | null>(null)

const previewTitle = computed(() => {
  const item = previewItem.value
  return item?.title ? `预览: ${item.title}` : '内容预览'
})

const hasPrev = computed(() => previewIndex.value > 0)
const hasNext = computed(() => previewIndex.value < items.value.length - 1)

async function openPreview(item: ExecutionItem) {
  const idx = items.value.findIndex(i => i.id === item.id)
  if (idx >= 0) previewIndex.value = idx
  previewItem.value = item
  previewVisible.value = true
  await loadPreviewContent(item)
}

async function loadPreviewContent(item: ExecutionItem) {
  previewContentHtml.value = ''
  previewContentText.value = ''
  if (!item.knowledgeId) {
    previewContentText.value = item.contentPreview || '暂无内容'
    return
  }
  previewLoading.value = true
  try {
    const res: any = await knowledgeApi.getById(item.knowledgeId)
    const data = res?.data
    if (data) {
      previewContentHtml.value = data.contentHtml || ''
      previewContentText.value = data.content || ''
    }
  } catch {
    previewContentText.value = item.contentPreview || '加载失败'
  } finally {
    previewLoading.value = false
  }
}

async function previewPrev() {
  if (!hasPrev.value) return
  previewIndex.value--
  const item = items.value[previewIndex.value]
  previewItem.value = item
  await loadPreviewContent(item)
}

async function previewNext() {
  if (!hasNext.value) return
  previewIndex.value++
  const item = items.value[previewIndex.value]
  previewItem.value = item
  await loadPreviewContent(item)
}

function formatDateTime(time?: string) { return time ? new Date(time).toLocaleString('zh-CN') : '-' }
function formatLogTime(timestamp: string) {
  return new Date(timestamp).toLocaleTimeString('zh-CN', { hour12: false })
}
function formatDuration(ms?: number) {
  if (!ms) return '-'
  const s = Math.floor(ms / 1000)
  if (s < 60) return `${s}秒`
  const m = Math.floor(s / 60)
  return `${m}分${s % 60}秒`
}
function getStatusType(status?: string) {
  const map: Record<string, string> = { success: 'success', failed: 'danger', running: 'warning', cancelled: 'info', pending: 'info' }
  return map[status || ''] || 'info'
}
function getStatusText(status?: string) {
  const map: Record<string, string> = { success: '成功', failed: '失败', running: '进行中', cancelled: '已取消', pending: '等待中' }
  return map[status || ''] || status || '-'
}
function getTriggerText(trigger?: string) {
  const map: Record<string, string> = { manual: '手动', scheduled: '定时', api: 'API' }
  return map[trigger || ''] || trigger || '-'
}
</script>

<style scoped>
.execution-detail {
  padding: 24px;
  background: #f5f7fa;
  min-height: 100vh;
}
.detail-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 20px; padding: 16px 20px;
  background: #fff; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}
.header-left { display: flex; align-items: center; gap: 16px; }
.header-title { margin: 0; font-size: 18px; font-weight: 600; color: #303133; }
.header-time { color: #909399; font-size: 14px; }
.info-card, .stats-card, .phase-card, .log-card, .items-card { margin-bottom: 20px; border-radius: 8px; }
.card-title { font-weight: 600; font-size: 16px; color: #303133; }
.error-text { color: #f56c6c; }

/* Stats Grid */
.stats-grid { display: flex; gap: 16px; }
.stat-box {
  flex: 1; text-align: center; padding: 20px 16px;
  border-radius: 8px; border: 1px solid #ebeef5;
}
.stat-value { font-size: 28px; font-weight: 700; }
.stat-label { font-size: 12px; color: #909399; margin-top: 4px; }
.stat-box.total .stat-value { color: #409eff; }
.stat-box.success .stat-value { color: #67c23a; }
.stat-box.skip .stat-value { color: #e6a23c; }
.stat-box.error .stat-value { color: #f56c6c; }
.stat-box.data .stat-value { color: #909399; }

/* Phase Bars */
.phase-bars { display: flex; flex-direction: column; gap: 12px; }
.phase-row { display: flex; align-items: center; gap: 12px; }
.phase-label { width: 40px; font-size: 13px; color: #606266; flex-shrink: 0; }
.phase-bar-track {
  flex: 1; height: 20px; background: #f0f2f5; border-radius: 10px; overflow: hidden;
}
.phase-bar-fill { height: 100%; border-radius: 10px; transition: width 0.5s ease; min-width: 4px; }
.phase-time { width: 50px; font-size: 12px; color: #909399; text-align: right; flex-shrink: 0; }

/* Items */
.items-header { display: flex; justify-content: space-between; align-items: center; }
.item-url { color: #409eff; text-decoration: none; font-size: 12px; }
.item-url:hover { text-decoration: underline; }
.no-action { color: #c0c4cc; }
.content-stats { font-size: 12px; color: #606266; }
.content-stats .no-data { color: #c0c4cc; }
.preview-popover { padding: 4px 0; }
.preview-popover .preview-stats { font-size: 12px; color: #909399; margin-bottom: 8px; }
.preview-popover .preview-text { font-size: 12px; line-height: 1.7; color: #303133; white-space: pre-wrap; word-break: break-all; margin: 0; max-height: 300px; overflow-y: auto; font-family: inherit; }

/* Preview Dialog */
.preview-dialog-body { min-height: 200px; max-height: 70vh; overflow-y: auto; }
.preview-dialog-meta { font-size: 12px; color: #909399; margin-bottom: 12px; padding-bottom: 8px; border-bottom: 1px solid #ebeef5; }
.preview-dialog-meta .preview-url { color: #409eff; text-decoration: none; }
.preview-dialog-meta .preview-url:hover { text-decoration: underline; }
.preview-dialog-content { font-size: 13px; line-height: 1.7; color: #303133; }
.preview-dialog-content :deep(p) { margin-bottom: 10px; }
.preview-dialog-content :deep(img) { max-width: 100%; height: auto; border-radius: 4px; margin: 8px 0; }
.preview-dialog-content :deep(table) { border-collapse: collapse; width: 100%; margin: 12px 0; font-size: 13px; }
.preview-dialog-content :deep(td),
.preview-dialog-content :deep(th) { border: 1px solid #e5e7eb; padding: 8px 10px; text-align: left; }
.preview-dialog-content :deep(th) { background: #f3f4f6; font-weight: 600; }
.preview-dialog-content :deep(tr:nth-child(even)) { background: #fafafa; }
.preview-dialog-content :deep(h1) { font-size: 18px; margin: 16px 0 8px; }
.preview-dialog-content .preview-text { font-size: 13px; line-height: 1.7; white-space: pre-wrap; word-break: break-all; font-family: inherit; margin: 0; }
.preview-empty { text-align: center; color: #c0c4cc; padding: 40px 0; }
.preview-dialog-footer { display: flex; align-items: center; gap: 8px; }
.preview-position { font-size: 13px; color: #909399; margin: 0 4px; }

/* Variety Stats */
.variety-stats { display: flex; flex-direction: column; gap: 8px; }
.variety-item { display: flex; align-items: center; gap: 12px; padding: 10px 12px; border-radius: 8px; border: 1px solid #ebeef5; background: #fafafa; }
.variety-icon { font-size: 20px; }
.variety-name { font-size: 14px; font-weight: 600; color: #303133; min-width: 70px; }
.variety-count { font-size: 14px; color: #409eff; font-weight: 600; min-width: 60px; }
.variety-success { font-size: 12px; color: #67c23a; }
.variety-error { font-size: 12px; color: #f56c6c; }

/* Log */
.log-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.log-actions { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.log-container {
  height: 400px; overflow-y: auto; padding: 12px;
  font-family: 'Consolas', 'Monaco', monospace; font-size: 13px;
  background: #1e1e1e; border-radius: 4px;
}
.log-empty { display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100%; color: #909399; gap: 12px; }
.log-item { display: flex; padding: 3px 0; line-height: 1.6; gap: 6px; flex-wrap: nowrap; align-items: baseline; }
.log-time { color: #888; flex-shrink: 0; }
.log-phase { font-size: 11px; font-weight: 600; padding: 0 4px; border-radius: 3px; flex-shrink: 0; background: rgba(255,255,255,0.05); }
.log-level { flex-shrink: 0; font-weight: 600; }
.level-info { color: #4fc3f7; }
.level-warn { color: #ffb74d; }
.level-error { color: #f48771; }
.level-debug { color: #81c784; }
.log-message { color: #d4d4d4; word-break: break-all; }
.log-warn .log-message { color: #ffb74d; }
.log-error .log-message { color: #f48771; }
</style>
