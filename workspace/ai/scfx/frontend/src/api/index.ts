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
