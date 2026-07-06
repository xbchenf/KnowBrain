package com.knowbrain.auth;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 部门 — kb_department
 * 支持树形结构（parent_id 自引用）
 */
@Data
@TableName("kb_department")
public class Department {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 部门名称 */
    private String name;

    /** 上级部门 ID（0 = 顶级部门） */
    private Long parentId;

    /** 排序序号 */
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    /** 子部门列表（非数据库字段，仅用于树形返回） */
    @TableField(exist = false)
    private List<Department> children;

    /** 下级部门总数（非数据库字段，递归含所有后代） */
    @TableField(exist = false)
    private int childCount;

    /** 部门成员总数（非数据库字段，递归含所有下级成员） */
    @TableField(exist = false)
    private int memberCount;

    /** 本部门直接成员数（非数据库字段，不含下级） */
    @TableField(exist = false)
    private int directMemberCount;
}
