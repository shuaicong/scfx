<!-- frontend/src/components/CollectionProgress.vue -->
<template>
  <el-drawer v-model="drawerVisible" title="执行进度" size="500px" direction="rtl">
    <div class="progress-info">
      <div class="execution-id">执行ID: {{ executionId }}</div>
      <div class="status-row">
        <el-tag :type="statusType">{{ statusText }}</el-tag>
        <el-progress :percentage="progressPercent" style="flex: 1; margin-left: 16px;" />
      </div>

      <div class="stats">
        <div class="stat-item">
          <span class="label">已处理</span>
          <span class="value">{{ processedCount }}</span>
        </div>
        <div class="stat-item">
          <span class="label">总数</span>
          <span class="value">{{ totalCount }}</span>
        </div>
      </div>
    </div>

    <div class="log-section">
      <ExecutionLogViewer :executionId="executionId" :logs="logs" />
    </div>

    <template #footer>
      <el-button @click="drawerVisible = false">关闭</el-button>
      <el-button type="danger" @click="handleCancel" :disabled="execution?.status === 'success' || execution?.status === 'failed'">取消执行</el-button>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { executionApi, type ExecutionLog } from '@/api'
import ExecutionLogViewer from './ExecutionLogViewer.vue'

const drawerVisible = ref(false)
const executionId = ref('')
const execution = ref<any>({})
const logs = ref<ExecutionLog[]>([])

const processedCount = computed(() => execution.value.processedCount || 0)
const totalCount = computed(() => execution.value.totalCount || 0)

const progressPercent = computed(() => {
  const total = totalCount.value
  if (!total) return 0
  return Math.round((processedCount.value / total) * 100)
})

const statusType = computed(() => {
  const map: Record<string, string> = {
    pending: 'info',
    running: 'primary',
    success: 'success',
    failed: 'danger',
    cancelled: 'warning'
  }
  return map[execution.value.status] || 'info'
})

const statusText = computed(() => {
  const map: Record<string, string> = {
    pending: '等待中',
    running: '执行中',
    success: '已完成',
    failed: '失败',
    cancelled: '已取消'
  }
  return map[execution.value.status] || execution.value.status || '等待中'
})

async function loadExecution() {
  if (!executionId.value) return
  try {
    const res: any = await executionApi.get(executionId.value)
    execution.value = res.data || {}
  } catch (error) {
    console.error('加载执行状态失败', error)
  }
}

async function loadLogs() {
  if (!executionId.value) return
  try {
    const res: any = await executionApi.logs(executionId.value)
    logs.value = res.data || []
  } catch (error) {
    console.error('加载执行日志失败', error)
  }
}

async function handleCancel() {
  try {
    await executionApi.cancel(executionId.value)
    drawerVisible.value = false
  } catch (error) {
    console.error('取消执行失败', error)
  }
}

// 定时刷新
let timer: number | null = null

function startPolling() {
  loadExecution()
  loadLogs()
  timer = window.setInterval(() => {
    loadExecution()
    loadLogs()
  }, 3000)
}

function stopPolling() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

watch(drawerVisible, (val) => {
  if (val) {
    startPolling()
  } else {
    stopPolling()
  }
})

// 暴露 open 方法供外部调用
function open(id: string) {
  executionId.value = id
  drawerVisible.value = true
}

defineExpose({ open })
</script>

<style scoped>
.progress-info {
  padding: 16px;
}
.execution-id {
  color: #666;
  margin-bottom: 16px;
  font-size: 13px;
}
.status-row {
  display: flex;
  align-items: center;
  margin-bottom: 20px;
}
.stats {
  display: flex;
  gap: 32px;
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
.log-section {
  padding: 16px;
  border-top: 1px solid #eee;
}
</style>