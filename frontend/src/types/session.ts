// AI 问答会话类型定义
export interface ChatSession {
  id: string
  title: string
  title_source: 'default' | 'auto' | 'manual'
  message_count: number
  last_message: string
  updated_at: string
}

export interface SessionListResponse {
  records: ChatSession[]
  total: number
  page: number
  size: number
}

export interface SessionListParams {
  page: number
  size: number
  keyword?: string
  start?: string
  end?: string
}

/** 历史消息条目 */
export interface HistoryMessage {
  role: 'user' | 'assistant'
  content: string
  message_id: number
  group_id: number
  seq: number
}
