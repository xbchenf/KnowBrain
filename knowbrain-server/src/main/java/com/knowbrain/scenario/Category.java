package com.knowbrain.scenario;

import lombok.Data;

import java.util.List;

/**
 * 知识分类节点
 */
@Data
public class Category {

    /** 分类名称 */
    private String name;

    /** 分类唯一标识 */
    private String key;

    /** 子分类 */
    private List<Category> children;
}
