import request from './index'

export const reportApi = {
  list: (params: { page?: number; size?: number; variety?: string; status?: string; keyword?: string }) =>
    request.get('/reports', { params }),

  create: (data: { templateId?: number; title: string }) =>
    request.post('/reports', data),

  get: (id: number) => request.get(`/reports/${id}`),

  delete: (id: number) => request.delete(`/reports/${id}`),

  generate: (id: number, data?: { dateRange?: string; instructions?: string }) =>
    request.post(`/reports/${id}/generate`, data || {}),

  generationStatus: (id: number) => request.get(`/reports/${id}/generation-status`),

  save: (id: number, data: { title?: string; richContent?: string; editorJson?: string; changeSummary?: string }) =>
    request.post(`/reports/${id}/save`, data),

  versions: (id: number) => request.get(`/reports/${id}/versions`),

  restore: (id: number, version: number) => request.post(`/reports/${id}/versions/${version}/restore`),

  export: (id: number, version?: number) =>
    request.post(`/reports/${id}/export`, null, { params: { version } }),

  download: (id: number, type: 'docx' | 'pdf') =>
    request.get(`/reports/${id}/download`, { params: { type }, responseType: 'blob' }),

  uploadImage: (file: File) => {
    const fd = new FormData()
    fd.append('file', file)
    return request.post('/reports/upload-image', fd, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },

  templateList: () => request.get('/reports/templates'),

  createTemplate: (data: any) => request.post('/reports/templates', data),

  getTemplate: (id: number) => request.get(`/reports/templates/${id}`),

  updateTemplate: (id: number, data: any) => request.put(`/reports/templates/${id}`, data),

  deleteTemplate: (id: number) => request.delete(`/reports/templates/${id}`),

  saveTemplateVersion: (id: number, data: any) => request.post(`/reports/templates/${id}/save`, data),

  templateVersions: (id: number) => request.get(`/reports/templates/${id}/versions`),

  restoreTemplate: (id: number, version: number) =>
    request.post(`/reports/templates/${id}/versions/${version}/restore`),
}
