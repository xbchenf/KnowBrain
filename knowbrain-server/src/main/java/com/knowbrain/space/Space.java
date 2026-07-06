package com.knowbrain.space;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档空间 — kb_space
 * 每个空间包含多个文档，空间内共享权限
 */
@Data
@TableName("kb_space")
public class Space {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 空间名称 */
    private String name;

    /** 空间描述 */
    private String description;

    /** 创建者用户 ID */
    private Long ownerId;

    /** 创建者姓名（非数据库字段，查询时填充） */
    @TableField(exist = false)
    private String ownerName;

    /** 当前用户是否可管理此空间（非数据库字段，查询时填充） */
    @TableField(exist = false)
    private Boolean isOwner;

    /** 可见性: PRIVATE(私有) / TEAM(团队) / PUBLIC(公开) */
    private String visibility;

    /** 部门可见范围（逗号分隔部门名，TEAM 模式生效） */
    private String departmentScope;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
