package com.knowbrain.auth;

import cn.hutool.crypto.digest.BCrypt;
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
     * 分页查询用户列表，支持按部门、角色、状态、关键词过滤
     */
    public Page<SysUser> listUsers(int page, int size, Long departmentId, String role, String status, String keyword) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (departmentId != null) {
            wrapper.eq(SysUser::getDepartmentId, departmentId);
        }
        if (StringUtils.hasText(role)) {
            wrapper.eq(SysUser::getRole, role);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(SysUser::getStatus, status);
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
     * 管理员创建用户
     */
    public SysUser createUser(String username, String password, String name, String phone,
                              String role, Long departmentId) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new BizException(400, "用户名和密码不能为空");
        }
        if (username.length() < 3 || username.length() > 50) {
            throw new BizException(400, "用户名需 3-50 个字符");
        }
        try {
            PasswordValidator.validate(password);
        } catch (IllegalArgumentException e) {
            throw new BizException(400, e.getMessage());
        }
        if (userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username)) > 0) {
            throw new BizException(400, "用户名已存在");
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(BCrypt.hashpw(password, BCrypt.gensalt()));
        user.setName(name != null ? name : username);
        user.setPhone(phone != null ? phone : "");
        user.setRole(role != null ? role : "USER");
        user.setDepartmentId(departmentId);
        user.setStatus("ACTIVE");
        userMapper.insert(user);

        log.info("管理员创建用户: username={}, id={}, role={}", username, user.getId(), user.getRole());
        return user;
    }

    /**
     * 更新用户信息（姓名、手机、部门、角色、状态）
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

    /**
     * 管理员重置用户密码
     */
    public void resetPassword(Long id, String newPassword) {
        SysUser user = userMapper.selectById(id);
        if (user == null) throw new BizException(404, "用户不存在");
        try {
            PasswordValidator.validate(newPassword);
        } catch (IllegalArgumentException e) {
            throw new BizException(400, e.getMessage());
        }

        user.setPasswordHash(BCrypt.hashpw(newPassword, BCrypt.gensalt()));
        userMapper.updateById(user);
        log.info("密码重置: userId={}", id);
    }
}
