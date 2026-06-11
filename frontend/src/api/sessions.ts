// AI 问答会话管理 API
import request from './index'
import type { ChatSession, SessionListResponse, SessionListParams } from '@/types/session'

/** 获取会话列表（分页+搜索） */
export function getSessions(params: SessionListParams) {
  return request<SessionListResponse>({
    url: '/ai-chat/sessions',
    method: 'get',
    params
  })
}

/** 获取会话详情 */
export function getSessionDetail(id: string) {
  return request<ChatSession>({
    url: `/ai-chat/sessions/${id}`,
    method: 'get'
  })
}

/** 更新会话标题 */
export function updateSessionTitle(id: string, title: string, source: 'default' | 'auto' | 'manual') {
  return request({
    url: `/ai-chat/sessions/${id}/title`,
    method: 'patch',
    data: { title, source }
  })
}

/** 批量软删除会话 */
export function batchDeleteSessions(ids: string[]) {
  return request({
    url: '/ai-chat/sessions',
    method: 'delete',
    data: { ids }
  })
}
