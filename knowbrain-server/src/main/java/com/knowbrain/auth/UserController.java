package com.knowbrain.auth;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowbrain.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

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
