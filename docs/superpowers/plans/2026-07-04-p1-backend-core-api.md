# P1 — 后端核心 API 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现部门管理 CRUD、用户管理、权限服务重写（TEAM 逻辑）、角色访问控制

**Architecture:** 在现有 Service→Controller 分层模式上扩展，复用已有的 MyBatis-Plus 和 JWT 基础设施。部门管理新增树形查询；权限服务从「空间成员」模型升级为「可见性+部门+角色」三因子模型；拦截器增加 ADMIN 角色检查。

**Tech Stack:** Java 17, Spring Boot 3.3, MyBatis-Plus, Lombok, JWT

**Spec:** [2026-07-04-department-team-permission-design.md](../specs/2026-07-04-department-team-permission-design.md)

**前置:** P0 已完成 — kb_department 表、Department 实体/Mapper、SysUser.departmentId 已就绪

## Global Constraints

- 基础包名：`com.knowbrain`
- 统一返回体 `Result<T>`
- API 路径前缀：`/api/v1/`
- 管理接口：`/api/v1/admin/**`（需 ADMIN 角色）
- 所有用户必须登录（前台+后台），RAG 端点从白名单移除
- 写权限 = 角色决定（ADMIN/MANAGER/Owner）
- TEAM 空间按部门 ID 匹配，departmentScope 存逗号分隔的部门 ID
- 注册默认 role=USER
- 遵循现有代码风格（`@RequiredArgsConstructor` 构造注入、Lombok、Javadoc）

---

### Task 1: DepartmentService + DepartmentController — 部门管理 CRUD

**Files:**
- Create: `knowbrain-server/src/main/java/com/knowbrain/auth/DepartmentService.java`
- Create: `knowbrain-server/src/main/java/com/knowbrain/auth/DepartmentController.java`

**Interfaces:**
- Consumes: `DepartmentMapper` (extends BaseMapper<Department>), `SysUserMapper` (check users before delete)
- Produces: `DepartmentService.listAsTree()`, `create()`, `update()`, `delete()`
- Produces: `GET/POST/PUT/DELETE /api/v1/admin/departments`

- [ ] **Step 1: 创建 DepartmentService**

```java
package com.knowbrain.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowbrain.common.GlobalExceptionHandler.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 部门管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentMapper departmentMapper;
    private final SysUserMapper userMapper;

    /**
     * 查询部门树（顶级部门 → 子部门嵌套）
     */
    public List<Department> listAsTree() {
        List<Department> all = departmentMapper.selectList(
                new LambdaQueryWrapper<Department>()
                        .orderByAsc(Department::getSortOrder));

        // 按 parentId 分组
        Map<Long, List<Department>> childrenMap = all.stream()
                .filter(d -> d.getParentId() != null && d.getParentId() > 0)
                .collect(Collectors.groupingBy(Department::getParentId));

        // 组装树：顶级部门 + 递归附加子节点
        List<Department> tree = new ArrayList<>();
        for (Department dept : all) {
            if (dept.getParentId() == null || dept.getParentId() == 0) {
                attachChildren(dept, childrenMap);
                tree.add(dept);
            }
        }
        return tree;
    }

    private void attachChildren(Department parent, Map<Long, List<Department>> childrenMap) {
        List<Department> children = childrenMap.get(parent.getId());
        parent.setChildren(children != null ? children : List.of());
        if (children != null) {
            for (Department child : children) {
                attachChildren(child, childrenMap);
            }
        }
    }

    /** 创建部门 */
    public Department create(Department dept) {
        departmentMapper.insert(dept);
        log.info("部门创建: id={}, name={}", dept.getId(), dept.getName());
        return dept;
    }

    /** 更新部门 */
    public Department update(Long id, Department updates) {
        Department dept = departmentMapper.selectById(id);
        if (dept == null) throw new BizException(404, "部门不存在");
        if (updates.getName() != null) dept.setName(updates.getName());
        if (updates.getParentId() != null) dept.setParentId(updates.getParentId());
        if (updates.getSortOrder() != null) dept.setSortOrder(updates.getSortOrder());
        departmentMapper.updateById(dept);
        log.info("部门更新: id={}", id);
        return dept;
    }

    /** 删除部门（需检查无用户关联） */
    public void delete(Long id) {
        long userCount = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getDepartmentId, id));
        if (userCount > 0) {
            throw new BizException(400, "该部门下还有 " + userCount + " 名用户，无法删除");
        }
        // 检查子部门
        long childCount = departmentMapper.selectCount(
                new LambdaQueryWrapper<Department>().eq(Department::getParentId, id));
        if (childCount > 0) {
            throw new BizException(400, "该部门下还有 " + childCount + " 个子部门，请先删除子部门");
        }
        departmentMapper.deleteById(id);
        log.info("部门删除: id={}", id);
    }
}
```

> **注意**：需要在 `Department.java` 实体中增加 `@TableField(exist = false)` 的 `children` 字段（见下方 Step 2）。

- [ ] **Step 2: Department 实体增加 children 字段**

修改 `knowbrain-server/src/main/java/com/knowbrain/auth/Department.java`，在类末尾（`deleted` 字段之后）添加：

```java
    /** 子部门列表（非数据库字段，仅用于树形返回） */
    @TableField(exist = false)
    private List<Department> children;
```

同时需要添加 import：

```java
import java.util.List;
```

- [ ] **Step 3: 创建 DepartmentController**

```java
package com.knowbrain.auth;

import com.knowbrain.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 部门管理控制器 — 仅 ADMIN 可访问（由 AuthInterceptor 拦截）
 */
@Tag(name = "部门管理", description = "部门 CRUD（ADMIN 权限）")
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    @Operation(summary = "部门树")
    @GetMapping
    public Result<List<Department>> list() {
        return Result.ok(departmentService.listAsTree());
    }

    @Operation(summary = "创建部门")
    @PostMapping
    public Result<Department> create(@RequestBody Department dept) {
        if (dept.getName() == null || dept.getName().isBlank()) {
            return Result.badRequest("部门名称不能为空");
        }
        return Result.ok("创建成功", departmentService.create(dept));
    }

    @Operation(summary = "更新部门")
    @PutMapping("/{id}")
    public Result<Department> update(@PathVariable Long id, @RequestBody Department dept) {
        return Result.ok("更新成功", departmentService.update(id, dept));
    }

    @Operation(summary = "删除部门")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        departmentService.delete(id);
        return Result.ok("删除成功", null);
    }
}
```

- [ ] **Step 4: 验证编译通过**

```bash
cd knowbrain-server && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add knowbrain-server/src/main/java/com/knowbrain/auth/Department.java \
        knowbrain-server/src/main/java/com/knowbrain/auth/DepartmentService.java \
        knowbrain-server/src/main/java/com/knowbrain/auth/DepartmentController.java
git commit -m "feat: 部门管理 CRUD（DepartmentService + Controller + 树形查询）"
```

---

### Task 2: UserService + UserController — 用户管理

**Files:**
- Create: `knowbrain-server/src/main/java/com/knowbrain/auth/UserService.java`
- Create: `knowbrain-server/src/main/java/com/knowbrain/auth/UserController.java`

**Interfaces:**
- Consumes: `SysUserMapper` (extends BaseMapper<SysUser>), `DepartmentMapper`
- Produces: `UserService.listUsers(page, size, departmentId, keyword)`, `updateUser(id, updates)`
- Produces: `GET/PUT /api/v1/admin/users`

- [ ] **Step 1: 创建 UserService**

```java
package com.knowbrain.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowbrain.common.GlobalExceptionHandler.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 用户管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper userMapper;

    /**
     * 分页查询用户列表，支持按部门和关键词过滤
     */
    public Page<SysUser> listUsers(int page, int size, Long departmentId, String keyword) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (departmentId != null) {
            wrapper.eq(SysUser::getDepartmentId, departmentId);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                    .like(SysUser::getUsername, keyword)
                    .or()
                    .like(SysUser::getName, keyword));
        }
        wrapper.orderByDesc(SysUser::getCreateTime);
        return userMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /**
     * 更新用户信息（部门、角色、状态）
     */
    public SysUser updateUser(Long id, SysUser updates) {
        SysUser user = userMapper.selectById(id);
        if (user == null) throw new BizException(404, "用户不存在");

        if (updates.getDepartmentId() != null) user.setDepartmentId(updates.getDepartmentId());
        if (updates.getRole() != null) user.setRole(updates.getRole());
        if (updates.getName() != null) user.setName(updates.getName());
        if (updates.getPhone() != null) user.setPhone(updates.getPhone());
        if (updates.getStatus() != null) user.setStatus(updates.getStatus());

        userMapper.updateById(user);
        log.info("用户更新: id={}, role={}, departmentId={}", id, user.getRole(), user.getDepartmentId());
        return user;
    }
}
```

- [ ] **Step 2: 创建 UserController**

```java
package com.knowbrain.auth;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowbrain.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户管理控制器 — 仅 ADMIN 可访问（由 AuthInterceptor 拦截）
 */
@Tag(name = "用户管理", description = "用户查询与信息管理（ADMIN 权限）")
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "用户列表（分页）")
    @GetMapping
    public Result<Page<SysUser>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String keyword) {
        return Result.ok(userService.listUsers(page, size, departmentId, keyword));
    }

    @Operation(summary = "更新用户信息")
    @PutMapping("/{id}")
    public Result<SysUser> update(@PathVariable Long id, @RequestBody SysUser updates) {
        return Result.ok("更新成功", userService.updateUser(id, updates));
    }
}
```

- [ ] **Step 3: 验证编译通过**

```bash
cd knowbrain-server && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add knowbrain-server/src/main/java/com/knowbrain/auth/UserService.java \
        knowbrain-server/src/main/java/com/knowbrain/auth/UserController.java
git commit -m "feat: 用户管理（UserService + Controller — 分页查询/部门过滤/角色变更）"
```

---

### Task 3: PermissionService — 权限三方法重写

**Files:**
- Modify: `knowbrain-server/src/main/java/com/knowbrain/permission/PermissionService.java`

**Interfaces:**
- Consumes: `SpaceMapper`, `SpaceMemberMapper`, `DepartmentMapper`, `SysUserMapper`
- Modifies: `checkReadAccess()` — 加入 TEAM 部门匹配逻辑
- Modifies: `checkWriteAccess()` — 简化为角色检查（ADMIN/MANAGER/Owner），去掉 Editor 逻辑
- Modifies: `getAccessibleSpaceIds()` — 加入 TEAM 逻辑，去掉 null userId 降级分支
- Produces: `getUserDepartment(Long userId)` — 新增私有方法

- [ ] **Step 1: 重写 PermissionService（完整替换）**

```java
package com.knowbrain.permission;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowbrain.auth.DepartmentMapper;
import com.knowbrain.auth.SysUser;
import com.knowbrain.auth.SysUserMapper;
import com.knowbrain.common.GlobalExceptionHandler.BizException;
import com.knowbrain.space.Space;
import com.knowbrain.space.SpaceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 权限管理服务 — 可见性 + 部门 + 角色 三因子权限模型
 *
 * 读权限：
 *   PUBLIC  → 所有登录用户
 *   TEAM    → 部门匹配 或 Owner
 *   PRIVATE → Owner 或显式成员
 *
 * 写权限：
 *   ADMIN / MANAGER / SpaceOwner → 可写
 *
 * 检索权限过滤：
 *   getAccessibleSpaceIds(userId) → 按上述规则返回空间 ID 列表
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final SpaceMapper spaceMapper;
    private final SpaceMemberMapper memberMapper;
    private final SysUserMapper userMapper;

    /** 检查用户是否有权读取空间 */
    public void checkReadAccess(Long spaceId, Long userId) {
        Space space = spaceMapper.selectById(spaceId);
        if (space == null) throw new BizException(404, "空间不存在");

        // 1. PUBLIC → 所有登录用户放行
        if ("PUBLIC".equals(space.getVisibility())) return;

        // 2. OWNER → 放行
        if (space.getOwnerId().equals(userId)) return;

        // 3. TEAM → 部门匹配
        if ("TEAM".equals(space.getVisibility())) {
            Long userDeptId = getUserDepartmentId(userId);
            if (userDeptId != null && isInDepartmentScope(space.getDepartmentScope(), userDeptId)) {
                return;
            }
            throw new BizException(403, "无权访问此空间（部门不匹配）");
        }

        // 4. PRIVATE → 显式成员
        SpaceMember member = getMember(spaceId, userId);
        if (member == null) throw new BizException(403, "无权访问此空间");
    }

    /** 检查用户是否有权编辑空间（写权限 = 角色决定） */
    public void checkWriteAccess(Long spaceId, Long userId) {
        Space space = spaceMapper.selectById(spaceId);
        if (space == null) throw new BizException(404, "空间不存在");

        // ADMIN / MANAGER → 全部可写
        String role = getUserRole(userId);
        if ("ADMIN".equals(role) || "MANAGER".equals(role)) return;

        // SpaceOwner → 自己的空间可写
        if (space.getOwnerId().equals(userId)) return;

        throw new BizException(403, "无权编辑此空间");
    }

    /** 检查用户是否为空间管理员（OWNER 或系统 ADMIN） */
    public void checkOwnerAccess(Long spaceId, Long userId) {
        Space space = spaceMapper.selectById(spaceId);
        if (space == null) throw new BizException(404, "空间不存在");

        String role = getUserRole(userId);
        if ("ADMIN".equals(role)) return;
        if (space.getOwnerId().equals(userId)) return;

        throw new BizException(403, "仅空间创建者或系统管理员可执行此操作");
    }

    /** 添加成员（仅 PRIVATE 空间使用） */
    public SpaceMember addMember(Long spaceId, Long userId, String role) {
        SpaceMember existing = getMember(spaceId, userId);
        if (existing != null) {
            existing.setRole(role);
            memberMapper.updateById(existing);
            log.info("成员角色更新: spaceId={}, userId={}, role={}", spaceId, userId, role);
            return existing;
        }

        SpaceMember member = new SpaceMember();
        member.setSpaceId(spaceId);
        member.setUserId(userId);
        member.setRole(role);
        memberMapper.insert(member);
        log.info("成员添加: spaceId={}, userId={}, role={}", spaceId, userId, role);
        return member;
    }

    /** 移除成员 */
    public void removeMember(Long spaceId, Long userId) {
        SpaceMember member = getMember(spaceId, userId);
        if (member != null) {
            memberMapper.deleteById(member.getId());
            log.info("成员移除: spaceId={}, userId={}", spaceId, userId);
        }
    }

    /** 获取空间所有成员 */
    public List<SpaceMember> listMembers(Long spaceId) {
        return memberMapper.selectList(
                new LambdaQueryWrapper<SpaceMember>().eq(SpaceMember::getSpaceId, spaceId));
    }

    /** 获取用户在某空间的角色，非成员返回 null */
    public String getUserRole(Long spaceId, Long userId) {
        Space space = spaceMapper.selectById(spaceId);
        if (space != null && space.getOwnerId().equals(userId)) return "OWNER";

        SpaceMember member = getMember(spaceId, userId);
        return member != null ? member.getRole() : null;
    }

    /**
     * 获取用户可读的空间 ID 列表（用于检索权限过滤）
     *
     * 规则：
     * - PUBLIC 空间 → 所有登录用户可见
     * - OWNER 的空间 → 全部可见
     * - TEAM 空间 → 部门匹配
     * - PRIVATE 空间 → 显式成员
     *
     * 前置条件：userId 非空（前端强制登录，AuthInterceptor 保证）
     */
    public List<Long> getAccessibleSpaceIds(Long userId) {
        List<Space> allSpaces = spaceMapper.selectList(null);
        List<Long> ids = new ArrayList<>();
        Long userDeptId = getUserDepartmentId(userId);

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
                if (userDeptId != null && isInDepartmentScope(s.getDepartmentScope(), userDeptId)) {
                    ids.add(s.getId());
                }
                continue;
            }

            // 4. PRIVATE → 显式成员
            if (isMember(s.getId(), userId)) {
                ids.add(s.getId());
            }
        }

        return ids;
    }

    // ==================== 私有辅助方法 ====================

    private SpaceMember getMember(Long spaceId, Long userId) {
        return memberMapper.selectOne(new LambdaQueryWrapper<SpaceMember>()
                .eq(SpaceMember::getSpaceId, spaceId)
                .eq(SpaceMember::getUserId, userId));
    }

    private boolean isMember(Long spaceId, Long userId) {
        return getMember(spaceId, userId) != null;
    }

    /** 获取用户的系统角色（从 kb_sys_user 表） */
    private String getUserRole(Long userId) {
        SysUser user = userMapper.selectById(userId);
        return user != null ? user.getRole() : null;
    }

    /** 获取用户的部门 ID */
    private Long getUserDepartmentId(Long userId) {
        SysUser user = userMapper.selectById(userId);
        return user != null ? user.getDepartmentId() : null;
    }

    /**
     * 检查用户部门是否在空间的部门可见范围内
     * departmentScope 格式："1,2,5"（逗号分隔的部门 ID）
     */
    private boolean isInDepartmentScope(String departmentScope, Long userDeptId) {
        if (departmentScope == null || departmentScope.isBlank()) return false;
        return Arrays.stream(departmentScope.split(","))
                .map(String::trim)
                .anyMatch(id -> id.equals(String.valueOf(userDeptId)));
    }
}
```

- [ ] **Step 2: 验证编译通过**

```bash
cd knowbrain-server && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add knowbrain-server/src/main/java/com/knowbrain/permission/PermissionService.java
git commit -m "feat: 权限服务重写 — 三因子模型（可见性+部门+角色）、TEAM 部门匹配、写权限简化"
```

---

### Task 4: AuthInterceptor 角色控制 + WebMvcConfig + AuthController 增强

**Files:**
- Modify: `knowbrain-server/src/main/java/com/knowbrain/auth/AuthInterceptor.java`
- Modify: `knowbrain-server/src/main/java/com/knowbrain/config/WebMvcConfig.java`
- Modify: `knowbrain-server/src/main/java/com/knowbrain/auth/AuthController.java`

**Interfaces:**
- Modifies: `AuthInterceptor.preHandle()` — `/api/v1/admin/**` 检查 role=ADMIN
- Modifies: `WebMvcConfig.addInterceptors()` — 移除 RAG 白名单，feedback 加入需登录
- Modifies: `AuthController.register()` — 增加 departmentId 参数

- [ ] **Step 1: AuthInterceptor 增加 ADMIN 角色检查**

在 `preHandle()` 方法中，Token 验证通过后、`return true` 之前，添加 ADMIN 路径检查：

```java
// 修改前（第 44-48 行）：
        // 注入用户信息到 request attributes
        request.setAttribute("userId", claims.get("userId"));
        request.setAttribute("username", claims.get("username"));
        request.setAttribute("role", claims.get("role"));
        return true;

// 修改后：
        // 注入用户信息到 request attributes
        request.setAttribute("userId", claims.get("userId"));
        request.setAttribute("username", claims.get("username"));
        request.setAttribute("role", claims.get("role"));

        // /api/v1/admin/** 仅 ADMIN 角色可访问
        String path = request.getRequestURI();
        if (path.startsWith("/api/v1/admin/")) {
            String role = (String) claims.get("role");
            if (!"ADMIN".equals(role)) {
                writeJson(response, 403, "需要系统管理员权限");
                return false;
            }
        }

        return true;
```

同时将 `write401` 方法改造为通用的 `writeJson` 方法：

```java
// 替换 write401 方法：
    private void writeJson(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + status + ",\"message\":\"" + message + "\"}");
    }
```

并更新调用处：

```java
// 第 33 行：write401(response, "未登录或Token已过期");
// 改为：
            writeJson(response, 401, "未登录或Token已过期");

// 第 40 行：write401(response, "Token无效或已过期");
// 改为：
            writeJson(response, 401, "Token无效或已过期");
```

- [ ] **Step 2: WebMvcConfig 更新白名单**

移除 RAG 和 feedback 端点从免登录列表，使全系统强制登录：

```java
// 修改前：
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(
                        // 健康检查
                        "/api/v1/health",
                        // 认证接口
                        "/api/v1/auth/**",
                        // RAG 问答（无需登录即可提问）
                        "/api/v1/rag/chat",
                        "/api/v1/rag/chat/stream",
                        "/api/v1/rag/search",
                        // 知识分类（公开读取）
                        "/api/v1/categories/**",
                        // 术语词典（公开读取）
                        "/api/v1/glossary/**",
                        // FAQ 预设库（公开读取）
                        "/api/v1/faq/**",
                        // 答案反馈（无需登录）
                        "/api/v1/feedback"
                );
    }

// 修改后：
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(
                        // 健康检查
                        "/api/v1/health",
                        // 认证接口（登录/注册）
                        "/api/v1/auth/**"
                );
    }
```

> **影响**：RAG 问答（/chat /search /chat/stream）、分类、术语、FAQ、反馈这 7 个端点从免登录变为需登录。前端需确保请求带 Bearer Token。

- [ ] **Step 3: RAGController 移除 null userId 降级**

修改 `RAGController.getAccessibleSpaceIds()` 和 `extractUserId()` 方法，去掉 null 降级：

```java
// 修改 getAccessibleSpaceIds 方法（第 154-157 行）：
    private List<Long> getAccessibleSpaceIds(HttpServletRequest request) {
        Long userId = extractUserId(request);
        // 前置条件：AuthInterceptor 保证 userId 非空
        return permissionService.getAccessibleSpaceIds(userId);
    }

// extractUserId 方法去掉手动解析 Header 的降级逻辑（第 159-174 行）：
    private Long extractUserId(HttpServletRequest request) {
        Object uid = request.getAttribute("userId");
        if (uid instanceof Number n) return n.longValue();
        throw new IllegalStateException("未登录用户不应到达此处（AuthInterceptor 拦截）");
    }
```

移除不再需要的 import：

```java
// 删除以下 import：
import com.knowbrain.auth.JwtUtil;
import java.util.Map;
```

同时移除 `RAGController` 构造函数中的 `JwtUtil` 依赖，改为：

```java
// 修改前：
    private final RAGService ragService;
    private final PermissionService permissionService;
    private final JwtUtil jwtUtil;

// 修改后：
    private final RAGService ragService;
    private final PermissionService permissionService;
```

- [ ] **Step 4: AuthController.register() 增加 departmentId 参数**

修改 `register()` 方法：

```java
// 修改前（第 76 行）：
        String phone = request.getOrDefault("phone", "");

// 修改后（在第 76 行之后添加）：
        String phone = request.getOrDefault("phone", "");
        Long departmentId = null;
        if (request.containsKey("departmentId") && request.get("departmentId") != null) {
            try {
                departmentId = Long.valueOf(request.get("departmentId").toString());
            } catch (NumberFormatException ignored) {
                // 非法值忽略，保持 null
            }
        }

// 在 user.setRole("USER") 之后、userMapper.insert(user) 之前添加（第 97 行附近）：
        user.setRole("USER");
        user.setDepartmentId(departmentId);  // <-- 新增
```

- [ ] **Step 5: 验证编译通过**

```bash
cd knowbrain-server && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add knowbrain-server/src/main/java/com/knowbrain/auth/AuthInterceptor.java \
        knowbrain-server/src/main/java/com/knowbrain/config/WebMvcConfig.java \
        knowbrain-server/src/main/java/com/knowbrain/retrieval/engine/RAGController.java \
        knowbrain-server/src/main/java/com/knowbrain/auth/AuthController.java
git commit -m "feat: 角色访问控制 — ADMIN 拦截 /api/v1/admin、全系统强制登录、注册支持部门"
```

---

## 完成标准

- [x] `GET /api/v1/admin/departments` 返回部门树
- [x] `POST/PUT/DELETE /api/v1/admin/departments` 部门 CRUD 正常
- [x] `GET /api/v1/admin/users?departmentId=1` 按部门过滤用户列表
- [x] `PUT /api/v1/admin/users/{id}` 可更新用户角色和部门
- [x] `checkReadAccess()` TEAM 空间同部门可见、跨部门拒绝
- [x] `checkWriteAccess()` ADMIN/MANAGER 可写、USER 拒绝
- [x] `getAccessibleSpaceIds()` TEAM 部门匹配正确
- [x] `AuthInterceptor` `/api/v1/admin/**` USER 返回 403
- [x] RAG 端点需登录才能访问
- [x] 注册接口支持 `departmentId` 参数

## 下一步

P1 完成后，进入 **P2 — 空间 API 适配**（SpaceController departmentScope 参数、成员管理 PUBLIC/TEAM 限制）。
