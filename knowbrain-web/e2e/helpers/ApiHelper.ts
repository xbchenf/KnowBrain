import { APIRequestContext } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';

/**
 * REST API 封装 — 纯接口测试 + 浏览器测试的种子数据/清理。
 */
export class ApiHelper {
  private token: string;

  constructor(private request: APIRequestContext) {
    this.token = fs.readFileSync(
      path.resolve('.auth/api-token.txt'), 'utf-8',
    ).trim();
  }

  private headers() {
    return {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${this.token}`,
    };
  }

  // ---- 健康检查 ----
  async healthCheck() {
    return this.request.get('/api/v1/health');
  }

  // ---- 认证 ----
  async login(username: string, password: string) {
    return this.request.post('/api/v1/auth/login', { data: { username, password } });
  }

  async register(username: string, password: string, name: string) {
    return this.request.post('/api/v1/auth/register', { data: { username, password, name } });
  }

  // ---- RAG 问答 ----
  async chat(question: string) {
    return this.request.post('/api/v1/rag/chat', {
      headers: this.headers(),
      data: { question, history: [] },
    });
  }

  // ---- 文档 ----
  async listDocuments(spaceId: number) {
    return this.request.get('/api/v1/documents', {
      headers: this.headers(),
      params: { spaceId, page: 1, size: 20 },
    });
  }

  async deleteDocument(id: number) {
    return this.request.delete(`/api/v1/documents/${id}`, { headers: this.headers() });
  }

  // ---- 空间 ----
  async createSpace(name: string, description: string, visibility = 'PRIVATE') {
    return this.request.post('/api/v1/spaces', {
      headers: this.headers(),
      data: { name, description, visibility },
    });
  }

  async deleteSpace(id: number) {
    return this.request.delete(`/api/v1/spaces/${id}`, { headers: this.headers() });
  }

  async listSpaces() {
    return this.request.get('/api/v1/spaces', {
      headers: this.headers(),
      params: { page: 1, size: 50 },
    });
  }

  // ---- 用户管理 ----
  async createUser(data: Record<string, any>) {
    return this.request.post('/api/v1/admin/users', {
      headers: this.headers(),
      data,
    });
  }

  async listUsers() {
    return this.request.get('/api/v1/admin/users', {
      headers: this.headers(),
      params: { page: 1, size: 100 },
    });
  }

  async deleteUser(id: number) {
    return this.request.delete(`/api/v1/admin/users/${id}`, { headers: this.headers() });
  }

  // ---- 评测 ----
  async startEvalRun(datasetId: number) {
    return this.request.post('/api/v1/admin/evaluation/runs', {
      headers: this.headers(),
      data: { datasetId },
    });
  }

  // ---- 审计 ----
  async listAuditLogs(params?: Record<string, any>) {
    return this.request.get('/api/v1/admin/audit-logs', {
      headers: this.headers(),
      params: { page: 1, size: 20, ...params },
    });
  }

  // ---- 场景配置 ----
  async createCategory(data: Record<string, any>) {
    return this.request.post('/api/v1/admin/scenario/categories', {
      headers: this.headers(),
      data,
    });
  }

  async listCategories() {
    return this.request.get('/api/v1/admin/scenario/categories', {
      headers: this.headers(),
    });
  }

  async deleteCategory(id: number) {
    return this.request.delete(`/api/v1/admin/scenario/categories/${id}`, { headers: this.headers() });
  }

  async createGlossary(data: Record<string, any>) {
    return this.request.post('/api/v1/admin/scenario/glossary', {
      headers: this.headers(),
      data,
    });
  }

  async listGlossary() {
    return this.request.get('/api/v1/admin/scenario/glossary', {
      headers: this.headers(),
    });
  }

  async deleteGlossary(id: number) {
    return this.request.delete(`/api/v1/admin/scenario/glossary/${id}`, { headers: this.headers() });
  }

  async createFaq(data: Record<string, any>) {
    return this.request.post('/api/v1/admin/scenario/faq', {
      headers: this.headers(),
      data,
    });
  }

  async listFaq() {
    return this.request.get('/api/v1/admin/scenario/faq', {
      headers: this.headers(),
    });
  }

  async deleteFaq(id: number) {
    return this.request.delete(`/api/v1/admin/scenario/faq/${id}`, { headers: this.headers() });
  }

  // ---- 评测 ----
  async createDataset(data: Record<string, any>) {
    return this.request.post('/api/v1/admin/evaluation/datasets', {
      headers: this.headers(),
      data,
    });
  }

  async listDatasets(params?: Record<string, any>) {
    return this.request.get('/api/v1/admin/evaluation/datasets', {
      headers: this.headers(),
      params: { page: 1, size: 20, ...params },
    });
  }

  async deleteDataset(id: number) {
    return this.request.delete(`/api/v1/admin/evaluation/datasets/${id}`, { headers: this.headers() });
  }

  /** 添加单条问题 */
  async addQuestion(datasetId: number, question: Record<string, any>) {
    return this.request.post(`/api/v1/admin/evaluation/datasets/${datasetId}/questions`, {
      headers: this.headers(),
      data: question,
    });
  }

  /** 批量导入问题（JSON 数组，不包裹） */
  async batchImportQuestions(datasetId: number, questions: Record<string, any>[]) {
    return this.request.post(`/api/v1/admin/evaluation/datasets/${datasetId}/questions/batch`, {
      headers: this.headers(),
      data: questions,
    });
  }

  async startEvalRun(datasetId: number) {
    return this.request.post('/api/v1/admin/evaluation/runs', {
      headers: this.headers(),
      data: { datasetId },
    });
  }

  async listRuns(params?: Record<string, any>) {
    return this.request.get('/api/v1/admin/evaluation/runs', {
      headers: this.headers(),
      params: { page: 1, size: 20, ...params },
    });
  }

  async deleteRun(id: number) {
    return this.request.delete(`/api/v1/admin/evaluation/runs/${id}`, { headers: this.headers() });
  }

  // ---- 空间成员 ----
  async addSpaceMember(spaceId: number, userId: number, role = 'VIEWER') {
    return this.request.post(`/api/v1/spaces/${spaceId}/members`, {
      headers: this.headers(),
      data: { userId, role },
    });
  }

  async listSpaceMembers(spaceId: number) {
    return this.request.get(`/api/v1/spaces/${spaceId}/members`, {
      headers: this.headers(),
    });
  }

  async removeSpaceMember(spaceId: number, userId: number) {
    return this.request.delete(`/api/v1/spaces/${spaceId}/members/${userId}`, {
      headers: this.headers(),
    });
  }

  // ---- 反馈 ----
  async getFeedbackStats(startDate?: string, endDate?: string) {
    return this.request.get('/api/v1/admin/feedback/stats', {
      headers: this.headers(),
      params: { startDate, endDate },
    });
  }

  async submitFeedback(question: string, answer: string, rating: string, comment?: string) {
    return this.request.post('/api/v1/feedback', {
      headers: this.headers(),
      data: { question, answer, rating, comment },
    });
  }

  async listFeedback(params?: Record<string, any>) {
    return this.request.get('/api/v1/admin/feedback', {
      headers: this.headers(),
      params: { page: 1, size: 20, ...params },
    });
  }

  // ---- 统计 ----
  async getStatistics() {
    return this.request.get('/api/v1/admin/stats', {
      headers: this.headers(),
    });
  }

  // ---- 部门 ----
  async listDepartments() {
    return this.request.get('/api/v1/admin/departments', {
      headers: this.headers(),
    });
  }

  // ---- Token 刷新 ----
  async refreshToken(refreshToken: string) {
    return this.request.post('/api/v1/auth/refresh', {
      data: { refreshToken },
    });
  }

  // ---- 用户状态切换 ----
  async updateUserStatus(id: number, status: string) {
    return this.request.put(`/api/v1/admin/users/${id}`, {
      headers: this.headers(),
      data: { status },
    });
  }

  // ---- 用户编辑 ----
  async updateUser(id: number, data: Record<string, any>) {
    return this.request.put(`/api/v1/admin/users/${id}`, {
      headers: this.headers(),
      data,
    });
  }

  // ---- 用户重置密码 ----
  async resetPassword(id: number, password: string) {
    return this.request.put(`/api/v1/admin/users/${id}/reset-password`, {
      headers: this.headers(),
      data: { password },
    });
  }

  // ---- 通用方法（带认证） ----
  getToken(): string {
    return this.token;
  }

  async authGet(path: string, params?: Record<string, any>) {
    return this.request.get(path, { headers: this.headers(), params });
  }

  async authPost(path: string, data?: Record<string, any>) {
    return this.request.post(path, { headers: this.headers(), data });
  }

  getRequest(): APIRequestContext {
    return this.request;
  }
}
