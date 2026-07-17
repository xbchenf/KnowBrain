import { test, expect } from '../../../fixtures/index.js';

/**
 * Regression: 场景配置 (R16-R18)
 *
 * R16: 分类 CRUD — 添加 → 查看树 → 验证可见
 * R17: 术语管理 — 添加术语 → 表格可见 → 删除
 * R18: FAQ CRUD — 添加 FAQ → 列表验证 → 编辑 → 删除
 */

const CAT_NAME = `E2E回归测试分类${Date.now()}`;
const CAT_KEY = `reg-test-cat-${Date.now()}`;
const TERM = `reg测试口语${Date.now()}`;
const FAQ_QUESTION = `回归测试问题${Date.now()}`;

test.describe('Regression | 场景配置 (R16-R18)', () => {

  // ==================== R16: 分类 CRUD ====================
  test('R16 | category CRUD — add → view in tree → verify', {
    tag: ['@regression', '@scenario'],
  }, async ({ adminPage, page }) => {
    // 1. 导航到场景配置页（默认显示知识分类 tab）
    await adminPage.goto('/scenarios');
    await page.waitForLoadState('networkidle');

    // 2. 等待 el-tree 渲染（分类 tab 用的是树，不是表格）
    await expect(page.locator('.el-tree, .cat-tree-panel').first()).toBeVisible({ timeout: 10000 });

    // 3. 点击「新增分类」
    await page.locator('button').filter({ hasText: '新增分类' }).first().click();
    const catDialog = page.locator('.el-dialog').filter({ hasText: /新增分类/i }).first();
    await expect(catDialog).toBeVisible({ timeout: 8000 });

    // 4. 填写表单
    await catDialog.locator('.el-form-item').filter({ hasText: '分类名称' }).first().locator('input').first().fill(CAT_NAME);
    await catDialog.locator('.el-form-item').filter({ hasText: '标识 Key' }).first().locator('input').first().fill(CAT_KEY);

    // 5. 提交
    await catDialog.locator('button').filter({ hasText: '确定' }).first().click();
    await adminPage.expectToast('分类创建成功');

    // 6. 验证树中出现新分类
    await expect(page.locator('.el-tree-node__content').filter({ hasText: CAT_NAME }).first())
      .toBeVisible({ timeout: 8000 });
  });

  // ==================== R17: 术语管理（API 操作，避免 UI MessageBox 复杂性） ====================
  test('R17 | glossary CRUD — add → verify table → delete (via API)', {
    tag: ['@regression', '@scenario'],
  }, async ({ adminPage, apiHelper, page }) => {
    // --- 添加术语（API） ---
    const createResp = await apiHelper.createGlossary({
      term: TERM,
      formal: 'E2E回归测试正式术语',
      synonyms: '同义A,同义B',
    });
    const createBody = await createResp.json();
    expect(createBody.code).toBe(200);
    const createdId = createBody.data?.id;

    // --- UI 验证 ---
    await adminPage.goto('/scenarios');
    await adminPage.switchTab('术语词典');
    await adminPage.waitForTable();

    // 验证表格中包含新术语
    await adminPage.expectTableContains(TERM);

    // --- 删除术语（API） ---
    if (createdId) {
      await apiHelper.deleteGlossary(createdId);
    }
  });

  // ==================== R18: FAQ CRUD ====================
  test('R18 | FAQ CRUD — add → verify → edit → delete', {
    tag: ['@regression', '@scenario'],
  }, async ({ adminPage, apiHelper, page }) => {
    // --- 新增 FAQ（API） ---
    const createResp = await apiHelper.createFaq({
      keywords: '回归测试,regtest,e2etest',
      question: FAQ_QUESTION,
      answer: '这是E2E回归测试的预设答案。如果您看到这条消息，说明FAQ匹配成功。',
      category: 'it-helpdesk',
    });
    const createBody = await createResp.json();
    expect(createBody.code).toBe(200);
    const createdId = createBody.data?.id;

    // --- UI 验证 ---
    await adminPage.goto('/scenarios');
    await adminPage.switchTab('预设问答');
    await adminPage.waitForTable();

    // --- 验证 FAQ 出现在列表中 ---
    await adminPage.expectTableContains(FAQ_QUESTION);

    // --- 编辑 FAQ（UI） ---
    const faqRow = page.locator('.el-table__body-wrapper tbody tr')
      .filter({ hasText: FAQ_QUESTION }).first();
    await faqRow.locator('button').filter({ hasText: '编辑' }).first().click();
    await page.waitForTimeout(500);

    const faqDialog = page.locator('.el-dialog').filter({ hasText: /编辑问答/i }).first();
    await expect(faqDialog).toBeVisible({ timeout: 8000 });

    // 修改问题文本
    const qInput = faqDialog.locator('.el-form-item').filter({ hasText: '标准问题' }).first().locator('input').first();
    await qInput.clear();
    await qInput.fill(FAQ_QUESTION + '_已编辑');

    await faqDialog.locator('button').filter({ hasText: '保存' }).first().click();
    await adminPage.expectToast('FAQ 已更新');

    // 清理
    const faqListResp = await apiHelper.listFaq();
    const faqs = (await faqListResp.json()).data || [];
    const toDelete = faqs.find((f: any) =>
      f.question === FAQ_QUESTION || f.question === FAQ_QUESTION + '_已编辑');
    if (toDelete) {
      await apiHelper.deleteFaq(toDelete.id);
    }
  });

  // ==================== 全局清理 ====================
  test.afterAll(async ({ apiHelper }) => {
    // 清理可能残留的分类
    const catListResp = await apiHelper.listCategories();
    const cats = (await catListResp.json()).data || [];
    const toDeleteCat = cats.find((c: any) => c.key === CAT_KEY || c.name === CAT_NAME);
    if (toDeleteCat) await apiHelper.deleteCategory(toDeleteCat.id).catch(() => {});

    // 清理可能残留的术语
    const glsListResp = await apiHelper.listGlossary();
    const glossaryItems = (await glsListResp.json()).data || [];
    const toDeleteGls = glossaryItems.find((g: any) => g.term === TERM || g.formal === 'E2E回归测试正式术语');
    if (toDeleteGls) await apiHelper.deleteGlossary(toDeleteGls.id).catch(() => {});

    // 清理可能残留的 FAQ
    const faqListResp = await apiHelper.listFaq();
    const faqs = (await faqListResp.json()).data || [];
    for (const f of faqs) {
      if (f.question?.includes('回归测试') || f.question === FAQ_QUESTION) {
        await apiHelper.deleteFaq(f.id).catch(() => {});
      }
    }
  });
});
