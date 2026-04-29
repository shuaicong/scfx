import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000
})

request.interceptors.request.use(
  config => {
    config.headers['Accept'] = 'application/json; charset=utf-8'
    config.headers['Content-Type'] = 'application/json; charset=utf-8'
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

request.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  error => {
    ElMessage.error(error.message || '网络错误')
    return Promise.reject(error)
  }
)

export default request

// ==================== SDK 管理 API ====================

export interface CollectorInfo {
  id?: number
  collectorName: string
  sdkVersion?: string
  source?: string
  subject?: string
  collType?: string
  collObject?: string
  description?: string
  status?: 'online' | 'offline' | 'disabled'
  lastHeartbeat?: string
  registeredAt?: string
  instanceCount?: number
}

export interface CollectorStats {
  total: number
  online: number
  offline: number
  disabled: number
}

export const collectorApi = {
  // 获取采集器列表
  list: (params: { page?: number; size?: number; status?: string; source?: string }) =>
    request.get<{ data: CollectorInfo[] }>('/collector/all', { params }),

  // 获取采集器详情
  getById: (id: number) =>
    request.get<{ data: CollectorInfo }>(`/collector/${id}`),

  // 更新采集器
  update: (id: number, data: CollectorInfo) =>
    request.put<{ data: CollectorInfo }>(`/collector/${id}`, data),

  // 启用采集器
  enable: (id: number) =>
    request.put(`/collector/${id}/enable`),

  // 禁用采集器
  disable: (id: number) =>
    request.put(`/collector/${id}/disable`),

  // 删除采集器
  delete: (id: number) =>
    request.delete(`/collector/${id}`),

  // 获取统计信息
  stats: () =>
    request.get<{ data: CollectorStats }>('/collector/stats'),

  // 获取在线采集器
  online: () =>
    request.get<{ data: CollectorInfo[] }>('/collector/online'),
}

// ==================== 采集脚本管理 API ====================

export interface CollectionScript {
  id?: number
  scriptName: string
  description?: string
  scriptPath?: string
  scriptContent?: string
  source?: string
  subject?: string
  collType?: string
  collObject?: string
  status?: 'enabled' | 'disabled'
  reportIntervalSeconds?: number
  triggerType?: 'manual' | 'single' | 'once' | 'repeat' | 'cron'
  triggerConfig?: string
  cronExpression?: string
  repeatType?: 'daily' | 'weekly' | 'monthly'
  repeatConfig?: string
  startTime?: string
  endTime?: string
  lastExecutionTime?: string
  nextExecutionTime?: string
  executionCount?: number
  successCount?: number
  failedCount?: number
  createdBy?: string
  createdAt?: string
  updatedAt?: string
  // 扩展字段
  repeatTime?: string
  weeklyDays?: string
  monthlyDay?: number
  monthlyLastDay?: boolean
  endType?: 'never' | 'date' | 'count'
  repeatCount?: number
  currentVersion?: number
}

export interface ScriptStats {
  total: number
  enabled: number
  disabled: number
}

export const scriptApi = {
  // 获取脚本列表
  list: (params: { page?: number; size?: number; status?: string; source?: string }) =>
    request.get<{ data: any }>('/scripts', { params }),

  // 获取脚本详情
  getById: (id: number) =>
    request.get<{ data: CollectionScript }>(`/scripts/${id}`),

  // 获取脚本内容（从文件读取）
  getContent: (id: number) =>
    request.get<{ data: string }>(`/scripts/${id}/content`),

  // 创建脚本（简化版）
  create: (scriptName: string, description: string, scriptContent: string) =>
    request.post<{ data: CollectionScript }>('/scripts', { scriptName, description, scriptContent }),

  // 上传脚本文件
  upload: (scriptName: string, description: string, file: File) => {
    const formData = new FormData()
    formData.append('scriptName', scriptName)
    formData.append('description', description)
    formData.append('file', file)
    return request.post<{ data: CollectionScript }>('/scripts/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },

  // 更新脚本
  update: (id: number, data: CollectionScript) =>
    request.put<{ data: CollectionScript }>(`/scripts/${id}`, data),

  // 更新脚本内容（同时更新文件）
  updateContent: (id: number, scriptContent: string) =>
    request.put<{ data: CollectionScript }>(`/scripts/${id}/content`, { scriptContent }),

  // 删除脚本
  delete: (id: number) =>
    request.delete(`/scripts/${id}`),

  // 启用脚本
  enable: (id: number) =>
    request.put(`/scripts/${id}/enable`),

  // 禁用脚本
  disable: (id: number) =>
    request.put(`/scripts/${id}/disable`),

  // 执行脚本
  execute: (id: number) =>
    request.post<{ data: any }>(`/scripts/${id}/execute`),

  // 获取统计信息
  stats: () =>
    request.get<{ data: ScriptStats }>('/scripts/stats'),

  // 获取所有启用的脚本
  enabled: () =>
    request.get<{ data: CollectionScript[] }>('/scripts/enabled'),

  // 验证Cron表达式
  validateCron: (cron: string) =>
    request.post<{ data: { valid: boolean } }>('/scripts/validate-cron', { cron }),
}

// ==================== 执行记录 API ====================

export interface TaskExecution {
  id?: number
  executionId: string
  scriptId: number
  versionId?: number
  triggerType: 'manual' | 'scheduled' | 'api'
  status: 'pending' | 'running' | 'success' | 'failed' | 'cancelled'
  startTime?: string
  endTime?: string
  durationMs?: number
  errorMessage?: string
  createdAt?: string
}

export interface ExecutionLog {
  id?: number
  executionId: string
  scriptId?: number
  level: 'DEBUG' | 'INFO' | 'WARN' | 'ERROR'
  message: string
  timestamp: string
}

export const executionApi = {
  execute: (scriptId: number) =>
    request.post<{ executionId: string }>(`/scripts/${scriptId}/execute`),

  list: (scriptId: number, params: { page?: number; size?: number }) =>
    request.get<any>(`/scripts/${scriptId}/executions`, { params }),

  get: (executionId: string) =>
    request.get<TaskExecution>(`/scripts/executions/${executionId}`),

  cancel: (executionId: string) =>
    request.post(`/scripts/executions/${executionId}/cancel`),

  logs: (executionId: string) =>
    request.get<ExecutionLog[]>(`/scripts/executions/${executionId}/logs`),
}

// ==================== 版本管理 API ====================

export interface ScriptVersion {
  id?: number
  scriptId: number
  versionNum: number
  scriptName?: string
  scriptContent?: string
  triggerType?: string
  repeatType?: string
  repeatTime?: string
  weeklyDays?: string
  monthlyDay?: number
  monthlyLastDay?: boolean
  cronExpression?: string
  endType?: string
  endTime?: string
  repeatCount?: number
  changeDescription: string
  createdBy?: string
  createdAt?: string
  isCurrent?: boolean
  executionCount?: number
  successCount?: number
  failedCount?: number
}

export const versionApi = {
  list: (scriptId: number) =>
    request.get<ScriptVersion[]>(`/scripts/${scriptId}/versions`),

  get: (scriptId: number, versionId: number) =>
    request.get<ScriptVersion>(`/scripts/${scriptId}/versions/${versionId}`),

  restore: (scriptId: number, versionId: number, changeDescription: string) =>
    request.post<CollectionScript>(
      `/scripts/${scriptId}/versions/${versionId}/restore`,
      null,
      { params: { changeDescription } }
    ),

  compare: (scriptId: number, versionId1: number, versionId2: number) =>
    request.get<{ v1: ScriptVersion; v2: ScriptVersion }>(
      `/scripts/${scriptId}/versions/compare`,
      { params: { versionId1, versionId2 } }
    ),
}

// ==================== Cron 校验 API ====================

export interface CronValidationResult {
  valid: boolean
  description?: string
  error?: string
  nextExecutions?: string[]
}

export const cronApi = {
  validate: (cron: string) =>
    request.post<{ data: CronValidationResult }>(
      '/scripts/validate-cron',
      { cron }
    ).then(res => res.data),
}
