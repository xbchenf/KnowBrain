package com.knowbrain.audit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowbrain.audit.entity.AuditLog;
import com.knowbrain.audit.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 审计日志服务 — 异步持久化 + 管理查询
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;

    /**
     * 异步写入审计日志，不阻塞主请求
     * 写入失败只记录日志，绝不抛出异常影响业务
     */
    @Async("auditLogExecutor")
    public void saveAsync(AuditLog auditLog) {
        try {
            auditLogMapper.insert(auditLog);
        } catch (Exception e) {
            log.error("审计日志写入失败: operatorId={}, operation={}, resourceType={}, error={}",
                    auditLog.getOperatorId(), auditLog.getOperationType(),
                    auditLog.getResourceType(), e.getMessage(), e);
        }
    }

    /**
     * 分页查询审计日志（管理后台使用）
     */
    public Map<String, Object> query(int page, int size,
                                     Long operatorId,
                                     String operationType,
                                     String resourceType,
                                     String keyword,
                                     String startDate,
                                     String endDate) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();

        if (operatorId != null) {
            wrapper.eq(AuditLog::getOperatorId, operatorId);
        }
        if (operationType != null && !operationType.isBlank()) {
            wrapper.eq(AuditLog::getOperationType, operationType.toUpperCase());
        }
        if (resourceType != null && !resourceType.isBlank()) {
            wrapper.eq(AuditLog::getResourceType, resourceType.toUpperCase());
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w
                    .like(AuditLog::getOperatorName, keyword)
                    .or()
                    .like(AuditLog::getResourceName, keyword)
                    .or()
                    .like(AuditLog::getDescription, keyword));
        }
        if (startDate != null && !startDate.isBlank()) {
            wrapper.ge(AuditLog::getCreateTime, LocalDateTime.of(LocalDate.parse(startDate), LocalTime.MIN));
        }
        if (endDate != null && !endDate.isBlank()) {
            wrapper.le(AuditLog::getCreateTime, LocalDateTime.of(LocalDate.parse(endDate), LocalTime.MAX));
        }

        wrapper.orderByDesc(AuditLog::getCreateTime);

        Page<AuditLog> result = auditLogMapper.selectPage(new Page<>(page, size), wrapper);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", result.getRecords());
        data.put("total", result.getTotal());
        data.put("page", result.getCurrent());
        data.put("size", result.getSize());
        return data;
    }
}
