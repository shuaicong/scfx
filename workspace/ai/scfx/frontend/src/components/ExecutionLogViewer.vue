<template>
  <div class="log-viewer">
    <div class="log-header">
      <span>执行日志</span>
      <div class="log-actions">
        <el-button size="small" @click="toggleScroll">
          {{ isPaused ? '继续滚动' : '暂停滚动' }}
        </el-button>
        <el-button size="small" @click="exportLogs">导出</el-button>
      </div>
    </div>
    <div ref="logContainer" class="log-container">
      <div
        v-for="(log, idx) in logs"
        :key="idx"
        :class="['log-item', `log-${log.level.toLowerCase()}`]"
      >
        <span class="log-time">{{ formatTime(log.timestamp) }}</span>
        <span class="log-level">{{ getLevelIcon(log.level) }}</span>
        <span class="log-message">{{ log.message }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import type { ExecutionLog } from '@/api'

const props = defineProps<{
  executionId: string
  logs: ExecutionLog[]
}>()

const logContainer = ref<HTMLElement>()
const isPaused = ref(false)

function formatTime(timestamp: string) {
  const d = new Date(timestamp)
  return d.toLocaleTimeString('zh-CN', { hour12: false })
}

function getLevelIcon(level: string) {
  switch (level) {
    case 'INFO': return 'ℹ️'
    case 'WARN': return '⚠️'
    case 'ERROR': return '❌'
    case 'DEBUG': return '🔍'
    default: return 'ℹ️'
  }
}

function toggleScroll() {
  isPaused.value = !isPaused.value
}

function exportLogs() {
  const content = props.logs.map(l => `[${l.timestamp}] [${l.level}] ${l.message}`).join('\n')
  const blob = new Blob([content], { type: 'text/plain' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `execution-${props.executionId}.log`
  a.click()
}

watch(() => props.logs, () => {
  if (!isPaused.value) {
    nextTick(() => {
      if (logContainer.value) {
        logContainer.value.scrollTop = logContainer.value.scrollHeight
      }
    })
  }
}, { deep: true })
</script>

<style scoped>
.log-viewer {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
}
.log-header {
  display: flex;
  justify-content: space-between;
  padding: 8px 12px;
  background: #f5f7fa;
  border-bottom: 1px solid #dcdfe6;
}
.log-container {
  height: 300px;
  overflow-y: auto;
  padding: 8px 12px;
  font-family: monospace;
  font-size: 13px;
  background: #1e1e1e;
  color: #d4d4d4;
}
.log-item {
  display: flex;
  padding: 2px 0;
}
.log-time {
  color: #888;
  margin-right: 8px;
}
.log-level {
  margin-right: 8px;
}
.log-message {
  flex: 1;
}
.log-info .log-message { color: #d4d4d4; }
.log-warn .log-message { color: #dcd90c; }
.log-error .log-message { color: #f48771; }
.log-debug .log-message { color: #6a9955; }
</style>