import { Page, Locator, expect } from '@playwright/test';
import { BasePage } from './BasePage.js';

/**
 * 管理后台页面对象 — Element Plus 组件封装。
 *
 * 覆盖：表格行操作、对话框表单填充、标签页切换、树节点操作、
 *       ElMessageBox 确认/取消、分页、筛选器。
 */
export class AdminPage extends BasePage {
  constructor(page: Page) {
    super(page);
  }

  // ========== 导航 ==========

  /** 导航到管理后台路径 */
  async goto(path: string) {
    await this.navigate(`/admin${path}`);
  }

  /** 通过侧边栏导航（router-link 方式） */
  async clickSidebar(href: string) {
    await this.page.locator(`.nav-item[href="${href}"], a[href="${href}"]`).first().click();
    await this.page.waitForLoadState('networkidle');
  }

  // ========== 对话框 (el-dialog) ==========

  /** 等待对话框可见 */
  async waitForDialog(title?: string) {
    const dialog = this.page.locator('.el-dialog').first();
    await dialog.waitFor({ state: 'visible', timeout: 8000 });
    if (title) {
      await expect(dialog.locator('.el-dialog__title').first()).toContainText(title);
    }
  }

  /** 在可见对话框内填写表单 */
  async fillDialogField(label: string, value: string, type: 'input' | 'textarea' | 'select' = 'input') {
    const dialog = this.page.locator('.el-dialog').filter({ has: this.page.locator(':visible') }).first();
    const formItem = dialog.locator('.el-form-item').filter({ hasText: label }).first();

    if (type === 'select') {
      // 点击 select 触发下拉
      await formItem.locator('.el-select').first().click();
      await this.page.waitForTimeout(300);
      // 选择匹配的选项
      const option = this.page.locator('.el-select-dropdown:not(.is-hidden) .el-select-dropdown__item')
        .filter({ hasText: value }).first();
      await option.click();
      await this.page.waitForTimeout(200);
    } else {
      const input = formItem.locator('input, textarea').first();
      await input.fill(value);
    }
  }

  /** 在可见对话框内设置 el-select 的值（用于筛选栏） */
  async selectDropdown(label: string, value: string) {
    // 用于筛选栏中的 el-select（非对话框内）
    const select = this.page.locator('.el-select').filter({ hasText: label }).first();
    // 如果找不到带 label 的，尝试按 placeholder
    if (await select.count() === 0) {
      await this.fillDialogField(label, value, 'select');
      return;
    }
    await select.click();
    await this.page.waitForTimeout(300);
    const option = this.page.locator('.el-select-dropdown:not(.is-hidden) .el-select-dropdown__item')
      .filter({ hasText: value }).first();
    await option.click();
    await this.page.waitForTimeout(200);
  }

  /** 点击对话框底部按钮 */
  async clickDialogButton(label: string) {
    const dialog = this.page.locator('.el-dialog').filter({ has: this.page.locator(':visible') }).first();
    await dialog.locator('.el-dialog__footer button, .el-dialog__footer .el-button')
      .filter({ hasText: label }).first().click();
  }

  /** 关闭对话框 */
  async closeDialog() {
    await this.page.locator('.el-dialog__close, .el-drawer__close-btn').first().click();
    await this.page.locator('.el-dialog').first().waitFor({ state: 'hidden', timeout: 5000 }).catch(() => {});
  }

  // ========== Element Plus 消息框 (ElMessageBox) ==========

  /** 确认 ElMessageBox（点击"确定"按钮） */
  async confirmMessageBox() {
    const btn = this.page.locator('.el-message-box__btns button, .el-message-box__wrapper button')
      .filter({ hasText: /确定|确认|删除/ }).first();
    await btn.click();
    await this.page.waitForTimeout(500);
  }

  /** 取消 ElMessageBox */
  async cancelMessageBox() {
    const btn = this.page.locator('.el-message-box__btns button, .el-message-box__wrapper button')
      .filter({ hasText: '取消' }).first();
    await btn.click();
    await this.page.waitForTimeout(300);
  }

  // ========== 表格 (el-table) ==========

  /** 等待表格加载完成 */
  async waitForTable() {
    await this.page.locator('.el-table').first().waitFor({ state: 'visible', timeout: 10000 });
    // 等待 loading 遮罩消失
    await this.page.locator('.el-loading-mask').first().waitFor({ state: 'hidden', timeout: 5000 }).catch(() => {});
  }

  /** 获取表格行数 */
  async getTableRowCount(): Promise<number> {
    return await this.page.locator('.el-table__body-wrapper tbody tr.el-table__row').count();
  }

  /** 在表格中查找包含指定文本的行 */
  async findTableRow(containsText: string): Promise<Locator> {
    return this.page.locator('.el-table__body-wrapper tbody tr.el-table__row')
      .filter({ hasText: containsText }).first();
  }

  /** 点击表格行中的操作按钮 */
  async clickRowAction(rowText: string, actionLabel: string) {
    const row = await this.findTableRow(rowText);
    await row.locator('button, .el-button, a').filter({ hasText: actionLabel }).first().click();
    await this.page.waitForTimeout(300);
  }

  /** 断言表格包含指定文本 */
  async expectTableContains(text: string) {
    const row = await this.findTableRow(text);
    await expect(row).toBeVisible({ timeout: 5000 });
  }

  /** 断言表格不包含指定文本 */
  async expectTableNotContains(text: string) {
    const row = this.page.locator('.el-table__body-wrapper tbody tr.el-table__row')
      .filter({ hasText: text }).first();
    await expect(row).not.toBeVisible({ timeout: 3000 });
  }

  // ========== 标签页 (el-tabs) ==========

  /** 切换到指定标签页 */
  async switchTab(tabName: string) {
    const tab = this.page.locator('.el-tabs__item').filter({ hasText: tabName }).first();
    await tab.click();
    await this.page.waitForTimeout(500);
    await this.page.waitForLoadState('networkidle');
  }

  // ========== 树 (el-tree) ==========

  /** 在树中查找并点击节点 */
  async clickTreeNode(nodeName: string) {
    const node = this.page.locator('.el-tree-node__content').filter({ hasText: nodeName }).first();
    await node.click();
    await this.page.waitForTimeout(300);
  }

  /** 获取树节点数量 */
  async getTreeNodeCount(): Promise<number> {
    return await this.page.locator('.el-tree-node').count();
  }

  // ========== el-switch ==========

  /** 切换 el-switch */
  async toggleSwitch(rowText: string) {
    const row = await this.findTableRow(rowText);
    await row.locator('.el-switch').first().click();
    await this.page.waitForTimeout(500);
  }

  // ========== Toast / Message ==========

  /** 等待 ElMessage 出现并验证文本 */
  async expectToast(text: string) {
    const toast = this.page.locator('.el-message, .el-notification').filter({ hasText: text }).first();
    await expect(toast).toBeVisible({ timeout: 5000 });
  }

  // ========== 筛选栏 ==========

  /** 在筛选栏中填充输入框 */
  async fillFilter(placeholder: string, value: string) {
    const input = this.page.locator(`input[placeholder*="${placeholder}"]`).first();
    await input.fill(value);
    await input.press('Enter');
    await this.page.waitForTimeout(500);
  }

  /** 点击筛选栏中的查询按钮 */
  async clickFilterButton(label = '查询') {
    const btn = this.page.locator('.filter-bar button, .toolbar button')
      .filter({ hasText: label }).first();
    await btn.click();
    await this.page.waitForTimeout(500);
  }

  // ========== 统计卡片 ==========

  /** 获取统计卡片数量 */
  async getStatCards(): Promise<Locator> {
    return this.page.locator('.stat-card, .stats-row > *');
  }

  /** 等待统计卡片加载 */
  async waitForStats() {
    await this.page.locator('.stat-card .stat-num').first().waitFor({ state: 'visible', timeout: 8000 });
  }

  // ========== 分页 ==========

  /** 等待分页组件可见 */
  async waitForPagination() {
    await this.page.locator('.el-pagination').first().waitFor({ state: 'visible', timeout: 5000 }).catch(() => {});
  }
}
