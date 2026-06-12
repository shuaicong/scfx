<template>
  <div class="session-drawer" :class="{ 'drawer-open': visible }">
    <div v-if="visible" class="drawer-overlay" @click="$emit('close')"></div>
    <div class="drawer-panel">
      <div class="drawer-header">
        <h3>历史会话</h3>
        <button class="new-chat-btn" @click="handleNewChat">＋ 新建会话</button>
      </div>
      <div v-if="loading" class="drawer-loading">
        <span class="loading-spinner"></span>
      </div>
      <div v-else-if="store.error && !store.loading" class="drawer-error">
        <p class="error-text">{{ store.error }}</p>
        <button class="retry-btn" @click="store.fetchSessions()">重试</button>
      </div>
      <div v-else-if="sessions.length === 0" class="drawer-empty">
        <p>暂无历史对话，开始你的第一次提问</p>
      </div>
      <div v-else class="drawer-list">
        <div
          v-for="session in sessions"
          :key="session.id"
          class="session-item"
          :class="{ active: session.id === currentSessionId }"
          @click="handleSwitchSession(session.id)"
        >
          <div class="session-title">{{ session.title || '新会话' }}</div>
          <div class="session-meta">
            <span class="session-time">{{ formatTime(session.updated_at) }}</span>
            <span v-if="session.last_message" class="session-preview">{{ session.last_message }}</span>
          </div>
          <button class="session-delete-btn" @click.stop="handleDeleteSession(session.id)" title="删除会话">
            <svg width="14" height="14" viewBox="0 0 14 14"><path d="M1 1l12 12M13 1L1 13" stroke="currentColor" stroke-width="1.5"/></svg>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useChatStore } from '@/stores/chatStore'
import { ElMessageBox, ElMessage } from 'element-plus'

const props = defineProps<{ visible: boolean }>()
const emit = defineEmits<{ close: []; switch: [id: string]; newChat: [] }>()

const store = useChatStore()

const sessions = computed(() => store.sessions)
const currentSessionId = computed(() => store.currentSessionId)
const loading = computed(() => store.loading)

function handleSwitchSession(id: string) {
  store.setCurrentSessionId(id)
  emit('switch', id)
  emit('close')
}

function handleNewChat() {
  store.clearCurrentSession()
  emit('newChat')
  emit('close')
}

async function handleDeleteSession(id: string) {
  try {
    await ElMessageBox.confirm('删除后仅隐藏，可在后续版本恢复，是否继续？', '确认删除', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await store.deleteSessions([id])
    ElMessage.success('已删除')
  } catch {
    // 取消操作，不处理
  }
}

function formatTime(isoStr: string): string {
  if (!isoStr) return ''
  const date = new Date(isoStr)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMin = Math.floor(diffMs / 60000)
  if (diffMin < 1) return '刚刚'
  if (diffMin < 60) return `${diffMin}分钟前`
  const diffHour = Math.floor(diffMin / 60)
  if (diffHour < 24) return `${diffHour}小时前`
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${m}-${d} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}
</script>

<style scoped>
.session-drawer {
  position: fixed; top: 0; left: 0; width: 100%; height: 100%;
  z-index: 1000; pointer-events: none;
}
.session-drawer.drawer-open { pointer-events: auto; }
.drawer-overlay {
  position: absolute; width: 100%; height: 100%;
  background: rgba(0,0,0,0.3);
}
.drawer-panel {
  position: absolute; left: 0; top: 0; width: 320px; height: 100%;
  background: #1e2433; box-shadow: 2px 0 12px rgba(0,0,0,0.3);
  display: flex; flex-direction: column;
  transform: translateX(-100%); transition: transform 0.25s ease;
}
.drawer-open .drawer-panel { transform: translateX(0); }
.drawer-header {
  padding: 16px; border-bottom: 1px solid rgba(255,255,255,0.06);
  display: flex; justify-content: space-between; align-items: center;
}
.drawer-header h3 { margin: 0; font-size: 16px; color: #e6edf3; }
.new-chat-btn {
  background: #f5c87a; border: none; color: #fff;
  padding: 6px 12px; border-radius: 4px; cursor: pointer; font-size: 13px;
}
.drawer-loading { display: flex; justify-content: center; padding: 40px; }
.loading-spinner {
  width: 24px; height: 24px; border: 2px solid rgba(255,255,255,0.08);
  border-top-color: #f5c87a; border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
.drawer-empty { padding: 40px 16px; text-align: center; color: #8b949e; font-size: 14px; }
.drawer-error {
  padding: 40px 16px; text-align: center;
}
.error-text { color: #f85149; font-size: 14px; margin: 0 0 12px 0; }
.retry-btn {
  background: rgba(245,200,122,0.15);
  border: 1px solid rgba(245,200,122,0.3);
  color: #f5c87a; padding: 6px 16px; border-radius: 6px;
  cursor: pointer; font-size: 13px; transition: all 0.2s;
}
.retry-btn:hover { background: rgba(245,200,122,0.25); }
.drawer-list { flex: 1; overflow-y: auto; padding: 8px 0; }
.session-item {
  position: relative; padding: 12px 16px; cursor: pointer;
  border-bottom: 1px solid rgba(255,255,255,0.04); transition: background 0.15s;
}
.session-item:hover { background: rgba(255,255,255,0.05); }
.session-item.active { background: rgba(245,200,122,0.1); }
.session-title {
  font-size: 14px; color: #e6edf3; margin-bottom: 4px;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.session-meta { font-size: 12px; color: #8b949e; }
.session-preview { margin-left: 8px; }
.session-delete-btn {
  position: absolute; right: 12px; top: 50%; transform: translateY(-50%);
  opacity: 0; background: none; border: none; cursor: pointer;
  color: #6e7681; padding: 4px;
}
.session-item:hover .session-delete-btn { opacity: 1; }
.session-delete-btn:hover { color: #f85149; }
</style>
