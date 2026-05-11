import request from './index'

export interface Category {
  id: number
  name: string
  icon: string
  color?: string
  description?: string
  parentId: number | null
  sortOrder: number
  pinned?: number
  lastOperatedBy?: string
  lastOperatedAt?: string
  permissionLevel?: string
  allowedUsers?: string
  activeSeasonStart?: string
  activeSeasonEnd?: string
  createdAt?: string
  updatedAt?: string
  deletedAt?: string
  version?: number
  knowledgeCount?: number
  children?: Category[]
}

export interface CategoryTreeResponse {
  code: number
  data: Category[]
  version: number
}

export interface TrashResponse {
  code: number
  data: Category[]
}

export const categoryApi = {
  tree: () => request.get<CategoryTreeResponse>('/category/tree'),
  version: () => request.get<{ code: number; data: { version: number } }>('/category/version'),
  search: (name: string) => request.get<Category[]>('/category/search', { params: { name } }),
  trash: () => request.get<TrashResponse>('/category/trash'),
  getById: (id: number) => request.get<Category>(`/category/${id}`),
  create: (data: Partial<Category>) => request.post<Category>('/category', data),
  update: (id: number, data: Partial<Category>) => request.put<Category>(`/category/${id}`, data),
  delete: (id: number, operator?: string) => request.delete(`/category/${id}`, { params: { operator } }),
  restore: (id: number, operator?: string) => request.post(`/category/${id}/restore`, {}, { params: { operator } }),
  permanentDelete: (id: number) => request.delete(`/category/${id}/permanent`),
  merge: (sourceId: number, targetId: number) => request.post('/category/merge', { sourceId, targetId }),
  preview: (id: number) => request.get<Category>(`/category/preview/${id}`),
  stats: () => request.get('/category/stats'),
  history: (id: number) => request.get(`/category/${id}/history`),
  recentHistory: () => request.get('/category/history/recent'),
  mergeSuggestions: () => request.get('/category/merge-suggestions'),
  export: () => request.get<{ code: number; data: { data: string } }>('/category/export'),
  import: (data: string, operator?: string) => request.post('/category/import', { data, operator }),
  hotAnalysis: () => request.get('/category/hot-analysis'),
}