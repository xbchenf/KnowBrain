package com.knowbrain.scenario;

import com.knowbrain.scenario.entity.ScenarioCategory;
import com.knowbrain.scenario.mapper.ScenarioCategoryMapper;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 场景配置 — 从 DB 读取分类树，首次启动从 JSON 种子导入
 */
@Slf4j
@Component
public class ScenarioConfig {

    private final ScenarioCategoryMapper mapper;

    @Getter
    private List<Category> categories = Collections.emptyList();

    /** DB 扁平行 → 内存分类树 */
    private final Map<String, Category> nodeMap = new HashMap<>();

    public ScenarioConfig(ScenarioCategoryMapper mapper) {
        this.mapper = mapper;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    /** 从 DB 重新加载分类树 */
    public synchronized void reload() {
        List<ScenarioCategory> rows = mapper.selectList(null);
        if (rows.isEmpty()) {
            categories = Collections.emptyList();
            log.warn("分类数据为空，等待 ScenarioDataInitializer 导入种子");
            return;
        }

        nodeMap.clear();
        List<Category> roots = new ArrayList<>();

        for (ScenarioCategory row : rows) {
            Category node = new Category();
            node.setName(row.getName());
            node.setKey(row.getKey());
            node.setChildren(new ArrayList<>());
            nodeMap.put(row.getKey(), node);

            if (row.getParentKey() == null) {
                roots.add(node);
            }
        }

        // 组装父子关系
        for (ScenarioCategory row : rows) {
            if (row.getParentKey() != null) {
                Category parent = nodeMap.get(row.getParentKey());
                Category child = nodeMap.get(row.getKey());
                if (parent != null && child != null) {
                    parent.getChildren().add(child);
                }
            }
        }

        this.categories = roots;
        log.info("分类树加载完成: {} 个一级分类, {} 个节点", roots.size(), nodeMap.size());
    }

    /** 根据 key 查找分类名称（递归） */
    public String findCategoryName(String key) {
        for (Category cat : categories) {
            String name = findInTree(cat, key);
            if (name != null) return name;
        }
        return null;
    }

    private String findInTree(Category node, String key) {
        if (key.equals(node.getKey())) return node.getName();
        if (node.getChildren() != null) {
            for (Category child : node.getChildren()) {
                String name = findInTree(child, key);
                if (name != null) return name;
            }
        }
        return null;
    }
}
