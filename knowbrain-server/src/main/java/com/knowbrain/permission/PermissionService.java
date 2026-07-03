package com.knowbrain.permission;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowbrain.common.GlobalExceptionHandler.BizException;
import com.knowbrain.space.Space;
import com.knowbrain.space.SpaceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 权限管理服务 — 空间级 RBAC
 *
 * 权限判断逻辑（按优先级）：
 * 1. PUBLIC 空间 → 所有人可读
 * 2. OWNER → 全部权限
 * 3. EDITOR → 读写文档
 * 4. VIEWER → 只读
 * 5. 非成员且非 PUBLIC → 无权限
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final SpaceMapper spaceMapper;
    private final SpaceMemberMapper memberMapper;

    /** 检查用户是否有权读取空间 */
    public void checkReadAccess(Long spaceId, Long userId) {
        Space space = spaceMapper.selectById(spaceId);
        if (space == null) throw new BizException(404, "空间不存在");

        // PUBLIC 空间所有人可读
        if ("PUBLIC".equals(space.getVisibility())) return;

        // OWNER 直接放行
        if (space.getOwnerId().equals(userId)) return;

        // 检查是否空间成员
        SpaceMember member = getMember(spaceId, userId);
        if (member == null) throw new BizException(403, "无权访问此空间");
    }

    /** 检查用户是否有权编辑空间内的文档 */
    public void checkWriteAccess(Long spaceId, Long userId) {
        Space space = spaceMapper.selectById(spaceId);
        if (space == null) throw new BizException(404, "空间不存在");

        // OWNER → 可写
        if (space.getOwnerId().equals(userId)) return;

        // EDITOR → 可写
        SpaceMember member = getMember(spaceId, userId);
        if (member != null && ("EDITOR".equals(member.getRole()) || "OWNER".equals(member.getRole()))) {
            return;
        }

        throw new BizException(403, "无权编辑此空间");
    }

    /** 检查用户是否为空间管理员（OWNER） */
    public void checkOwnerAccess(Long spaceId, Long userId) {
        Space space = spaceMapper.selectById(spaceId);
        if (space == null) throw new BizException(404, "空间不存在");
        if (!space.getOwnerId().equals(userId)) {
            throw new BizException(403, "仅空间创建者可执行此操作");
        }
    }

    /** 添加成员 */
    public SpaceMember addMember(Long spaceId, Long userId, String role) {
        // 检查是否已是成员
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
     * - PUBLIC 空间所有人可读
     * - 未登录用户只能看 PUBLIC
     * - 已登录用户可看 PUBLIC + 自己拥有的 + 自己加入的
     */
    public List<Long> getAccessibleSpaceIds(Long userId) {
        // 1. 所有 PUBLIC 空间
        List<Space> allSpaces = spaceMapper.selectList(null);
        List<Long> ids = new java.util.ArrayList<>();
        for (Space s : allSpaces) {
            if ("PUBLIC".equals(s.getVisibility())) {
                ids.add(s.getId());
            }
        }

        // 2. 未登录 → 只看 PUBLIC
        if (userId == null) return ids;

        // 3. 已登录 → 自己的空间 + 加入的空间
        for (Space s : allSpaces) {
            if (s.getOwnerId().equals(userId) && !ids.contains(s.getId())) {
                ids.add(s.getId());
            }
        }

        // 4. 加入的成员空间
        List<SpaceMember> memberships = memberMapper.selectList(
                new LambdaQueryWrapper<SpaceMember>().eq(SpaceMember::getUserId, userId));
        for (SpaceMember m : memberships) {
            if (!ids.contains(m.getSpaceId())) {
                ids.add(m.getSpaceId());
            }
        }

        return ids;
    }

    private SpaceMember getMember(Long spaceId, Long userId) {
        return memberMapper.selectOne(new LambdaQueryWrapper<SpaceMember>()
                .eq(SpaceMember::getSpaceId, spaceId)
                .eq(SpaceMember::getUserId, userId));
    }
}
