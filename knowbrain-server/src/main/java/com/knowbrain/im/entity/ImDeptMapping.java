package com.knowbrain.im.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * IM 平台部门 → KB 部门映射 — kb_im_dept_mapping
 *
 * <p>将企微/钉钉/飞书的部门 ID 映射到 KnowBrain 内部部门 ID，
 * 用于 IM 用户首次发消息时自动解析所属部门。
 */
@Data
@TableName("kb_im_dept_mapping")
public class ImDeptMapping {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** IM 平台: wecom / dingtalk / feishu */
    private String platform;

    /** IM 平台侧的部门 ID（企微/钉钉/飞书的部门标识） */
    private String externalDeptId;

    /** 对应 KB 部门 ID (kb_department.id) */
    private Long kbDeptId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
