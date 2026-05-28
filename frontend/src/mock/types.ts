export interface CollectionScript {
  id: number
  scriptName: string
  source: string
  status: 'enabled' | 'disabled'
  triggerType: 'once' | 'cycle' | 'cron'
  cronExpression?: string
  repeatType?: 'daily' | 'weekly' | 'monthly'
  repeatTime?: string
  weeklyDays?: string
  monthlyDay?: number
  monthlyLastDay?: boolean
  description?: string
  executionCount: number
  successCount: number
  failedCount: number
  nextExecutionTime?: string
  lastExecutionTime?: string
  createdAt: string
  updatedAt: string
  createdBy: string
  updatedBy: string
}

export interface Execution {
  id: number
  scriptId: number
  status: 'success' | 'failed' | 'running' | 'cancelled'
  triggerType: 'manual' | 'scheduled'
  version: string
  duration: number
  startTime: string
  endTime?: string
  collectedCount?: number
  errorMessage?: string
}

export interface ScriptVersion {
  id: number
  scriptId: number
  version: string
  scriptContent: string
  changeDescription: string
  author: string
  createdAt: string
  isCurrent: boolean
}