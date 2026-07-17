import { test, expect } from '../../fixtures/index.js';

/**
 * Smoke Test S9: Space — create → verify in list → cleanup
 */
test('S9 | create space — appears in space list', { tag: ['@smoke', '@space'] }, async ({ apiHelper }) => {
  const spaceName = `e2e-smoke-${Date.now()}`;

  const createResp = await apiHelper.createSpace(spaceName, 'Smoke test');
  expect(createResp.status()).toBe(200);
  const createBody = await createResp.json();
  expect(createBody.data.id).toBeTruthy();

  // 验证出现在列表中
  const listResp = await apiHelper.listSpaces();
  const listBody = await listResp.json();
  const found = listBody.data.records.some((s: any) => s.name === spaceName);
  expect(found).toBe(true);

  // 清理
  await apiHelper.deleteSpace(createBody.data.id);
});
