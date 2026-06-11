<template>
  <div class="ai-chat-page">
    <!-- 顶部导航 -->
    <div class="chat-header">
      <div class="header-left">
        <button class="sidebar-toggle" @click="toggleDrawer" title="历史会话">
          <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
            <path d="M2 4h16M2 10h16M2 16h16" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          </svg>
        </button>
        <button class="back-btn" @click="goBack">
          <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
            <path d="M12.5 15L7.5 10L12.5 5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          <span>返回</span>
        </button>
        <div class="header-title">
          <div class="title-icon">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
              <path d="M12 2C6.48 2 2 6.48 2 12C2 17.52 6.48 22 12 22C17.52 22 22 17.52 22 12C22 6.48 17.52 2 12 2Z" fill="url(#ai-gradient)" opacity="0.2"/>
              <path d="M12 2C6.48 2 2 6.48 2 12C2 17.52 6.48 22 12 22C17.52 22 22 17.52 22 12C22 6.48 17.52 2 12 2ZM12 20C7.59 20 4 16.41 4 12C4 7.59 7.59 4 12 4C16.41 4 20 7.59 20 12C20 16.41 16.41 20 12 20Z" fill="url(#ai-gradient)"/>
              <path d="M12 6C8.69 6 6 8.69 6 12C6 15.31 8.69 18 12 18C15.31 18 18 15.31 18 12" stroke="url(#ai-gradient)" stroke-width="1.5" stroke-linecap="round"/>
              <circle cx="12" cy="12" r="2" fill="url(#ai-gradient)"/>
              <defs>
                <linearGradient id="ai-gradient" x1="2" y1="2" x2="22" y2="22">
                  <stop stop-color="#f5c87a"/>
                  <stop offset="1" stop-color="#d4a574"/>
                </linearGradient>
              </defs>
            </svg>
          </div>
          <template v-if="titleEditing && currentSessionId">
            <input class="title-input" v-model="titleDraft"
              @blur="saveTitle" @keydown.enter="saveTitle" @keydown.escape="cancelTitleEdit"
              ref="titleInputRef" autofocus />
          </template>
          <template v-else>
            <span class="title-text" :class="{ clickable: !!currentSessionId }" @click="startTitleEdit">
              {{ chatStore.currentSession?.title || 'AI 知识问答' }}
            </span>
          </template>
        </div>
      </div>
      <div class="header-right">
        <button class="history-btn" @click="goToHistory" title="历史记录">
          <svg width="18" height="18" viewBox="0 0 18 18" fill="none">
            <path d="M9 1C4.58 1 1 4.58 1 9s3.58 8 8 8 8-3.58 8-8-3.58-8-8-8zm0 14c-3.31 0-6-2.69-6-6s2.69-6 6-6 6 2.69 6 6-2.69 6-6 6zm1-11H8v5l4.25 2.52.75-1.23-3.5-2.08V4z" fill="currentColor"/>
          </svg>
          <span>历史</span>
        </button>
        <div class="data-sources">
          <span class="sources-label">数据来源</span>
          <div class="source-tags">
            <span
              v-for="source in availableSources"
              :key="source"
              class="source-tag"
              :class="{ active: selectedSources.includes(source) }"
              @click="toggleSource(source)"
            >
              {{ source }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- 主内容区 -->
    <div class="chat-content">
      <!-- 欢迎信息 -->
      <div v-if="!currentAnswer && !isLoading" class="welcome-section">
        <div class="welcome-icon">
          <svg width="64" height="64" viewBox="0 0 64 64" fill="none">
            <circle cx="32" cy="32" r="30" stroke="url(#welcome-gradient)" stroke-width="2" opacity="0.3"/>
            <circle cx="32" cy="32" r="20" stroke="url(#welcome-gradient)" stroke-width="2" opacity="0.5"/>
            <circle cx="32" cy="32" r="10" fill="url(#welcome-gradient)" opacity="0.8"/>
            <path d="M32 12V20M32 44V52M12 32H20M44 32H52" stroke="url(#welcome-gradient)" stroke-width="2" stroke-linecap="round"/>
            <defs>
              <linearGradient id="welcome-gradient" x1="2" y1="2" x2="62" y2="62">
                <stop stop-color="#f5c87a"/>
                <stop offset="1" stop-color="#d4a574"/>
              </linearGradient>
            </defs>
          </svg>
        </div>
        <h2 class="welcome-title">您好，我是农业情报助手</h2>
        <p class="welcome-desc">基于采集的粮信网、我的钢铁、中华粮网等数据源，为您解答农业市场相关问题</p>
        <div class="suggestion-chips">
          <div
            v-for="suggestion in suggestions"
            :key="suggestion"
            class="suggestion-chip"
            @click="askQuestion(suggestion)"
          >
            {{ suggestion }}
          </div>
        </div>
      </div>

      <!-- 对话区域 -->
      <div v-else class="messages-container" ref="messagesContainer">
        <!-- 用户问题 -->
        <div class="message-item user">
          <div class="message-avatar">
            <div class="avatar user-avatar">
              <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                <path d="M8 8C9.6 8 10.67 6.93 10.67 5.33C10.67 3.73 9.6 2.67 8 2.67C6.4 2.67 5.33 3.73 5.33 5.33C5.33 6.93 6.4 8 8 8Z" fill="currentColor"/>
                <path d="M3.33 14C3.33 11.05 5.6 8.67 8.5 8.67V8.67C11.4 8.67 13.67 11.05 13.67 14V14.67H3.33V14Z" fill="currentColor"/>
              </svg>
            </div>
          </div>
          <div class="message-body">
            <div class="message-content">{{ lastQuestion }}</div>
          </div>
        </div>

        <!-- AI 回复区域 -->
        <div class="message-item assistant">
          <div class="message-avatar">
            <div class="avatar ai-avatar">
              <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                <circle cx="8" cy="8" r="6" fill="currentColor" opacity="0.2"/>
                <circle cx="8" cy="8" r="3" fill="currentColor"/>
              </svg>
            </div>
          </div>
          <div class="message-body">
            <!-- 思考过程 -->
            <ThoughtProcess v-if="thoughts.length > 0" :thoughts="thoughts" />

            <!-- 来源展示 -->
            <div v-if="sources.length > 0" class="sources-grid">
              <SourceCard
                v-for="source in sources"
                :key="source.index"
                :source="source"
              />
            </div>

            <!-- 回答内容 -->
            <div v-if="currentAnswer" class="message-content ai-content">{{ currentAnswer }}</div>

            <!-- 错误信息 -->
            <div v-if="errorMessage" class="error-message">
              <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                <circle cx="7" cy="7" r="6" stroke="currentColor" stroke-width="1.5"/>
                <path d="M7 4V8M7 10V10.01" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
              </svg>
              <span>{{ errorMessage }}</span>
            </div>

            <!-- 加载 / 重连指示 -->
            <div v-if="isLoading && !currentAnswer && thoughts.length === 0" class="typing-indicator">
              <span></span>
              <span></span>
              <span></span>
            </div>
            <div v-if="reconnecting" class="reconnecting-hint">
              <span>连接中断，正在重连...</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部输入区 -->
    <div class="chat-input-area">
      <div class="input-options">
        <button
          class="deep-thinking-btn"
          disabled
          title="该功能即将上线（Phase 2）"
        >
          <span class="btn-icon">💭</span>
          <span class="btn-text">深度思考</span>
        </button>
        <button
          class="internet-search-btn"
          disabled
          title="该功能即将上线（Phase 2）"
        >
          <span class="btn-icon">🌐</span>
          <span class="btn-text">联网搜索</span>
        </button>
      </div>
      <div class="input-wrapper">
        <input
          v-model="question"
          type="text"
          class="chat-input"
          placeholder="输入您的问题..."
          :disabled="isLoading"
          maxlength="500"
          @keyup.enter="askQuestion(question)"
        />
        <button
          class="send-btn"
          :class="{ active: question.trim() }"
          :disabled="!question.trim() || isLoading"
          @click="askQuestion(question)"
        >
          <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
            <path d="M18 10L2 2L6 10L2 18L18 10Z" fill="currentColor"/>
          </svg>
        </button>
      </div>
      <div class="input-hint">
        <span>基于{{ availableSources.join('、') }}等数据源回答</span>
      </div>
    </div>

    <!-- 报告详情弹窗 -->
    <el-dialog
      v-model="showReportDialog"
      :title="currentReport?.title"
      width="800px"
      class="report-dialog"
    >
      <div v-if="currentReport" class="report-content">
        <div class="report-meta">
          <span class="meta-item">
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
              <circle cx="7" cy="7" r="6" stroke="currentColor" stroke-width="1.5"/>
              <path d="M7 4V7H10" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
            </svg>
            {{ currentReport.publish_time }}
          </span>
          <span class="meta-item">
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
              <path d="M7 1C3.69 1 1 3.69 1 7C1 10.31 3.69 13 7 13C10.31 13 13 10.31 13 7C13 3.69 10.31 1 7 1Z" stroke="currentColor" stroke-width="1.5"/>
              <path d="M7 4V7L9 9" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
            </svg>
            {{ currentReport.source }}
          </span>
        </div>
        <div class="report-body" v-html="currentReport.content"></div>
      </div>
    </el-dialog>

    <!-- 文档预览弹窗 -->
    <DocumentPreview
      v-model="showDocPreview"
      :url="previewDocUrl"
      :title="previewDocTitle"
    />

    <!-- 历史会话抽屉 -->
    <SessionDrawer
      :visible="drawerVisible"
      @close="drawerVisible = false"
      @switch="handleSwitchSession"
      @new-chat="handleNewChat"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { aiChatApiV2, type SSEEvent, type Source } from '@/api/ai-chat'
import { useChatStore } from '@/stores/chatStore'
import { getSessionDetail } from '@/api/sessions'
import { storeToRefs } from 'pinia'
import SourceCard from './components/SourceCard.vue'
import ThoughtProcess from './components/ThoughtProcess.vue'
import DocumentPreview from './components/DocumentPreview.vue'
import SessionDrawer from './components/SessionDrawer.vue'

const router = useRouter()

// 会话管理 Store
const chatStore = useChatStore()
const { currentSessionId } = storeToRefs(chatStore)

// 抽屉/标题编辑状态
const drawerVisible = ref(false)
const titleEditing = ref(false)
const titleDraft = ref('')
const titleInputRef = ref<HTMLInputElement | null>(null)

function toggleDrawer() {
  drawerVisible.value = !drawerVisible.value
  if (drawerVisible.value) {
    chatStore.fetchSessions()
  }
}

async function handleSwitchSession(sessionId: string) {
  try {
    isLoading.value = true
    const res = await getSessionDetail(sessionId)
    const session = (res as any).data
    // 加载消息列表到聊天区域
    messages.value = session.messages || []
    currentAnswer.value = ''
    errorMessage.value = ''
  } catch (e) {
    console.error('加载会话失败', e)
    ElMessage.error('加载会话失败')
  } finally {
    isLoading.value = false
  }
}

function handleNewChat() {
  messages.value = []
  currentAnswer.value = ''
  errorMessage.value = ''
  thoughts.value = []
  sources.value = []
  chatStore.clearCurrentSession()
}

function startTitleEdit() {
  if (!currentSessionId.value) return
  titleDraft.value = chatStore.currentSession?.title || ''
  titleEditing.value = true
  nextTick(() => titleInputRef.value?.focus())
}

async function saveTitle() {
  if (!currentSessionId.value || !titleDraft.value.trim()) {
    titleEditing.value = false
    return
  }
  try {
    await chatStore.updateTitle(currentSessionId.value, titleDraft.value.trim(), 'manual')
    ElMessage.success('标题已更新')
  } catch {
    ElMessage.error('标题更新失败')
  }
  titleEditing.value = false
}

function cancelTitleEdit() {
  titleEditing.value = false
}

// 会话状态
const sessionId = ref(crypto.randomUUID())
const clientMsgId = ref(crypto.randomUUID())

// 按钮禁用态
const deepThinkingDisabled = ref(true)
const internetSearchDisabled = ref(true)

// 用户输入
const question = ref('')
const lastQuestion = ref('')

// SSE 状态
const sources = ref<Source[]>([])
const thoughts = ref<string[]>([])
const currentAnswer = ref('')
const isLoading = ref(false)
const errorMessage = ref('')

// 重连状态
const MAX_RETRIES = 3
const reconnectDelay = ref(0)
const reconnecting = ref(false)

// 全局超时
const globalTimeout = ref<ReturnType<typeof setTimeout>>()
// 心跳超时检测（18s 无事件 → 判定断连）
let heartbeatTimer: ReturnType<typeof setTimeout> | null = null

// 视图元素
const messagesContainer = ref<HTMLElement | null>(null)
const showReportDialog = ref(false)
const currentReport = ref<{ title?: string; publish_time?: string; source?: string; content?: string } | null>(null)
const showDocPreview = ref(false)
const previewDocUrl = ref('')
const previewDocTitle = ref('')

// 数据源
const availableSources = ['粮信网', '我的钢铁', '中华粮网', 'USDA', '气象数据']
const selectedSources = ref<string[]>([])

// 建议问题
const suggestions = [
  '今天玉米价格是多少？',
  '最近小麦市场行情如何？',
  '大豆进口情况分析',
  'USDA 最新供需报告'
]

// ---- 心跳计时器 ----
function resetHeartbeatTimer() {
  if (heartbeatTimer) clearTimeout(heartbeatTimer)
  // 仅在请求未完成时启动心跳超时检测，避免流结束后误触发重连
  if (!isLoading.value) return
  heartbeatTimer = setTimeout(() => {
    // 18s 无任何 SSE 事件 → 主动触发重连（Python 心跳 12s，留 6s 缓冲）
    if (!isLoading.value) return // 再次检查，防止关闭窗口后触发
    reconnecting.value = true
    reconnectDelay.value = 2000
  }, 18000)
}

function clearHeartbeatTimer() {
  if (heartbeatTimer) {
    clearTimeout(heartbeatTimer)
    heartbeatTimer = null
  }
}

onMounted(() => {
  sessionId.value = crypto.randomUUID()
})

onUnmounted(() => {
  if (globalTimeout.value) clearTimeout(globalTimeout.value)
  if (heartbeatTimer) clearTimeout(heartbeatTimer)
})

// ---- UI 功能 ----

// 切换数据源筛选
const toggleSource = (source: string) => {
  const index = selectedSources.value.indexOf(source)
  if (index === -1) {
    selectedSources.value.push(source)
  } else {
    selectedSources.value.splice(index, 1)
  }
}

// 返回
const goBack = () => {
  router.push('/dashboard')
}

// 跳转到历史记录页面
const goToHistory = () => {
  router.push('/ai-chat/history')
}

// 滚动到底部
const scrollToBottom = () => {
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

// 预览文档或跳转链接
const previewDocument = (url: string, title?: string) => {
  if (!url) return

  if (url.startsWith('http://') || url.startsWith('https://')) {
    window.open(url, '_blank')
  } else {
    previewDocUrl.value = url
    previewDocTitle.value = title || '文档预览'
    showDocPreview.value = true
  }
}

// ---- SSE 流式对话 ----

/** 生成匿名用户标识（会话生命周期内固定） */
const anonymousUserId = 'anon-' + crypto.randomUUID().slice(0, 8)

async function askQuestion(q: string) {
  if (!q.trim() || isLoading.value) return
  // 前端校验
  if (q.trim().length > 500) {
    ElMessage.warning('问题长度不能超过 500 字符')
    return
  }

  lastQuestion.value = q.trim()
  clientMsgId.value = crypto.randomUUID()
  isLoading.value = true
  sources.value = []
  thoughts.value = []
  currentAnswer.value = ''
  errorMessage.value = ''
  reconnecting.value = false
  question.value = ''

  await nextTick()
  scrollToBottom()

  // 全局超时 45s
  globalTimeout.value = setTimeout(() => {
    isLoading.value = false
    errorMessage.value = errorMessage.value || '请求超时，请重试'
    reconnecting.value = false
  }, 45000)

  await startSSEStream(q.trim())
}

// ★ SSE 流读取（含自动重连）
async function startSSEStream(q: string, retryCount = 0) {
  resetHeartbeatTimer()

  try {
    const stream = await aiChatApiV2.chatV2Stream({
      sessionId: sessionId.value,
      clientMsgId: clientMsgId.value,
      question: q,
      userId: anonymousUserId,
    })
    if (!stream) throw new Error('Stream error')

    // 重连成功 → 清空错误提示
    if (retryCount > 0) {
      errorMessage.value = ''
      reconnecting.value = false
      ElMessage.success('连接已恢复')
    }

    const reader = stream.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let currentEventType = ''
    let currentDataLines: string[] = []
    let nonSSELines: string[] = []

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      resetHeartbeatTimer()        // ★ 有数据到达 → 重置心跳超时
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (line.startsWith('event: ')) {
          flushSSEEvent(currentEventType, currentDataLines)
          currentEventType = line.slice(7).trim()
          currentDataLines = []
        } else if (line.startsWith('data: ')) {
          currentDataLines.push(line.slice(6))   // ★ 多行 data 累加
        } else if (line === '' && currentEventType) {
          // ★ 空行 = SSE 事件结束
          const evt = currentEventType
          const data = currentDataLines
          flushSSEEvent(evt, data)
          currentEventType = ''
          currentDataLines = []
        } else if (line.trim()) {
          nonSSELines.push(line.trim())
        }
      }
    }
    // 流结束时处理残留 SSE 事件
    flushSSEEvent(currentEventType, currentDataLines)

    // 回退解析：SSE 未产生事件且存在非 SSE 行，尝试作为 JSON 错误解析
    if (thoughts.value.length === 0 && !currentAnswer.value && nonSSELines.length > 0) {
      const raw = nonSSELines.join('')
      try {
        const fallback = JSON.parse(raw)
        if (fallback.type === 'error') {
          isLoading.value = false
          clearHeartbeatTimer()
          errorMessage.value = fallback.message || '请求失败'
          clearTimeout(globalTimeout.value)
        }
      } catch { /* JSON 也解析失败，静默忽略 */ }
    }
  } catch (err) {
    console.error('[SSE] stream error:', err)
    // ★ 自动重连（指数退避，最多 3 次）
    if (retryCount < MAX_RETRIES && !reconnecting.value) {
      reconnecting.value = true
      reconnectDelay.value = Math.min(1000 * Math.pow(2, retryCount), 8000)
      errorMessage.value = `连接中断，${reconnectDelay.value / 1000}s 后自动重连... (${retryCount + 1}/${MAX_RETRIES})`
      await new Promise(r => setTimeout(r, reconnectDelay.value))
      if (isLoading.value) {
        await startSSEStream(q, retryCount + 1)
        return
      }
    } else {
      isLoading.value = false
      reconnecting.value = false
      clearHeartbeatTimer()
      errorMessage.value = '网络连接已断开，请稍后重试'
    }
  }
}

// ★ SSE 事件分发函数（支持 event/data/空行三行协议 + 多行 data 累加）
function flushSSEEvent(eventType: string, dataLines: string[]) {
  if (!eventType || dataLines.length === 0) return
  try {
    const data: SSEEvent = JSON.parse(dataLines.join(''))
    switch (eventType) {
      case 'thought':
        thoughts.value.push(data.content || '')
        break
      case 'source':
        if (data.sources) sources.value = data.sources
        break
      case 'content':
        currentAnswer.value += data.content || ''
        break
      case 'done':
        isLoading.value = false
        clearHeartbeatTimer()
        clearTimeout(globalTimeout.value)
        break
      case 'error':
        isLoading.value = false
        clearHeartbeatTimer()
        errorMessage.value = data.message || '服务异常'
        clearTimeout(globalTimeout.value)
        break
      case 'heartbeat':
        // ★ 心跳保活 — 不渲染 UI，resetHeartbeatTimer() 已在外层循环调用
        break
      case 'abort':
        isLoading.value = false
        clearHeartbeatTimer()
        currentAnswer.value += data.partial_content || ''
        clearTimeout(globalTimeout.value)
        break
    }
    nextTick()
    scrollToBottom()
  } catch (e) {
    // JSON 解析失败 → 忽略异常行
  }
}
</script>

<style scoped>
.ai-chat-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #0f1419 0%, #1a1f2e 100%);
}

/* 顶部导航 */
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  background: rgba(255, 255, 255, 0.02);
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.sidebar-toggle {
  display: flex; align-items: center; justify-content: center;
  width: 36px; height: 36px;
  background: rgba(255,255,255,0.05);
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 8px;
  color: #8b949e;
  cursor: pointer;
  transition: all 0.2s;
}
.sidebar-toggle:hover {
  background: rgba(255,255,255,0.08);
  color: #e6edf3;
}

.title-text.clickable { cursor: pointer; }
.title-text.clickable:hover { color: #f5c87a; }

.title-input {
  background: rgba(255,255,255,0.08);
  border: 1px solid rgba(245,200,122,0.4);
  border-radius: 6px;
  padding: 4px 10px;
  font-size: 16px;
  color: #f5c87a;
  outline: none;
  width: 240px;
}
.title-input:focus {
  box-shadow: 0 0 0 2px rgba(245,200,122,0.2);
}

.history-btn {
  display: flex; align-items: center; gap: 6px;
  padding: 6px 12px;
  background: rgba(255,255,255,0.05);
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 8px;
  color: #8b949e;
  cursor: pointer;
  font-size: 13px;
  margin-right: 16px;
  transition: all 0.2s;
}
.history-btn:hover {
  background: rgba(255,255,255,0.08);
  color: #e6edf3;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  color: #8b949e;
  cursor: pointer;
  transition: all 0.2s;
}

.back-btn:hover {
  background: rgba(255, 255, 255, 0.08);
  color: #e6edf3;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.title-icon {
  display: flex;
  align-items: center;
  justify-content: center;
}

.title-text {
  font-size: 18px;
  font-weight: 600;
  color: #f5c87a;
  letter-spacing: 0.5px;
}

.header-right {
  display: flex;
  align-items: center;
}

.data-sources {
  display: flex;
  align-items: center;
  gap: 12px;
}

.sources-label {
  font-size: 12px;
  color: #6e7681;
}

.source-tags {
  display: flex;
  gap: 8px;
}

.source-tag {
  padding: 4px 10px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  font-size: 12px;
  color: #8b949e;
  cursor: pointer;
  transition: all 0.2s;
}

.source-tag:hover {
  background: rgba(255, 255, 255, 0.08);
}

.source-tag.active {
  background: rgba(245, 200, 122, 0.15);
  border-color: rgba(245, 200, 122, 0.3);
  color: #f5c87a;
}

/* 主内容区 */
.chat-content {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

/* 欢迎区 */
.welcome-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  text-align: center;
}

.welcome-icon {
  margin-bottom: 24px;
  animation: pulse 3s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 0.8; transform: scale(1); }
  50% { opacity: 1; transform: scale(1.05); }
}

.welcome-title {
  font-size: 24px;
  font-weight: 600;
  color: #f5f7fa;
  margin: 0 0 12px 0;
}

.welcome-desc {
  font-size: 14px;
  color: #6e7681;
  margin: 0 0 32px 0;
  max-width: 400px;
}

.suggestion-chips {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 12px;
  max-width: 600px;
}

.suggestion-chip {
  padding: 10px 18px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 20px;
  font-size: 13px;
  color: #8b949e;
  cursor: pointer;
  transition: all 0.2s;
}

.suggestion-chip:hover {
  background: rgba(245, 200, 122, 0.1);
  border-color: rgba(245, 200, 122, 0.3);
  color: #f5c87a;
}

/* 消息列表 */
.messages-container {
  max-width: 800px;
  margin: 0 auto;
}

.message-item {
  display: flex;
  gap: 16px;
  margin-bottom: 24px;
}

.message-item.user {
  flex-direction: row-reverse;
}

.message-avatar {
  flex-shrink: 0;
}

.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.user-avatar {
  background: linear-gradient(135deg, #58a6ff 0%, #1f6feb 100%);
  color: #fff;
}

.ai-avatar {
  background: linear-gradient(135deg, #f5c87a 0%, #d4a574 100%);
  color: #1a1f2e;
}

.message-body {
  flex: 1;
  max-width: 70%;
}

.message-item.user .message-body {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.message-content {
  padding: 14px 18px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 16px;
  border-top-left-radius: 4px;
  font-size: 14px;
  line-height: 1.6;
  color: #e6edf3;
}

.message-item.user .message-content {
  background: linear-gradient(135deg, #58a6ff 0%, #1f6feb 100%);
  border-color: transparent;
  border-top-left-radius: 16px;
  border-top-right-radius: 4px;
}

.ai-content {
  margin-top: 12px;
}

/* 来源网格 */
.sources-grid {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
}

/* 错误消息 */
.error-message {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: rgba(248, 81, 73, 0.1);
  border: 1px solid rgba(248, 81, 73, 0.2);
  border-radius: 10px;
  font-size: 13px;
  color: #f85149;
  margin-top: 8px;
}

/* 重连提示 */
.reconnecting-hint {
  padding: 8px 12px;
  font-size: 12px;
  color: #d29922;
  background: rgba(210, 153, 34, 0.1);
  border: 1px solid rgba(210, 153, 34, 0.2);
  border-radius: 8px;
  margin-top: 8px;
  animation: blink 1.5s ease-in-out infinite;
}

@keyframes blink {
  0%, 100% { opacity: 0.6; }
  50% { opacity: 1; }
}

/* Loading 动画 */
.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 14px 18px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 16px;
  border-top-left-radius: 4px;
}

.typing-indicator span {
  width: 8px;
  height: 8px;
  background: #6e7681;
  border-radius: 50%;
  animation: typing 1.4s ease-in-out infinite;
}

.typing-indicator span:nth-child(2) { animation-delay: 0.2s; }
.typing-indicator span:nth-child(3) { animation-delay: 0.4s; }

@keyframes typing {
  0%, 100% { opacity: 0.4; transform: translateY(0); }
  50% { opacity: 1; transform: translateY(-4px); }
}

/* 底部输入区 */
.chat-input-area {
  padding: 16px 24px 24px;
  background: rgba(255, 255, 255, 0.02);
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}

.input-options {
  display: flex;
  align-items: center;
  gap: 12px;
  max-width: 800px;
  margin: 0 auto 12px auto;
}

.deep-thinking-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 20px;
  font-size: 13px;
  color: #484f58;
  cursor: not-allowed;
  opacity: 0.5;
  transition: all 0.25s ease;
}

.deep-thinking-btn:hover {
  background: rgba(255, 255, 255, 0.03);
  border-color: rgba(255, 255, 255, 0.08);
  color: #484f58;
}

.internet-search-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 16px;
  color: #484f58;
  font-size: 12px;
  cursor: not-allowed;
  opacity: 0.5;
  transition: all 0.2s;
}

.internet-search-btn:hover {
  background: rgba(255, 255, 255, 0.03);
  color: #484f58;
}

.internet-search-btn .btn-icon {
  font-size: 14px;
}

.input-wrapper {
  display: flex;
  align-items: center;
  gap: 12px;
  max-width: 800px;
  margin: 0 auto;
  padding: 12px 16px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 24px;
  transition: all 0.2s;
}

.input-wrapper:focus-within {
  border-color: rgba(245, 200, 122, 0.4);
  box-shadow: 0 0 0 4px rgba(245, 200, 122, 0.1);
}

.chat-input {
  flex: 1;
  background: none;
  border: none;
  outline: none;
  font-size: 14px;
  color: #e6edf3;
}

.chat-input::placeholder {
  color: #6e7681;
}

.send-btn {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.05);
  border: none;
  border-radius: 50%;
  color: #6e7681;
  cursor: pointer;
  transition: all 0.2s;
}

.send-btn.active {
  background: linear-gradient(135deg, #f5c87a 0%, #d4a574 100%);
  color: #1a1f2e;
}

.send-btn.active:hover {
  transform: scale(1.05);
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.input-hint {
  text-align: center;
  margin-top: 12px;
  font-size: 11px;
  color: #4a5568;
}

/* 报告弹窗 */
.report-content {
  background: #161b22;
  border-radius: 8px;
  padding: 20px;
}

.report-meta {
  display: flex;
  gap: 20px;
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #8b949e;
}

.report-body {
  font-size: 14px;
  line-height: 1.8;
  color: #c9d1d9;
}
</style>
