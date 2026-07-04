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
