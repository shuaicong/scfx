<template>
  <div class="history-page">
    <div class="page-header">
      <h2>历史记录</h2>
      <button class="back-btn" @click="goBack">← 返回问答</button>
    </div>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <input class="search-input" v-model="keyword" placeholder="搜索会话标题..."
        @input="debouncedSearch" />
      <input class="date-input" type="date" v-model="startDate" @change="handleSearch" />
      <span class="date-separator">至</span>
      <input class="date-input" type="date" v-model="endDate" @change="handleSearch" />
      <button class="search-btn" @click="handleSearch">搜索</button>
      <button class="clear-btn" @click="clearSearch">清空</button>
    </div>

    <!-- 批量操作栏 -->
    <div class="batch-bar" v-if="selectedIds.length > 0">
      <span>已选择 {{ selectedIds.length }} 项</span>
      <label class="select-all">
        <input type="checkbox" :checked="isAllSelected" @change="toggleSelectAll" /> 全选
      </label>
      <button class="batch-delete-btn" @click="handleBatchDelete">批量删除</button>
    </div>

    <!-- 加载态 -->
    <div v-if="store.loading" class="loading-area">
      <span class="loading-spinner"></span>
    </div>

    <!-- 空状态 -->
    <div v-else-if="store.sessions.length === 0" class="empty-area">
      <p>{{ keyword || startDate ? '未找到匹配的会话' : '暂无历史对话' }}</p>
    </div>

    <!-- 会话列表 -->
    <div v-else class="session-list">
      <div v-for="session in store.sessions" :key="session.id" class="session-card"
        :class="{ selected: selectedIds.includes(session.id) }">
        <input type="checkbox" :checked="selectedIds.includes(session.id)" @change="toggleSelect(session.id)" />
        <div class="card-body" @click="goToChat(session.id)">
          <div class="card-title">{{ session.title || '新会话' }}</div>
          <div class="card-preview">{{ session.last_message }}</div>
          <div class="card-meta">
            <span>{{ formatTime(session.updated_at) }}</span>
            <span>{{ session.message_count }} 条消息</span>
          </div>
        </div>
        <div class="card-actions">
          <button @click.stop="startRename(session)">重命名</button>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <div class="pagination" v-if="totalPages > 1">
      <button :disabled="currentPage <= 1" @click="goPage(currentPage - 1)">上一页</button>
      <span>{{ currentPage }} / {{ totalPages }}</span>
      <button :disabled="currentPage >= totalPages" @click="goPage(currentPage + 1)">下一页</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useChatStore } from '@/stores/chatStore'
import { ElMessageBox, ElMessage } from 'element-plus'
import type { ChatSession } from '@/types/session'

const router = useRouter()
const store = useChatStore()

const keyword = ref('')
const startDate = ref('')
const endDate = ref('')
const selectedIds = ref<string[]>([])
const currentPage = ref(1)
const pageSize = 20

const totalPages = computed(() => Math.ceil(store.total / pageSize) || 1)
const isAllSelected = computed(() => store.sessions.length > 0 && selectedIds.value.length === store.sessions.length)

let debounceTimer: ReturnType<typeof setTimeout>

onMounted(() => { store.fetchSessions({ page: 1, size: pageSize }) })

function goBack() {
  router.push('/ai-chat')
}

function debouncedSearch() {
  clearTimeout(debounceTimer)
  debounceTimer = setTimeout(handleSearch, 300)
}

function handleSearch() {
  selectedIds.value = []
  currentPage.value = 1
  store.fetchSessions({ page: 1, size: pageSize, keyword: keyword.value, start: startDate.value, end: endDate.value })
}

function clearSearch() {
  keyword.value = ''
  startDate.value = ''
  endDate.value = ''
  handleSearch()
}

function goPage(page: number) {
  currentPage.value = page
  store.fetchSessions({ page, size: pageSize, keyword: keyword.value, start: startDate.value, end: endDate.value })
}

function toggleSelect(id: string) {
  const idx = selectedIds.value.indexOf(id)
  if (idx >= 0) selectedIds.value.splice(idx, 1)
  else selectedIds.value.push(id)
}

function toggleSelectAll() {
  if (isAllSelected.value) selectedIds.value = []
  else selectedIds.value = store.sessions.map(s => s.id)
}

function goToChat(id: string) {
  store.setCurrentSessionId(id)
  router.push('/ai-chat')
}

async function handleBatchDelete() {
  if (selectedIds.value.length === 0) return
  try {
    await ElMessageBox.confirm('删除后仅隐藏，可在后续版本恢复，是否继续？', '确认删除', { type: 'warning' })
    await store.deleteSessions(selectedIds.value)
    selectedIds.value = []
    ElMessage.success('已删除')
  } catch {
    // 取消
  }
}

// 重命名
const renamingSession = ref<ChatSession | null>(null)
const renameDraft = ref('')

function startRename(session: ChatSession) {
  renamingSession.value = session
  renameDraft.value = session.title
}

async function confirmRename() {
  if (!renamingSession.value || !renameDraft.value.trim()) return
  await store.updateTitle(renamingSession.value.id, renameDraft.value.trim(), 'manual')
  renamingSession.value = null
  ElMessage.success('已重命名')
}

function formatTime(isoStr: string): string {
  if (!isoStr) return ''
  const date = new Date(isoStr)
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${m}-${d} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}
</script>

<style scoped>
.history-page {
  padding: 32px;
  max-width: 960px;
  margin: 0 auto;
  min-height: 100%;
  background: #0f1419;
  color: #e6edf3;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}
.page-header h2 { margin: 0; font-size: 22px; color: #f5c87a; }
.back-btn {
  background: none; border: 1px solid rgba(255,255,255,0.1);
  padding: 6px 14px; border-radius: 6px; color: #8b949e;
  cursor: pointer; font-size: 13px;
}
.back-btn:hover { color: #e6edf3; border-color: rgba(255,255,255,0.2); }

.search-bar {
  display: flex; gap: 8px; align-items: center;
  margin-bottom: 16px; flex-wrap: wrap;
}
.search-input {
  flex: 1; min-width: 200px;
  padding: 8px 12px; border: 1px solid rgba(255,255,255,0.1);
  border-radius: 6px; font-size: 14px;
  background: rgba(255,255,255,0.03); color: #e6edf3;
}
.search-input::placeholder { color: #6e7681; }
.date-input {
  padding: 8px; border: 1px solid rgba(255,255,255,0.1);
  border-radius: 6px; background: rgba(255,255,255,0.03);
  color: #e6edf3; font-size: 13px;
  color-scheme: dark;
}
.date-separator { color: #6e7681; }
.search-btn {
  padding: 8px 16px; border: none; border-radius: 6px;
  background: #f5c87a; color: #1a1f2e; cursor: pointer; font-size: 13px;
}
.clear-btn {
  padding: 8px 16px; border: 1px solid rgba(255,255,255,0.1);
  border-radius: 6px; background: transparent; color: #8b949e;
  cursor: pointer; font-size: 13px;
}
.clear-btn:hover { color: #e6edf3; }

.batch-bar {
  display: flex; align-items: center; gap: 12px;
  padding: 10px 16px; background: rgba(245,200,122,0.08);
  border: 1px solid rgba(245,200,122,0.2);
  border-radius: 8px; margin-bottom: 12px; font-size: 14px;
}
.select-all { display: flex; align-items: center; gap: 6px; cursor: pointer; }
.batch-delete-btn {
  background: #e74c3c; color: #fff; border: none;
  padding: 6px 14px; border-radius: 6px; cursor: pointer; margin-left: auto;
}

.loading-area, .empty-area {
  display: flex; justify-content: center; padding: 80px; color: #6e7681;
}
.loading-spinner {
  width: 28px; height: 28px; border: 2px solid rgba(255,255,255,0.1);
  border-top-color: #f5c87a; border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

.session-list { display: flex; flex-direction: column; gap: 10px; }

.session-card {
  display: flex; align-items: flex-start; gap: 12px;
  padding: 16px; border: 1px solid rgba(255,255,255,0.06);
  border-radius: 10px; cursor: pointer;
  transition: all 0.15s;
}
.session-card:hover { border-color: rgba(245,200,122,0.3); background: rgba(255,255,255,0.02); }
.session-card.selected { border-color: rgba(245,200,122,0.5); background: rgba(245,200,122,0.05); }

.card-body { flex: 1; min-width: 0; }
.card-title { font-size: 15px; font-weight: 600; color: #e6edf3; margin-bottom: 4px; }
.card-preview {
  font-size: 13px; color: #6e7681; margin-bottom: 6px;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.card-meta { font-size: 12px; color: #484f58; display: flex; gap: 16px; }

.card-actions button {
  background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.1);
  padding: 4px 10px; border-radius: 6px; cursor: pointer;
  font-size: 12px; color: #8b949e;
}
.card-actions button:hover { color: #e6edf3; border-color: rgba(255,255,255,0.2); }

.pagination {
  display: flex; justify-content: center; align-items: center;
  gap: 16px; padding: 24px 0;
}
.pagination button {
  padding: 6px 16px; border: 1px solid rgba(255,255,255,0.1);
  border-radius: 6px; background: rgba(255,255,255,0.03);
  color: #8b949e; cursor: pointer;
}
.pagination button:disabled { opacity: 0.3; cursor: not-allowed; }
.pagination button:hover:not(:disabled) { color: #e6edf3; }
.pagination span { color: #6e7681; font-size: 13px; }
</style>
