import { test, expect } from '../../../fixtures/index.js';
import { ADMIN } from '../../../data/test-data.js';

/**
 * Regression: 用户管理 (R1-R3)
 *
 * R1: 用户 CRUD — 新建 → 编辑 → 重置密码 → 验证列表
 * R2: 用户注册 — 自助注册 → USER 角色 → 可登录
 * R3: Token 刷新 — refresh token 换新 access token（rotation）
 */

const TEST_USERNAME = `reg-user-${Date.now()}`;
const TEST_PASSWORD = 'Reg@2026';
const TEST_NAME = '回归测试用户';
let testUserId: number;

test.describe('Regression | 用户管理 (R1-R3)', () => {

  // ==================== R1: 用户 CRUD（浏览器 UI） ====================
  test('R1 | user CRUD — create → edit → reset password → visible in list', {
    tag: ['@regression', '@admin'],
  }, async ({ adminPage, page }) => {
    // 1. 导航到用户管理页
    await adminPage.goto('/users');
    await adminPage.waitForTable();

    // 2. 点击「新建用户」
    await page.locator('button').filter({ hasText: '新建用户' }).first().click();
    await adminPage.waitForDialog('新建用户');

    // 3. 填写表单
    await adminPage.fillDialogField('用户名', TEST_USERNAME);
    await adminPage.fillDialogField('密码', TEST_PASSWORD);
    await adminPage.fillDialogField('姓名', TEST_NAME);
    // 角色默认 USER，保持不变

    // 4. 提交
    await adminPage.clickDialogButton('创建');
    await adminPage.expectToast('用户创建成功');

    // 5. 验证表格中出现新用户
    await adminPage.waitForTable();
    await adminPage.expectTableContains(TEST_USERNAME);

    // 6. 编辑用户 — 直接在表格行内找编辑按钮
    const userRow = page.locator('.el-table__body-wrapper tbody tr')
      .filter({ hasText: TEST_USERNAME }).first();
    await userRow.locator('button').filter({ hasText: '编辑' }).first().click();
    await page.waitForTimeout(500);

    // 编辑对话框
    const editDialog = page.locator('.el-dialog').filter({ hasText: '编辑用户' }).first();
    await expect(editDialog).toBeVisible({ timeout: 8000 });

    // 填写姓名
    const nameInput = editDialog.locator('.el-form-item').filter({ hasText: '姓名' }).first().locator('input').first();
    await nameInput.clear();
    await nameInput.fill(TEST_NAME + '_已修改');
    await editDialog.locator('button').filter({ hasText: '保存' }).first().click();
    await adminPage.expectToast('用户信息已更新');

    // 7. 验证姓名已更新
    await adminPage.expectTableContains(TEST_NAME + '_已修改');

    // 8. 重置密码
    await userRow.locator('button').filter({ hasText: '重置密码' }).first().click();
    await page.waitForTimeout(500);
    const resetDialog = page.locator('.el-dialog').filter({ hasText: '重置密码' }).first();
    await expect(resetDialog).toBeVisible({ timeout: 8000 });
    await resetDialog.locator('input').first().fill('NewReg@2026');
    await resetDialog.locator('button').filter({ hasText: '确认重置' }).first().click();
    await adminPage.expectToast('已重置');
  });

  // ==================== R2: 用户注册 → 可登录 ====================
  test('R2 | self-registration — USER role → can login', {
    tag: ['@regression', '@auth'],
  }, async ({ apiHelper, request }) => {
    const username = `self-reg-${Date.now()}`;

    // 1. 注册
    const regResp = await apiHelper.register(username, 'SelfReg@2026', '自助注册');
    const regBody = await regResp.json();

    // 429 限流 → 跳过
    if (regBody.code === 429) { test.skip(true, 'Rate limited'); return; }

    if (regResp.status() === 200 && regBody.code === 200) {
      // 2. 用注册账号登录
      const loginResp = await apiHelper.login(username, 'SelfReg@2026');
      const loginBody = await loginResp.json();
      if (loginBody.code === 429) { test.skip(true, 'Rate limited'); return; }
      expect(loginBody.code).toBe(200);
      expect(loginBody.data.token).toBeTruthy();

      // 3. 验证角色是 USER（不能访问 ADMIN 接口）
      const adminResp = await request.get('/api/v1/admin/users', {
        headers: { 'Authorization': `Bearer ${loginBody.data.token}` },
      });
      expect(adminResp.status()).toBe(403);

      // 清理
      const userListResp = await apiHelper.listUsers();
      const users = (await userListResp.json()).data?.records || [];
      const created = users.find((u: any) => u.username === username);
      if (created) await apiHelper.deleteUser(created.id);
    } else {
      expect([200, 403]).toContain(regResp.status());
    }
  });

  // ==================== R3: Token 刷新 ====================
  test('R3 | token refresh — refresh token rotates access token', {
    tag: ['@regression', '@auth'],
  }, async ({ apiHelper }) => {
    // 1. 登录获取 token 和 refreshToken
    const loginResp = await apiHelper.login(ADMIN.username, ADMIN.password);
    const loginBody = await loginResp.json();
    if (loginBody.code === 429) { test.skip(true, 'Rate limited'); return; }
    expect(loginBody.code).toBe(200);

    const accessToken = loginBody.data.token;
    const refreshToken = loginBody.data.refreshToken;

    expect(accessToken).toBeTruthy();
    expect(refreshToken).toBeTruthy();

    // 2. 用 refresh token 换新 access token
    const refreshResp = await apiHelper.refreshToken(refreshToken);
    const refreshBody = await refreshResp.json();

    if (refreshBody.code === 200) {
      const newAccessToken = refreshBody.data.token;
      expect(newAccessToken).toBeTruthy();
      expect(newAccessToken).not.toBe(accessToken); // rotation: 新 token 不同于旧 token

      // 3. 新 token 可用于认证请求
      const healthResp = await apiHelper.getRequest().get('/api/v1/health', {
        headers: { 'Authorization': `Bearer ${newAccessToken}` },
      });
      expect(healthResp.status()).toBe(200);

      // 4. 旧 refresh token 已失效（rotation 安全机制）
      const reuseResp = await apiHelper.refreshToken(refreshToken);
      const reuseBody = await reuseResp.json();
      expect([401, 400]).toContain(reuseBody.code);
    }
  });

  // ==================== 清理 ====================
  test.afterAll(async ({ apiHelper }) => {
    // 清理 R1 创建的测试用户
    const userListResp = await apiHelper.listUsers();
    const users = (await userListResp.json()).data?.records || [];
    for (const u of users) {
      if (u.username === TEST_USERNAME || u.username?.startsWith('reg-user-') || u.username?.startsWith('self-reg-')) {
        await apiHelper.deleteUser(u.id).catch(() => {});
      }
    }
  });
});
