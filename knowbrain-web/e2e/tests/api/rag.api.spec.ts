import { test, expect } from '../../fixtures/index.js';

/**
 * API: RAG 接口测试 (A6-A8)
 */
test.describe('API | RAG', () => {

  test('A6 | chat non-streaming — returns answer + sources', { tag: ['@api'] }, async ({ apiHelper }) => {
    const resp = await apiHelper.chat('年假有几天');
    expect(resp.status()).toBe(200);

    const body = await resp.json();
    expect(body.code).toBe(200);
    expect(body.data.answer).toBeTruthy();
    expect(typeof body.data.answer).toBe('string');
    expect(body.data.answer.length).toBeGreaterThan(0);
  });

  test('A7 | chat no-result — returns graceful fallback', { tag: ['@api'] }, async ({ apiHelper }) => {
    const resp = await apiHelper.chat('火星移民政策2026');
    expect(resp.status()).toBe(200);

    const body = await resp.json();
    expect(body.code).toBe(200);
    expect(body.data.answer).toBeTruthy();
  });

  test('A8 | search API — returns results via auth GET', { tag: ['@api'] }, async ({ apiHelper }) => {
    const resp = await apiHelper.authGet('/api/v1/rag/search', { q: 'VPN', topK: 5 });
    expect(resp.status()).toBe(200);

    const body = await resp.json();
    expect(body.code).toBe(200);
    expect(Array.isArray(body.data)).toBe(true);
  });
});
