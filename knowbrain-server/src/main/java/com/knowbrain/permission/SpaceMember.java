package com.knowbrain.permission;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 空间成员权限 — kb_space_member
 */
@Data
@TableName("kb_space_member")
public class SpaceMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 空间 ID */
    private Long spaceId;

    /** 用户 ID */
    private Long userId;

    /** 角色: OWNER / EDITOR / VIEWER */
    private String role;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
