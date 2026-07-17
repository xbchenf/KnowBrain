import { Page, Locator } from '@playwright/test';

/**
 * 共享页面基类 — 导航、等待、Element Plus 组件封装。
 */
export class BasePage {
  constructor(protected page: Page) {}

  /** 按角色文本点击 Element Plus 按钮 */
  async clickButton(label: string) {
    await this.page.locator('button, .el-button, a.el-button').filter({ hasText: label }).first().click();
  }

  /** 等待 Element Plus 消息提示出现 */
  async waitForToast(text?: string) {
    const toast = this.page.locator('.el-message, .el-notification');
    await toast.first().waitFor({ state: 'visible', timeout: 5000 });
    if (text) {
      await this.page.locator('.el-message, .el-notification').filter({ hasText: text }).first().waitFor({ timeout: 3000 });
    }
  }

  /** 导航到指定路径 */
  async navigate(path: string) {
    await this.page.goto(path);
    await this.page.waitForLoadState('networkidle');
  }

  /** 等待 Element Plus 表格加载完成 */
  async waitForTable() {
    await this.page.locator('.el-table').first().waitFor({ state: 'visible', timeout: 10000 });
  }

  /** 等待 Element Plus 对话框出现 */
  async waitForDialog() {
    await this.page.locator('.el-dialog, .el-drawer').first().waitFor({ state: 'visible', timeout: 5000 });
  }

  /** 关闭对话框 */
  async closeDialog() {
    await this.page.locator('.el-dialog__close, .el-drawer__close-btn').first().click();
    await this.page.locator('.el-dialog, .el-drawer').first().waitFor({ state: 'hidden', timeout: 3000 });
  }
}
