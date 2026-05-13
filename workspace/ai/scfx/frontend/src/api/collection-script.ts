import request from './index'

// 获取脚本内容
export function getScriptContent(id: number) {
  return request.get<{ data: string }>(`/scripts/${id}/content`)
}

// 保存脚本内容（创建新版本）
export function saveScript(id: number, data: {
  version: string
  content: string
  changelog: string
}) {
  return request.post(`/scripts/${id}/versions`, data)
}

// 校验脚本语法
export function validateScript(id: number, content: string) {
  return request.post(`/scripts/${id}/validate`, { content })
}
