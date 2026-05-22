<template>
  <el-drawer v-model="drawerVisible" title="执行进度" size="500px" direction="rtl">
    <div class="progress-info">
      <div class="execution-id">执行ID: {{ executionId }}</div>
      <div class="status-row">
        <el-tag :type="statusType">{{ statusText }}</el-tag>
        <el-progress :percentage="progressPercent" style="flex: 1; margin-left: 16px;" />
      </div>

      <!-- 实时统计 -->
      <div v-if="hasStats" class="realtime-stats">
        <div class="stat-row">
          <span class="stat-item">总处理 <strong>{{ stats.total }}</strong></span>
          <span class="stat-item success">成功 <strong>{{ stats.success }}</strong></span>
          <span class="stat-item skip">去重 <strong>{{ stats.skip }}</strong></span>
          <span class="stat-item error">失败 <strong>{{ stats.error }}</strong></span>
        </div>
      </div>

      <!-- 阶段耗时 -->
      <div v-if="hasPhases" class="phase-summary">
        <span v-if="execution?.phaseLoginMs" class="phase-tag login">登录 {{ fmt(execution.phaseLoginMs) }}</span>
        <span v-if="execution?.phaseCrawlMs" class="phase-tag crawl">抓取 {{ fmt(execution.phaseCrawlMs) }}</span>
        <span v-if="execution?.phaseParseMs" class="phase-tag parse">解析 {{ fmt(execution.phaseParseMs) }}</span>
        <span v-if="execution?.phaseReportMs" class="phase-tag report">上报 {{ fmt(execution.phaseReportMs) }}</span>
      </div>
    </div>

    <!-- 日志 -->
    <div class="log-section">
      <ExecutionLogViewer :executionId="executionId" :logs="logs" />
    </div>

    <template #footer>
      <el-button @click="drawerVisible = false">关闭</el-button>
      <el-button type="danger" @click="handleCancel" :disabled="isTerminal">取消执行</el-button>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, computed, watch, onUnmounted } from 'vue'
import { executionApi, type ExecutionLog, type TaskExecution } from '@/api'
import ExecutionLogViewer from './ExecutionLogViewer.vue'

const drawerVisible = ref(false)
const executionId = ref('')
const execution = ref<TaskExecution | null>(null)
const logs = ref<ExecutionLog[]>([])

const hasStats = computed(() =>
  execution.value?.totalCount != null && execution.value!.totalCount! > 0
)
const hasPhases = computed(() =>
  execution.value?.phaseLoginMs || execution.value?.phaseCrawlMs ||
  execution.value?.phaseParseMs || execution.value?.phaseReportMs
)
const isTerminal = computed(() => {
  const s = (execution.value as any)?.status
  return s === 'success' || s === 'failed' || s === 'cancelled'
})

const stats = computed(() => ({
  total: (execution.value as any)?.totalCount ?? 0,
  success: (execution.value as any)?.successCount ?? 0,
  skip: (execution.value as any)?.skipCount ?? 0,
  error: (execution.value as any)?.errorCount ?? 0,
}))

const processedCount = computed(() => stats.value.success + stats.value.error)
const totalCount = computed(() => (execution.value as any)?.totalCount || 0)
const progressPercent = computed(() => {
  const total = totalCount.value
  if (!total) return 0
  return Math.round((processedCount.value / total) * 100)
})

const statusType = computed(() => {
  const map: Record<string, string> = { pending: 'info', running: 'primary', success: 'success', failed: 'danger', cancelled: 'warning' }
  return map[(execution.value as any)?.status || 'pending'] || 'info'
})
const statusText = computed(() => {
  const map: Record<string, string> = { pending: '等待中', running: '执行中', success: '已完成', failed: '失败', cancelled: '已取消' }
  return map[(execution.value as any)?.status || 'pending'] || '等待中'
})

function fmt(ms: number) {
  if (!ms) return ''
  return ms >= 1000 ? `${(ms / 1000).toFixed(1)}s` : `${ms}ms`
}

async function loadExecution() {
  if (!executionId.value) return
  try {
    const res: any = await executionApi.get(executionId.value)
    execution.value = res.data || {}
  } catch (e) { console.error('加载执行状态失败', e) }
}

async function loadLogs() {
  if (!executionId.value) return
  try {
    const res: any = await executionApi.logs(executionId.value)
    logs.value = res.data || []
  } catch (e) { console.error('加载执行日志失败', e) }
}

async function handleCancel() {
  try {
    await executionApi.cancel(executionId.value)
    drawerVisible.value = false
  } catch (e) { console.error('取消执行失败', e) }
}

let timer: number | null = null
function startPolling() {
  loadExecution(); loadLogs()
  timer = window.setInterval(() => { loadExecution(); loadLogs() }, 3000)
}
function stopPolling() {
  if (timer) { clearInterval(timer); timer = null }
}
watch(drawerVisible, (val) => { val ? startPolling() : stopPolling() })
onUnmounted(() => stopPolling())

function open(id: string) { executionId.value = id; drawerVisible.value = true }
defineExpose({ open })
</script>

<style scoped>
.progress-info { padding: 16px; }
.execution-id { color: #666; margin-bottom: 16px; font-size: 13px; }
.status-row { display: flex; align-items: center; margin-bottom: 20px; }

.realtime-stats { margin-bottom: 12px; }
.stat-row { display: flex; gap: 16px; font-size: 13px; }
.stat-item { color: #606266; }
.stat-item strong { font-size: 16px; margin-left: 4px; }
.stat-item.success strong { color: #67c23a; }
.stat-item.skip strong { color: #e6a23c; }
.stat-item.error strong { color: #f56c6c; }

.phase-summary { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; }
.phase-tag {
  font-size: 11px; padding: 2px 8px; border-radius: 4px;
  background: #f0f2f5; color: #606266;
}
.phase-tag.login { border-left: 3px solid #58a6ff; }
.phase-tag.crawl { border-left: 3px solid #3fb950; }
.phase-tag.parse { border-left: 3px solid #d29922; }
.phase-tag.report { border-left: 3px solid #a371f7; }

.log-section { padding: 16px; border-top: 1px solid #eee; }
</style>
