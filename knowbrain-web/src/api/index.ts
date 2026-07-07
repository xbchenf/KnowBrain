import axios from 'axios'

const api = axios.create({
  baseURL: '/api/v1',
  timeout: 60000,
  headers: { 'Content-Type': 'application/json' }
})

// 请求拦截 — 自动注入 Token
api.interceptors.request.use(config => {
  const token = localStorage.getItem('kb_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截 — 业务错误统一处理
api.interceptors.response.use(
  res => {
    // 兜底：即使 HTTP 200，body.code 非 200 也视为错误
    if (res.data?.code && res.data.code !== 200) {
      return Promise.reject({ response: { status: res.data.code, data: res.data } })
    }
    return res
  },
  err => {
    if (err.response?.status === 401) {
      localStorage.removeItem('kb_token')
      localStorage.removeItem('kb_user')
      window.location.href = '/login'
    }
    return Promise.reject(err)
  }
)

// ================== 认证 ==================
export const login = (username: string, password: string) =>
  api.post('/auth/login', { username, password })

export const register = (username: string, password: string, name: string) =>
  api.post('/auth/register', { username, password, name })

// ================== 空间 ==================
export const listSpaces = (page = 1, size = 20) =>
  api.get('/spaces', { params: { page, size } })

export const getSpace = (id: number) =>
  api.get(`/spaces/${id}`)

export const createSpace = (name: string, description: string, visibility: string, departmentScope?: number[]) =>
  api.post('/spaces', { name, description, visibility, departmentScope })

export const updateSpace = (id: number, data: Record<string, any>) =>
  api.put(`/spaces/${id}`, data)

export const deleteSpace = (id: number) =>
  api.delete(`/spaces/${id}`)

export const listMembers = (spaceId: number) =>
  api.get(`/spaces/${spaceId}/members`)

export const addMember = (spaceId: number, userId: number, role: string) =>
  api.post(`/spaces/${spaceId}/members`, { userId, role })

export const removeMember = (spaceId: number, userId: number) =>
  api.delete(`/spaces/${spaceId}/members/${userId}`)

// ================== 文档 ==================
export const uploadDocument = (file: File, spaceId: number, category?: string) => {
  const fd = new FormData()
  fd.append('file', file)
  fd.append('spaceId', String(spaceId))
  if (category) fd.append('category', category)
  return api.post('/documents/upload', fd, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export const listDocuments = (spaceId: number, page = 1, size = 20) =>
  api.get('/documents', { params: { spaceId, page, size } })

export const getDocument = (id: number) =>
  api.get(`/documents/${id}`)

export const updateDocument = (id: number, data: Record<string, any>) =>
  api.put(`/documents/${id}`, data)

export const deleteDocument = (id: number) =>
  api.delete(`/documents/${id}`)

// ================== 部门管理 ==================
export const listDepartments = () =>
  api.get('/admin/departments')

export const createDepartment = (data: Record<string, any>) =>
  api.post('/admin/departments', data)

export const updateDepartment = (id: number, data: Record<string, any>) =>
  api.put(`/admin/departments/${id}`, data)

export const deleteDepartment = (id: number) =>
  api.delete(`/admin/departments/${id}`)

// ================== 用户管理 ==================
export const listUsers = (params: Record<string, any>) =>
  api.get('/admin/users', { params })

export const createUser = (data: Record<string, any>) =>
  api.post('/admin/users', data)

export const updateUser = (id: number, data: Record<string, any>) =>
  api.put(`/admin/users/${id}`, data)

export const resetPassword = (id: number, password: string) =>
  api.put(`/admin/users/${id}/reset-password`, { password })

// ================== 场景配置 ==================
// 公开分类（上传文档时选择分类用）
export const listPublicCategories = () =>
  api.get('/categories')

// 知识分类
export const listCategories = () =>
  api.get('/admin/scenario/categories')

export const createCategory = (data: Record<string, any>) =>
  api.post('/admin/scenario/categories', data)

export const deleteCategory = (id: number) =>
  api.delete(`/admin/scenario/categories/${id}`)

// 术语词典
export const listGlossary = () =>
  api.get('/admin/scenario/glossary')

export const createGlossary = (data: Record<string, any>) =>
  api.post('/admin/scenario/glossary', data)

export const deleteGlossary = (id: number) =>
  api.delete(`/admin/scenario/glossary/${id}`)

// 预设问答
export const listFaq = () =>
  api.get('/admin/scenario/faq')

export const createFaq = (data: Record<string, any>) =>
  api.post('/admin/scenario/faq', data)

export const updateFaq = (id: number, data: Record<string, any>) =>
  api.put(`/admin/scenario/faq/${id}`, data)

export const deleteFaq = (id: number) =>
  api.delete(`/admin/scenario/faq/${id}`)

// ================== 反馈统计 ==================
export const getFeedbackStats = (startDate?: string, endDate?: string) =>
  api.get('/admin/feedback/stats', { params: { startDate, endDate } })

export const listFeedback = (params: Record<string, any>) =>
  api.get('/admin/feedback/list', { params })

// ================== 使用统计 ==================
export const getStats = () =>
  api.get('/admin/stats')

// ================== 审计日志 ==================
export const listAuditLogs = (params: Record<string, any>) =>
  api.get('/admin/audit-logs', { params })

// ================== 公开 FAQ ==================
export function listPublicFaq() {
  return api.get('/faq')
}

// ================== RAG 问答 ==================

/** 对话历史条目 */
export interface HistoryMessage {
  role: 'user' | 'assistant'
  content: string
}

/** RAG 问答（带可选对话历史） */
export function chatWithKnowledge(question: string, history?: HistoryMessage[]) {
  return api.post('/rag/chat', { question, history: history || [] })
}

/**
 * RAG 流式问答（SSE）— POST + fetch ReadableStream
 */
export async function chatWithKnowledgeStream(
  question: string,
  callbacks: {
    onToken: (token: string) => void
    onSources: (sources: any[]) => void
    onDone: (meta: { confidence: string; fallback: boolean }) => void
    onError: (message: string) => void
  },
  history?: HistoryMessage[],
  category?: string,
  spaceIds?: number[]
): Promise<void> {
  const token = localStorage.getItem('kb_token') || ''

  try {
    const body: Record<string, any> = { question, history: history || [] }
    if (category) body.category = category
    if (spaceIds && spaceIds.length) body.spaceIds = spaceIds

    const response = await fetch('/api/v1/rag/chat/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { 'Authorization': `Bearer ${token}` } : {})
      },
      body: JSON.stringify(body)
    })

    if (!response.ok) {
      if (response.status === 401) {
        localStorage.removeItem('kb_token')
        localStorage.removeItem('kb_user')
        window.location.href = '/login'
        return
      }
      callbacks.onError(`请求失败 (${response.status})`)
      return
    }

    const reader = response.body!.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let streamEndedNormally = false
    let eventType = ''

    try {
      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          if (line.startsWith('event:')) {
            eventType = line.slice(6).trim()
          } else if (line.startsWith('data:')) {
            const data = line.slice(5).trim()
            if (eventType === 'token') {
              callbacks.onToken(data)
            } else if (eventType === 'sources') {
              try {
                callbacks.onSources(JSON.parse(data))
              } catch { /* ignore parse errors */ }
            } else if (eventType === 'done') {
              streamEndedNormally = true
              try {
                callbacks.onDone(JSON.parse(data))
              } catch {
                callbacks.onDone({ confidence: 'low', fallback: false })
              }
            } else if (eventType === 'error') {
              streamEndedNormally = true
              callbacks.onError(data)
            } else {
              callbacks.onToken(data)
            }
            eventType = ''
          }
        }
      }
    } finally {
      if (!streamEndedNormally) {
        callbacks.onError('连接意外断开，请重试')
      }
    }
  } catch (err: any) {
    callbacks.onError(err.message || '网络错误')
  }
}

/** 纯检索 */
export function searchKnowledge(q: string, topK = 5) {
  return api.get('/rag/search', { params: { q, topK } })
}

// ================== 反馈 ==================

/** 提交答案反馈 */
export function submitFeedback(data: {
  question: string
  answer: string
  rating: 'useful' | 'useless'
  comment?: string
  userId?: number
}) {
  return api.post('/feedback', data)
}
