import { test, expect } from '../../fixtures/index.js';
import { FAQ_QUESTIONS, RAG_QUESTIONS, SSE_TIMEOUT_MS } from '../../data/test-data.js';

/**
 * Smoke: Chat / RAG 模块 (S4-S6)
 */
test.describe('Chat (S4-S6)', () => {

  test('S4 | FAQ hit — preset answer returned quickly', { tag: ['@smoke', '@rag'] }, async ({ chatPage, page }) => {
    await chatPage.goto();
    await chatPage.askQuestion(FAQ_QUESTIONS[0].question);

    // FAQ 命中应快速返回（无需 LLM）
    const answerEl = page.locator('.msg-row.assistant, .msg-ai-text').first();
    await expect(answerEl).toBeVisible({ timeout: 5000 });

    const text = await chatPage.getAnswerText();
    expect(text.length).toBeGreaterThan(0);
  });

  test('S5 | RAG answer — normal question gets response', { tag: ['@smoke', '@rag'] }, async ({ chatPage, page }) => {
    await chatPage.goto();
    await chatPage.askQuestion(RAG_QUESTIONS.simple);

    await page.waitForTimeout(SSE_TIMEOUT_MS);

    const text = await chatPage.getAnswerText();
    expect(text.length).toBeGreaterThan(0);
  });

  test('S6 | SSE stream — answer area populates after streaming', { tag: ['@smoke', '@rag'] }, async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('networkidle');

    const input = page.locator('textarea.chat-input').first();
    const sendBtn = page.locator('button.send-btn').first();
    await input.fill(RAG_QUESTIONS.simple);
    await sendBtn.click();

    // 等待 SSE 推送完成
    await page.waitForTimeout(SSE_TIMEOUT_MS);

    // 答案区域应有内容
    const answerEl = page.locator('.msg-row.assistant, .msg-ai-text').first();
    await expect(answerEl).toBeVisible({ timeout: 5000 });
    const text = await answerEl.textContent();
    expect(text!.length).toBeGreaterThan(5);
  });
});
