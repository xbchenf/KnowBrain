# P4 — 数据迁移 + 端到端测试 实施计划

**Goal:** 验证权限矩阵：3 角色 × 3 可见性 = 9 种组合，跨部门检索隔离

**前置:** P0-P3 全部完成

---

### Task 1: 数据准备 — 创建测试用户

创建 2 个测试用户用于验证：

```bash
# 技术部普通用户
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"testtech","password":"123456","name":"测试-技术部","departmentId":1}'

# HR 部管理员
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"testhr","password":"123456","name":"测试-HR部","departmentId":3}'
```

---

### Task 2: 创建测试空间（3 种可见性）

用 admin 账号创建 3 个空间：

```bash
# 获取 admin token
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.data.token')

# PUBLIC 空间
curl -X POST http://localhost:8080/api/v1/spaces \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"公开知识库","visibility":"PUBLIC"}'

# TEAM 空间（仅技术部可见）
curl -X POST http://localhost:8080/api/v1/spaces \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"技术部内部库","visibility":"TEAM","departmentScope":[1]}'

# PRIVATE 空间
curl -X POST http://localhost:8080/api/v1/spaces \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"我的私有库","visibility":"PRIVATE"}'
```

---

### Task 3: 权限矩阵验证（9 种组合）

| 角色 | PUBLIC | TEAM(技术部) | PRIVATE |
|------|--------|-------------|---------|
| ADMIN(技术部) | ✅ 可见 | ✅ 可见 | ❌ 不可见 |
| USER(技术部) | ✅ 可见 | ✅ 可见 | ❌ 不可见 |
| USER(HR部) | ✅ 可见 | ❌ 不可见 | ❌ 不可见 |

用 curl 获取各用户的空间列表验证。

---

### Task 4: 角色访问控制验证

- USER 访问 /api/v1/admin/departments → 403
- MANAGER 访问 /api/v1/admin/users → 403
- ADMIN 访问 /api/v1/admin/** → 200

---

### Task 5: 跨部门检索隔离

- 技术部 USER 搜索 → 结果仅来自 PUBLIC + 技术部 TEAM 空间
- HR 部 USER 搜索 → 结果仅来自 PUBLIC 空间
