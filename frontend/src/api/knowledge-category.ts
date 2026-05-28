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

  // 批量移动知识到指定分类
  batchMove: (knowledgeIds: number[], targetCategoryId: number, operator?: string) =>
    request.post('/knowledge/batch/move', { knowledgeIds, targetCategoryId, operator }),

  // 获取分类下的知识数量
  getCountByCategory: (categoryId: number) =>
    request.get<{ data: { count: number } }>(`/knowledge/category/${categoryId}/count`),
}