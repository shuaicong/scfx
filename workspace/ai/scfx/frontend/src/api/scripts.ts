import request from './index'

export const getScripts = (params: any) => request.get('/scripts', { params })

export const getScriptById = (id: number) => request.get(`/scripts/${id}`)

export const createScript = (data: any) => request.post('/scripts', data)

export const updateScript = (id: number, data: any) => request.put(`/scripts/${id}`, data)

export const deleteScript = (id: number) => request.delete(`/scripts/${id}`)

export const getScriptStats = () => request.get('/scripts/stats')

export const executeScript = (id: number) => request.post(`/scripts/${id}/execute`)

export const validateCron = (cron: string) => request.post('/scripts/validate-cron', { cron })

export const scriptApi = {
  getScripts,
  getScriptById,
  createScript,
  updateScript,
  deleteScript,
  getScriptStats,
  executeScript,
  validateCron
}