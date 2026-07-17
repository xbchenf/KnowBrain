import { test, expect } from '../../fixtures/index.js';

/**
 * Smoke: Document 模块 (S7-S8)
 */
test.describe('Document (S7-S8)', () => {

  test('S7 | document list — returns paginated data', { tag: ['@smoke', '@doc'] }, async ({ apiHelper }) => {
    const spacesResp = await apiHelper.listSpaces();
    const spacesBody = await spacesResp.json();
    const spaces = spacesBody.data?.records || [];

    if (spaces.length === 0) {
      test.skip(true, 'No spaces available');
      return;
    }

    const resp = await apiHelper.listDocuments(spaces[0].id);
    expect(resp.status()).toBe(200);

    const body = await resp.json();
    expect(body.code).toBe(200);
    expect(body.data).toBeTruthy();
  });

  test('S8 | document list with empty space — returns empty records', { tag: ['@smoke', '@doc'] }, async ({ apiHelper }) => {
    const resp = await apiHelper.listDocuments(99999);
    expect(resp.status()).toBe(200);

    const body = await resp.json();
    expect(body.code).toBe(200);
    // 不存在的 space 应返回空列表
    const records = body.data?.records || [];
    expect(records.length).toBe(0);
  });
});
