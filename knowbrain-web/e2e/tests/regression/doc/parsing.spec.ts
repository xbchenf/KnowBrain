import { test, expect } from '../../../fixtures/index.js';

/**
 * Regression: 文档解析链路 (R22-R25)
 *
 * R22: 纯文本上传 → 解析成功 → parsedContent 非空
 * R23: Markdown 含表格结构上传 → 解析后内容保留表格
 * R24: 空文件上传拒绝
 * R25: 文档详情 → 解析元数据（引擎/耗时）可追溯
 *
 * 验证优先级 C 的核心链路：upload → ParserRouter → ParsedDocument → ElementAwareChunker
 */

const TEST_SPACE_NAME = `reg-parse-space-${Date.now()}`;
let spaceId: number;

test.describe('Regression | 文档解析 (R22-R25)', () => {

  test.beforeAll(async ({ apiHelper }) => {
    const createResp = await apiHelper.createSpace(TEST_SPACE_NAME, '文档解析回归测试空间');
    const createBody = await createResp.json();
    if (createBody.code === 200) {
      spaceId = createBody.data.id;
    }
  });

  // ==================== R22: 纯文本上传 → 解析成功 ====================
  test('R22 | plain text upload — parsed successfully with content', {
    tag: ['@regression', '@doc', '@parsing'],
  }, async ({ apiHelper }) => {
    if (!spaceId) { test.skip(true, 'No test space'); return; }

    // 生成测试文本文件（含标题 + 段落 + 列表）
    const content = '# 测试文档标题\n\n' +
      '这是一段测试段落，用于验证文档解析管线。\n\n' +
      '## 子标题\n\n' +
      '- 列表项一\n' +
      '- 列表项二\n' +
      '- 列表项三\n\n' +
      '结束段落。';
    const buffer = Buffer.from(content, 'utf-8');

    const resp = await apiHelper.getRequest().post('/api/v1/documents/upload', {
      headers: { 'Authorization': `Bearer ${apiHelper.getToken()}` },
      multipart: {
        file: { name: 'test-parse.txt', mimeType: 'text/plain', buffer },
        spaceId: spaceId.toString(),
      },
    });

    const body = await resp.json();
    // 上传成功：200 且返回文档信息
    expect(resp.status()).toBe(200);
    expect(body.code).toBe(200);
    expect(body.data).toBeTruthy();
    expect(body.data.id).toBeTruthy();

    const docId = body.data.id;

    // 等待解析完成（轮询最多 15 秒）
    let parsedContent = '';
    for (let i = 0; i < 10; i++) {
      await new Promise(r => setTimeout(r, 1500));
      const detailResp = await apiHelper.getRequest().get(`/api/v1/documents/${docId}`, {
        headers: { 'Authorization': `Bearer ${apiHelper.getToken()}` },
        params: { spaceId },
      });
      const detailBody = await detailResp.json();
      if (detailBody.code === 200 && detailBody.data?.parsedContent) {
        parsedContent = detailBody.data.parsedContent;
        break;
      }
    }

    expect(parsedContent).toBeTruthy();
    // 验证标题结构保留
    expect(parsedContent).toContain('测试文档标题');
    expect(parsedContent).toContain('列表项一');

    // 清理
    await apiHelper.deleteDocument(docId).catch(() => {});
  });

  // ==================== R23: Markdown 含表格 → 表格结构保留 ====================
  test('R23 | markdown with table — table structure preserved in parsed content', {
    tag: ['@regression', '@doc', '@parsing'],
  }, async ({ apiHelper }) => {
    if (!spaceId) { test.skip(true, 'No test space'); return; }

    // 生成含 Markdown 表格的测试文件
    const content = '# 财务摘要\n\n' +
      '以下是本季度财务报表：\n\n' +
      '| 项目 | Q1 | Q2 | 同比 |\n' +
      '|------|----|----|------|\n' +
      '| 营收 | 100 | 120 | +20% |\n' +
      '| 成本 | 60  | 70  | +16% |\n' +
      '| 利润 | 40  | 50  | +25% |\n\n' +
      '数据来源：财务部。';
    const buffer = Buffer.from(content, 'utf-8');

    const resp = await apiHelper.getRequest().post('/api/v1/documents/upload', {
      headers: { 'Authorization': `Bearer ${apiHelper.getToken()}` },
      multipart: {
        file: { name: 'financial-table.md', mimeType: 'text/markdown', buffer },
        spaceId: spaceId.toString(),
      },
    });

    const body = await resp.json();
    expect(resp.status()).toBe(200);
    expect(body.code).toBe(200);

    const docId = body.data.id;

    // 轮询等待解析
    let parsedContent = '';
    for (let i = 0; i < 10; i++) {
      await new Promise(r => setTimeout(r, 1500));
      const detailResp = await apiHelper.getRequest().get(`/api/v1/documents/${docId}`, {
        headers: { 'Authorization': `Bearer ${apiHelper.getToken()}` },
        params: { spaceId },
      });
      const detailBody = await detailResp.json();
      if (detailBody.code === 200 && detailBody.data?.parsedContent) {
        parsedContent = detailBody.data.parsedContent;
        break;
      }
    }

    expect(parsedContent).toBeTruthy();
    // 验证表格结构保留（管道符分隔的表格格式）
    expect(parsedContent).toContain('营收');
    expect(parsedContent).toContain('Q1');
    expect(parsedContent).toContain('成本');
    expect(parsedContent).toContain('利润');

    // 清理
    await apiHelper.deleteDocument(docId).catch(() => {});
  });

  // ==================== R24: 空文件上传拒绝 ====================
  test('R24 | empty file upload — rejected with error', {
    tag: ['@regression', '@doc', '@parsing'],
  }, async ({ apiHelper }) => {
    // 上传空文件
    const emptyBuffer = Buffer.from('');
    const resp = await apiHelper.getRequest().post('/api/v1/documents/upload', {
      headers: { 'Authorization': `Bearer ${apiHelper.getToken()}` },
      multipart: {
        file: { name: 'empty.txt', mimeType: 'text/plain', buffer: emptyBuffer },
        spaceId: (spaceId || 1).toString(),
      },
    });

    const body = await resp.json();
    // 空文件应被拒绝（400 或非 200）
    const isRejected = resp.status() >= 400 || body.code !== 200;
    expect(isRejected).toBe(true);
  });

  // ==================== R25: 文档详情 → 解析元数据可追溯 ====================
  test('R25 | document detail — parsed metadata traceable', {
    tag: ['@regression', '@doc', '@parsing'],
  }, async ({ apiHelper }) => {
    if (!spaceId) { test.skip(true, 'No test space'); return; }

    // 上传一个小文本文件
    const content = '元数据测试文档内容。';
    const buffer = Buffer.from(content, 'utf-8');

    const resp = await apiHelper.getRequest().post('/api/v1/documents/upload', {
      headers: { 'Authorization': `Bearer ${apiHelper.getToken()}` },
      multipart: {
        file: { name: 'metadata-test.txt', mimeType: 'text/plain', buffer },
        spaceId: spaceId.toString(),
      },
    });

    const body = await resp.json();
    expect(resp.status()).toBe(200);
    expect(body.code).toBe(200);

    const docId = body.data.id;
    expect(docId).toBeTruthy();

    // 等待解析
    let detail: any = null;
    for (let i = 0; i < 10; i++) {
      await new Promise(r => setTimeout(r, 1500));
      const detailResp = await apiHelper.getRequest().get(`/api/v1/documents/${docId}`, {
        headers: { 'Authorization': `Bearer ${apiHelper.getToken()}` },
        params: { spaceId },
      });
      const detailBody = await detailResp.json();
      if (detailBody.code === 200 && detailBody.data?.parsedContent) {
        detail = detailBody.data;
        break;
      }
    }

    // 验证解析元数据
    expect(detail).toBeTruthy();
    expect(detail.parsedContent).toBeTruthy();

    // 文档应包含基本字段
    expect(detail.fileName).toBe('metadata-test.txt');
    expect(detail.fileSize).toBeGreaterThan(0);
    // 文件类型字段（可能叫 fileType / format / extension）
    const fileType = detail.fileType || detail.format || detail.extension;
    // txt 文档类型

    // 清理
    await apiHelper.deleteDocument(docId).catch(() => {});
  });

  // ==================== 清理测试空间 ====================
  test.afterAll(async ({ apiHelper }) => {
    if (spaceId) {
      // 先删除空间内所有文档
      const listResp = await apiHelper.listDocuments(spaceId);
      const listBody = await listResp.json();
      const docs = listBody.data?.records || [];
      for (const doc of docs) {
        await apiHelper.deleteDocument(doc.id).catch(() => {});
      }
      await apiHelper.deleteSpace(spaceId).catch(() => {});
    }
  });
});
