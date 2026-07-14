package com.knowbrain.im;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowbrain.common.Result;
import com.knowbrain.im.entity.ImDeptMapping;
import com.knowbrain.im.entity.ImUserIdentity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * IM 集成管理控制器 — 仅 ADMIN/MANAGER 可访问（AuthInterceptor 拦截）。
 */
@Tag(name = "IM 集成管理", description = "部门映射 + 身份关联（ADMIN 权限）")
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/im")
@RequiredArgsConstructor
public class ImAdminController {

    private final ImAdminService imAdminService;

    // ==================== 部门映射 ====================

    @Operation(summary = "部门映射列表")
    @GetMapping("/dept-mappings")
    public Result<Page<ImAdminService.DeptMappingVO>> listDeptMappings(
            @RequestParam(required = false) String platform,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(imAdminService.listDeptMappings(platform, page, size));
    }

    @Operation(summary = "创建部门映射")
    @PostMapping("/dept-mappings")
    public Result<ImDeptMapping> createDeptMapping(@RequestBody ImDeptMapping mapping) {
        if (mapping.getPlatform() == null || mapping.getPlatform().isBlank()) {
            return Result.badRequest("平台不能为空");
        }
        if (mapping.getExternalDeptId() == null || mapping.getExternalDeptId().isBlank()) {
            return Result.badRequest("IM 部门 ID 不能为空");
        }
        if (mapping.getKbDeptId() == null) {
            return Result.badRequest("KB 部门不能为空");
        }
        return Result.ok("创建成功", imAdminService.createDeptMapping(mapping));
    }

    @Operation(summary = "更新部门映射")
    @PutMapping("/dept-mappings/{id}")
    public Result<ImDeptMapping> updateDeptMapping(@PathVariable Long id,
                                                    @RequestBody ImDeptMapping mapping) {
        return Result.ok("更新成功", imAdminService.updateDeptMapping(id, mapping));
    }

    @Operation(summary = "删除部门映射")
    @DeleteMapping("/dept-mappings/{id}")
    public Result<Void> deleteDeptMapping(@PathVariable Long id) {
        imAdminService.deleteDeptMapping(id);
        return Result.ok("删除成功", null);
    }

    // ==================== IM 身份 ====================

    @Operation(summary = "IM 身份列表（unboundOnly=true 过滤自动创建的 IM 用户）")
    @GetMapping("/identities")
    public Result<Page<ImAdminService.IdentityVO>> listIdentities(
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean unboundOnly) {
        return Result.ok(imAdminService.listIdentities(platform, keyword, page, size, unboundOnly));
    }

    @Operation(summary = "手动绑定 IM 身份")
    @PostMapping("/identities")
    public Result<ImUserIdentity> bindIdentity(@RequestBody BindRequest req) {
        if (req.getKbUserId() == null) return Result.badRequest("KB 用户不能为空");
        if (req.getPlatform() == null || req.getPlatform().isBlank()) return Result.badRequest("平台不能为空");
        if (req.getPlatformUid() == null || req.getPlatformUid().isBlank()) return Result.badRequest("IM 用户 ID 不能为空");
        return Result.ok("绑定成功", imAdminService.bindIdentity(
                req.getKbUserId(), req.getPlatform(), req.getPlatformUid()));
    }

    @Operation(summary = "解绑 IM 身份")
    @DeleteMapping("/identities/{id}")
    public Result<Void> unbindIdentity(@PathVariable Long id) {
        imAdminService.unbindIdentity(id);
        return Result.ok("解绑成功", null);
    }

    // ==================== 聚合信息 ====================

    @Operation(summary = "KB 部门列表 + 可选平台部门（供表单下拉选择 + 拉取平台部门）")
    @GetMapping("/departments-info")
    public Result<ImAdminService.DepartmentsInfoVO> getDepartmentsInfo(
            @RequestParam(required = false) String platform) {
        return Result.ok(imAdminService.getDepartmentsInfo(platform));
    }

    // ==================== DTO ====================

    @lombok.Data
    public static class BindRequest {
        private Long kbUserId;
        private String platform;
        private String platformUid;
    }
}
