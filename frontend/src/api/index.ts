import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
  paramsSerializer: (params) => {
    const searchParams = new URLSearchParams()
    Object.keys(params).forEach(key => {
      const value = params[key]
      if (value === undefined || value === null || value === '') return
      if (Array.isArray(value)) {
        value.forEach(v => searchParams.append(key, v))
      } else {
        searchParams.append(key, String(value))
      }
    })
    return searchParams.toString()
  }
})

request.interceptors.request.use(
  config => {
    config.headers['Accept'] = 'application/json; charset=utf-8'
    // Don't set Content-Type for FormData - browser will set correct multipart/form-data with boundary
    if (!(config.data instanceof FormData)) {
      config.headers['Content-Type'] = 'application/json; charset=utf-8'
    }
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
      // 409 业务提示不弹错误，让调用方处理
      if (res.code === 409) {
        return Promise.reject(new Error(res.message || '文件内容与最新版本相同'))
      }
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

  /** 最近执行状态（由服务端查询时填充） */
  lastExecutionStatus?: 'success' | 'failed' | 'running' | 'pending' | null

  /** 是否同步到知识库 */
  syncToKnowledgeBase?: boolean

  /** 关联分类ID */
  categoryId?: number
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

  // 创建脚本
  create: (data: {
    scriptName: string
    description?: string
    source?: string
    triggerType?: string
    cronExpression?: string
    startTime?: string
    endTime?: string
    endType?: string | null
    repeatCount?: number | null
    syncToKnowledgeBase?: boolean
    categoryId?: number | null
  }) =>
    request.post<{ data: CollectionScript }>('/scripts', data),

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
  versionNum?: number
  triggerType: 'manual' | 'scheduled' | 'api'
  status: 'pending' | 'running' | 'success' | 'failed' | 'cancelled'
  startTime?: string
  endTime?: string
  durationMs?: number
  errorMessage?: string
  createdAt?: string
  collectedCount?: number
  totalCount?: number
  successCount?: number
  skipCount?: number
  errorCount?: number
  dataSizeMb?: number
  phaseLoginMs?: number
  phaseCrawlMs?: number
  phaseParseMs?: number
  phaseReportMs?: number
}

export interface ExecutionLog {
  id?: number
  executionId: string
  scriptId?: number
  level: 'DEBUG' | 'INFO' | 'WARN' | 'ERROR'
  message: string
  timestamp: string
  phase?: 'login' | 'crawl' | 'parse' | 'report' | 'system'
  category?: 'progress' | 'data' | 'error' | 'metric' | 'checkpoint'
  elapsedMs?: number
}

// Re-export executionApi from execution module
export { executionApi } from './execution'
export type { ExecutionDetail, ExecutionItem, LogEntry, LogsResponse } from './execution'

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
