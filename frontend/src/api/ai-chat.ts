import axios from 'axios'
import request from './index'

// ==================== AI Chat 类型定义 ====================

export interface ChatMessage {
  role: 'user' | 'assistant' | 'system'
  content: string
  timestamp: string
  references?: ChatReference[]
}

export interface ChatReference {
  report_id: string
  title: string
  source: string
  publish_time?: string
  similarity?: number
  url?: string
}

export interface SearchResult {
  id: number
  title: string
  content: string
  source: string
  score: number
  url?: string
  timestamp?: string
  publish_time?: string
}

export interface ChatStreamResponse {
  type: 'text' | 'sources' | 'source' | 'done' | 'error' | 'thinking'
  content?: string
  sources?: Source[]
  error?: string
}

export interface Source {
  index: number
  title: string
  source: string
  date: string
  content: string
  relevance: number
}

export interface ChatStreamParams {
  question: string
  top_k?: number
  source_filter?: string[]
  deep_thinking?: boolean
  use_internet?: boolean
}

// ==================== AI Chat API ====================

// 创建独立实例，跳过响应拦截器的transform
const chatRequest = axios.create({
  baseURL: '/api',
  timeout: 30000
})

export const aiChatApi = {
  // 流式对话 - 使用原生 fetch 避免 axios 拦截器干扰
  chatStream: async (params: ChatStreamParams): Promise<ReadableStream<Uint8Array>> => {
    const response = await fetch('/api/ai-chat/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(params)
    })
    return response.body as ReadableStream<Uint8Array>
  },

  // 非流式对话
  chat: (params: ChatStreamParams) =>
    request.post('/ai-chat/chat', params),

  // 获取聊天历史
  history: (params: { page?: number; size?: number }) =>
    request.get('/ai-chat/history', { params }),

  // 清除聊天历史
  clearHistory: () =>
    request.delete('/ai-chat/history'),

  // 搜索知识库
  searchKnowledge: (query: string, topK: number = 5) =>
    request.get('/ai-chat/search', { params: { query, top_k: topK } }),
}

// ==================== AI Chat V2 类型定义 ====================

export interface ChatV2StreamParams {
  sessionId: string
  clientMsgId: string
  question: string
  userId?: string
}

export interface SSEEvent {
  type: 'thought' | 'source' | 'content' | 'done' | 'error' | 'abort'
  content?: string
  sources?: Source[]
  code?: string
  message?: string
  token_used?: number
  compressed?: boolean
  seq?: number
  partial_content?: string
  retry_after?: number
  request_id?: string
}

// ==================== AI Chat V2 API ====================

export const aiChatApiV2 = {
  chatV2Stream: async (params: ChatV2StreamParams): Promise<ReadableStream<Uint8Array>> => {
    const response = await fetch('/api/ai-chat/v2/stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        session_id: params.sessionId,
        client_msg_id: params.clientMsgId,
        question: params.question,
        user_id: params.userId || '',
      }),
    })
    return response.body as ReadableStream<Uint8Array>
  },

  closeSession: (sessionId: string) =>
    request.post('/ai-chat/session/close', { sessionId }),
}