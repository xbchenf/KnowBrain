package com.knowbrain.scenario;

import lombok.Data;

import java.util.List;

/**
 * 知识分类节点
 */
@Data
public class Category {

    /** 数据库 ID（用于删除操作） */
    private Long id;

    /** 分类名称 */
    private String name;

    /** 分类唯一标识 */
    private String key;

    /** 上级分类 key */
    private String parentKey;

    /** 子分类 */
    private List<Category> children;
}
