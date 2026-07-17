import { test as base, expect } from '@playwright/test';
import { ChatPage } from '../pages/ChatPage.js';
import { LoginPage } from '../pages/LoginPage.js';
import { AdminPage } from '../pages/AdminPage.js';
import { ApiHelper } from '../helpers/ApiHelper.js';

/**
 * 自定义夹具 — 注入 Page Objects + API Helper。
 *
 * 用法：
 *   test('example', async ({ chatPage, adminPage, apiHelper }) => { ... })
 */
type Fixtures = {
  chatPage: ChatPage;
  loginPage: LoginPage;
  adminPage: AdminPage;
  apiHelper: ApiHelper;
};

export const test = base.extend<Fixtures>({
  chatPage: async ({ page }, use) => {
    await use(new ChatPage(page));
  },
  loginPage: async ({ page }, use) => {
    await use(new LoginPage(page));
  },
  adminPage: async ({ page }, use) => {
    await use(new AdminPage(page));
  },
  apiHelper: async ({ request }, use) => {
    await use(new ApiHelper(request));
  },
});

export { expect };
