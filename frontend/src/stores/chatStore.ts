// AI 问答会话状态管理
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getSessions, batchDeleteSessions, updateSessionTitle } from '@/api/sessions'
import type { ChatSession, SessionListParams } from '@/types/session'

export const useChatStore = defineStore('chat', () => {
  const sessions = ref<ChatSession[]>([])
  const total = ref(0)
  const currentSessionId = ref<string | null>(null)
  const loading = ref(false)
  const queryParams = ref<SessionListParams>({ page: 1, size: 20 })

  const currentSession = computed(() =>
    sessions.value.find(s => s.id === currentSessionId.value) || null
  )

  /** 拉取会话列表 */
  async function fetchSessions(params?: Partial<SessionListParams>) {
    loading.value = true
    try {
      if (params) Object.assign(queryParams.value, params)
      const res = await getSessions(queryParams.value)
      const data = (res as any).data
      sessions.value = data.records || []
      total.value = data.total || 0
    } catch (e) {
      console.error('[ChatStore] fetch sessions failed:', e)
      // 失败不修改 sessions 状态，保持旧数据可见
    } finally {
      loading.value = false
    }
  }

  /** 批量删除会话（软删除） */
  async function deleteSessions(ids: string[]) {
    await batchDeleteSessions(ids)
    // 从本地列表移除，避免等待刷新
    sessions.value = sessions.value.filter(s => !ids.includes(s.id))
    total.value -= ids.length
    if (currentSessionId.value && ids.includes(currentSessionId.value)) {
      currentSessionId.value = null
    }
  }

  /** 更新会话标题 */
  async function updateTitle(id: string, title: string, source: 'default' | 'auto' | 'manual') {
    await updateSessionTitle(id, title, source)
    const session = sessions.value.find(s => s.id === id)
    if (session) {
      session.title = title
      session.title_source = source
    }
  }

  function setCurrentSessionId(id: string | null) {
    currentSessionId.value = id
  }

  function clearCurrentSession() {
    currentSessionId.value = null
  }

  return {
    sessions, total, currentSessionId, currentSession, loading, queryParams,
    fetchSessions, deleteSessions, updateTitle, setCurrentSessionId, clearCurrentSession
  }
})
