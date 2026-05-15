<template>
  <el-dialog
    v-model="visible"
    title="对比执行日志"
    width="1000px"
    :close-on-click-modal="false"
  >
    <!-- Selection Phase -->
    <div v-if="phase === 'select'" class="compare-select">
      <div class="select-row">
        <el-select v-model="executionId1" placeholder="选择第一个执行记录" style="width: 100%">
          <el-option
            v-for="exec in executionList"
            :key="exec.executionId"
            :label="formatExecutionLabel(exec)"
            :value="exec.executionId"
          />
        </el-select>
        <span class="compare-divider">VS</span>
        <el-select v-model="executionId2" placeholder="选择第二个执行记录" style="width: 100%">
          <el-option
            v-for="exec in executionList"
            :key="exec.executionId"
            :label="formatExecutionLabel(exec)"
            :value="exec.executionId"
          />
        </el-select>
      </div>
      <div class="select-actions">
        <el-button @click="handleCancel">取消</el-button>
        <el-button type="primary" :disabled="!executionId1 || !executionId2" @click="handleCompare">
          开始对比
        </el-button>
      </div>
    </div>

    <!-- Result Phase -->
    <div v-if="phase === 'result'" class="compare-result">
      <div class="result-header">
        <el-button @click="phase = 'select'" :icon="ArrowLeft">返回选择</el-button>
        <span class="result-title">日志对比结果</span>
      </div>

      <!-- Summary Statistics -->
      <div class="log-summary">
        <div class="summary-item">
          <span class="summary-label">总日志条数:</span>
          <span class="summary-value">{{ summaryStats.total }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-label summary-same">相同:</span>
          <span class="summary-value">{{ summaryStats.same }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-label summary-warning">警告差异:</span>
          <span class="summary-value">{{ summaryStats.warningDiff }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-label summary-error">错误差异:</span>
          <span class="summary-value">{{ summaryStats.errorDiff }}</span>
        </div>
        <div class="summary-item" v-if="firstDiffLog">
          <span class="summary-label">首次差异:</span>
          <span class="summary-value">{{ firstDiffLog }}</span>
        </div>
      </div>

      <!-- Aligned Logs -->
      <div class="aligned-logs">
        <div class="logs-header">
          <div class="logs-header-left">
            <span>执行 1 ({{ formatDateTime(execution1?.startTime) }})</span>
          </div>
          <div class="logs-header-right">
            <span>执行 2 ({{ formatDateTime(execution2?.startTime) }})</span>
          </div>
        </div>

        <div class="logs-content">
          <div
            v-for="(row, idx) in alignedLogs"
            :key="idx"
            :class="['log-row', `log-${row.diffType}`]"
          >
            <div class="log-cell left-cell">
              <span v-if="row.log1" class="log-marker">{{ getDiffMarker(row.diffType) }}</span>
              <span v-if="row.log1" class="log-time">{{ formatLogTime(row.log1.timestamp) }}</span>
              <span v-if="row.log1" :class="['log-level', `level-${row.log1.level.toLowerCase()}`]">[{{ row.log1.level }}]</span>
              <span v-if="row.log1" class="log-message">{{ row.log1.message }}</span>
              <span v-if="!row.log1" class="log-empty">-</span>
            </div>
            <div class="log-cell right-cell">
              <span v-if="row.log2" class="log-marker">{{ getDiffMarker(row.diffType) }}</span>
              <span v-if="row.log2" class="log-time">{{ formatLogTime(row.log2.timestamp) }}</span>
              <span v-if="row.log2" :class="['log-level', `level-${row.log2.level.toLowerCase()}`]">[{{ row.log2.level }}]</span>
              <span v-if="row.log2" class="log-message">{{ row.log2.message }}</span>
              <span v-if="!row.log2" class="log-empty">-</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <template #footer v-if="phase === 'select'">
      <span></span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import type { TaskExecution, ExecutionLog } from '@/api'
import { executionApi } from '@/api/execution'

const props = defineProps<{
  modelValue: boolean
  executionList: TaskExecution[]
  scriptId: number
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  compare: [executionId1: string, executionId2: string]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const phase = ref<'select' | 'result'>('select')
const executionId1 = ref('')
const executionId2 = ref('')
const execution1 = ref<TaskExecution>()
const execution2 = ref<TaskExecution>()
const logs1 = ref<ExecutionLog[]>([])
const logs2 = ref<ExecutionLog[]>([])

interface AlignedLogRow {
  log1?: ExecutionLog
  log2?: ExecutionLog
  diffType: 'same' | 'warning' | 'error'
}

const alignedLogs = computed<AlignedLogRow[]>(() => {
  const result: AlignedLogRow[] = []
  let i = 0
  let j = 0

  while (i < logs1.value.length || j < logs2.value.length) {
    const log1 = logs1.value[i]
    const log2 = logs2.value[j]

    if (!log1) {
      // All remaining logs in logs2 are additions
      result.push({ log2, diffType: 'warning' })
      j++
    } else if (!log2) {
      // All remaining logs in logs1 are deletions
      result.push({ log1, diffType: 'warning' })
      i++
    } else if (log1.message === log2.message) {
      // Same log message
      const diffType: 'same' | 'warning' | 'error' =
        log1.level !== log2.level ? 'warning' :
        log1.message.includes('error') || log2.message.includes('error') ? 'error' : 'same'
      result.push({ log1, log2, diffType })
      i++
      j++
    } else {
      // Different messages - check which one appears first
      const nextMatchIn1 = logs1.value.slice(i + 1).findIndex(l => l.message === log2.message)
      const nextMatchIn2 = logs2.value.slice(j + 1).findIndex(l => l.message === log1.message)

      if (nextMatchIn1 === -1 && nextMatchIn2 === -1) {
        // Neither has a match, treat as modification
        result.push({ log1, log2, diffType: 'error' })
        i++
        j++
      } else if (nextMatchIn1 !== -1 && (nextMatchIn2 === -1 || nextMatchIn1 < nextMatchIn2)) {
        // log2 appears later in logs1 - log1 has extra content
        result.push({ log1, diffType: 'warning' })
        i += nextMatchIn1 + 1
      } else {
        // log1 appears later in logs2 - log2 has extra content
        result.push({ log2, diffType: 'warning' })
        j += nextMatchIn2 + 1
      }
    }
  }

  return result
})

interface SummaryStats {
  total: number
  same: number
  warningDiff: number
  errorDiff: number
}

const summaryStats = computed<SummaryStats>(() => {
  let same = 0
  let warningDiff = 0
  let errorDiff = 0

  for (const row of alignedLogs.value) {
    if (row.diffType === 'same') same++
    else if (row.diffType === 'warning') warningDiff++
    else if (row.diffType === 'error') errorDiff++
  }

  return {
    total: alignedLogs.value.length,
    same,
    warningDiff,
    errorDiff
  }
})

const firstDiffLog = computed<string | undefined>(() => {
  for (const row of alignedLogs.value) {
    if (row.diffType !== 'same') {
      return row.log1?.message || row.log2?.message || undefined
    }
  }
  return undefined
})

function formatExecutionLabel(exec: TaskExecution) {
  return `${formatDateTime(exec.startTime)} - ${getStatusText(exec.status)}`
}

function formatDateTime(time?: string) {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}

function formatLogTime(timestamp: string) {
  const d = new Date(timestamp)
  return d.toLocaleTimeString('zh-CN', { hour12: false })
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

function getDiffMarker(diffType: 'same' | 'warning' | 'error') {
  switch (diffType) {
    case 'same': return '✓'
    case 'warning': return '⚠'
    case 'error': return '✗'
  }
}

async function handleCompare() {
  if (!executionId1.value || !executionId2.value) {
    ElMessage.warning('请选择两个执行记录')
    return
  }

  if (executionId1.value === executionId2.value) {
    ElMessage.warning('请选择不同的执行记录')
    return
  }

  try {
    const exec1 = props.executionList.find(e => e.executionId === executionId1.value)
    const exec2 = props.executionList.find(e => e.executionId === executionId2.value)

    if (exec1) execution1.value = exec1
    if (exec2) execution2.value = exec2

    // Load logs for both executions
    const [logsRes1, logsRes2] = await Promise.all([
      executionApi.getLogs(executionId1.value),
      executionApi.getLogs(executionId2.value)
    ])

    logs1.value = (logsRes1 as any).data?.logs || []
    logs2.value = (logsRes2 as any).data?.logs || []

    phase.value = 'result'
    emit('compare', executionId1.value, executionId2.value)
  } catch (e) {
    ElMessage.error('加载日志失败')
  }
}

function handleCancel() {
  visible.value = false
}

watch(() => props.modelValue, (val) => {
  if (val) {
    phase.value = 'select'
    executionId1.value = ''
    executionId2.value = ''
    execution1.value = undefined
    execution2.value = undefined
    logs1.value = []
    logs2.value = []
  }
})
</script>

<style scoped>
.compare-select {
  padding: 20px 0;
}

.select-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 20px;
  margin-bottom: 24px;
}

.compare-divider {
  font-weight: 600;
  font-size: 18px;
  color: #909399;
}

.select-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
}

.compare-result {
  padding: 0;
}

.result-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid #dcdfe6;
}

.result-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.log-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: #f5f7fa;
  border-radius: 8px;
}

.summary-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.summary-label {
  color: #606266;
}

.summary-same {
  color: #67c23a;
}

.summary-warning {
  color: #e6a23c;
}

.summary-error {
  color: #f56c6c;
}

.summary-value {
  font-weight: 600;
  color: #303133;
}

.aligned-logs {
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  overflow: hidden;
}

.logs-header {
  display: flex;
  background: #f5f7fa;
  border-bottom: 1px solid #dcdfe6;
  font-size: 12px;
  font-weight: 500;
  color: #606266;
}

.logs-header-left,
.logs-header-right {
  flex: 1;
  padding: 8px 12px;
}

.logs-header-left {
  border-right: 1px solid #dcdfe6;
}

.logs-content {
  max-height: 400px;
  overflow-y: auto;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
}

.log-row {
  display: flex;
  min-height: 28px;
  line-height: 28px;
  border-bottom: 1px solid #f0f0f0;
}

.log-row:last-child {
  border-bottom: none;
}

.log-row.diff-same {
  background: #fff;
}

.log-row.diff-warning {
  background: #fef9e6;
}

.log-row.diff-error {
  background: #fef0f0;
}

.log-cell {
  flex: 1;
  display: flex;
  align-items: center;
  padding: 0 8px;
  gap: 6px;
  overflow: hidden;
}

.left-cell {
  border-right: 1px solid #dcdfe6;
}

.log-marker {
  width: 20px;
  flex-shrink: 0;
  font-weight: 600;
}

.diff-same .log-marker {
  color: #67c23a;
}

.diff-warning .log-marker {
  color: #e6a23c;
}

.diff-error .log-marker {
  color: #f56c6c;
}

.log-time {
  color: #909399;
  flex-shrink: 0;
}

.log-level {
  flex-shrink: 0;
  font-weight: 600;
}

.level-info { color: #4fc3f7; }
.level-warn { color: #ffb74d; }
.level-error { color: #f48771; }
.level-debug { color: #81c784; }

.log-message {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #606266;
}

.log-empty {
  color: #c0c4cc;
}

.log-row.diff-same .log-message {
  color: #909399;
}

.log-row.diff-warning .log-message {
  color: #e6a23c;
}

.log-row.diff-error .log-message {
  color: #f56c6c;
}
</style>