package com.knowbrain.space;

import com.knowbrain.common.Result;
import com.knowbrain.permission.PermissionService;
import com.knowbrain.permission.SpaceMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
    public Result<Space> create(@RequestBody Map<String, String> request,
                                 HttpServletRequest servletRequest) {
        String name = request.get("name");
        if (name == null || name.isBlank()) {
            return Result.badRequest("空间名称不能为空");
        }
        Long userId = getUserId(servletRequest);
        String desc = request.getOrDefault("description", "");
        String visibility = request.getOrDefault("visibility", "PRIVATE");
        Space space = spaceService.create(name, desc, userId, visibility);
        return Result.ok("创建成功", space);
    }

    @Operation(summary = "查询空间详情")
    @GetMapping("/{id}")
    public Result<Space> getById(@PathVariable Long id, HttpServletRequest req) {
        Long userId = getUserId(req);
        permissionService.checkReadAccess(id, userId);
        Space space = spaceService.getById(id);
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
                                 @RequestBody Map<String, String> request,
                                 HttpServletRequest req) {
        Long userId = getUserId(req);
        permissionService.checkOwnerAccess(id, userId);
        Space space = spaceService.update(id,
                request.get("name"),
                request.get("description"),
                request.get("visibility"));
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
    public Result<List<SpaceMember>> members(@PathVariable Long id, HttpServletRequest req) {
        Long userId = getUserId(req);
        permissionService.checkReadAccess(id, userId);
        return Result.ok(permissionService.listMembers(id));
    }

    @Operation(summary = "添加成员")
    @PostMapping("/{id}/members")
    public Result<Void> addMember(@PathVariable Long id,
                                   @RequestBody Map<String, Object> body,
                                   HttpServletRequest req) {
        Long userId = getUserId(req);
        permissionService.checkOwnerAccess(id, userId);
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
        permissionService.removeMember(id, memberUserId);
        return Result.ok("移除成功", null);
    }

    private Long getUserId(HttpServletRequest request) {
        Object uid = request.getAttribute("userId");
        return uid instanceof Number ? ((Number) uid).longValue() : null;
    }
}
