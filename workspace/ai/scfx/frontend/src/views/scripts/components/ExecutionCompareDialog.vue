<template>
  <el-dialog
    v-model="visible"
    title="对比执行记录"
    width="900px"
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
        <span class="result-title">执行记录对比</span>
      </div>

      <div class="execution-summary">
        <div class="summary-column">
          <div class="column-header">执行 1</div>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="开始时间">{{ formatDateTime(execution1?.startTime) }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="getStatusType(execution1?.status)">{{ getStatusText(execution1?.status) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="采集数量">{{ execution1?.collectedCount ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="执行时长">{{ formatDuration(execution1?.durationMs) }}</el-descriptions-item>
            <el-descriptions-item label="脚本版本">v{{ execution1?.versionId || '-' }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="summary-column">
          <div class="column-header">执行 2</div>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="开始时间">{{ formatDateTime(execution2?.startTime) }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="getStatusType(execution2?.status)">{{ getStatusText(execution2?.status) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="采集数量">{{ execution2?.collectedCount ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="执行时长">{{ formatDuration(execution2?.durationMs) }}</el-descriptions-item>
            <el-descriptions-item label="脚本版本">v{{ execution2?.versionId || '-' }}</el-descriptions-item>
          </el-descriptions>
        </div>
      </div>

      <!-- Change Analysis -->
      <div class="change-analysis">
        <div class="analysis-title">变更分析</div>
        <div class="analysis-content">
          <div v-for="item in changeAnalysis" :key="item.label" :class="['analysis-row', item.class]">
            <span class="analysis-label">{{ item.label }}</span>
            <span class="analysis-value">
              <span class="base-value">{{ item.value1 }}</span>
              <span class="arrow">→</span>
              <span class="target-value">{{ item.value2 }}</span>
            </span>
            <span v-if="item.diff" :class="['analysis-diff', item.diffClass]">
              {{ item.diff }}
            </span>
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
import type { ExecutionDetail } from '@/api/execution'
import type { TaskExecution } from '@/api'

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
const execution1 = ref<ExecutionDetail>()
const execution2 = ref<ExecutionDetail>()

function formatExecutionLabel(exec: TaskExecution) {
  return `${formatDateTime(exec.startTime)} - ${getStatusText(exec.status)}`
}

function formatDateTime(time?: string) {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
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

const changeAnalysis = computed(() => {
  const e1 = execution1.value
  const e2 = execution2.value
  if (!e1 || !e2) return []

  const results: Array<{
    label: string
    value1: string
    value2: string
    diff?: string
    class?: string
    diffClass?: string
  }> = []

  // Status comparison
  results.push({
    label: '状态',
    value1: getStatusText(e1.status),
    value2: getStatusText(e2.status),
    class: e1.status !== e2.status ? 'diff-changed' : ''
  })

  // Duration comparison
  const duration1 = e1.durationMs || 0
  const duration2 = e2.durationMs || 0
  const durationDiff = duration2 - duration1
  const durationPercent = duration1 > 0 ? ((durationDiff / duration1) * 100).toFixed(1) : '0'
  results.push({
    label: '执行时长',
    value1: formatDuration(e1.durationMs),
    value2: formatDuration(e2.durationMs),
    diff: durationDiff !== 0 ? `${durationDiff > 0 ? '+' : ''}${formatDuration(Math.abs(durationDiff))} (${durationPercent}%)` : undefined,
    class: durationDiff !== 0 ? 'diff-changed' : ''
  })

  // Collected count comparison
  const count1 = e1.collectedCount || 0
  const count2 = e2.collectedCount || 0
  const countDiff = count2 - count1
  const countPercent = count1 > 0 ? ((countDiff / count1) * 100).toFixed(1) : '0'
  results.push({
    label: '采集数量',
    value1: String(count1),
    value2: String(count2),
    diff: countDiff !== 0 ? `${countDiff > 0 ? '+' : ''}${countDiff} (${countPercent}%)` : undefined,
    class: countDiff > 0 ? 'diff-positive' : countDiff < 0 ? 'diff-negative' : ''
  })

  // Script version
  results.push({
    label: '脚本版本',
    value1: `v${e1.versionId || '-'}`,
    value2: `v${e2.versionId || '-'}`,
    class: e1.versionId !== e2.versionId ? 'diff-changed' : ''
  })

  return results
})

async function handleCompare() {
  if (!executionId1.value || !executionId2.value) {
    ElMessage.warning('请选择两个执行记录')
    return
  }

  if (executionId1.value === executionId2.value) {
    ElMessage.warning('请选择不同的执行记录')
    return
  }

  // Load execution details - for now using data from the list
  // In a real scenario, you would call API to get details
  const exec1 = props.executionList.find(e => e.executionId === executionId1.value)
  const exec2 = props.executionList.find(e => e.executionId === executionId2.value)

  if (exec1 && exec2) {
    execution1.value = {
      executionId: exec1.executionId,
      scriptId: exec1.scriptId,
      versionId: exec1.versionId,
      status: exec1.status,
      startTime: exec1.startTime,
      endTime: exec1.endTime,
      durationMs: exec1.durationMs,
      triggerType: exec1.triggerType
    }
    execution2.value = {
      executionId: exec2.executionId,
      scriptId: exec2.scriptId,
      versionId: exec2.versionId,
      status: exec2.status,
      startTime: exec2.startTime,
      endTime: exec2.endTime,
      durationMs: exec2.durationMs,
      triggerType: exec2.triggerType
    }
    phase.value = 'result'
    emit('compare', executionId1.value, executionId2.value)
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
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #dcdfe6;
}

.result-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.execution-summary {
  display: flex;
  gap: 24px;
  margin-bottom: 20px;
}

.summary-column {
  flex: 1;
}

.column-header {
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 8px;
  color: #606266;
}

.change-analysis {
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  padding: 16px;
  background: #f5f7fa;
}

.analysis-title {
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 12px;
  color: #303133;
}

.analysis-content {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.analysis-row {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  background: #fff;
  border-radius: 4px;
  gap: 12px;
}

.analysis-label {
  width: 80px;
  color: #606266;
  font-size: 13px;
}

.analysis-value {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.base-value {
  color: #909399;
}

.arrow {
  color: #c0c4cc;
}

.target-value {
  color: #303133;
  font-weight: 500;
}

.analysis-diff {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
}

.diff-positive .analysis-diff {
  background: #f0f9eb;
  color: #67c23a;
}

.diff-negative .analysis-diff {
  background: #fef0f0;
  color: #f56c6c;
}

.diff-changed .analysis-diff {
  background: #ecf5ff;
  color: #409eff;
}

.analysis-row.diff-positive {
  background: #f0f9eb;
}

.analysis-row.diff-negative {
  background: #fef0f0;
}

.analysis-row.diff-changed {
  background: #ecf5ff;
}
</style>