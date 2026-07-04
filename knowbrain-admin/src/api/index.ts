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

// 响应拦截 — 401 跳转登录
api.interceptors.response.use(
  res => res,
  err => {
    if (err.response?.status === 401) {
      localStorage.removeItem('kb_token')
      localStorage.removeItem('kb_user')
      window.location.href = '/admin/login'
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

export const updateSpace = (id: number, data: Record<string, string>) =>
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
export const uploadDocument = (file: File, spaceId: number) => {
  const fd = new FormData()
  fd.append('file', file)
  fd.append('spaceId', String(spaceId))
  return api.post('/documents/upload', fd, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export const listDocuments = (spaceId: number, page = 1, size = 20) =>
  api.get('/documents', { params: { spaceId, page, size } })

export const getDocument = (id: number) =>
  api.get(`/documents/${id}`)

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
