import request from './index'

export const knowledgeApi = {
  list: (params: { page?: number; size?: number; sourceType?: string; vectorStatus?: string }) =>
    request.get('/knowledge', { params }),

  getById: (id: number) =>
    request.get(`/knowledge/${id}`),

  delete: (id: number) =>
    request.delete(`/knowledge/${id}`),

  revectorize: (id: number) =>
    request.post(`/knowledge/${id}/revectorize`),

  stats: () =>
    request.get('/knowledge/stats'),

  search: (query: string, topK: number = 5) =>
    request.get('/knowledge/search', { params: { query, top_k: topK } }),

  upload: (formData: FormData) =>
    request.post('/knowledge/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    }),

  manualAdd: (data: { title: string; content: string; source?: string; author?: string; publishTime?: string }) =>
    request.post('/knowledge/manual', data),
}