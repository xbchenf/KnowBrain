import { Page } from '@playwright/test';
import { BasePage } from './BasePage.js';

/**
 * 登录页面 — 独立的 /login 路由。
 */
export class LoginPage extends BasePage {
  private usernameInput = this.page.locator('input[type="text"], input[placeholder*="用户"]').first();
  private passwordInput = this.page.locator('input[type="password"]').first();
  private loginButton = this.page.locator('button').filter({ hasText: /登录|登 录/ }).first();

  constructor(page: Page) {
    super(page);
  }

  async goto() {
    await this.navigate('/login');
  }

  async fillUsername(username: string) {
    await this.usernameInput.fill(username);
  }

  async fillPassword(password: string) {
    await this.passwordInput.fill(password);
  }

  async submit() {
    await this.loginButton.click();
  }

  /** 完整登录流程 */
  async login(username: string, password: string) {
    await this.goto();
    await this.fillUsername(username);
    await this.fillPassword(password);
    await this.submit();
  }
}
