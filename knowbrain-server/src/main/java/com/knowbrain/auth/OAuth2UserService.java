package com.knowbrain.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowbrain.im.entity.ImUserIdentity;
import com.knowbrain.im.mapper.ImUserIdentityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OIDC 用户查找 / 自动创建（JIT Provisioning）。
 *
 * <p>通过已有的 {@code kb_user_identity} 表关联外部 IdP 身份与本地用户，
 * 无需修改 {@code kb_sys_user} 表结构。
 * 首次登录时自动创建本地用户（角色默认为 USER），并记录身份绑定。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2UserService {

    private final SysUserMapper userMapper;
    private final ImUserIdentityMapper identityMapper;

    /**
     * 查找或创建 OIDC 用户。
     *
     * @param platform    平台标识（对应 OAuth2 registrationId）：feishu / dingtalk / oidc
     * @param platformUid IdP 侧用户唯一标识（OIDC 的 sub claim、钉钉的 openId）
     * @param name        平台侧显示名
     * @param mobile      手机号（如有，用于跨平台身份匹配）
     * @return 对应的本地用户
     */
    @Transactional
    public SysUser lookupOrCreate(String platform, String platformUid, String name, String mobile) {
        // 1. 通过 kb_user_identity 查是否已有绑定
        ImUserIdentity identity = identityMapper.selectOne(
                new LambdaQueryWrapper<ImUserIdentity>()
                        .eq(ImUserIdentity::getPlatform, platform)
                        .eq(ImUserIdentity::getPlatformUid, platformUid));

        // 1b. platform+platformUid 未匹配 → 尝试手机号兜底匹配
        // 钉钉 IM Bot 和 OAuth2 返回的用户标识符可能不同（userid vs openId），
        // 但手机号相同，可以关联到同一用户
        if (identity == null && mobile != null && !mobile.isEmpty()) {
            // 先查 kb_user_identity 中有没有同手机号的绑定
            ImUserIdentity mobileIdentity = identityMapper.selectOne(
                    new LambdaQueryWrapper<ImUserIdentity>()
                            .eq(ImUserIdentity::getPlatform, platform)
                            .eq(ImUserIdentity::getMobile, mobile));
            if (mobileIdentity != null) {
                mobileIdentity.setPlatformUid(platformUid);
                if (name != null) mobileIdentity.setPlatformName(name);
                identityMapper.updateById(mobileIdentity);
                identity = mobileIdentity;
            }
            // 再查 kb_sys_user 中的手机号（IM Bot 绑定可能没填 identity.mobile）
            if (identity == null) {
                SysUser userByPhone = userMapper.selectOne(
                        new LambdaQueryWrapper<SysUser>()
                                .eq(SysUser::getPhone, mobile));
                if (userByPhone != null) {
                    // 新增一条 identity 绑定到该手机号对应的用户
                    ImUserIdentity newLink = new ImUserIdentity();
                    newLink.setKbUserId(userByPhone.getId());
                    newLink.setPlatform(platform);
                    newLink.setPlatformUid(platformUid);
                    newLink.setPlatformName(name);
                    newLink.setMobile(mobile);
                    identityMapper.insert(newLink);
                    identity = newLink;
                }
            }
        }

        if (identity != null) {
            SysUser user = userMapper.selectById(identity.getKbUserId());
            if (user != null && "ACTIVE".equals(user.getStatus())) {
                // 更新平台显示名（可能已变更）
                if (name != null && !name.equals(identity.getPlatformName())) {
                    identity.setPlatformName(name);
                    identityMapper.updateById(identity);
                }
                // 同步更新本地用户的显示名和手机号
                boolean needUpdate = false;
                if (name != null && !name.equals(user.getName())) {
                    user.setName(name);
                    needUpdate = true;
                }
                if (mobile != null && !mobile.isEmpty() && !mobile.equals(user.getPhone())) {
                    user.setPhone(mobile);
                    needUpdate = true;
                }
                if (needUpdate) {
                    userMapper.updateById(user);
                }
                log.debug("[OIDC] 已有用户: platform={}, platformUid={}, kbUserId={}",
                        platform, platformUid, user.getId());
                return user;
            }
            if (user != null && "DISABLED".equals(user.getStatus())) {
                log.warn("[OIDC] 用户已禁用，拒绝登录: platform={}, platformUid={}, kbUserId={}",
                        platform, platformUid, user.getId());
                throw new IllegalStateException("该账号已被禁用，请联系管理员");
            }
        }

        // 2. 不存在 → JIT Provisioning：创建本地用户
        String username = generateUsername(platform, platformUid);

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash("");  // OIDC 用户无密码，无法用密码登录
        user.setName(name != null ? name : username);
        user.setPhone(mobile != null ? mobile : "");
        user.setRole(RoleEnum.USER.getCode());
        user.setStatus("ACTIVE");
        userMapper.insert(user);

        // 3. 记录身份绑定
        ImUserIdentity newIdentity = new ImUserIdentity();
        newIdentity.setKbUserId(user.getId());
        newIdentity.setPlatform(platform);
        newIdentity.setPlatformUid(platformUid);
        newIdentity.setPlatformName(name);
        newIdentity.setMobile(mobile);
        identityMapper.insert(newIdentity);

        log.info("[OIDC] 新用户已创建: platform={}, platformUid={}, kbUserId={}, username={}",
                platform, platformUid, user.getId(), username);
        return user;
    }

    /**
     * 为新用户生成唯一用户名。
     * 格式：{platform}_{platformUid 前 30 位}
     * 冲突时追加数字后缀。
     */
    private String generateUsername(String platform, String platformUid) {
        String base = platform + "_" + platformUid.substring(0, Math.min(platformUid.length(), 30));
        // 清理非法字符（只保留字母数字和下划线）
        base = base.replaceAll("[^a-zA-Z0-9_]", "_");

        if (userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, base)) == 0) {
            return base;
        }
        // 冲突 → 追加序号
        for (int i = 2; i < 100; i++) {
            String candidate = base + "_" + i;
            if (candidate.length() > 50) {
                candidate = base.substring(0, 50 - String.valueOf(i).length() - 1) + "_" + i;
            }
            if (userMapper.selectCount(
                    new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, candidate)) == 0) {
                return candidate;
            }
        }
        throw new IllegalStateException("无法生成唯一用户名: " + base);
    }
}
