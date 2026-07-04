package com.knowbrain.permission;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowbrain.auth.SysUser;
import com.knowbrain.auth.SysUserMapper;
import com.knowbrain.common.GlobalExceptionHandler.BizException;
import com.knowbrain.space.Space;
import com.knowbrain.space.SpaceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 权限管理服务 — 可见性 + 部门 + 角色 三因子权限模型
 *
 * 读权限：
 *   PUBLIC  → 所有登录用户
 *   TEAM    → 部门匹配 或 Owner
 *   PRIVATE → Owner 或显式成员
 *
 * 写权限：
 *   ADMIN / MANAGER / SpaceOwner → 可写
 *
 * 检索权限过滤：
 *   getAccessibleSpaceIds(userId) → 按上述规则返回空间 ID 列表
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final SpaceMapper spaceMapper;
    private final SpaceMemberMapper memberMapper;
    private final SysUserMapper userMapper;

    /** 检查用户是否有权读取空间 */
    public void checkReadAccess(Long spaceId, Long userId) {
        Space space = spaceMapper.selectById(spaceId);
        if (space == null) throw new BizException(404, "空间不存在");

        // 1. PUBLIC → 所有登录用户放行
        if ("PUBLIC".equals(space.getVisibility())) return;

        // 2. OWNER → 放行
        if (space.getOwnerId().equals(userId)) return;

        // 3. TEAM → 部门匹配
        if ("TEAM".equals(space.getVisibility())) {
            Long userDeptId = getUserDepartmentId(userId);
            if (userDeptId != null && isInDepartmentScope(space.getDepartmentScope(), userDeptId)) {
                return;
            }
            throw new BizException(403, "无权访问此空间（部门不匹配）");
        }

        // 4. PRIVATE → 显式成员
        SpaceMember member = getMember(spaceId, userId);
        if (member == null) throw new BizException(403, "无权访问此空间");
    }

    /** 检查用户是否有权编辑空间（写权限 = 角色决定） */
    public void checkWriteAccess(Long spaceId, Long userId) {
        Space space = spaceMapper.selectById(spaceId);
        if (space == null) throw new BizException(404, "空间不存在");

        // ADMIN / MANAGER → 全部可写
        String role = getUserRole(userId);
        if ("ADMIN".equals(role) || "MANAGER".equals(role)) return;

        // SpaceOwner → 自己的空间可写
        if (space.getOwnerId().equals(userId)) return;

        throw new BizException(403, "无权编辑此空间");
    }

    /** 检查用户是否为空间管理员（OWNER 或系统 ADMIN） */
    public void checkOwnerAccess(Long spaceId, Long userId) {
        Space space = spaceMapper.selectById(spaceId);
        if (space == null) throw new BizException(404, "空间不存在");

        String role = getUserRole(userId);
        if ("ADMIN".equals(role)) return;
        if (space.getOwnerId().equals(userId)) return;

        throw new BizException(403, "仅空间创建者或系统管理员可执行此操作");
    }

    /** 添加成员（仅 PRIVATE 空间使用） */
    public SpaceMember addMember(Long spaceId, Long userId, String role) {
        SpaceMember existing = getMember(spaceId, userId);
        if (existing != null) {
            existing.setRole(role);
            memberMapper.updateById(existing);
            log.info("成员角色更新: spaceId={}, userId={}, role={}", spaceId, userId, role);
            return existing;
        }

        SpaceMember member = new SpaceMember();
        member.setSpaceId(spaceId);
        member.setUserId(userId);
        member.setRole(role);
        memberMapper.insert(member);
        log.info("成员添加: spaceId={}, userId={}, role={}", spaceId, userId, role);
        return member;
    }

    /** 移除成员 */
    public void removeMember(Long spaceId, Long userId) {
        SpaceMember member = getMember(spaceId, userId);
        if (member != null) {
            memberMapper.deleteById(member.getId());
            log.info("成员移除: spaceId={}, userId={}", spaceId, userId);
        }
    }

    /** 获取空间所有成员 */
    public List<SpaceMember> listMembers(Long spaceId) {
        return memberMapper.selectList(
                new LambdaQueryWrapper<SpaceMember>().eq(SpaceMember::getSpaceId, spaceId));
    }

    /** 获取用户在某空间的角色，非成员返回 null */
    public String getUserRole(Long spaceId, Long userId) {
        Space space = spaceMapper.selectById(spaceId);
        if (space != null && space.getOwnerId().equals(userId)) return "OWNER";

        SpaceMember member = getMember(spaceId, userId);
        return member != null ? member.getRole() : null;
    }

    /**
     * 获取用户可读的空间 ID 列表（用于检索权限过滤）
     *
     * 规则：
     * - PUBLIC 空间 → 所有登录用户可见
     * - OWNER 的空间 → 全部可见
     * - TEAM 空间 → 部门匹配
     * - PRIVATE 空间 → 显式成员
     *
     * 前置条件：userId 非空（前端强制登录，AuthInterceptor 保证）
     */
    public List<Long> getAccessibleSpaceIds(Long userId) {
        List<Space> allSpaces = spaceMapper.selectList(null);
        List<Long> ids = new ArrayList<>();
        Long userDeptId = getUserDepartmentId(userId);

        for (Space s : allSpaces) {
            // 1. PUBLIC → 所有登录用户
            if ("PUBLIC".equals(s.getVisibility())) {
                ids.add(s.getId());
                continue;
            }

            // 2. OWNER → 自己的空间全部可见
            if (s.getOwnerId().equals(userId)) {
                ids.add(s.getId());
                continue;
            }

            // 3. TEAM → 部门匹配
            if ("TEAM".equals(s.getVisibility())) {
                if (userDeptId != null && isInDepartmentScope(s.getDepartmentScope(), userDeptId)) {
                    ids.add(s.getId());
                }
                continue;
            }

            // 4. PRIVATE → 显式成员
            if (isMember(s.getId(), userId)) {
                ids.add(s.getId());
            }
        }

        return ids;
    }

    // ==================== 私有辅助方法 ====================

    private SpaceMember getMember(Long spaceId, Long userId) {
        return memberMapper.selectOne(new LambdaQueryWrapper<SpaceMember>()
                .eq(SpaceMember::getSpaceId, spaceId)
                .eq(SpaceMember::getUserId, userId));
    }

    private boolean isMember(Long spaceId, Long userId) {
        return getMember(spaceId, userId) != null;
    }

    /** 获取用户的系统角色（从 kb_sys_user 表） */
    private String getUserRole(Long userId) {
        SysUser user = userMapper.selectById(userId);
        return user != null ? user.getRole() : null;
    }

    /** 获取用户的部门 ID */
    private Long getUserDepartmentId(Long userId) {
        SysUser user = userMapper.selectById(userId);
        return user != null ? user.getDepartmentId() : null;
    }

    /**
     * 检查用户部门是否在空间的部门可见范围内
     * departmentScope 格式："1,2,5"（逗号分隔的部门 ID）
     */
    private boolean isInDepartmentScope(String departmentScope, Long userDeptId) {
        if (departmentScope == null || departmentScope.isBlank()) return false;
        return Arrays.stream(departmentScope.split(","))
                .map(String::trim)
                .anyMatch(id -> id.equals(String.valueOf(userDeptId)));
    }
}
