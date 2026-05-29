import request from './index'

export interface AlertRecord {
  id: number
  alertType: string
  alertLevel: string
  alertTitle: string
  alertContent: string
  targetId?: string
  status: 'pending' | 'sent' | 'failed' | 'resolved'
  notifiedChannels?: string
  resolvedBy?: string
  resolvedAt?: string
  createdAt: string
}

export interface AlertRule {
  id?: number
  ruleName: string
  ruleType: 'CONTINUOUS_FAIL' | 'SERVICE_OFFLINE'
  condition: string
  enabled: number
  notifyChannels?: string
  notifyTarget?: string
  createdAt?: string
  updatedAt?: string
}

export interface AlertStats {
  critical: number
  error: number
  warning: number
  info: number
}

export const alertApi = {
  // ======== Alert Records ========
  list: (params: { page?: number; size?: number; status?: string; level?: string }) =>
    request.get<{ data: { records: AlertRecord[]; total: number } }>('/alerts', { params }),

  stats: () =>
    request.get<{ data: AlertStats }>('/alerts/stats'),

  resolve: (id: number, resolvedBy?: string) =>
    request.put(`/alerts/${id}/resolve`, null, { params: { resolvedBy: resolvedBy || 'admin' } }),

  create: (data: { type: string; level: string; title: string; content: string }) =>
    request.post('/alerts', data),

  // ======== Alert Rules ========
  getRules: (params: { page?: number; size?: number; ruleType?: string; enabled?: number }) =>
    request.get<{ data: { records: AlertRule[]; total: number } }>('/alerts/rules', { params }),

  getRule: (id: number) =>
    request.get<{ data: AlertRule }>(`/alerts/rules/${id}`),

  createRule: (data: AlertRule) =>
    request.post<{ data: AlertRule }>('/alerts/rules', data),

  updateRule: (id: number, data: AlertRule) =>
    request.put<{ data: AlertRule }>(`/alerts/rules/${id}`, data),

  deleteRule: (id: number) =>
    request.delete(`/alerts/rules/${id}`),
}
