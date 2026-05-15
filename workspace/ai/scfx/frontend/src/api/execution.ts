import request from './index'

export interface ExecutionDetail {
  executionId: string
  scriptId: number
  versionId?: number
  scriptName?: string
  triggerType: 'manual' | 'scheduled' | 'api'
  status: 'pending' | 'running' | 'success' | 'failed' | 'cancelled'
  startTime?: string
  endTime?: string
  durationMs?: number
  errorMessage?: string
  collectedCount?: number
  source?: string
}

export interface LogEntry {
  id?: number
  executionId: string
  scriptId?: number
  level: 'DEBUG' | 'INFO' | 'WARN' | 'ERROR'
  message: string
  timestamp: string
}

export interface LogsResponse {
  logs: LogEntry[]
  status: string
}

export const executionApi = {
  getById: (executionId: string) =>
    request.get<{ data: ExecutionDetail }>(`/scripts/executions/${executionId}`),

  getLogs: (executionId: string, offset: number = 0) =>
    request.get<{ data: LogsResponse }>(`/scripts/executions/${executionId}/logs?offset=${offset}`),

  compare: (scriptId: number, executionId1: string, executionId2: string) =>
    request.get<any>(`/scripts/${scriptId}/executions/compare?executionId1=${executionId1}&executionId2=${executionId2}`),

  // Re-execute a script
  reExecute: (scriptId: number) =>
    request.post<{ executionId: string }>(`/scripts/${scriptId}/execute`),
}