package com.knowbrain.space;

import com.knowbrain.common.Result;
import com.knowbrain.permission.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 空间管理控制器
 */
@Tag(name = "空间管理", description = "文档空间的创建、管理与权限控制")
@Slf4j
@RestController
@RequestMapping("/api/v1/spaces")
@RequiredArgsConstructor
public class SpaceController {

    private final SpaceService spaceService;
    private final PermissionService permissionService;

    @Operation(summary = "创建空间")
    @PostMapping
    public Result<Space> create(@RequestBody Map<String, Object> request,
                                 HttpServletRequest servletRequest) {
        String name = (String) request.get("name");
        if (name == null || name.isBlank()) {
            return Result.badRequest("空间名称不能为空");
        }
        Long userId = getUserId(servletRequest);
        String desc = (String) request.getOrDefault("description", "");
        String visibility = (String) request.getOrDefault("visibility", "PRIVATE");
        // departmentScope 数组 → 逗号分隔字符串
        String departmentScope = null;
        Object deptScopeObj = request.get("departmentScope");
        if (deptScopeObj instanceof List<?> list && !list.isEmpty()) {
            departmentScope = list.stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(","));
        }
        Space space = spaceService.create(name, desc, userId, visibility, departmentScope);
        return Result.ok("创建成功", space);
    }

    @Operation(summary = "查询空间详情")
    @GetMapping("/{id}")
    public Result<Space> getById(@PathVariable Long id, HttpServletRequest req) {
        Long userId = getUserId(req);
        permissionService.checkReadAccess(id, userId);
        Space space = spaceService.getById(id);
        // 服务端判断当前用户是否可管理此空间
        String role = (String) req.getAttribute("role");
        space.setIsOwner("ADMIN".equals(role) || space.getOwnerId().equals(userId));
        return Result.ok(space);
    }

    @Operation(summary = "我的空间列表")
    @GetMapping
    public Result<Object> list(HttpServletRequest req,
                                @RequestParam(defaultValue = "1") int page,
                                @RequestParam(defaultValue = "20") int size) {
        Long userId = getUserId(req);
        return Result.ok(spaceService.listByUser(userId, page, size));
    }

    @Operation(summary = "更新空间信息")
    @PutMapping("/{id}")
    public Result<Space> update(@PathVariable Long id,
                                 @RequestBody Map<String, Object> request,
                                 HttpServletRequest req) {
        Long userId = getUserId(req);
        permissionService.checkOwnerAccess(id, userId);
        // departmentScope 数组 → 逗号分隔字符串
        String departmentScope = null;
        Object deptScopeObj = request.get("departmentScope");
        if (deptScopeObj instanceof List<?> list && !list.isEmpty()) {
            departmentScope = list.stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(","));
        }
        Space space = spaceService.update(id,
                (String) request.get("name"),
                (String) request.get("description"),
                (String) request.get("visibility"),
                departmentScope);
        return Result.ok("更新成功", space);
    }

    @Operation(summary = "删除空间")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest req) {
        Long userId = getUserId(req);
        permissionService.checkOwnerAccess(id, userId);
        spaceService.delete(id);
        return Result.ok("删除成功", null);
    }

    // ==================== 成员管理 ====================

    @Operation(summary = "空间成员列表")
    @GetMapping("/{id}/members")
    public Result<Object> members(@PathVariable Long id, HttpServletRequest req) {
        Long userId = getUserId(req);
        permissionService.checkReadAccess(id, userId);
        Space space = spaceService.getById(id);
        if ("PUBLIC".equals(space.getVisibility()) || "TEAM".equals(space.getVisibility())) {
            return Result.fail(400, "PUBLIC 和 TEAM 空间无需管理成员");
        }
        return Result.ok(permissionService.listMembers(id));
    }

    @Operation(summary = "添加成员")
    @PostMapping("/{id}/members")
    public Result<Void> addMember(@PathVariable Long id,
                                   @RequestBody Map<String, Object> body,
                                   HttpServletRequest req) {
        Long userId = getUserId(req);
        permissionService.checkOwnerAccess(id, userId);
        Space space = spaceService.getById(id);
        if ("PUBLIC".equals(space.getVisibility()) || "TEAM".equals(space.getVisibility())) {
            return Result.fail(400, "PUBLIC 和 TEAM 空间无需管理成员");
        }
        Long memberUserId = Long.valueOf(body.get("userId").toString());
        String role = body.getOrDefault("role", "VIEWER").toString();
        permissionService.addMember(id, memberUserId, role);
        return Result.ok("添加成功", null);
    }

    @Operation(summary = "移除成员")
    @DeleteMapping("/{id}/members/{memberUserId}")
    public Result<Void> removeMember(@PathVariable Long id,
                                      @PathVariable Long memberUserId,
                                      HttpServletRequest req) {
        Long userId = getUserId(req);
        permissionService.checkOwnerAccess(id, userId);
        Space space = spaceService.getById(id);
        if ("PUBLIC".equals(space.getVisibility()) || "TEAM".equals(space.getVisibility())) {
            return Result.fail(400, "PUBLIC 和 TEAM 空间无需管理成员");
        }
        permissionService.removeMember(id, memberUserId);
        return Result.ok("移除成功", null);
    }

    private Long getUserId(HttpServletRequest request) {
        Object uid = request.getAttribute("userId");
        return uid instanceof Number ? ((Number) uid).longValue() : null;
    }
}
