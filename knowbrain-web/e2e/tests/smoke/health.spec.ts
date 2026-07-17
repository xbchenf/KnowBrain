import { test, expect } from '../../fixtures/index.js';

/**
 * Smoke Test S10: Health check — all components UP
 */
test('S10 | health check — /health returns UP with all components', { tag: ['@smoke', '@health'] }, async ({ apiHelper }) => {
  const resp = await apiHelper.healthCheck();
  expect(resp.status()).toBe(200);

  const body = await resp.json();
  expect(body.status).toBe('UP');
  expect(body.components.mysql.status).toBe('UP');
  expect(body.components.redis.status).toBe('UP');
  expect(body.components.milvus.status).toBe('UP');
  expect(body.components.llm.status).toBe('UP');
});
