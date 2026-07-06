# P2 — 空间 API 适配 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 空间创建/编辑支持 departmentScope 参数，PUBLIC/TEAM 空间拒绝成员管理 API

**Architecture:** 修改 SpaceController 和 SpaceService，增加 departmentScope 字段接收，成员管理端点按可见性拦截

**Spec:** [2026-07-04-department-team-permission-design.md](../specs/2026-07-04-department-team-permission-design.md)

**前置:** P0 + P1 已完成

## Global Constraints

- 统一返回体 `Result<T>`
- departmentScope 前端传数组 `[1,2,5]`，后端转逗号分隔字符串 `"1,2,5"` 存储
- PUBLIC/TEAM 空间调用成员 API 返回 400
- PRIVATE 空间成员管理不变

---

### Task 1: SpaceService 支持 departmentScope

**Files:**
- Modify: `knowbrain-server/src/main/java/com/knowbrain/space/SpaceService.java`
- Modify: `knowbrain-server/src/main/java/com/knowbrain/space/SpaceServiceImpl.java`

- [ ] **Step 1: SpaceService 接口增加参数**

```java
// create 方法签名改为：
Space create(String name, String description, Long ownerId, String visibility, String departmentScope);

// update 方法签名改为：
Space update(Long id, String name, String description, String visibility, String departmentScope);
```

- [ ] **Step 2: SpaceServiceImpl 实现**

```java
// create 方法：
@Override
public Space create(String name, String description, Long ownerId, String visibility, String departmentScope) {
    Space space = new Space();
    space.setName(name);
    space.setDescription(description);
    space.setOwnerId(ownerId);
    space.setVisibility(visibility != null ? visibility : "PRIVATE");
    space.setDepartmentScope(departmentScope);  // 新增
    spaceMapper.insert(space);
    log.info("空间创建: id={}, name={}, ownerId={}, visibility={}", space.getId(), name, ownerId, visibility);
    return space;
}

// update 方法，在 if (visibility != null) 后添加：
if (departmentScope != null) space.setDepartmentScope(departmentScope);
```

- [ ] **Step 3: Commit**

```bash
git commit -m "feat: SpaceService 支持 departmentScope 参数（P2 空间 API 适配）"
```

---

### Task 2: SpaceController 适配

**Files:**
- Modify: `knowbrain-server/src/main/java/com/knowbrain/space/SpaceController.java`

- [ ] **Step 1: create() 接收 departmentScope 数组**

```java
// 在 create() 方法中，visibility 之后添加：
String visibility = request.getOrDefault("visibility", "PRIVATE");
// 新增：departmentScope 数组 → 逗号分隔字符串
String departmentScope = null;
Object deptScopeObj = request.get("departmentScope");
if (deptScopeObj instanceof List<?> list && !list.isEmpty()) {
    departmentScope = list.stream()
            .map(Object::toString)
            .collect(Collectors.joining(","));
}
// 调用改为：
Space space = spaceService.create(name, desc, userId, visibility, departmentScope);
```

需要添加 import：
```java
import java.util.List;
import java.util.stream.Collectors;
```

- [ ] **Step 2: update() 接收 departmentScope 数组**

```java
// 在 update() 方法中，同样处理：
String departmentScope = null;
Object deptScopeObj = request.get("departmentScope");
if (deptScopeObj instanceof List<?> list && !list.isEmpty()) {
    departmentScope = list.stream()
            .map(Object::toString)
            .collect(Collectors.joining(","));
}
// 调用改为：
Space space = spaceService.update(id,
        request.get("name"),
        request.get("description"),
        request.get("visibility"),
        departmentScope);
```

- [ ] **Step 3: 成员管理 API 拒绝 PUBLIC/TEAM**

在 members()、addMember()、removeMember() 三个方法开头添加：

```java
// 在 permissionService.checkReadAccess(id, userId) 之后添加：
Space space = spaceService.getById(id);
if ("PUBLIC".equals(space.getVisibility()) || "TEAM".equals(space.getVisibility())) {
    return Result.fail(400, "PUBLIC 和 TEAM 空间无需管理成员");
}
```

members() 需要把返回值从 `Result<List<SpaceMember>>` 改为 `Result<Object>` 以兼容 `Result.fail()`，或者直接用 try-catch。

更简洁的方式：`return Result.fail(400, "...")` 在三个方法中（members 返回类型需要适配）。

- [ ] **Step 4: 验证编译**

```bash
cd knowbrain-server && mvn compile -q
```

- [ ] **Step 5: Commit**

```bash
git commit -m "feat: SpaceController 适配 — departmentScope 数组接收 + PUBLIC/TEAM 拒绝成员管理（P2）"
```

---

## 完成标准

- [ ] 创建空间时可传入 departmentScope 数组
- [ ] 编辑空间时可更新 departmentScope
- [ ] PUBLIC 空间调用 GET/POST/DELETE /members 返回 400
- [ ] TEAM 空间调用 GET/POST/DELETE /members 返回 400
- [ ] PRIVATE 空间成员管理正常
