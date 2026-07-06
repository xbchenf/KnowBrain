import axios from 'axios'

const api = axios.create({
  baseURL: '/api/v1',
  timeout: 60000,
  headers: { 'Content-Type': 'application/json' }
})

// 请求拦截：自动附带 token
api.interceptors.request.use(config => {
  const token = localStorage.getItem('kb_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截：401 跳转登录页
api.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401) {
      localStorage.removeItem('kb_token')
      localStorage.removeItem('kb_user')
      window.location.href = '/admin/login'
    }
    return Promise.reject(error)
  }
)

// ==================== 文档管理 ====================

/** 上传文档 */
export function uploadDocument(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return api.post('/documents/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/** 查询文档 */
export function getDocument(id: number) {
  return api.get(`/documents/${id}`)
}

/** 文档列表（按空间） */
export function listDocuments(spaceId: number, page = 1, size = 20) {
  return api.get('/documents', { params: { spaceId, page, size } })
}

/** 可访问空间列表 */
export function listSpaces() {
  return api.get('/spaces')
}

// ==================== 公开分类 ====================
export function listPublicCategories() {
  return api.get('/categories')
}

// ==================== 公开 FAQ ====================
export function listPublicFaq() {
  return api.get('/faq')
}

// ==================== RAG 问答 ====================

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
 *
 * @param question  用户问题
 * @param callbacks 回调（onToken / onSources / onDone / onError）
 * @param history   可选对话历史（最近 N 轮）
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
        window.location.href = '/admin/login'
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
        // 保留最后一个可能不完整的行
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
              // 兼容无 event 类型的纯 data 行
              callbacks.onToken(data)
            }
            eventType = ''
          }
        }
      }
    } finally {
      // 兜底：流关闭但未收到 done/error 事件时，通知前端结束加载
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

// ==================== 反馈 ====================

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
