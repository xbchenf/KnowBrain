# P0 — 部门体系数据基础 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 创建 `kb_department` 表、Department 实体/Mapper，SysUser 新增 `departmentId` 字段，预置种子数据。

**Architecture:** 在现有 MyBatis-Plus + Lombok 模式下新增 Department 实体，SysUser 增加部门外键，通过 SQL 脚本初始化基础数据。遵循现有 auth 包的代码风格。

**Tech Stack:** Java 17, Spring Boot 3.3, MyBatis-Plus, Lombok, MySQL 8.0

**Spec:** [2026-07-04-department-team-permission-design.md](../specs/2026-07-04-department-team-permission-design.md)

## Global Constraints

- 基础包名：`com.knowbrain`
- 数据库表前缀：`kb_`
- 实体使用 Lombok `@Data` + MyBatis-Plus `@TableName`
- Mapper 继承 `BaseMapper<T>` + `@Mapper` 注解
- SQL 脚本使用 `IF NOT EXISTS` 保证幂等
- 遵循现有代码风格（字段注释、Javadoc）

---

### Task 1: 创建 `kb_department` 建表 SQL

**Files:**
- Create: `knowbrain-server/docker/mysql/init/02-department.sql`

**Interfaces:**
- Produces: `kb_department` 表（id, name, parent_id, sort_order, create_time, update_time, deleted）
- Produces: `kb_sys_user` 表新增 `department_id` 列
- Produces: 预置部门种子数据 + ADMIN 账号更新

- [ ] **Step 1: 创建 SQL 文件**

```sql
-- KnowBrain 部门体系初始化脚本
-- 依赖: 01-init-schema.sql 已执行（kb_sys_user 表已存在）

-- ==================== 部门表 ====================
CREATE TABLE IF NOT EXISTS `kb_department` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        VARCHAR(100) NOT NULL COMMENT '部门名称',
    `parent_id`   BIGINT       DEFAULT 0 COMMENT '上级部门 ID（0=顶级部门）',
    `sort_order`  INT          DEFAULT 0 COMMENT '排序序号',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      DEFAULT 0 COMMENT '逻辑删除: 0=正常, 1=删除',
    PRIMARY KEY (`id`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门表';

-- ==================== 用户表新增部门字段 ====================
-- 幂等：如果列已存在则忽略（MySQL 不支持 IF NOT EXISTS for ALTER，但脚本首次执行后不会重复运行）
-- 若需重复执行，建议先检查：SELECT COUNT(*) FROM information_schema.COLUMNS
--     WHERE TABLE_SCHEMA = 'knowbrain' AND TABLE_NAME = 'kb_sys_user' AND COLUMN_NAME = 'department_id'

ALTER TABLE `kb_sys_user`
    ADD COLUMN `department_id` BIGINT DEFAULT NULL COMMENT '所属部门 ID';

-- ==================== 种子数据：预置部门 ====================
INSERT IGNORE INTO `kb_department` (`id`, `name`, `parent_id`, `sort_order`) VALUES
    (1, '技术部',   0, 1),
    (2, '产品部',   0, 2),
    (3, '人力资源部', 0, 3),
    (4, '财务部',   0, 4),
    (5, '市场部',   0, 5),
    (6, '运营部',   0, 6),
    (7, '行政部',   0, 7);

-- ==================== 种子数据：ADMIN 账号归属 ====================
-- 将已有 admin 账号归属到技术部
UPDATE `kb_sys_user` SET `department_id` = 1 WHERE `username` = 'admin' AND `department_id` IS NULL;
```

- [ ] **Step 2: 验证 SQL 文件存在**

Run: `Get-ChildItem knowbrain-server\docker\mysql\init\` (via PowerShell)
Expected: 列出 `01-init-schema.sql` 和 `02-department.sql`

- [ ] **Step 3: Commit**

```bash
git add knowbrain-server/docker/mysql/init/02-department.sql
git commit -m "feat: 添加 kb_department 建表 SQL + 种子数据（P0 数据基础）"
```

---

### Task 2: 创建 `Department` 实体 + `DepartmentMapper`

**Files:**
- Create: `knowbrain-server/src/main/java/com/knowbrain/auth/Department.java`
- Create: `knowbrain-server/src/main/java/com/knowbrain/auth/DepartmentMapper.java`

**Interfaces:**
- Produces: `Department` 实体类，映射 `kb_department` 表
- Produces: `DepartmentMapper` 接口，继承 `BaseMapper<Department>`

- [ ] **Step 1: 创建 Department 实体类**

```java
package com.knowbrain.auth;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 部门 — kb_department
 * 支持树形结构（parent_id 自引用）
 */
@Data
@TableName("kb_department")
public class Department {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 部门名称 */
    private String name;

    /** 上级部门 ID（0 = 顶级部门） */
    private Long parentId;

    /** 排序序号 */
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
```

- [ ] **Step 2: 创建 DepartmentMapper 接口**

```java
package com.knowbrain.auth;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 部门 Mapper
 */
@Mapper
public interface DepartmentMapper extends BaseMapper<Department> {
}
```

- [ ] **Step 3: 验证文件编译通过**

Run: `cd knowbrain-server; mvn compile -q`
Expected: BUILD SUCCESS（无编译错误）

- [ ] **Step 4: Commit**

```bash
git add knowbrain-server/src/main/java/com/knowbrain/auth/Department.java \
        knowbrain-server/src/main/java/com/knowbrain/auth/DepartmentMapper.java
git commit -m "feat: 添加 Department 实体和 Mapper（P0 数据基础）"
```

---

### Task 3: `SysUser` 新增 `departmentId` 字段

**Files:**
- Modify: `knowbrain-server/src/main/java/com/knowbrain/auth/SysUser.java`

**Interfaces:**
- Modifies: `SysUser` 实体新增 `departmentId` (Long, nullable) 属性
- Consumes: `kb_sys_user` 表已有 `department_id` 列（Task 1 创建）

- [ ] **Step 1: 在 SysUser 实体中新增字段**

在 `SysUser.java` 的 `role` 字段后添加 `departmentId`：

```java
// 在 private String role; 之后、private String status; 之前添加：

    /** 所属部门 ID */
    private Long departmentId;
```

完整修改后的文件：

```java
package com.knowbrain.auth;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户 — kb_sys_user
 */
@Data
@TableName("kb_sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户名（登录账号） */
    private String username;

    /** BCrypt 密码哈希 */
    private String passwordHash;

    /** 显示名称 */
    private String name;

    /** 手机号 */
    private String phone;

    /** 角色：USER / MANAGER / ADMIN */
    private String role;

    /** 所属部门 ID */
    private Long departmentId;

    /** 状态：ACTIVE / DISABLED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
```

> 注意：同时将 role 字段注释从 `USER / ADMIN` 更新为 `USER / MANAGER / ADMIN`，为 P1 角色扩展做准备。

- [ ] **Step 2: 验证编译通过**

Run: `cd knowbrain-server; mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add knowbrain-server/src/main/java/com/knowbrain/auth/SysUser.java
git commit -m "feat: SysUser 新增 departmentId 字段，role 注释扩展为三元角色（P0 数据基础）"
```

---

### Task 4: 端到端验证

**验证目标：** 启动应用后，数据库表和数据就绪，实体能正常读写。

- [ ] **Step 1: 启动 MySQL（如未运行）**

```bash
docker ps --filter name=mysql
```

如果未运行，通过 Docker Compose 启动：

```bash
docker-compose up -d mysql
```

- [ ] **Step 2: 验证数据库表存在**

```bash
docker exec -i knowbrain-mysql mysql -uroot -pknowbrain123 knowbrain -e "SHOW TABLES LIKE 'kb_department';"
```
Expected: 输出 `kb_department`

- [ ] **Step 3: 验证部门种子数据存在**

```bash
docker exec -i knowbrain-mysql mysql -uroot -pknowbrain123 knowbrain -e "SELECT * FROM kb_department;"
```
Expected: 输出 7 条部门记录（技术部、产品部、人力资源部、财务部、市场部、运营部、行政部）

- [ ] **Step 4: 验证 kb_sys_user 新增 department_id 列**

```bash
docker exec -i knowbrain-mysql mysql -uroot -pknowbrain123 knowbrain -e "DESCRIBE kb_sys_user;"
```
Expected: 输出中包含 `department_id` 列（类型 bigint, Default NULL）

- [ ] **Step 5: 验证 admin 用户部门归属**

```bash
docker exec -i knowbrain-mysql mysql -uroot -pknowbrain123 knowbrain -e "SELECT id, username, role, department_id FROM kb_sys_user WHERE username='admin';"
```
Expected: admin 的 `department_id = 1`（技术部）

- [ ] **Step 6: 启动 Spring Boot 应用验证实体映射**

```bash
cd knowbrain-server; mvn spring-boot:run
```

启动日志中应无 MyBatis-Plus 映射错误。访问 `http://localhost:8080/api/v1/health` 返回 200。

- [ ] **Step 7: Commit（如有验证脚本或配置调整）**

如果验证过程中有调整，提交变更。否则此任务无额外提交。

---

## 完成标准

- [x] `kb_department` 表存在且有 7 条预置部门
- [x] `kb_sys_user` 表有 `department_id` 列
- [x] `admin` 用户归属技术部（department_id=1）
- [x] `Department.java` 实体编译通过
- [x] `DepartmentMapper.java` 编译通过
- [x] `SysUser.java` 新增 `departmentId` 字段且编译通过
- [x] 应用启动无 MyBatis 映射错误

---

## 下一步

P0 完成后，进入 **P1 — 后端核心 API**：
- `DepartmentService` / `DepartmentController` 部门管理
- `UserService` / `UserController` 用户管理
- `PermissionService` 权限重写
- `AuthInterceptor` 角色控制增强
