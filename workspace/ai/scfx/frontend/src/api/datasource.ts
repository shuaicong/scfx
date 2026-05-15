import request from './index'

export interface DataSource {
  id?: number
  code: string
  name: string
  description?: string
  logoUrl?: string
  enabled: number
  sortOrder?: number
  config?: string
  lastHeartbeat?: string
  createdAt?: string
  updatedAt?: string
}

export interface ScriptVersion {
  id: number
  datasourceCode: string
  version: number
  filePath: string
  fileMd5: string
  fileSize: number
  isCurrent: number
  createdAt: string
  createdBy: string
}

export const datasourceApi = {
  list: () => request.get<DataSource[]>('/datasource'),

  getByCode: (code: string) => request.get<DataSource>(`/datasource/${code}`),

  create: (data: Partial<DataSource>) => request.post<DataSource>('/datasource', data),

  update: (code: string, data: Partial<DataSource>) =>
    request.put<DataSource>(`/datasource/${code}`, data),

  delete: (code: string) => request.delete(`/datasource/${code}`),

  enable: (code: string) => request.post(`/datasource/${code}/enable`),

  disable: (code: string) => request.post(`/datasource/${code}/disable`),

  uploadScript: (file: File, code: string, operator?: string) => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('code', code)
    if (operator) formData.append('operator', operator)
    return request.post<{ code: string; version: number; md5: string }>(
      '/datasource/upload-collector',
      formData,
      { headers: { 'Content-Type': 'multipart/form-data' } }
    )
  },

  getScriptContent: (code: string, version?: number) =>
    request.get<string>(`/datasource/${code}/script${version ? `?version=${version}` : ''}`),

  checkScriptExists: (code: string) =>
    request.get<{ exists: boolean }>(`/datasource/${code}/exists`),

  getVersions: (code: string) =>
    request.get<ScriptVersion[]>(`/datasource/${code}/versions`)
}