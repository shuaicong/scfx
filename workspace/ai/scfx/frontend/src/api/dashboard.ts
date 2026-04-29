import request from './index'

export const getDashboard = () => request.get('/dashboard')

export const getStatistics = (period: string) => request.get('/dashboard/statistics', { params: { period } })

export const triggerCollection = () => request.post('/dashboard/collect')

export const getTasks = (params: any) => request.get('/tasks', { params })

export const getTaskById = (id: number) => request.get(`/tasks/${id}`)

export const createTask = (data: any) => request.post('/tasks', data)

export const updateTaskStatus = (id: number, status: string) => request.put(`/tasks/${id}/status`, null, { params: { status } })

export const getLogs = (params: any) => request.get('/logs', { params })

export const getLogStats = () => request.get('/logs/stats')

export const getAlerts = (params: any) => request.get('/alerts', { params })

export const getAlertStats = () => request.get('/alerts/stats')

export const resolveAlert = (id: number, resolvedBy?: string) => request.put(`/alerts/${id}/resolve`, null, { params: { resolvedBy } })

export const getReports = (params: any) => request.get('/reports', { params })

export const getReportById = (id: number) => request.get(`/reports/${id}`)

export const getRecentReports = (limit: number) => request.get('/reports/recent', { params: { limit } })

export const collectLiangxinwang = () => request.post('/collection/liangxinwang')

export const getCollectionStatus = () => request.get('/collection/status')
