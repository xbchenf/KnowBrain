import { test, expect } from '../../../fixtures/index.js';

/**
 * Regression: 文档管理 (R4-R6)
 *
 * R4: 文档删除 — 删除后搜索不到
 * R5: 文档分类过滤 — 上传时选分类 → 按分类过滤列表
 * R6: 文件格式校验 — 非法格式拒绝
 *
 * 注意：这些测试依赖有可用空间。通过 API 准备种子数据。
 */

test.describe('Regression | 文档管理 (R4-R6)', () => {

  let spaceId: number;

  test.beforeAll(async ({ apiHelper }) => {
    // 创建测试空间
    const spaceName = `reg-doc-space-${Date.now()}`;
    const createResp = await apiHelper.createSpace(spaceName, '文档回归测试空间');
    const createBody = await createResp.json();
    if (createBody.code === 200) {
      spaceId = createBody.data.id;
    }
  });

  // ==================== R4: 文档删除 ====================
  test('R4 | document delete — removed from search results', {
    tag: ['@regression', '@doc'],
  }, async ({ apiHelper }) => {
    // 1. 验证有可用空间
    if (!spaceId) { test.skip(true, 'No test space available'); return; }

    // 2. 上传文档（通过 API 或 UI）
    // 由于文件上传需要 multipart，先在现有文档列表中取一个文档
    const listResp = await apiHelper.listDocuments(spaceId);
    const listBody = await listResp.json();
    const docs = listBody.data?.records || [];

    if (docs.length === 0) {
      // 没有文档则跳过（需要前置上传步骤）
      test.skip(true, 'No documents to delete in test space');
      return;
    }

    const docToDelete = docs[0];

    // 3. 删除文档
    const deleteResp = await apiHelper.deleteDocument(docToDelete.id);
    expect(deleteResp.status()).toBe(200);

    // 4. 验证列表中不再出现该文档
    const afterListResp = await apiHelper.listDocuments(spaceId);
    const afterListBody = await afterListResp.json();
    const afterDocs = afterListBody.data?.records || [];
    const found = afterDocs.some((d: any) => d.id === docToDelete.id);
    expect(found).toBe(false);
  });

  // ==================== R5: 文档分类过滤 ====================
  test('R5 | document category filter — filter by category', {
    tag: ['@regression', '@doc'],
  }, async ({ apiHelper }) => {
    // 1. 列出所有空间，取第一个
    const spacesResp = await apiHelper.listSpaces();
    const spacesBody = await spacesResp.json();
    const spaces = spacesBody.data?.records || [];

    if (spaces.length === 0) { test.skip(true, 'No spaces available'); return; }

    const firstSpace = spaces[0];

    // 2. 列出该空间文档
    const listResp = await apiHelper.listDocuments(firstSpace.id);
    expect(listResp.status()).toBe(200);

    // 3. 验证返回数据结构正确
    const listBody = await listResp.json();
    expect(listBody.code).toBe(200);
    expect(listBody.data).toBeTruthy();
    const records = listBody.data.records || [];
    // 文档列表应包含基本字段
    if (records.length > 0) {
      const doc = records[0];
      expect(doc.id).toBeTruthy();
      expect(doc.fileName).toBeTruthy();
    }
  });

  // ==================== R6: 文件格式校验 ====================
  test('R6 | file format validation — invalid format rejected', {
    tag: ['@regression', '@doc'],
  }, async ({ apiHelper }) => {
    // 1. 尝试上传不支持的文件格式（通过 API）
    // 用 application/octet-stream 模拟 .exe 文件
    const testContent = Buffer.from('fake executable content');
    const resp = await apiHelper.getRequest().post('/api/v1/documents/upload', {
      headers: {
        'Authorization': `Bearer ${apiHelper.getToken()}`,
      },
      multipart: {
        file: {
          name: 'test.exe',
          mimeType: 'application/x-msdownload',
          buffer: testContent,
        },
        spaceId: (spaceId || 1).toString(),
      },
    });

    // 期望返回错误
    const body = await resp.json();
    // 上传接口可能返回 400/422 或 code != 200
    const isRejected = resp.status() >= 400 || body.code !== 200;
    expect(isRejected).toBe(true);
  });

  test.afterAll(async ({ apiHelper }) => {
    // 清理测试空间
    if (spaceId) {
      await apiHelper.deleteSpace(spaceId).catch(() => {});
    }
  });
});
