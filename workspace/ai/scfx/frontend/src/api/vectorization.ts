import request from './index'

export interface VectorizationTask {
  id?: number
  categoryId: number
  batchSize: number
  status: string
  totalCount: number
  processedCount: number
  failedCount: number
  priority: number
  triggerType: string
  triggerSource: string
  failedSample?: string
  createdAt?: string
  completedAt?: string
}

export interface VectorizationStats {
  pending: number
  processing: number
  vectorized: number
  failed: number
  total: number
}

export const vectorizationApi = {
  getStats: () =>
    request.get<{ data: VectorizationStats }>('/vectorization/stats'),

  getTasks: (page: number = 1, size: number = 20) =>
    request.get<{ data: VectorizationTask[] }>('/vectorization/tasks', { params: { page, size } }),

  getConfig: () =>
    request.get<{ data: { enabled: boolean; mode: string } }>('/vectorization/config'),

  trigger: (categoryId: number) =>
    request.post(`/vectorization/trigger/${categoryId}`),

  triggerBatch: (categoryIds: number[]) =>
    request.post('/vectorization/trigger/batch', categoryIds),

  retry: (knowledgeId: number) =>
    request.post(`/vectorization/retry/${knowledgeId}`),

  taskLogs: (taskId: number) =>
    request.get<{ data: Array<{ knowledgeId: number; title: string; status: string; errorMessage: string; vectorId: string; processTimeMs: number; retryCount: number }> }>(`/vectorization/tasks/${taskId}/logs`),
}