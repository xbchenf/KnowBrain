package com.knowbrain.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowbrain.common.GlobalExceptionHandler.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 部门管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentMapper departmentMapper;
    private final SysUserMapper userMapper;

    /**
     * 查询部门树（顶级部门 → 子部门嵌套），含成员数和子部门数统计
     */
    public List<Department> listAsTree() {
        List<Department> all = departmentMapper.selectList(
                new LambdaQueryWrapper<Department>()
                        .orderByAsc(Department::getSortOrder));

        // 统计每个部门的活跃成员数（排除禁用用户）
        List<SysUser> users = userMapper.selectList(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getStatus, "ACTIVE"));
        Map<Long, Long> memberCountMap = users.stream()
                .filter(u -> u.getDepartmentId() != null)
                .collect(Collectors.groupingBy(SysUser::getDepartmentId, Collectors.counting()));

        // 填充统计字段
        for (Department dept : all) {
            dept.setMemberCount(memberCountMap.getOrDefault(dept.getId(), 0L).intValue());
        }

        // 按 parentId 分组
        Map<Long, List<Department>> childrenMap = all.stream()
                .filter(d -> d.getParentId() != null && d.getParentId() > 0)
                .collect(Collectors.groupingBy(Department::getParentId));

        // 组装树：顶级部门 + 递归附加子节点
        List<Department> tree = new ArrayList<>();
        for (Department dept : all) {
            if (dept.getParentId() == null || dept.getParentId() == 0) {
                attachChildren(dept, childrenMap);
                tree.add(dept);
            }
        }

        // 自底向上递归汇总：成员数 + 子部门数（含所有下级）
        for (Department root : tree) {
            computeRecursiveStats(root);
        }

        return tree;
    }

    private void attachChildren(Department parent, Map<Long, List<Department>> childrenMap) {
        List<Department> children = childrenMap.get(parent.getId());
        parent.setChildren(children != null ? children : List.of());
        if (children != null) {
            for (Department child : children) {
                attachChildren(child, childrenMap);
            }
        }
    }

    /**
     * 自底向上递归汇总：将直接成员数和直接子节点数汇总为含所有下级的统计值。
     * memberCount 和 childCount 最终为递归汇总值，directMemberCount 保留本级直接成员数。
     */
    private int computeRecursiveStats(Department dept) {
        int directMembers = dept.getMemberCount(); // 汇总前存的是本级直接成员数
        dept.setDirectMemberCount(directMembers);

        int totalDescendants = 0;
        int totalMembers = directMembers;

        for (Department child : dept.getChildren()) {
            totalMembers += computeRecursiveStats(child);
            // 1（子部门本身）+ 子部门的所有后代
            totalDescendants += 1 + child.getChildCount();
        }

        dept.setChildCount(totalDescendants);
        dept.setMemberCount(totalMembers);
        return totalMembers;
    }

    /** 创建部门 */
    public Department create(Department dept) {
        departmentMapper.insert(dept);
        log.info("部门创建: id={}, name={}", dept.getId(), dept.getName());
        return dept;
    }

    /** 更新部门 */
    public Department update(Long id, Department updates) {
        Department dept = departmentMapper.selectById(id);
        if (dept == null) throw new BizException(404, "部门不存在");
        if (updates.getName() != null) dept.setName(updates.getName());
        if (updates.getParentId() != null) dept.setParentId(updates.getParentId());
        if (updates.getSortOrder() != null) dept.setSortOrder(updates.getSortOrder());
        departmentMapper.updateById(dept);
        log.info("部门更新: id={}", id);
        return dept;
    }

    /** 删除部门（递归检查：本级+所有下级部门无用户才能删除） */
    public void delete(Long id) {
        // 递归收集本级及所有下级部门 ID
        Set<Long> allIds = new HashSet<>();
        allIds.add(id);
        collectDescendantIds(id, allIds);

        // 检查这些部门下是否有用户
        long userCount = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().in(SysUser::getDepartmentId, allIds));
        if (userCount > 0) {
            throw new BizException(400, "该部门及其下级共有 " + userCount + " 名用户，无法删除。请先迁移或清空相关人员");
        }

        // 检查是否有子部门（直接子部门）
        long childCount = departmentMapper.selectCount(
                new LambdaQueryWrapper<Department>().eq(Department::getParentId, id));
        if (childCount > 0) {
            throw new BizException(400, "该部门下还有 " + childCount + " 个子部门，请先删除子部门");
        }

        departmentMapper.deleteById(id);
        log.info("部门删除: id={}", id);
    }

    /** 递归收集指定部门的所有后代部门 ID */
    private void collectDescendantIds(Long parentId, Set<Long> result) {
        List<Department> children = departmentMapper.selectList(
                new LambdaQueryWrapper<Department>().eq(Department::getParentId, parentId));
        for (Department child : children) {
            result.add(child.getId());
            collectDescendantIds(child.getId(), result);
        }
    }
}
