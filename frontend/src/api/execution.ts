import request from './index'

export interface ExecutionDetail {
  executionId: string
  scriptId: number
  versionId?: number
  versionNum?: number
  scriptName?: string
  triggerType: 'manual' | 'scheduled' | 'api'
  status: 'pending' | 'running' | 'success' | 'failed' | 'cancelled'
  startTime?: string
  endTime?: string
  durationMs?: number
  errorMessage?: string
  collectedCount?: number
  source?: string
  // 执行统计
  totalCount?: number
  successCount?: number
  skipCount?: number
  errorCount?: number
  dataSizeMb?: number
  // 阶段耗时
  phaseLoginMs?: number
  phaseCrawlMs?: number
  phaseParseMs?: number
  phaseReportMs?: number
}

export interface LogEntry {
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

export interface LogsResponse {
  logs: LogEntry[]
  status: string
}

export interface ExecutionItem {
  id: number
  executionId: string
  knowledgeId?: number
  title?: string
  url?: string
  action: 'created' | 'skipped_duplicate' | 'skipped_existing' | 'error'
  errorMessage?: string
  createdAt: string
  // 内容统计（由服务端填充）
  contentLength?: number
  imageCount?: number
  contentPreview?: string
}

export const executionApi = {
  execute: (scriptId: number, params?: { date?: string }) =>
    request.post<{ executionId: string }>(`/scripts/${scriptId}/execute`, params),

  list: (scriptId: number, params: { page?: number; size?: number; status?: string; triggerType?: string }) =>
    request.get<any>(`/scripts/${scriptId}/executions`, { params }),

  get: (executionId: string) =>
    request.get<ExecutionDetail>(`/scripts/executions/${executionId}`),

  getById: (executionId: string) =>
    request.get<{ data: any }>(`/scripts/executions/${executionId}`),

  cancel: (executionId: string) =>
    request.post(`/scripts/executions/${executionId}/cancel`),

  logs: (executionId: string) =>
    request.get<LogEntry[]>(`/scripts/executions/${executionId}/logs`),

  getLogs: (executionId: string, offset: number = 0) =>
    request.get<{ data: LogsResponse }>(`/scripts/executions/${executionId}/logs?offset=${offset}`),

  compare: (scriptId: number, executionId1: string, executionId2: string) =>
    request.get<any>(`/scripts/${scriptId}/executions/compare?executionId1=${executionId1}&executionId2=${executionId2}`),

  // Re-execute a script
  reExecute: (scriptId: number) =>
    request.post<{ executionId: string }>(`/scripts/${scriptId}/execute`),

  // Get execution items
  items: (executionId: string) =>
    request.get<ExecutionItem[]>(`/scripts/executions/${executionId}/items`),
}