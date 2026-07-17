import { test as setup } from '@playwright/test';
import { ADMIN } from '../data/test-data.js';
import * as fs from 'fs';
import * as path from 'path';

const AUTH_DIR = path.resolve('.auth');

/**
 * 全局初始化：API 登录 → 保存 storageState
 *
 * 后续浏览器测试自动注入 storageState，跳过 UI 登录步骤，提速 60-80%。
 * 含 429 限流自动重试（最长等待 5 分钟）。
 */
setup('global setup — authenticate via API', async ({ request }) => {
  let lastError: any;
  const maxRetries = 10;
  const retryDelayMs = 30000; // 30 秒间隔（限流窗口最多 4 分钟）

  for (let attempt = 1; attempt <= maxRetries; attempt++) {
    const resp = await request.post('/api/v1/auth/login', {
      data: { username: ADMIN.username, password: ADMIN.password },
    });
    const body = await resp.json();

    if (body.code === 200) {
      const { token, refreshToken, name, role, userId } = body.data;

      // 构建 Playwright storageState 格式
      const storageState = {
        cookies: [],
        origins: [
          {
            origin: 'http://localhost',
            localStorage: [
              { name: 'kb_token', value: token },
              { name: 'kb_user', value: JSON.stringify({ name, role, userId, username: ADMIN.username }) },
            ],
          },
        ],
      };

      // 写入磁盘
      fs.mkdirSync(AUTH_DIR, { recursive: true });
      fs.writeFileSync(
        path.join(AUTH_DIR, 'admin.json'),
        JSON.stringify(storageState, null, 2),
      );

      // 同时写入 API token（供纯 API 测试使用）
      fs.writeFileSync(
        path.join(AUTH_DIR, 'api-token.txt'),
        token,
      );

      if (attempt > 1) {
        console.log(`  ✅ Login succeeded after ${attempt} attempts`);
      }
      return;
    }

    // 429 限流 → 等待后重试
    if (body.code === 429 && attempt < maxRetries) {
      console.log(`  ⏳ Rate limited (attempt ${attempt}/${maxRetries}), waiting ${retryDelayMs / 1000}s...`);
      await new Promise(resolve => setTimeout(resolve, retryDelayMs));
      lastError = body;
      continue;
    }

    lastError = body;
    break;
  }

  throw new Error(`Login failed after retries: ${JSON.stringify(lastError)}`);
});
