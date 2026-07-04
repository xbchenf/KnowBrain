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
@Tag(name = "用户管理", description = "用户 CRUD 与密码管理（ADMIN 权限）")
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
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return Result.ok(userService.listUsers(page, size, departmentId, role, status, keyword));
    }

    @Operation(summary = "管理员创建用户")
    @PostMapping
    public Result<SysUser> create(@RequestBody Map<String, Object> request) {
        String username = request.get("username") != null ? request.get("username").toString() : null;
        String password = request.get("password") != null ? request.get("password").toString() : null;
        String name = request.get("name") != null ? request.get("name").toString() : null;
        String phone = request.get("phone") != null ? request.get("phone").toString() : null;
        String role = request.get("role") != null ? request.get("role").toString() : null;
        Long departmentId = null;
        if (request.containsKey("departmentId") && request.get("departmentId") != null) {
            try {
                departmentId = Long.valueOf(request.get("departmentId").toString());
            } catch (NumberFormatException ignored) {
                // 非法值忽略，保持 null
            }
        }
        return Result.ok("创建成功", userService.createUser(username, password, name, phone, role, departmentId));
    }

    @Operation(summary = "更新用户信息")
    @PutMapping("/{id}")
    public Result<SysUser> update(@PathVariable Long id, @RequestBody SysUser updates) {
        return Result.ok("更新成功", userService.updateUser(id, updates));
    }

    @Operation(summary = "重置用户密码")
    @PutMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> request) {
        userService.resetPassword(id, request.get("password"));
        return Result.ok("密码重置成功");
    }
}
