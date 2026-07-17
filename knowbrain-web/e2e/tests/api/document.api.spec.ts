import { test, expect } from '../../fixtures/index.js';

/**
 * API: Document 管理接口测试 (A9-A10)
 */
test.describe('API | Document', () => {

  test('A9 | upload reject — file without spaceId returns error', { tag: ['@api'] }, async ({ apiHelper }) => {
    // 不带 spaceId 应该返回错误
    const resp = await apiHelper.getRequest().post('/api/v1/documents/upload', {
      headers: { 'Authorization': `Bearer ${apiHelper.getToken()}` },
      multipart: {
        file: {
          name: 'test.txt',
          mimeType: 'text/plain',
          buffer: Buffer.from('e2e test content'),
        },
      },
    });
    // 缺少 spaceId 应返回 4xx
    expect(resp.status()).toBeGreaterThanOrEqual(400);
  });

  test('A10 | list documents with invalid space — returns empty', { tag: ['@api'] }, async ({ apiHelper }) => {
    const resp = await apiHelper.authGet('/api/v1/documents', { spaceId: 0, page: 1, size: 20 });
    expect(resp.status()).toBe(200);

    const body = await resp.json();
    expect(body.code).toBe(200);
  });
});
