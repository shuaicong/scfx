<!-- frontend/src/components/CollectionProgress.vue -->
<template>
  <el-drawer v-model="visible" title="采集详情" size="500px">
    <div class="progress-info">
      <div class="execution-id">执行ID: {{ executionId }}</div>
      <div class="status">
        <el-tag :type="statusType">{{ statusText }}</el-tag>
      </div>

      <el-progress :percentage="progressPercent" style="margin: 20px 0;" />

      <div class="stats">
        <div class="stat-item">
          <span class="label">已采集</span>
          <span class="value">{{ collectedCount }}</span>
        </div>
        <div class="stat-item">
          <span class="label">已提交</span>
          <span class="value">{{ submittedCount }}</span>
        </div>
        <div class="stat-item">
          <span class="label">失败</span>
          <span class="value error">{{ failedCount }}</span>
        </div>
      </div>
    </div>

    <div class="log-section">
      <div class="log-header">
        <span>实时日志</span>
        <el-button link @click="downloadLog">下载日志</el-button>
      </div>
      <div class="log-list" ref="logListRef">
        <div v-for="(log, idx) in logs" :key="idx" class="log-item" :class="log.level">
          <span class="time">{{ log.time }}</span>
          <span class="message">{{ log.message }}</span>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button type="danger" @click="handleCancel">取消执行</el-button>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { getExecution, getExecutionLogs, cancelExecution } from '@/api/dashboard'

const props = defineProps<{ modelValue: boolean; executionId: string }>()
const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const execution = ref<any>({})
const logs = ref<any[]>([])

const collectedCount = computed(() => execution.value.collectedCount || 0)
const submittedCount = computed(() => execution.value.submittedCount || 0)
const failedCount = computed(() => execution.value.failedCount || 0)
const progressPercent = computed(() => {
  const total = collectedCount.value
  if (!total) return 0
  return Math.round((submittedCount.value / total) * 100)
})

const statusType = computed(() => {
  const map: Record<string, string> = { running: 'primary', success: 'success', failed: 'danger' }
  return map[execution.value.status] || 'info'
})

const statusText = computed(() => {
  const map: Record<string, string> = { running: '运行中', success: '成功', failed: '失败' }
  return map[execution.value.status] || execution.value.status
})

async function loadExecution() {
  const res: any = await getExecution(props.executionId)
  if (res.code === 200) execution.value = res.data
}

async function loadLogs() {
  const res: any = await getExecutionLogs(props.executionId)
  if (res.code === 200) logs.value = res.data
}

async function handleCancel() {
  await cancelExecution(props.executionId)
  visible.value = false
}

function downloadLog() {
  // TODO: 下载日志文件
}

// 定时刷新
let timer: number | null = null

function startPolling() {
  timer = window.setInterval(() => {
    loadExecution()
    loadLogs()
  }, 3000)
}

function stopPolling() {
  if (timer) clearInterval(timer)
}

watch(visible, (val) => {
  if (val) {
    loadExecution()
    loadLogs()
    startPolling()
  } else {
    stopPolling()
  }
})
</script>

<style scoped>
.progress-info {
  padding: 16px;
}
.execution-id {
  color: #666;
  margin-bottom: 12px;
}
.status {
  margin-bottom: 12px;
}
.stats {
  display: flex;
  gap: 24px;
}
.stat-item {
  display: flex;
  flex-direction: column;
}
.stat-item .label {
  font-size: 12px;
  color: #999;
}
.stat-item .value {
  font-size: 24px;
  font-weight: bold;
}
.stat-item .value.error {
  color: #f56c6c;
}
.log-section {
  padding: 16px;
  border-top: 1px solid #eee;
}
.log-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.log-list {
  max-height: 300px;
  overflow-y: auto;
  font-family: monospace;
  font-size: 12px;
}
.log-item {
  padding: 4px 0;
}
.log-item .time {
  color: #999;
  margin-right: 8px;
}
.log-item.error .message {
  color: #f56c6c;
}
</style>