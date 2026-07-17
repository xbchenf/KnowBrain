import { test, expect } from '../../fixtures/index.js';

/**
 * Smoke: Auth 模块 (S1-S3)
 *
 * 注意：与 API 项目（含 A1 限流测试）合跑时，可能因 429 跳过。
 * 单独跑 chromium 项目不会受影响。
 */
test.describe('Auth (S1-S3)', () => {

  test('S1 | login success — returns JWT + user info', { tag: ['@smoke', '@auth'] }, async ({ apiHelper }) => {
    const resp = await apiHelper.login('admin', 'KnowBrain@2026');
    const body = await resp.json();
    if (body.code === 429) {
      test.skip(true, 'Rate limited by previous test run');
      return;
    }
    expect(resp.status()).toBe(200);
    expect(body.code).toBe(200);
    expect(body.data.token).toBeTruthy();
    expect(body.data.role).toBe('ADMIN');
    expect(body.data.name).toBeTruthy();
  });

  test('S2 | login failure — wrong password returns code 401', { tag: ['@smoke', '@auth'] }, async ({ apiHelper }) => {
    const resp = await apiHelper.login('admin', 'WrongPassword123');
    const body = await resp.json();
    if (body.code === 429) {
      test.skip(true, 'Rate limited by previous test run');
      return;
    }
    expect(body.code).toBe(401);
    expect(body.message).toContain('用户名或密码错误');
  });

  test('S3 | unauthenticated access — no token returns 401', { tag: ['@smoke', '@auth'] }, async ({ request }) => {
    const resp = await request.post('/api/v1/rag/chat', {
      data: { question: 'hello', history: [] },
    });
    expect(resp.status()).toBe(401);
  });
});
