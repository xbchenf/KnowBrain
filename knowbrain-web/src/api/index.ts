import axios from 'axios'

const api = axios.create({
  baseURL: '/api/v1',
  timeout: 60000,
  headers: { 'Content-Type': 'application/json' }
})

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

// ==================== RAG 问答 ====================

/** RAG 问答 */
export function chatWithKnowledge(question: string) {
  return api.post('/rag/chat', { question })
}

/**
 * RAG 流式问答（SSE）— POST + fetch ReadableStream
 *
 * @param question  用户问题
 * @param onToken   收到新 token 时回调
 * @param onSources 收到溯源列表时回调
 * @param onDone    收到完成信号时回调
 * @param onError   发生错误时回调
 */
export async function chatWithKnowledgeStream(
  question: string,
  callbacks: {
    onToken: (token: string) => void
    onSources: (sources: any[]) => void
    onDone: (meta: { confidence: string; fallback: boolean }) => void
    onError: (message: string) => void
  }
): Promise<void> {
  const token = localStorage.getItem('token') || ''

  try {
    const response = await fetch('/api/v1/rag/chat/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { 'Authorization': `Bearer ${token}` } : {})
      },
      body: JSON.stringify({ question })
    })

    if (!response.ok) {
      callbacks.onError(`请求失败 (${response.status})`)
      return
    }

    const reader = response.body!.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      // 保留最后一个可能不完整的行
      buffer = lines.pop() || ''

      let eventType = ''
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
            try {
              callbacks.onDone(JSON.parse(data))
            } catch {
              callbacks.onDone({ confidence: 'low', fallback: false })
            }
          } else if (eventType === 'error') {
            callbacks.onError(data)
          } else {
            // 兼容无 event 类型的纯 data 行
            callbacks.onToken(data)
          }
          eventType = ''
        }
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
