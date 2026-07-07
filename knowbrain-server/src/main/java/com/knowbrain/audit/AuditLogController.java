package com.knowbrain.audit;

import com.knowbrain.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 审计日志查询 API — 管理员查看操作审计记录
 * 路径 /api/v1/admin/audit-logs 由 AuthInterceptor 保护（ADMIN 读写 / MANAGER 只读）
 */
@Tag(name = "审计日志", description = "操作审计日志查询（ADMIN / MANAGER 权限）")
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @Operation(summary = "审计日志列表（分页 + 多条件筛选）")
    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return Result.ok(auditLogService.query(
                page, size, operatorId, operationType,
                resourceType, keyword, startDate, endDate));
    }
}
