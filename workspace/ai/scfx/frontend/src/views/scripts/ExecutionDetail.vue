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
        <el-descriptions-item label="脚本版本">{{ execution?.versionId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusType(execution?.status)">
            {{ getStatusText(execution?.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="采集数量">{{ execution?.collectedCount ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="执行时长" v-if="execution?.durationMs">
          {{ formatDuration(execution.durationMs) }}
        </el-descriptions-item>
        <el-descriptions-item label="错误信息" v-if="execution?.errorMessage" :span="2">
          <span class="error-text">{{ execution.errorMessage }}</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- Execution Log Card -->
    <el-card class="log-card" shadow="never">
      <template #header>
        <div class="log-header">
          <span class="card-title">执行日志</span>
          <div class="log-actions">
            <el-button size="small" @click="toggleAutoScroll">
              {{ autoScroll ? '暂停滚动' : '继续滚动' }}
            </el-button>
            <el-button size="small" @click="downloadLogs">下载</el-button>
            <el-button size="small" @click="clearLogs">清空</el-button>
          </div>
        </div>
      </template>
      <div ref="logContainer" class="log-container">
        <div v-if="logs.length === 0" class="log-empty">
          <el-icon :size="32"><Loading /></el-icon>
          <span>等待日志输出...</span>
        </div>
        <div
          v-for="(log, idx) in logs"
          :key="idx"
          :class="['log-item', `log-${log.level.toLowerCase()}`]"
        >
          <span class="log-time">{{ formatLogTime(log.timestamp) }}</span>
          <span :class="['log-level', `level-${log.level.toLowerCase()}`]">[{{ log.level }}]</span>
          <span class="log-message">{{ log.message }}</span>
        </div>
      </div>
    </el-card>

    <!-- Action Buttons -->
    <div class="action-buttons">
      <el-button type="primary" @click="handleReExecute">重新执行</el-button>
      <el-button @click="showCompareExecution = true">对比执行记录</el-button>
      <el-button @click="showCompareVersion = true">对比脚本版本</el-button>
      <el-button @click="showCompareLogs = true">对比执行日志</el-button>
    </div>

    <!-- Compare Execution Dialog -->
    <el-dialog v-model="showCompareExecution" title="对比执行记录" width="800px">
      <div class="compare-select">
        <el-select v-model="compareExecutionId1" placeholder="选择第一个执行记录" style="width: 45%">
          <el-option
            v-for="exec in executionList"
            :key="exec.executionId"
            :label="`${formatDateTime(exec.startTime)} - ${getStatusText(exec.status)}`"
            :value="exec.executionId"
          />
        </el-select>
        <span class="compare-divider">VS</span>
        <el-select v-model="compareExecutionId2" placeholder="选择第二个执行记录" style="width: 45%">
          <el-option
            v-for="exec in executionList"
            :key="exec.executionId"
            :label="`${formatDateTime(exec.startTime)} - ${getStatusText(exec.status)}`"
            :value="exec.executionId"
          />
        </el-select>
      </div>
      <template #footer>
        <el-button @click="showCompareExecution = false">取消</el-button>
        <el-button type="primary" @click="handleCompareExecution">对比</el-button>
      </template>
    </el-dialog>

    <!-- Compare Version Dialog -->
    <el-dialog v-model="showCompareVersion" title="对比脚本版本" width="900px">
      <div class="compare-select">
        <el-select v-model="compareVersionId1" placeholder="选择第一个版本" style="width: 45%">
          <el-option
            v-for="v in versionList"
            :key="v.id"
            :label="`v${v.versionNum} - ${formatDateTime(v.createdAt)}`"
            :value="v.id"
          />
        </el-select>
        <span class="compare-divider">VS</span>
        <el-select v-model="compareVersionId2" placeholder="选择第二个版本" style="width: 45%">
          <el-option
            v-for="v in versionList"
            :key="v.id"
            :label="`v${v.versionNum} - ${formatDateTime(v.createdAt)}`"
            :value="v.id"
          />
        </el-select>
      </div>
      <template #footer>
        <el-button @click="showCompareVersion = false">取消</el-button>
        <el-button type="primary" @click="handleCompareVersion">对比</el-button>
      </template>
    </el-dialog>

    <!-- Compare Logs Dialog -->
    <el-dialog v-model="showCompareLogs" title="对比执行日志" width="900px">
      <div class="compare-logs">
        <div class="compare-log-panel">
          <div class="panel-header">执行 1</div>
          <div class="compare-log-content">
            <div v-for="(log, idx) in compareLogs1" :key="idx" :class="['log-item', `log-${log.level.toLowerCase()}`]">
              <span class="log-time">{{ formatLogTime(log.timestamp) }}</span>
              <span :class="['log-level', `level-${log.level.toLowerCase()}`]">[{{ log.level }}]</span>
              <span class="log-message">{{ log.message }}</span>
            </div>
          </div>
        </div>
        <div class="compare-log-panel">
          <div class="panel-header">执行 2</div>
          <div class="compare-log-content">
            <div v-for="(log, idx) in compareLogs2" :key="idx" :class="['log-item', `log-${log.level.toLowerCase()}`]">
              <span class="log-time">{{ formatLogTime(log.timestamp) }}</span>
              <span :class="['log-level', `level-${log.level.toLowerCase()}`]">[{{ log.level }}]</span>
              <span class="log-message">{{ log.message }}</span>
            </div>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Loading } from '@element-plus/icons-vue'
import { executionApi } from '@/api/execution'
import { scriptApi, versionApi } from '@/api'
import type { ExecutionDetail, LogEntry } from '@/api/execution'
import type { ScriptVersion, TaskExecution } from '@/api'

const route = useRoute()
const router = useRouter()

const scriptId = ref<number>(0)
const executionId = ref<string>('')
const execution = ref<ExecutionDetail>()
const logs = ref<LogEntry[]>([])
const logContainer = ref<HTMLElement | null>(null)
const autoScroll = ref(true)

let pollingTimer: number | null = null

// Compare dialogs
const showCompareExecution = ref(false)
const showCompareVersion = ref(false)
const showCompareLogs = ref(false)
const executionList = ref<TaskExecution[]>([])
const versionList = ref<ScriptVersion[]>([])
const compareExecutionId1 = ref('')
const compareExecutionId2 = ref('')
const compareVersionId1 = ref<number>()
const compareVersionId2 = ref<number>()
const compareLogs1 = ref<LogEntry[]>([])
const compareLogs2 = ref<LogEntry[]>([])

onMounted(async () => {
  scriptId.value = Number(route.params.scriptId)
  executionId.value = String(route.params.executionId)

  await loadExecution()
  startLogPolling()
  loadExecutionList()
  loadVersionList()
})

onUnmounted(() => {
  if (pollingTimer) {
    clearInterval(pollingTimer)
  }
})

async function loadExecution() {
  try {
    const res = await executionApi.getById(executionId.value) as any
    execution.value = res.data
  } catch (e) {
    ElMessage.error('加载执行详情失败')
  }
}

function startLogPolling() {
  let offset = 0
  pollingTimer = window.setInterval(async () => {
    try {
      const res = await executionApi.getLogs(executionId.value, offset) as any
      const newLogs: LogEntry[] = res.data?.logs || []
      if (newLogs.length > 0) {
        // Check if we have new logs by comparing timestamps
        const existingTimestamps = new Set(logs.value.map((l: LogEntry) => l.timestamp))
        const uniqueNewLogs = newLogs.filter((l: LogEntry) => !existingTimestamps.has(l.timestamp))
        if (uniqueNewLogs.length > 0) {
          logs.value = [...logs.value, ...uniqueNewLogs]
          offset += uniqueNewLogs.length
          if (autoScroll.value) {
            scrollToBottom()
          }
        }
      }

      // Check if execution finished
      const status = res.data?.status
      if (status === 'success' || status === 'failed' || status === 'cancelled') {
        stopPolling()
        // Update execution status
        await loadExecution()
      }
    } catch (e) {
      // Ignore polling errors
    }
  }, 2000)
}

function stopPolling() {
  if (pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (logContainer.value) {
      logContainer.value.scrollTop = logContainer.value.scrollHeight
    }
  })
}

function toggleAutoScroll() {
  autoScroll.value = !autoScroll.value
}

function downloadLogs() {
  const content = logs.value.map(l => `[${l.timestamp}] [${l.level}] ${l.message}`).join('\n')
  const blob = new Blob([content], { type: 'text/plain' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `execution-${executionId.value}.log`
  a.click()
  URL.revokeObjectURL(url)
}

function clearLogs() {
  logs.value = []
}

function goBack() {
  router.back()
}

async function handleReExecute() {
  try {
    ElMessage.info('开始重新执行...')
    const res = await executionApi.reExecute(scriptId.value)
    ElMessage.success(`执行已启动，执行ID: ${res.data.executionId}`)
    // Navigate to new execution
    router.push(`/scripts/${scriptId.value}/executions/${res.data.executionId}`)
  } catch (e) {
    ElMessage.error('重新执行失败')
  }
}

async function loadExecutionList() {
  try {
    const res = await scriptApi.getById(scriptId.value)
    // Execution list would need to be loaded differently
    // For now, we'll use the existing executions from the list
  } catch (e) {
    // Ignore
  }
}

async function loadVersionList() {
  try {
    const res = await versionApi.list(scriptId.value)
    versionList.value = res.data || []
  } catch (e) {
    // Ignore
  }
}

async function handleCompareExecution() {
  if (!compareExecutionId1.value || !compareExecutionId2.value) {
    ElMessage.warning('请选择两个执行记录')
    return
  }
  try {
    const res = await executionApi.compare(scriptId.value, compareExecutionId1.value, compareExecutionId2.value)
    ElMessage.success('对比结果已生成')
    console.log('Compare result:', res.data)
  } catch (e) {
    ElMessage.error('对比失败')
  }
  showCompareExecution.value = false
}

async function handleCompareVersion() {
  if (!compareVersionId1.value || !compareVersionId2.value) {
    ElMessage.warning('请选择两个版本')
    return
  }
  try {
    const res = await versionApi.compare(scriptId.value, compareVersionId1.value, compareVersionId2.value)
    ElMessage.success('版本对比已生成')
    console.log('Version compare result:', res.data)
  } catch (e) {
    ElMessage.error('版本对比失败')
  }
  showCompareVersion.value = false
}

// Utility functions
function formatDateTime(time?: string) {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}

function formatLogTime(timestamp: string) {
  const d = new Date(timestamp)
  return d.toLocaleTimeString('zh-CN', { hour12: false })
}

function formatDuration(ms?: number) {
  if (!ms) return '-'
  const s = Math.floor(ms / 1000)
  if (s < 60) return `${s}秒`
  const m = Math.floor(s / 60)
  return `${m}分${s % 60}秒`
}

function getStatusType(status?: string) {
  switch (status) {
    case 'success': return 'success'
    case 'failed': return 'danger'
    case 'running': return 'warning'
    case 'cancelled': return 'info'
    case 'pending': return ''
    default: return ''
  }
}

function getStatusText(status?: string) {
  switch (status) {
    case 'success': return '成功'
    case 'failed': return '失败'
    case 'running': return '进行中'
    case 'cancelled': return '已取消'
    case 'pending': return '等待'
    default: return status || '-'
  }
}

function getTriggerText(trigger?: string) {
  switch (trigger) {
    case 'manual': return '手动'
    case 'scheduled': return '定时'
    case 'api': return 'API'
    default: return trigger || '-'
  }
}
</script>

<style scoped>
.execution-detail {
  padding: 24px;
  background: #f5f7fa;
  min-height: 100vh;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding: 16px 20px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.header-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.header-time {
  color: #909399;
  font-size: 14px;
}

.info-card {
  margin-bottom: 20px;
  border-radius: 8px;
}

.card-title {
  font-weight: 600;
  font-size: 16px;
  color: #303133;
}

.log-card {
  margin-bottom: 20px;
  border-radius: 8px;
}

.log-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.log-actions {
  display: flex;
  gap: 8px;
}

.log-container {
  height: 400px;
  overflow-y: auto;
  padding: 12px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  background: #1e1e1e;
  border-radius: 4px;
}

.log-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #909399;
  gap: 12px;
}

.log-item {
  display: flex;
  padding: 4px 0;
  line-height: 1.6;
}

.log-time {
  color: #888;
  margin-right: 12px;
  flex-shrink: 0;
}

.log-level {
  margin-right: 8px;
  flex-shrink: 0;
  font-weight: 600;
}

.level-info { color: #4fc3f7; }
.level-warn { color: #ffb74d; }
.level-error { color: #f48771; }
.level-debug { color: #81c784; }

.log-message {
  color: #d4d4d4;
  word-break: break-all;
}

.log-info .log-message { color: #d4d4d4; }
.log-warn .log-message { color: #ffb74d; }
.log-error .log-message { color: #f48771; }
.log-debug .log-message { color: #81c784; }

.action-buttons {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.error-text {
  color: #f56c6c;
}

.compare-select {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
}

.compare-divider {
  font-weight: 600;
  color: #909399;
}

.compare-logs {
  display: flex;
  gap: 16px;
  height: 500px;
}

.compare-log-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  overflow: hidden;
}

.panel-header {
  padding: 8px 12px;
  background: #f5f7fa;
  border-bottom: 1px solid #dcdfe6;
  font-weight: 600;
  font-size: 14px;
}

.compare-log-content {
  flex: 1;
  overflow-y: auto;
  padding: 8px 12px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  background: #1e1e1e;
  color: #d4d4d4;
}
</style>