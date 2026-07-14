package com.knowbrain.im.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * IM 平台用户身份 → KB 用户关联 — kb_user_identity
 *
 * <p>同一 KB 用户可关联多个 IM 平台的身份（企微 + 钉钉 + 飞书），
 * 以手机号为匹配键自动归并，避免多平台产生独立账号。
 */
@Data
@TableName("kb_user_identity")
public class ImUserIdentity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** KB 用户 ID (kb_sys_user.id) */
    private Long kbUserId;

    /** IM 平台: wecom / dingtalk / feishu */
    private String platform;

    /** 平台侧用户唯一标识 */
    private String platformUid;

    /** 平台侧显示名 */
    private String platformName;

    /** 手机号（用于跨平台身份匹配） */
    private String mobile;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime linkedAt;
}
