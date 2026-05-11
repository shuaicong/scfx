import request from './index'

export const knowledgeCategoryApi = {
  getCategories: (knowledgeId: number) =>
    request.get<number[]>(`/knowledge/${knowledgeId}/categories`),

  assign: (knowledgeId: number, categoryIds: number[], operator?: string) =>
    request.post(`/knowledge/${knowledgeId}/categories`, { categoryIds, operator }),

  remove: (knowledgeId: number, categoryId: number) =>
    request.delete(`/knowledge/${knowledgeId}/categories/${categoryId}`),

  replace: (knowledgeId: number, categoryIds: number[], operator?: string) =>
    request.put(`/knowledge/${knowledgeId}/categories/replace`, { categoryIds, operator }),

  getUncategorized: () => request.get<number[]>('/knowledge/uncategorized'),

  getMoveHistory: (knowledgeId: number) =>
    request.get(`/knowledge/${knowledgeId}/move-history`),
}