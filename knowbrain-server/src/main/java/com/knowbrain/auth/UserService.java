package com.knowbrain.auth;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowbrain.common.GlobalExceptionHandler.BizException;
import com.knowbrain.im.entity.ImUserIdentity;
import com.knowbrain.im.mapper.ImUserIdentityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.time.Instant;

/**
 * 用户管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper userMapper;
    private final StringRedisTemplate redis;
    private final ImUserIdentityMapper identityMapper;

    private static final String INVALID_BEFORE_KEY = "kb:token:invalid_before:";

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
        // 手机号必填 + 格式 + 唯一性校验
        if (phone == null || phone.isBlank()) {
            throw new BizException(400, "手机号不能为空");
        }
        if (!phone.matches("^1[3-9]\\d{9}$")) {
            throw new BizException(400, "手机号格式不正确");
        }
        if (userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getPhone, phone)) > 0) {
            throw new BizException(400, "该手机号已被其他用户使用");
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(BCrypt.hashpw(password, BCrypt.gensalt()));
        user.setName(name != null ? name : username);
        user.setPhone(phone != null ? phone : "");
        user.setRole(role != null ? role : RoleEnum.USER.getCode());
        user.setDepartmentId(departmentId);
        user.setStatus("ACTIVE");
        userMapper.insert(user);

        log.info("管理员创建用户: username={}, id={}, role={}", username, user.getId(), user.getRole());
        return user;
    }

    /**
     * 更新用户信息（姓名、手机、部门、角色、状态）
     * 当用户被禁用时，使其所有现有 Token 立即失效
     */
    public SysUser updateUser(Long id, SysUser updates) {
        SysUser user = userMapper.selectById(id);
        if (user == null) throw new BizException(404, "用户不存在");

        if (updates.getDepartmentId() != null) user.setDepartmentId(updates.getDepartmentId());
        if (updates.getRole() != null) user.setRole(updates.getRole());
        if (updates.getName() != null) user.setName(updates.getName());
        if (updates.getPhone() != null) {
            String phone = updates.getPhone();
            if (!phone.isBlank()) {
                if (!phone.matches("^1[3-9]\\d{9}$")) {
                    throw new BizException(400, "手机号格式不正确");
                }
                if (userMapper.selectCount(
                        new LambdaQueryWrapper<SysUser>()
                                .eq(SysUser::getPhone, phone)
                                .ne(SysUser::getId, id)) > 0) {
                    throw new BizException(400, "该手机号已被其他用户使用");
                }
            }
            user.setPhone(phone);
        }
        if (updates.getStatus() != null) {
            user.setStatus(updates.getStatus());
            // 用户被禁用 → 立即作废所有现有 Token
            if ("DISABLED".equals(updates.getStatus())) {
                invalidateAllTokens(id);
            }
        }

        userMapper.updateById(user);
        log.info("用户更新: id={}, role={}, departmentId={}", id, user.getRole(), user.getDepartmentId());
        return user;
    }

    /**
     * 管理员重置用户密码 → 立即作废所有现有 Token，强制重新登录
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
        invalidateAllTokens(id);
        log.info("密码重置并作废Token: userId={}", id);
    }

    /**
     * 作废用户所有现有 Token：记录一个时间戳，iat 早于此时间戳的 Token 全部失效
     */
    private void invalidateAllTokens(Long userId) {
        long now = System.currentTimeMillis() / 1000;
        redis.opsForValue().set(INVALID_BEFORE_KEY + userId,
                String.valueOf(now),
                Duration.ofHours(48)); // TTL = JWT 最大有效期
        log.info("用户 Token 全部作废: userId={}, invalidBefore={}", userId, now);
    }

    /**
     * 获取用户 Token 失效时间戳（秒），无记录则返回 0
     */
    public long getTokenInvalidBefore(Long userId) {
        String val = redis.opsForValue().get(INVALID_BEFORE_KEY + userId);
        if (val == null) return 0;
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 自服务：获取个人资料（含关联的 OAuth2 身份列表）
     */
    public Map<String, Object> getProfile(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) throw new BizException(404, "用户不存在");

        List<Map<String, Object>> identities = identityMapper.selectList(
                        new LambdaQueryWrapper<ImUserIdentity>()
                                .eq(ImUserIdentity::getKbUserId, userId))
                .stream().map(i -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", i.getId());
                    m.put("platform", i.getPlatform());
                    m.put("platformUid", i.getPlatformUid());
                    m.put("platformName", i.getPlatformName());
                    m.put("linkedAt", i.getLinkedAt());
                    return m;
                }).collect(Collectors.toList());

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("userId", user.getId());
        profile.put("username", user.getUsername());
        profile.put("name", user.getName());
        profile.put("phone", user.getPhone());
        profile.put("role", user.getRole());
        profile.put("status", user.getStatus());
        profile.put("departmentId", user.getDepartmentId());
        profile.put("createTime", user.getCreateTime());
        profile.put("identities", identities);
        return profile;
    }

    /**
     * 自服务：更新个人资料（姓名、手机号）
     */
    public SysUser updateProfile(Long userId, String name, String phone) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) throw new BizException(404, "用户不存在");

        if (phone != null && !phone.isBlank()) {
            if (!phone.matches("^1[3-9]\\d{9}$")) {
                throw new BizException(400, "手机号格式不正确");
            }
            // 唯一性检查（排除自身）
            if (userMapper.selectCount(
                    new LambdaQueryWrapper<SysUser>()
                            .eq(SysUser::getPhone, phone)
                            .ne(SysUser::getId, userId)) > 0) {
                throw new BizException(400, "该手机号已被其他用户使用");
            }
            user.setPhone(phone);
        }
        if (name != null && !name.isBlank()) {
            user.setName(name);
        }
        userMapper.updateById(user);
        log.info("个人资料更新: userId={}", userId);
        return user;
    }
}
