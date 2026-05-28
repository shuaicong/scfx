import type { CollectionScript, Execution, ScriptVersion } from './types'

export function generateMockScripts(count: number = 10): CollectionScript[] {
  const sources = ['liangxin', 'mysteel', 'chinagrain', 'usda', 'market']
  const triggerTypes: ('once' | 'cycle' | 'cron')[] = ['once', 'cycle', 'cron']
  const repeatTypes: ('daily' | 'weekly' | 'monthly')[] = ['daily', 'weekly', 'monthly']

  return Array.from({ length: count }, (_, i) => {
    const triggerType = triggerTypes[i % 3]
    const repeatType = triggerTypes[i % 3] === 'cycle' ? repeatTypes[i % 3] : undefined

    return {
      id: i + 1,
      scriptName: `采集任务 ${i + 1}`,
      source: sources[i % sources.length],
      status: i % 3 === 0 ? 'disabled' : 'enabled',
      triggerType,
      cronExpression: triggerType === 'cron' ? '0 0 8 * * *' : undefined,
      repeatType: repeatType,
      repeatTime: repeatType ? '08:00' : undefined,
      weeklyDays: repeatType === 'weekly' ? '1,3,5' : undefined,
      monthlyDay: repeatType === 'monthly' ? 1 : undefined,
      monthlyLastDay: repeatType === 'monthly' ? false : undefined,
      description: `这是任务 ${i + 1} 的描述`,
      executionCount: Math.floor(Math.random() * 200),
      successCount: Math.floor(Math.random() * 190),
      failedCount: Math.floor(Math.random() * 10),
      nextExecutionTime: triggerType !== 'once' ? '2026-05-08 08:00:00' : undefined,
      lastExecutionTime: i % 2 === 0 ? '2026-05-07 08:00:00' : undefined,
      createdAt: '2026-04-01 10:00:00',
      updatedAt: '2026-05-01 10:00:00',
      createdBy: 'admin',
      updatedBy: 'admin',
    }
  })
}

export function generateMockExecutions(scriptId: number, count: number = 20): Execution[] {
  const statuses: ('success' | 'failed' | 'running' | 'cancelled')[] = ['success', 'failed', 'running', 'cancelled']
  const triggerTypes: ('manual' | 'scheduled')[] = ['manual', 'scheduled']

  return Array.from({ length: count }, (_, i) => {
    const status = statuses[i % 4]
    const duration = Math.floor(Math.random() * 300) + 10
    const startTime = new Date(Date.now() - i * 3600000).toISOString().replace('T', ' ').substring(0, 19)

    return {
      id: i + 1,
      scriptId,
      status,
      triggerType: triggerTypes[i % 2],
      version: `v1.${count - i - 1}`,
      duration,
      startTime,
      endTime: status !== 'running' ? new Date(Date.now() - i * 3600000 + duration * 1000).toISOString().replace('T', ' ').substring(0, 19) : undefined,
      collectedCount: status === 'success' ? Math.floor(Math.random() * 1000) : undefined,
      errorMessage: status === 'failed' ? '采集失败：网络超时' : undefined,
    }
  })
}

export function generateMockVersions(scriptId: number, count: number = 5): ScriptVersion[] {
  return Array.from({ length: count }, (_, i) => ({
    id: i + 1,
    scriptId,
    version: `v1.${count - i - 1}`,
    scriptContent: `// Script content for version 1.${count - i - 1}\nfunction collect() {\n  console.log('采集数据...')\n}`,
    changeDescription: i === 0 ? '初始版本' : `更新版本 1.${count - i - 1}`,
    author: i % 2 === 0 ? 'admin' : 'user1',
    createdAt: new Date(Date.now() - i * 86400000).toISOString().replace('T', ' ').substring(0, 19),
    isCurrent: i === 0,
  }))
}