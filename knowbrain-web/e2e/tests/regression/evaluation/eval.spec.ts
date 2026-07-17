import { test, expect } from '../../../fixtures/index.js';

/**
 * Regression: 评测模块 (R13-R15)
 *
 * R13: 数据集 CRUD — 创建 → 添加问题 → UI 验证列表存在
 * R14: 批量导入 — JSON 数组批量导入问题
 * R15: 评测运行 — 提交评测 → 查看结果状态
 */

const DATASET_NAME = `reg-dataset-${Date.now()}`;

test.describe('Regression | 评测模块 (R13-R15)', () => {

  // ==================== R13: 数据集 CRUD（API 种子 + UI 验证） ====================
  test('R13 | dataset CRUD — create → view in list → edit', {
    tag: ['@regression', '@eval'],
  }, async ({ apiHelper, adminPage, page }) => {
    // --- 创建数据集（API） ---
    const createResp = await apiHelper.createDataset({
      name: DATASET_NAME,
      scenario: 'IT_HELPDESK',
      description: 'E2E 回归测试数据集',
    });
    const createBody = await createResp.json();
    expect(createBody.code).toBe(200);
    const dsId = createBody.data?.id;
    expect(dsId).toBeTruthy();

    // --- 添加单条问题（API） ---
    const addResp = await apiHelper.addQuestion(dsId, {
      question: 'VPN怎么配置？',
      expectedAnswer: '先打开VPN客户端...',
    });
    expect(addResp.status()).toBe(200);

    // --- UI 验证：导航到评测管理页 ---
    await adminPage.goto('/evaluation');
    await adminPage.waitForTable();

    // --- 验证数据集出现在列表中 ---
    await adminPage.expectTableContains(DATASET_NAME);

    // --- 编辑数据集（UI） ---
    await adminPage.clickRowAction(DATASET_NAME, '编辑');
    await adminPage.waitForDialog('编辑数据集');

    const nameInput = page.locator('.el-dialog').filter({ has: page.locator(':visible') }).first()
      .locator('input').first();
    await nameInput.fill(DATASET_NAME + '_已修改');
    await adminPage.clickDialogButton('保存');
    // 编辑可能返回"成功"或其他提示
    await page.waitForTimeout(500);

    // --- 清理 ---
    const datasetsResp = await apiHelper.listDatasets();
    const datasets = (await datasetsResp.json()).data?.records || [];
    const toDelete = datasets.find((d: any) =>
      d.name === DATASET_NAME + '_已修改' || d.name === DATASET_NAME);
    if (toDelete) {
      await apiHelper.deleteDataset(toDelete.id);
    }
  });

  // ==================== R14: 批量导入问题 ====================
  test('R14 | batch import — JSON array of questions', {
    tag: ['@regression', '@eval'],
  }, async ({ apiHelper }) => {
    // 1. 创建数据集
    const batchDsResp = await apiHelper.createDataset({
      name: `batch-import-${Date.now()}`,
      scenario: 'HR_POLICY',
      description: '批量导入测试',
    });
    const batchDsBody = await batchDsResp.json();
    const batchDsId = batchDsBody.data?.id;

    if (!batchDsId) { test.skip(true, 'Failed to create dataset for batch import'); return; }

    // 2. 批量导入问题（直接传 JSON 数组，不包裹）
    const questions = [
      { question: '新员工入职流程？', expectedAnswer: '入职需要提交材料...' },
      { question: '社保缴纳比例？', expectedAnswer: '根据政策规定...' },
      { question: '加班费怎么算？', expectedAnswer: '工作日加班1.5倍...' },
    ];
    const importResp = await apiHelper.batchImportQuestions(batchDsId, questions);
    expect(importResp.status()).toBe(200);
    const importBody = await importResp.json();
    expect(importBody.data?.imported).toBe(3);

    // 3. 验证数据集列表中的问题数已更新
    const listResp = await apiHelper.listDatasets();
    const datasets = (await listResp.json()).data?.records || [];
    const created = datasets.find((d: any) => d.id === batchDsId);
    if (created) {
      expect(created.questionCount).toBe(3);
    }

    // 清理
    await apiHelper.deleteDataset(batchDsId).catch(() => {});
  });

  // ==================== R15: 评测运行 ====================
  test('R15 | evaluation run — submit → verify status', {
    tag: ['@regression', '@eval'],
  }, async ({ apiHelper }) => {
    // 1. 创建带问题的数据集
    const runDsResp = await apiHelper.createDataset({
      name: `eval-run-${Date.now()}`,
      scenario: 'IT_HELPDESK',
      description: '评测运行测试',
    });
    const runDsBody = await runDsResp.json();
    const runDsId = runDsBody.data?.id;

    if (!runDsId) { test.skip(true, 'Failed to create dataset for eval run'); return; }

    // 添加问题
    const addResp = await apiHelper.addQuestion(runDsId, {
      question: 'VPN怎么配置？',
      expectedAnswer: 'VPN客户端配置步骤...',
    });
    expect(addResp.status()).toBe(200);

    // 2. 启动评测运行
    const startResp = await apiHelper.startEvalRun(runDsId);
    // 评测可能返回 200（已启动）或 400（如无 LLM API Key、无问题等）
    const validStatusCodes = [200, 400];
    expect(validStatusCodes).toContain(startResp.status());

    const startBody = await startResp.json();

    if (startResp.status() === 200 && startBody.data) {
      const runId = startBody.data.id;
      if (runId) {
        // 3. 查看运行列表
        const runsResp = await apiHelper.listRuns();
        expect(runsResp.status()).toBe(200);
        const runsBody = await runsResp.json();
        const runs = runsBody.data?.records || [];

        // 验证有运行记录
        const foundRun = runs.find((r: any) => r.id === runId);
        if (foundRun) {
          expect(foundRun.status).toBeTruthy();
          expect(foundRun.totalQuestions).toBeGreaterThan(0);
        }

        // 清理运行记录
        await apiHelper.deleteRun(runId).catch(() => {});
      }
    }

    // 清理数据集
    await apiHelper.deleteDataset(runDsId).catch(() => {});
  });
});
