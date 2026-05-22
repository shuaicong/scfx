import request from './index'

export const knowledgeApi = {
  list: (params: { page?: number; size?: number; sourceType?: string; vectorStatus?: string; categoryId?: number; executionId?: string }) =>
    request.get('/knowledge/list', { params }),

  getById: (id: number) =>
    request.get(`/knowledge/${id}`),

  update: (id: number, data: { title?: string; content?: string; sourceType?: string; author?: string }) =>
    request.put(`/knowledge/${id}`, data),

  delete: (id: number) =>
    request.delete(`/knowledge/${id}`),

  revectorize: (id: number) =>
    request.post(`/knowledge/${id}/revectorize`),

  upload: (formData: FormData) =>
    request.post('/knowledge/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    }),

  manualAdd: (data: { title: string; content: string; source?: string; author?: string; publishTime?: string }) =>
    request.post('/knowledge/manual', data),

  // 获取未分类知识数量
  getUncategorizedCount: () =>
    request.get('/knowledge/uncategorized/count'),

  // 获取指定分类的可视化数据（PCA 降维后 2D 坐标）
  getVisualization: (categoryId: number, params?: { page?: number; size?: number; sample?: boolean }) =>
    request.get<{
      points: Array<{ id: number; title: string; x: number; y: number; z: number; vectorStatus: string; vizStatus: string; contentType: string }>;
      total: number;
      page: number;
      size: number;
      sample: boolean;
      similarities: Record<number, Array<{ id: number; score: number }>>;
    }>(`/knowledge/${categoryId}/visualization`, { params }),

  // 手动触发 PCA 重算
  recomputeVisualization: (categoryId: number) =>
    request.post(`/knowledge/${categoryId}/visualization/recompute`),
}