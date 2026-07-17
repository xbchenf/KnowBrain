import { Page, expect } from '@playwright/test';
import { BasePage } from './BasePage.js';
import { SSE_TIMEOUT_MS } from '../data/test-data.js';

/**
 * 问答页面 — 核心 ChatView 页面。
 */
export class ChatPage extends BasePage {
  private questionInput = this.page.locator('textarea.chat-input').first();
  private sendButton = this.page.locator('button.send-btn').first();
  private answerArea = this.page.locator('[class*="message"], [class*="answer"], [class*="chat"]').first();

  constructor(page: Page) {
    super(page);
  }

  async goto() {
    await this.navigate('/');
  }

  async askQuestion(question: string) {
    await this.questionInput.fill(question);
    await this.sendButton.click();
  }

  /** 等待回答出现 */
  async waitForAnswer(timeout = SSE_TIMEOUT_MS) {
    await this.page.waitForTimeout(500); // 等待 SSE 开始推送
    // 等待非空文本出现在回答区域
    await expect(this.page.locator('.msg-row.assistant, .msg-ai-text, .chat-message--assistant').first()).toContainText(/\S/, { timeout });
  }

  /** 等待 SSE 事件完成（检查 sources 或 done 标记） */
  async waitForStreamComplete(timeout = SSE_TIMEOUT_MS) {
    await this.page.waitForTimeout(timeout);
  }

  /** 获取页面显示的答案文本 */
  async getAnswerText(): Promise<string> {
    const answer = this.page.locator('.msg-row.assistant, .msg-ai-text').first();
    await answer.waitFor({ state: 'visible', timeout: 10000 });
    return (await answer.textContent()) || '';
  }
}
