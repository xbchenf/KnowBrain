package com.knowbrain.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowbrain.common.GlobalExceptionHandler.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 用户管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper userMapper;

    /**
     * 分页查询用户列表，支持按部门和关键词过滤
     */
    public Page<SysUser> listUsers(int page, int size, Long departmentId, String keyword) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (departmentId != null) {
            wrapper.eq(SysUser::getDepartmentId, departmentId);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                    .like(SysUser::getUsername, keyword)
                    .or()
                    .like(SysUser::getName, keyword));
        }
        wrapper.orderByDesc(SysUser::getCreateTime);
        return userMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /**
     * 更新用户信息（部门、角色、状态）
     */
    public SysUser updateUser(Long id, SysUser updates) {
        SysUser user = userMapper.selectById(id);
        if (user == null) throw new BizException(404, "用户不存在");

        if (updates.getDepartmentId() != null) user.setDepartmentId(updates.getDepartmentId());
        if (updates.getRole() != null) user.setRole(updates.getRole());
        if (updates.getName() != null) user.setName(updates.getName());
        if (updates.getPhone() != null) user.setPhone(updates.getPhone());
        if (updates.getStatus() != null) user.setStatus(updates.getStatus());

        userMapper.updateById(user);
        log.info("用户更新: id={}, role={}, departmentId={}", id, user.getRole(), user.getDepartmentId());
        return user;
    }
}
