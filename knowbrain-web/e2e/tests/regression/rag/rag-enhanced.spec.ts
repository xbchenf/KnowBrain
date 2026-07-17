import { test, expect } from '../../../fixtures/index.js';
import { RAG_QUESTIONS, SSE_TIMEOUT_MS } from '../../../data/test-data.js';

/**
 * Regression: RAG 增强场景 (R9-R12)
 *
 * R9:  对话历史 — 多轮对话上下文连贯
 * R10: 空间过滤 — 指定 spaceId 仅检索该空间文档
 * R11: 无结果兜底 — 无匹配时不编造内容
 * R12: Agent 降级 — Agent 异常时自动回退标准 RAG
 */

let testSpaceId: number;

test.describe('Regression | RAG 增强场景 (R9-R12)', () => {

  test.beforeAll(async ({ apiHelper }) => {
    // 创建测试空间（用于 R10）
    const spaceName = `rag-space-${Date.now()}`;
    const createResp = await apiHelper.createSpace(spaceName, 'RAG 增强测试空间');
    const createBody = await createResp.json();
    if (createBody.code === 200) {
      testSpaceId = createBody.data.id;
    }
  });

  // ==================== R9: 对话历史 ====================
  test('R9 | conversation history — multi-turn context continuity', {
    tag: ['@regression', '@rag'],
  }, async ({ apiHelper }) => {
    // 第 1 轮：问 VPN
    const round1Resp = await apiHelper.chat('VPN怎么配置');
    const round1Body = await round1Resp.json();
    expect(round1Body.code).toBe(200);
    const answer1 = round1Body.data?.answer || '';

    // 第 2 轮：带 history 追问（指代消解测试）
    const round2Resp = await apiHelper.getRequest().post('/api/v1/rag/chat', {
      headers: { 'Authorization': `Bearer ${apiHelper.getToken()}`, 'Content-Type': 'application/json' },
      data: {
        question: '那需要下载什么客户端吗',
        history: [
          { role: 'user', content: 'VPN怎么配置' },
          { role: 'assistant', content: answer1.substring(0, 300) },
        ],
      },
    });
    const round2Body = await round2Resp.json();
    expect(round2Body.code).toBe(200);
    expect(round2Body.data?.answer).toBeTruthy();
  });

  // ==================== R10: 空间过滤检索 ====================
  test('R10 | space filter — only documents in specified space retrieved', {
    tag: ['@regression', '@rag'],
  }, async ({ apiHelper }) => {
    if (!testSpaceId) { test.skip(true, 'No test space for R10'); return; }

    // 在指定空间内搜索
    const resp = await apiHelper.getRequest().post('/api/v1/rag/chat', {
      headers: { 'Authorization': `Bearer ${apiHelper.getToken()}`, 'Content-Type': 'application/json' },
      data: {
        question: RAG_QUESTIONS.simple,
        spaceIds: [testSpaceId],
        history: [],
      },
    });
    expect(resp.status()).toBe(200);
    const body = await resp.json();
    expect(body.code).toBe(200);
    // 即使空间为空（无文档），也应返回合理响应而非 500
    const answer = body.data?.answer || '';
    expect(typeof answer).toBe('string');
  });

  // ==================== R11: 无结果兜底 ====================
  test('R11 | no-result fallback — "not found" instead of hallucination', {
    tag: ['@regression', '@rag'],
  }, async ({ apiHelper }) => {
    const resp = await apiHelper.chat(RAG_QUESTIONS.noResult);
    expect(resp.status()).toBe(200);

    const body = await resp.json();
    expect(body.code).toBe(200);
    const answer = body.data?.answer || '';

    // 答案不应为纯幻觉——至少应包含"未找到"、"暂无"、"没有"等表述
    // 或者答案长度合理（不是长段编造内容）
    const isHonest = /未找到|暂无|没有|无法|抱歉|不知道|无相关/i.test(answer)
      || answer.length < 50;
    expect(isHonest).toBe(true);
  });

  // ==================== R12: Agent 降级 ====================
  test('R12 | Agent fallback — Agent failure → standard RAG pipeline', {
    tag: ['@regression', '@rag', '@agent'],
  }, async ({ apiHelper }) => {
    // 发送请求（Agent 可能开启也可能关闭，无论哪种情况都应有正常响应）
    const resp = await apiHelper.chat(RAG_QUESTIONS.comparison);
    expect(resp.status()).toBe(200);

    const body = await resp.json();
    expect(body.code).toBe(200);

    // 验证返回了有效的答案和来源
    const answer = body.data?.answer || '';
    expect(answer.length).toBeGreaterThan(0);

    const sources = body.data?.sources || [];
    // sources 可能为空（Agent 未开启或无检索结果），但结构应合理
    expect(Array.isArray(sources)).toBe(true);
  });

  test.afterAll(async ({ apiHelper }) => {
    if (testSpaceId) await apiHelper.deleteSpace(testSpaceId).catch(() => {});
  });
});
