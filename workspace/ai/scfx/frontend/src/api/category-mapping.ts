import request from './index'

export interface CategoryMapping {
  id?: number
  sourceType: string
  variety?: string
  reportType?: string
  categoryId: number
  priority: number
  enabled: number
  description?: string
  startTime?: string
  endTime?: string
  createdBy?: string
  updatedBy?: string
  matchCount?: number
  createdAt?: string
  updatedAt?: string
}

export interface Category {
  id: number
  name: string
  parentId?: number
  path?: string
}

export function listCategoryMappings() {
  return request.get<CategoryMapping[]>('/category-mapping/list')
}

export function createCategoryMapping(data: CategoryMapping) {
  return request.post('/category-mapping', data)
}

export function updateCategoryMapping(id: number, data: CategoryMapping) {
  return request.put(`/category-mapping/${id}`, data)
}

export function deleteCategoryMapping(id: number) {
  return request.delete(`/category-mapping/${id}`)
}

export function previewCategoryMapping(source: string, variety?: string, reportType?: string) {
  return request.get<Category[]>('/category-mapping/preview', {
    params: { source, variety, reportType }
  })
}

export function getCategoryDependency(categoryId: number) {
  return request.get(`/category-mapping/dependency/${categoryId}`)
}

export function getMappingsByCategory(categoryId: number) {
  return request.get(`/category-mapping/by-category/${categoryId}`)
}