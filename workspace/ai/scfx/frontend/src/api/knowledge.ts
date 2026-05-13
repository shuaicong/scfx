import request from './index'

export const knowledgeApi = {
  list: (params: { page?: number; size?: number; sourceType?: string; vectorStatus?: string; categoryId?: number }) =>
    request.get('/knowledge/list', { params }),

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

  // 按分类搜索知识
  searchInCategory: (categoryId: number, query: string) =>
    request.get(`/knowledge/category/${categoryId}/search`, { params: { query } }),

  upload: (formData: FormData) =>
    request.post('/knowledge/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    }),

  manualAdd: (data: { title: string; content: string; source?: string; author?: string; publishTime?: string }) =>
    request.post('/knowledge/manual', data),

  // 获取未分类知识数量
  getUncategorizedCount: () =>
    request.get('/knowledge/uncategorized/count'),
}