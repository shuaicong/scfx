import { ref, onMounted, onUnmounted } from 'vue'
import { alertApi } from '@/api/alert'
import type { AlertStats, AlertRecord } from '@/api/alert'

const POLL_INTERVAL = 30000 // 30 秒轮询一次
const MAX_DROPDOWN_ITEMS = 10

// 全局单例状态
const totalUnresolved = ref(0)
const recentAlerts = ref<AlertRecord[]>([])
const stats = ref<AlertStats>({ critical: 0, error: 0, warning: 0, info: 0 })

let pollTimer: ReturnType<typeof setInterval> | null = null
let titleInterval: ReturnType<typeof setInterval> | null = null
let originalTitle = document.title
let isFlashing = false
let flashState = false

function startTitleFlash(count: number) {
  if (isFlashing) return
  isFlashing = true
  originalTitle = document.title
  flashState = false

  titleInterval = setInterval(() => {
    flashState = !flashState
    document.title = flashState
      ? `⚠️ 有 ${count} 条告警 - 采集任务管理`
      : '采集任务管理'
  }, 1000)
}

function stopTitleFlash() {
  if (titleInterval) {
    clearInterval(titleInterval)
    titleInterval = null
  }
  isFlashing = false
  document.title = originalTitle
}

function handleVisibilityChange() {
  if (document.hidden) {
    // 页面隐藏时不操作
  } else {
    // 页面可见时停止闪烁，重置标题
    stopTitleFlash()
  }
}

function handleWindowFocus() {
  stopTitleFlash()
}

async function fetchAlertData() {
  try {
    const statsRes: any = await alertApi.stats()
    if (statsRes.data) {
      stats.value = statsRes.data
      const s = statsRes.data as AlertStats
      const alertCount = (s.critical || 0) + (s.error || 0)
      totalUnresolved.value = alertCount

      if (alertCount > 0 && document.hidden) {
        startTitleFlash(alertCount)
      } else if (alertCount === 0) {
        stopTitleFlash()
      }
    }
  } catch (_) {
    // 静默忽略轮询错误
  }

  try {
    const listRes: any = await alertApi.list({ status: 'pending', page: 1, size: MAX_DROPDOWN_ITEMS })
    if (listRes.data?.records) {
      recentAlerts.value = listRes.data.records
    }
  } catch (_) {
    // 静默忽略
  }
}

export function useAlertPolling() {
  onMounted(() => {
    originalTitle = document.title
    document.addEventListener('visibilitychange', handleVisibilityChange)
    window.addEventListener('focus', handleWindowFocus)

    // 立即拉取一次，然后开始轮询
    fetchAlertData()
    if (!pollTimer) {
      pollTimer = setInterval(fetchAlertData, POLL_INTERVAL)
    }
  })

  onUnmounted(() => {
    document.removeEventListener('visibilitychange', handleVisibilityChange)
    window.removeEventListener('focus', handleWindowFocus)
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
    stopTitleFlash()
  })

  return {
    totalUnresolved,
    recentAlerts,
    stats,
  }
}
