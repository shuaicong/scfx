<template>
  <div class="service-status" :class="{ offline: !isOnline }">
    <span class="status-icon">{{ isOnline ? '●' : '⚠' }}</span>
    <span class="status-text">{{ isOnline ? '采集服务正常' : '采集服务离线' }}</span>
    <el-button v-if="!isOnline" size="small" @click="handleReconnect">重新连接</el-button>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'

const isOnline = ref(true)
let pollTimer: number | null = null

const props = defineProps<{
  pollingInterval?: number
}>()

onMounted(() => {
  checkStatus()
  startPolling()
})

onUnmounted(() => {
  stopPolling()
})

function startPolling() {
  pollTimer = window.setInterval(() => {
    checkStatus()
  }, props.pollingInterval || 10000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

async function checkStatus() {
  try {
    const res = await fetch('/api/collector/heartbeat/status')
    const data = await res.json()

    if (data.code === 200) {
      isOnline.value = data.data.online
    }
  } catch {
    isOnline.value = false
  }
}

async function handleReconnect() {
  try {
    await fetch('/api/collector/heartbeat/refresh', { method: 'POST' })
    await checkStatus()
    if (isOnline.value) {
      ElMessage.success('重新连接成功')
    }
  } catch {
    ElMessage.error('重新连接失败')
  }
}
</script>

<style scoped>
.service-status {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border-radius: 4px;
  background: #f6ffed;
  border: 1px solid #b7eb8f;
}

.service-status.offline {
  background: #fff2e8;
  border-color: #ffbb96;
}

.status-icon {
  font-size: 16px;
}

.status-icon:not(.offline) .status-icon {
  color: #52c41a;
}

.status-icon.offline {
  color: #ff4d4f;
}

.status-text {
  font-size: 14px;
}
</style>