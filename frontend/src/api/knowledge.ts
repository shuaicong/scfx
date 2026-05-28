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

  // 上传文档（含分类绑定 + 幂等控制）
  uploadDocument: (
    file: File,
    categoryId: number,
    title: string,
    idempotentKey: string,
    onProgress?: (pct: number) => void
  ) => {
    const fd = new FormData()
    fd.append('file', file)
    fd.append('categoryId', String(categoryId))
    fd.append('title', title)
    fd.append('idempotentKey', idempotentKey)
    return request.post('/knowledge/upload', fd, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: (e: any) => {
        if (onProgress && e.total) onProgress(Math.round((e.loaded / e.total) * 100))
      },
    })
  },

  // .docx 文件下载 URL（供 docx-preview 渲染）
  getDownloadUrl: (id: number) => `/api/knowledge/${id}/file`,

  // 获取未分类知识数量
  getUncategorizedCount: () =>
    request.get('/knowledge/uncategorized/count'),

  // 获取指定分类的可视化数据（降维坐标）
  getVisualization: (categoryId: number, params?: { page?: number; size?: number; sample?: boolean; algorithm?: string }) =>
    request.get<{
      points: Array<{
        id: number; title: string; x: number; y: number; z: number;
        vectorStatus: string; vizStatus: string; contentType: string;
        isZeroVector?: boolean;
      }>;
      total: number;
      page: number;
      size: number;
      sample: boolean;
      hasData: boolean;
      version: number | null;
      similarities: Record<number, Array<{ id: number; score: number }>>;
    }>(`/knowledge/${categoryId}/visualization`, { params }),

  // 手动触发降维重算
  recomputeVisualization: (categoryId: number, data?: { algorithm?: string }) =>
    request.post<{ status: string }>(`/knowledge/${categoryId}/visualization/recompute`, data),

  // 获取单点详情（含向量预览 + 实时相似条目）
  getPointDetail: (knowledgeId: number, params?: { algorithm?: string }) =>
    request.get<{
      id: number; title: string; content: string; contentHtml: string;
      algorithm: string;
      coords: { x: number; y: number; z: number };
      vectorPreview: number[];
      fullVector?: number[];
      globalMaxAbs: number;
      vectorStatus: string; vizStatus: string; contentType: string;
      isZeroVector: boolean;
      neighbors: Array<{ id: number; title: string; score: number; isZeroVector: boolean }>;
    }>(`/knowledge/${knowledgeId}/point-detail`, { params }),

  // 获取文档切片列表
  getChunks: (knowledgeId: number) =>
    request.get(`/knowledge/${knowledgeId}/chunks`),
}