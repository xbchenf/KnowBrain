package com.knowbrain.audit.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志实体 — kb_audit_log
 * 记录管理员所有写操作（创建/更新/删除），满足企业合规审计要求
 */
@Data
@TableName("kb_audit_log")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作人用户ID */
    private Long operatorId;

    /** 操作人用户名 */
    private String operatorName;

    /** 操作类型: CREATE / UPDATE / DELETE / OTHER */
    private String operationType;

    /** 目标资源类型: USER / DEPARTMENT / SPACE / DOCUMENT / SCENARIO_CATEGORY / ... */
    private String resourceType;

    /** 目标资源ID */
    private Long resourceId;

    /** 目标资源名称/摘要 */
    private String resourceName;

    /** 操作详情描述 */
    private String description;

    /** 客户端IP地址 */
    private String ipAddress;

    /** 操作结果: 1=成功, 0=失败 */
    private Integer success;

    /** 失败原因 */
    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
