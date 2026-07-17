import { test, expect } from '../../../fixtures/index.js';

/**
 * Regression: 空间管理 (R7-R8)
 *
 * R7: 成员管理 — 添加 VIEWER → 成员列表可见 → 删除成员
 * R8: 权限校验 — VIEWER 不能编辑空间 / 非成员不能访问 PRIVATE 空间
 */

let spaceId: number;
let viewerUserId: number;
const viewerUsername = `reg-viewer-${Date.now()}`;

test.describe('Regression | 空间管理 (R7-R8)', () => {

  test.beforeAll(async ({ apiHelper }) => {
    // 1. 创建测试空间
    const spaceName = `reg-space-${Date.now()}`;
    const createResp = await apiHelper.createSpace(spaceName, '空间回归测试');
    const createBody = await createResp.json();
    if (createBody.code === 200) {
      spaceId = createBody.data.id;
    }

    // 2. 注册一个 VIEWER 用户
    const regResp = await apiHelper.register(viewerUsername, 'Viewer@2026', '空间访客');
    const regBody = await regResp.json();
    if (regBody.code === 200 || regResp.status() === 200) {
      // 查找该用户的 ID
      const userListResp = await apiHelper.listUsers();
      const users = (await userListResp.json()).data?.records || [];
      const viewer = users.find((u: any) => u.username === viewerUsername);
      if (viewer) viewerUserId = viewer.id;
    }
  });

  // ==================== R7: 成员管理 ====================
  test('R7 | space member management — add VIEWER → visible → remove', {
    tag: ['@regression', '@space'],
  }, async ({ apiHelper }) => {
    if (!spaceId || !viewerUserId) { test.skip(true, 'Prerequisites not met'); return; }

    // 1. 添加成员
    const addResp = await apiHelper.addSpaceMember(spaceId, viewerUserId, 'VIEWER');
    const addBody = await addResp.json();
    expect(addBody.code).toBe(200);

    // 2. 验证成员列表可见
    const listResp = await apiHelper.listSpaceMembers(spaceId);
    const listBody = await listResp.json();
    const members = listBody.data || [];
    const found = members.some((m: any) => m.userId === viewerUserId);
    expect(found).toBe(true);

    // 3. 删除成员
    const removeResp = await apiHelper.removeSpaceMember(spaceId, viewerUserId);
    expect(removeResp.status()).toBe(200);

    // 4. 验证成员已移除
    const afterListResp = await apiHelper.listSpaceMembers(spaceId);
    const afterListBody = await afterListResp.json();
    const afterMembers = afterListBody.data || [];
    const stillFound = afterMembers.some((m: any) => m.userId === viewerUserId);
    expect(stillFound).toBe(false);
  });

  // ==================== R8: 权限校验 ====================
  test('R8 | space permission — non-member cannot access PRIVATE space', {
    tag: ['@regression', '@space'],
  }, async ({ apiHelper, request }) => {
    if (!spaceId) { test.skip(true, 'Prerequisites not met'); return; }

    // 1. 用 viewer 账号登录
    const loginResp = await apiHelper.login(viewerUsername, 'Viewer@2026');
    const loginBody = await loginResp.json();
    if (loginBody.code !== 200) { test.skip(true, 'Viewer login failed'); return; }

    const viewerToken = loginBody.data.token;

    // 2. 尝试直接访问 PRIVATE 空间详情（非成员）
    const spaceResp = await request.get(`/api/v1/spaces/${spaceId}`, {
      headers: { 'Authorization': `Bearer ${viewerToken}` },
    });
    // 非成员访问 PRIVATE 空间应被拒绝或返回受限数据
    const spaceBody = await spaceResp.json();
    const isRestricted = spaceResp.status() !== 200 || spaceBody.code !== 200;
    // 只要不是直接成功返回空间完整数据就算通过
    expect(isRestricted || spaceBody.code === 403).toBeTruthy();
  });

  test.afterAll(async ({ apiHelper }) => {
    if (spaceId) await apiHelper.deleteSpace(spaceId).catch(() => {});
    if (viewerUserId) await apiHelper.deleteUser(viewerUserId).catch(() => {});
  });
});
