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
