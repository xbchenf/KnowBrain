import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  timeout: 60000,
  expect: { timeout: 10000 },
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: [
    ['html'],
    ['list'],
    ['junit', { outputFile: 'test-results/junit.xml' }],
  ],
  use: {
    baseURL: process.env.BASE_URL || 'http://localhost',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },

  projects: [
    // ---- 浏览器 UI 测试 ----
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        storageState: '.auth/admin.json',
      },
      testMatch: ['smoke/**/*.spec.ts', 'regression/**/*.spec.ts'],
      dependencies: ['setup'],
    },

    // ---- 纯 API 测试（无浏览器） ----
    {
      name: 'api',
      use: { baseURL: process.env.BASE_URL || 'http://localhost' },
      testMatch: ['api/**/*.spec.ts'],
    },

    // ---- 全局初始化：登录并保存 storageState ----
    {
      name: 'setup',
      testMatch: /global\.setup\.ts/,
      timeout: 300000, // 5 分钟（含 429 限流等待）
    },
  ],
});
