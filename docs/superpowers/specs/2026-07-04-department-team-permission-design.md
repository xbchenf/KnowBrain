# 部门体系 + TEAM 权限模式 + 角色控制 — 设计方案

> 日期：2026-07-04 | 状态：待评审

---

## 一、现状与问题

### 1.1 当前状态

```
kb_sys_user ──────────── kb_space ──────────── kb_space_member
  id                        id                    id
  username                  name                  space_id
  name                      owner_id              user_id
  phone                     visibility            role
  role (USER/ADMIN)         departmentScope (未使用)
  status (ACTIVE/DISABLED)
```

**两个核心缺口：**
1. User 没有部门字段 → TEAM 模式无法实施
2. 角色只有 USER/ADMIN 二元 → 无法区分「能上传文档的人」和「只能提问的人」

### 1.2 设计目标

**A. 空间可见性（已定）**

| 可见性 | 谁能看 | 成员管理 |
|--------|--------|---------|
| PUBLIC | 全公司 + 未登录 | 不需要 |
| TEAM | 指定部门全员 | 不需要（部门自动） |
| PRIVATE | 显式添加的成员 | 需要 |

**B. 系统角色（新增）**

| 角色 | 能做什么 | 不能做什么 |
|------|---------|-----------|
| **ADMIN** 系统管理员 | 管理用户、管理部门的增删改查、所有空间管理、访问前台对话 | — |
| **MANAGER** 知识管理员 | 创建/管理空间、上传/删除文档、访问前台对话 | 不能管理用户、不能管理部门 |
| **USER** 普通员工 | **只能**访问前台对话页面 | 不能登录后台 |

```
        ADMIN ─── 后台(全部) + 前台对话
       /
  MANAGER ─── 后台(文档/空间) + 前台对话
     /
  USER  ─── 前台对话 only
```

---

## 二、数据库设计

### 2.1 新增：部门表 `kb_department`

```sql
CREATE TABLE kb_department (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,         -- 部门名称（如：技术部、人力资源部）
    parent_id   BIGINT DEFAULT 0,              -- 上级部门 ID（0=顶级部门）
    sort_order  INT DEFAULT 0,                 -- 排序
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT DEFAULT 0              -- 逻辑删除
);
```

### 2.2 变更：`kb_sys_user`

```sql
-- 新增部门归属
ALTER TABLE kb_sys_user ADD COLUMN department_id BIGINT DEFAULT NULL;

-- role 字段语义变更
-- 旧：USER / ADMIN（二元）
-- 新：USER / MANAGER / ADMIN（三层）
-- 已有数据：ADMIN 保留，其余全部改为 USER（MANAGER 由管理员手动指定）
UPDATE kb_sys_user SET role = 'USER' WHERE role NOT IN ('ADMIN', 'MANAGER');
```

### 2.3 `kb_space` 的 `departmentScope` 调整

当前字段 `VARCHAR` 存逗号分隔部门名。改为存部门 ID，便于 JOIN 和精确匹配：

```sql
-- 当前
departmentScope VARCHAR(255)  -- 如 "技术部,产品部"

-- 改为
department_scope VARCHAR(255) DEFAULT NULL
    COMMENT '可见部门 ID 列表（逗号分隔），仅 visibility=TEAM 时生效'
```

> 为兼容现有数据暂不改名，内容从「部门名」改为「部门 ID」，如 `"1,3,5"`。

---

## 三、权限模型重设计

### 3.1 读权限矩阵

> **前置条件**：所有用户必须登录才能使用系统（前台对话 + 后台管理）。

```
checkReadAccess(spaceId, userId)：

  switch (space.visibility):
    case PUBLIC:
      → 直接放行（所有已登录用户）

    case TEAM:
      → space.departmentScope 包含用户的 departmentId？放行
      → user.isOwner？放行
      → 否则拒绝

    case PRIVATE:
      → user.isOwner？放行
      → user 是 kb_space_member 成员？放行
      → 否则拒绝
```

### 3.2 写权限（统一由角色控制）

```
checkWriteAccess(spaceId, userId)：

  → user.role == ADMIN ？放行
  → user.role == MANAGER ？放行
  → user.isSpaceOwner ？放行
  → 否则拒绝
```

**写权限 = 角色决定，与空间无关。** 不再有空间级 Editor，空间成员只控制读权限。

> **设计决策**：ADMIN 和 MANAGER 本身就是管理角色，没必要在每个空间再指定 Editor。简化后只有一套权限体系。如果将来需要更细粒度的空间级写权限，再扩展 SpaceMember 的 role。

### 3.3 检索权限 `getAccessibleSpaceIds()`

```java
// 所有调用方已确保 userId 非空（前端强制登录）
public List<Long> getAccessibleSpaceIds(Long userId) {
    List<Long> ids = new ArrayList<>();

    for (Space s : allSpaces) {
        // 1. PUBLIC → 所有登录用户
        if ("PUBLIC".equals(s.getVisibility())) {
            ids.add(s.getId());
            continue;
        }

        // 2. OWNER → 自己的空间全部可见
        if (s.getOwnerId().equals(userId)) {
            ids.add(s.getId());
            continue;
        }

        // 3. TEAM → 部门匹配
        if ("TEAM".equals(s.getVisibility())) {
            Long userDept = getUserDepartment(userId);
            if (userDept != null && isInDepartmentScope(s.getDepartmentScope(), userDept)) {
                ids.add(s.getId());
                continue;
            }
        }

        // 4. PRIVATE → 显式成员
        if (isMember(s.getId(), userId)) {
            ids.add(s.getId());
        }
    }
    return ids;
}
```

---

## 四、角色访问控制（新增）

### 4.1 三层角色定义

| 角色 | JWT role | 后台登录 | 后台权限范围 | 前台对话 |
|------|----------|---------|------------|---------|
| **系统管理员** | `ADMIN` | ✅ | 全部：用户管理、部门管理、所有空间管理、系统配置 | ✅ |
| **知识管理员** | `MANAGER` | ✅ | 受限：自己创建/管理的空间、文档上传/删除 | ✅ |
| **普通员工** | `USER` | ❌ | 无后台权限 | ✅ |

### 4.2 后台页面级权限

| 页面/功能 | ADMIN | MANAGER | USER |
|-----------|-------|---------|------|
| 登录后台 | ✅ | ✅ | ❌ (重定向到前台) |
| 仪表盘/统计 | ✅ | ✅ | — |
| 创建空间 | ✅ | ✅ | — |
| 删除任意空间 | ✅ | ❌ (仅自己的) | — |
| 管理成员 | ✅ | ✅ (仅自己的空间) | — |
| 部门管理 | ✅ | ❌ | — |
| 用户管理 | ✅ | ❌ | — |
| 访问前台对话 | ✅ | ✅ | ✅ |

### 4.3 API 层拦截

```
AuthInterceptor.preHandle() 增强：

  POST /api/v1/admin/** → 检查 role == ADMIN
  PUT  /api/v1/admin/** → 检查 role == ADMIN
  DELETE /api/v1/admin/** → 检查 role == ADMIN

后台登录 (/api/v1/auth/login)：
  → 返回 role 字段，前端根据 role 决定是否允许进入后台

Admin SPA 路由守卫：
  → router.beforeEach → 检查 localStorage.kb_user.role
  → USER → 重定向到 /（前台）
  → MANAGER/ADMIN → 放行
```

### 4.4 注册默认角色

```
POST /api/v1/auth/register：
  → 默认 role = "USER"（普通员工，不能登录后台）
  → 管理员在后台手动将用户提升为 MANAGER
```

---

## 五、API 设计

### 5.1 部门管理（ADMIN 权限）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/admin/departments` | 部门树（含子部门） |
| POST | `/api/v1/admin/departments` | 创建部门 |
| PUT | `/api/v1/admin/departments/{id}` | 更新部门 |
| DELETE | `/api/v1/admin/departments/{id}` | 删除部门（需检查无用户关联） |

### 4.2 用户管理（ADMIN 权限）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/admin/users` | 用户列表（分页，可按部门过滤） |
| PUT | `/api/v1/admin/users/{id}` | 更新用户（含部门分配） |
| PUT | `/api/v1/admin/users/{id}/department` | 单独变更用户部门 |

### 4.3 注册接口增强

`POST /api/v1/auth/register` 增加可选字段 `departmentId`：

```json
{
  "username": "zhangsan",
  "password": "123456",
  "name": "张三",
  "phone": "13800138000",
  "departmentId": 1
}
```

### 4.4 空间创建/编辑增强

`POST /api/v1/spaces` 和 `PUT /api/v1/spaces/{id}` 增加字段：

```json
{
  "name": "技术部文档库",
  "description": "...",
  "visibility": "TEAM",
  "departmentScope": [1, 2, 5]   // 部门 ID 数组，仅 TEAM 模式
}
```

后端将 `departmentScope` 转为逗号分隔字符串存储。

### 4.5 成员管理（仅 PRIVATE 需要，PUBLIC/TEAM 拒绝）

| 方法 | 路径 | PUBLIC | TEAM | PRIVATE |
|------|------|--------|------|---------|
| GET | `/spaces/{id}/members` | 拒绝 400 | 拒绝 400 | 返回显式成员 |
| POST | `/spaces/{id}/members` | 拒绝 400 | 拒绝 400 | 添加成员（读权限） |
| DELETE | `/spaces/{id}/members/{userId}` | 拒绝 400 | 拒绝 400 | 移除成员 |

> 写权限由 ADMIN/MANAGER 角色统一控制。空间成员仅用于 PRIVATE 空间的读授权。

---

## 五、前端调整

### 5.1 部门管理页面（管理员）

- 左侧部门树，右侧用户列表（按部门过滤）
- 支持新建/编辑/删除部门
- 支持拖拽用户换部门

### 5.2 空间创建/编辑对话框

- 选择可见性时展示不同提示：
  - PUBLIC：「全公司可读，无需添加成员」
  - TEAM：显示部门多选下拉框（`<el-select multiple>`）
  - PRIVATE：显示当前提示
- TEAM/PUBLIC 模式下隐藏「成员管理」标签页

### 5.3 注册页面

- 注册表单增加「所属部门」下拉选择（可选）

### 5.4 成员管理（仅 PRIVATE 空间显示）

- 从全公司部门树中选择用户添加
- 显示部门 → 用户层级

---

## 七、实施计划（按优先级）

> 依赖关系：P1 依赖 P0 的数据库层，P2 依赖 P1 的 API，P3 依赖 P2。

---

### P0 — 数据基础（必须先完成，约 45 min）

后续所有功能都依赖这层，打地基。

| # | 步骤 | 文件 | 预估 |
|---|------|------|------|
| 1.1 | 创建 `kb_department` 建表 SQL，放到 `docker/mysql/init/` 目录 | `docker/mysql/init/02-department.sql` | 10 min |
| 1.2 | 创建 `Department` 实体类 + `DepartmentMapper` | `auth/Department.java`, `auth/DepartmentMapper.java` | 10 min |
| 1.3 | `SysUser` 新增 `departmentId` 字段，DB 加列 | `auth/SysUser.java` | 5 min |
| 1.4 | 初始化种子数据：预置几个部门（技术部、产品部、HR、财务等）+ 初始化 ADMIN 账号 | `docker/mysql/init/02-department.sql` | 10 min |
| 1.5 | 验证：启动后 DB 有表有数据，`SysUser` 能读写 `departmentId` | — | 10 min |

**验证标准**：`SELECT * FROM kb_department` 有数据，`SysUser` 实体 `departmentId` 字段可用。

---

### P1 — 后端核心 API（约 120 min）

P0 完成后开始，前后端并行开发的前提。

| # | 步骤 | 文件 | 预估 |
|---|------|------|------|
| **2.1 部门管理** | | | |
| 2.1.1 | `DepartmentService`：树形查询、CRUD | `auth/DepartmentService.java` | 15 min |
| 2.1.2 | `DepartmentController`：`GET/POST/PUT/DELETE /api/v1/admin/departments` | `auth/DepartmentController.java` | 15 min |
| **2.2 用户管理** | | | |
| 2.2.1 | `UserService`：列表查询（分页+按部门过滤）、更新用户（部门/角色）| `auth/UserService.java` | 10 min |
| 2.2.2 | `UserController`：`GET/PUT /api/v1/admin/users` | `auth/UserController.java` | 10 min |
| **2.3 权限核心** | | | |
| 2.3.1 | `PermissionService.checkReadAccess()` 重写：加入 TEAM 逻辑 | `permission/PermissionService.java` | 20 min |
| 2.3.2 | `PermissionService.checkWriteAccess()` 简化：只检查角色 | `permission/PermissionService.java` | 10 min |
| 2.3.3 | `getAccessibleSpaceIds()` 重写：TEAM 部门匹配 + 去掉未登录分支 | `permission/PermissionService.java` | 15 min |
| **2.4 角色控制** | | | |
| 2.4.1 | `AuthInterceptor` 增强：`/api/v1/admin/**` 检查 ADMIN 角色 | `auth/AuthInterceptor.java` | 10 min |
| 2.4.2 | `WebMvcConfig` 更新：RAG 端点从免登录列表移除 | `config/WebMvcConfig.java` | 5 min |
| 2.4.3 | `AuthController.register()` 增加 `departmentId` 参数，默认 `role=USER` | `auth/AuthController.java` | 10 min |

**验证标准**：curl 测试 TEAM 空间 — 同部门可见，跨部门不可见；USER 访问 `/api/v1/admin/**` 返回 403。

---

### P2 — 空间 API 适配（约 45 min）

P1 权限层就绪后，让空间创建和成员管理按新规则运作。

| # | 步骤 | 文件 | 预估 |
|---|------|------|------|
| 3.1 | `SpaceController.create()` 增加 `departmentScope` 参数接收 | `space/SpaceController.java` | 10 min |
| 3.2 | `SpaceController.update()` 同上 | `space/SpaceController.java` | 5 min |
| 3.3 | `SpaceController.listMembers()` — PUBLIC/TEAM 返回 400 | `space/SpaceController.java` | 10 min |
| 3.4 | `SpaceController.addMember()` / `removeMember()` — PUBLIC/TEAM 返回 400 | `space/SpaceController.java` | 10 min |
| 3.5 | `RAGController` 移除 `getAccessibleSpaceIds` 的 null 降级逻辑 | `retrieval/engine/RAGController.java` | 5 min |
| 3.6 | 验证完整的 curl 测试链 | — | 5 min |

**验证标准**：PUBLIC/TEAM 空间调用成员 API 返回 400；PRIVATE 空间正常。

---

### P3 — 前端（约 120 min）

后端全部就绪后统一做前端，避免返工。

| # | 步骤 | 文件 | 预估 |
|---|------|------|------|
| **4.1 基础设施** | | | |
| 4.1.1 | admin API 模块新增：部门 API + 用户管理 API + `listDocuments` 修复 | `admin/src/api/index.ts` | 15 min |
| **4.2 页面开发** | | | |
| 4.2.1 | 部门管理页：左侧树 + 右侧维护表单 | `admin/src/views/DepartmentView.vue` (新) | 25 min |
| 4.2.2 | 用户管理页：列表 + 部门过滤 + 角色切换下拉 | `admin/src/views/UserView.vue` (新) | 25 min |
| 4.2.3 | 空间创建/编辑弹窗：可见性选择 + TEAM 显示部门多选 | `admin/src/views/DashboardView.vue` | 20 min |
| 4.2.4 | 空间详情页：PUBLIC/TEAM 隐藏成员管理 Tab | `admin/src/views/SpaceDetailView.vue` | 10 min |
| **4.3 路由与守卫** | | | |
| 4.3.1 | admin 路由守卫：USER 角色重定向到前台 | `admin/src/router/index.ts` | 10 min |
| 4.3.2 | admin 侧边栏加入部门管理、用户管理入口（ADMIN only） | `admin/src/App.vue` | 5 min |
| **4.4 前台适配** | | | |
| 4.4.1 | web 路由守卫：未登录重定向到登录页 | `web/src/router/index.ts` | 10 min |

**验证标准**：ADMIN 能看全部菜单，MANAGER 能看空间管理，USER 看不到后台。

---

### P4 — 数据迁移 + 端到端测试（约 30 min）

| # | 步骤 | 预估 |
|---|------|------|
| 5.1 | 已有 ADMIN 账号数据不变；其他用户批量迁为 USER | 5 min |
| 5.2 | 测试矩阵全覆盖：3 角色 × 3 可见性 = 9 种组合 | 20 min |
| 5.3 | 验证跨部门检索隔离：技术部 USER 搜不到 HR 的 TEAM 空间文档 | 5 min |

---

### 依赖关系总览

```
P0 (数据库) ──→ P1 (后端API) ──→ P2 (空间适配) ──→ P3 (前端) ──→ P4 (测试)
                    │
                    └── 角色控制 (2.4) 可独立提前做
```

总估约 **6 小时**，分 4 个优先级 14 个子任务，每个子任务有具体文件路径和验证标准。

---

## 八、设计决策记录

1. **写权限 = 角色，不搞空间级 Editor**：ADMIN 和 MANAGER 统一负责文档管理，空间成员只控制 PRIVATE 读权限，避免两套机制
2. **departmentScope 存 ID 不存名称**：部门改名不影响权限
3. **不删除现有 SpaceMember 表**：字段保留但简化用途（仅 PRIVATE 读）
4. **注册默认 role=USER**：不能登录后台，需管理员提升
5. **管理员由系统初始化创建**：首个 ADMIN 通过 SQL 脚本或环境变量注入
6. **全系统强制登录**：前台对话 + 后台管理均需登录。RAG /search /chat /chat/stream 从免登录列表中移除。未登录用户重定向到登录页。这样检索时总能拿到 userId 和部门信息，权限过滤一致可靠。
