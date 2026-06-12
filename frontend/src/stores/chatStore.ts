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
  const error = ref<string | null>(null)
  const queryParams = ref<SessionListParams>({ page: 1, size: 20 })

  const currentSession = computed(() =>
    sessions.value.find(s => s.id === currentSessionId.value) || null
  )

  // 统一错误设置/清除方法（禁止直接赋值 error）
  function setError(msg: string | null) {
    error.value = msg
  }
  function clearError() {
    error.value = null
  }

  /** 拉取会话列表 */
  async function fetchSessions(params?: Partial<SessionListParams>) {
    clearError()
    loading.value = true
    try {
      if (params) Object.assign(queryParams.value, params)
      const res = await getSessions(queryParams.value)
      const data = (res as any).data
      sessions.value = data.records || []
      total.value = data.total || 0
    } catch (e: any) {
      console.error('[ChatStore] fetch sessions failed:', e)
      setError('数据加载失败，请稍后重试')
      // 失败不修改 sessions 状态，保持旧数据可见
    } finally {
      loading.value = false
    }
  }

  /** 批量删除会话（软删除） */
  async function deleteSessions(ids: string[]) {
    clearError()
    try {
      await batchDeleteSessions(ids)
      // 从本地列表移除，避免等待刷新
      sessions.value = sessions.value.filter(s => !ids.includes(s.id))
      total.value -= ids.length
      if (currentSessionId.value && ids.includes(currentSessionId.value)) {
        currentSessionId.value = null
      }
    } catch (e: any) {
      setError('请求失败，请检查网络后重试')
      throw e
    }
  }

  /** 更新会话标题 */
  async function updateTitle(id: string, title: string, source: 'default' | 'auto' | 'manual') {
    clearError()
    try {
      await updateSessionTitle(id, title, source)
      const session = sessions.value.find(s => s.id === id)
      if (session) {
        session.title = title
        session.title_source = source
      }
    } catch (e: any) {
      setError('操作失败，请稍后重试')
      throw e
    }
  }

  function setCurrentSessionId(id: string | null) {
    currentSessionId.value = id
  }

  function clearCurrentSession() {
    currentSessionId.value = null
    clearError()
  }

  return {
    sessions, total, currentSessionId, currentSession, loading, error, queryParams,
    fetchSessions, deleteSessions, updateTitle, setCurrentSessionId, clearCurrentSession,
    setError, clearError
  }
})
