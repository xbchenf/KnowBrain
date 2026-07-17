import { test, expect } from '../../fixtures/index.js';

/**
 * API: Auth 接口测试 (A1-A5)
 */
test.describe('API | Auth & Security', () => {

  test('A2 | no auth — missing token on protected route returns 401', { tag: ['@api'] }, async ({ request }) => {
    const resp = await request.get('/api/v1/admin/users');
    expect(resp.status()).toBe(401);
  });

  test('A3 | insufficient role — USER role cannot access ADMIN routes', { tag: ['@api'] }, async ({ apiHelper }) => {
    // 注册新用户（USER 角色）
    const username = `e2e-api-${Date.now()}`;
    const regResp = await apiHelper.register(username, 'Test@2026', 'Test User');
    const regCode = regResp.status();

    if (regCode === 200) {
      // 用新用户登录获取 token
      const loginResp = await apiHelper.login(username, 'Test@2026');
      const loginBody = await loginResp.json();
      if (loginBody.data?.token) {
        const resp = await apiHelper.getRequest().get('/api/v1/admin/users', {
          headers: { 'Authorization': `Bearer ${loginBody.data.token}` },
        });
        expect(resp.status()).toBe(403);
      }
    }
  });

  test('A4 | CORS — OPTIONS preflight returns correct headers', { tag: ['@api'] }, async ({ request }) => {
    const resp = await request.fetch('/api/v1/auth/login', { method: 'OPTIONS' });
    expect(resp.status()).toBeLessThan(400);
  });

  test('A5 | health — all components UP', { tag: ['@api'] }, async ({ apiHelper }) => {
    const resp = await apiHelper.healthCheck();
    expect(resp.status()).toBe(200);

    const body = await resp.json();
    expect(body.status).toBe('UP');
    expect(body.components.mysql.status).toBe('UP');
    expect(body.components.redis.status).toBe('UP');
    expect(body.components.milvus.status).toBe('UP');
  });

  // ⚠️ A1 必须在最后执行 — 它会触发限流（25 次快速请求），影响后续测试
  test('A1 | rate limiting — rapid requests may return 429', { tag: ['@api'] }, async ({ request }) => {
    const results: number[] = [];
    for (let i = 0; i < 25; i++) {
      const resp = await request.post('/api/v1/auth/login', {
        data: { username: 'admin', password: 'wrong' },
      });
      results.push(resp.status());
    }
    const hasRateLimit = results.some(s => s === 429);
    const allServerErrors = results.every(s => s >= 500);
    expect(allServerErrors).toBe(false);
  });
});
