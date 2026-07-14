package com.knowbrain.im;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowbrain.auth.RoleEnum;
import com.knowbrain.auth.SysUser;
import com.knowbrain.auth.SysUserMapper;
import com.knowbrain.im.adapter.FeishuStreamBotHandler;
import com.knowbrain.im.adapter.WecomBotAdapter;
import com.knowbrain.im.entity.ImUserIdentity;
import com.knowbrain.im.mapper.ImUserIdentityMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * IM 用户 → KnowBrain 用户映射。
 *
 * <h3>身份统一策略（V2 — 跨平台归并）</h3>
 * <ol>
 *   <li>查 kb_user_identity 关联表 — 已有身份直接返回</li>
 *   <li>调平台 API 取手机号 → 跨平台匹配已有 KB 用户 → 追加身份</li>
 *   <li>Fallback: 查旧命名规则 (im_{platform}_{userId}) — 回填关联表</li>
 *   <li>以上都不命中 → 自动创建 KB 用户 + 关联记录</li>
 * </ol>
 *
 * <h3>自动创建的用户</h3>
 * 用户名: im_{platform}_{userId}<br>
 * 角色: USER<br>
 * 部门: 通过 ImDeptResolver 解析（失败降级为 null，仅 PUBLIC）<br>
 * 显示名: 优先 IM 消息中的 fromUserName → 平台 API 获取 → fallback 使用 username
 */
@Slf4j
@Component
public class ImUserMapping {

    private final SysUserMapper userMapper;
    private final ImDeptResolver deptResolver;
    private final ImUserIdentityMapper identityMapper;

    /** 可选注入：未配置企微时此字段为 null */
    @Autowired(required = false)
    private WecomBotAdapter wecomAdapter;

    /** 可选注入：未配置飞书时此字段为 null（@Lazy 打破循环依赖） */
    @Lazy
    @Autowired(required = false)
    private FeishuStreamBotHandler feishuHandler;

    private static final String USERNAME_PREFIX = "im_";

    public ImUserMapping(SysUserMapper userMapper, ImDeptResolver deptResolver,
                         ImUserIdentityMapper identityMapper) {
        this.userMapper = userMapper;
        this.deptResolver = deptResolver;
        this.identityMapper = identityMapper;
    }

    // ==================== 核心映射 ====================

    /**
     * 将 IM 用户映射为 KnowBrain 用户 ID。
     *
     * <p>按以下优先级查找或创建：
     * <ol>
     *   <li>查 kb_user_identity（platform + platform_uid）→ 命中直接返回</li>
     *   <li>手机号跨平台匹配 → 命中则追加本平台身份</li>
     *   <li>Fallback: 旧命名规则 username=im_{platform}_{userId} → 回填关联表</li>
     *   <li>创建新 KB 用户 + 关联记录</li>
     * </ol>
     *
     * @param platform   IM 平台（wecom / dingtalk / feishu）
     * @param imUserId   平台用户 ID
     * @param imUserName 平台显示名（可选，用于设置 KB 用户 name 字段）
     * @return KnowBrain 用户 ID（绝不会返回 null）
     */
    public Long resolveUserId(String platform, String imUserId, String imUserName) {
        // ---- Step 1: 查身份关联表 ----
        ImUserIdentity identity = identityMapper.selectOne(
                new LambdaQueryWrapper<ImUserIdentity>()
                        .eq(ImUserIdentity::getPlatform, platform)
                        .eq(ImUserIdentity::getPlatformUid, imUserId));
        if (identity != null) {
            SysUser user = userMapper.selectById(identity.getKbUserId());
            if (user != null) {
                if ("DISABLED".equals(user.getStatus())) {
                    log.warn("[IM] 用户已禁用: kbUserId={}, imUser={}/{}",
                            user.getId(), platform, imUserId);
                }
                // 懒更新: 部门 + 显示名（映射表可能刚刚配置）
                if (user.getDepartmentId() == null) {
                    tryUpdateDepartment(user, platform, imUserId);
                }
                // 补充手机号（首次匹配时可能没取到）
                if (identity.getMobile() == null || identity.getMobile().isBlank()) {
                    tryUpdateMobile(identity, platform, imUserId);
                }
                return user.getId();
            }
            // identity 指向的 KB 用户已被物理删除 → 删除烂数据，继续
            identityMapper.deleteById(identity.getId());
            log.warn("[IM] 关联记录指向已删除用户，已清理: identityId={}, kbUserId={}",
                    identity.getId(), identity.getKbUserId());
        }

        // ---- Step 2: 手机号跨平台匹配 ----
        String mobile = fetchMobile(platform, imUserId);
        if (mobile != null && !mobile.isBlank()) {
            ImUserIdentity matched = identityMapper.selectOne(
                    new LambdaQueryWrapper<ImUserIdentity>()
                            .isNotNull(ImUserIdentity::getKbUserId)
                            .eq(ImUserIdentity::getMobile, mobile)
                            .last("LIMIT 1"));
            if (matched != null && !matched.getKbUserId().equals(0L)) {
                SysUser user = userMapper.selectById(matched.getKbUserId());
                if (user != null) {
                    // 追加本平台身份到已有 KB 用户
                    insertIdentity(user.getId(), platform, imUserId, imUserName, mobile);
                    log.info("[IM] 手机号匹配成功: {}@{} → kbUserId={} (已有平台: {})",
                            imUserId, platform, user.getId(), matched.getPlatform());
                    // 懒更新部门
                    if (user.getDepartmentId() == null) {
                        tryUpdateDepartment(user, platform, imUserId);
                    }
                    return user.getId();
                }
            }
        }

        // ---- Step 3: Fallback — 查旧命名规则 ----
        String username = buildUsername(platform, imUserId);
        SysUser existing = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (existing != null) {
            // 回填关联表（旧用户升迁到新模型）
            insertIdentity(existing.getId(), platform, imUserId, imUserName, mobile);
            if (existing.getDepartmentId() == null) {
                tryUpdateDepartment(existing, platform, imUserId);
            }
            return existing.getId();
        }

        // ---- Step 4: 创建新 KB 用户 + 关联记录 ----
        SysUser newUser = createKbUser(platform, imUserId, imUserName);
        userMapper.insert(newUser);
        insertIdentity(newUser.getId(), platform, imUserId, newUser.getName(), mobile);
        log.info("[IM] 自动创建用户: kbUserId={}, imUser={}/{}, name={}, deptId={}, hasMobile={}",
                newUser.getId(), platform, imUserId, newUser.getName(),
                newUser.getDepartmentId(), mobile != null && !mobile.isBlank());
        return newUser.getId();
    }

    // ==================== 辅助方法 ====================

    /** 创建 KB 用户 */
    private SysUser createKbUser(String platform, String imUserId, String imUserName) {
        SysUser user = new SysUser();
        user.setUsername(buildUsername(platform, imUserId));
        user.setPasswordHash(""); // IM 用户无密码，仅通过 IM 身份认证
        user.setPhone("");
        user.setRole(RoleEnum.USER.getCode());
        user.setStatus("ACTIVE");

        // 显示名: 优先 IM 消息中的 fromUserName → API 获取 → fallback
        String displayName = imUserName;
        if (displayName == null || displayName.isBlank()) {
            displayName = fetchUserNameFromPlatform(platform, imUserId);
        }
        user.setName(displayName != null && !displayName.isBlank() ? displayName : user.getUsername());

        // 部门解析（失败不阻塞）
        try {
            user.setDepartmentId(deptResolver.resolveKbDeptId(platform, imUserId));
        } catch (Exception e) {
            log.warn("[IM] 部门解析失败，降级为 PUBLIC: imUser={}/{}", platform, imUserId, e);
        }

        return user;
    }

    /** 插入 IM 身份关联记录 */
    private void insertIdentity(Long kbUserId, String platform, String platformUid,
                                String platformName, String mobile) {
        ImUserIdentity identity = new ImUserIdentity();
        identity.setKbUserId(kbUserId);
        identity.setPlatform(platform);
        identity.setPlatformUid(platformUid);
        identity.setPlatformName(platformName);
        identity.setMobile(mobile);
        identityMapper.insert(identity);
    }

    /** 从平台 API 获取用户显示名（失败不阻塞，返回 null） */
    private String fetchUserNameFromPlatform(String platform, String imUserId) {
        try {
            return switch (platform) {
                case "wecom" -> wecomAdapter != null ? wecomAdapter.fetchUserName(imUserId) : null;
                case "feishu" -> feishuHandler != null ? feishuHandler.fetchUserName(imUserId) : null;
                default -> null;
            };
        } catch (Exception e) {
            log.debug("[IM] 获取显示名异常: platform={}, userId={}", platform, imUserId, e);
            return null;
        }
    }

    /** 获取平台侧手机号（失败不阻塞，返回 null） */
    private String fetchMobile(String platform, String imUserId) {
        try {
            return switch (platform) {
                case "wecom" -> wecomAdapter != null ? wecomAdapter.fetchUserMobile(imUserId) : null;
                case "feishu" -> feishuHandler != null ? feishuHandler.fetchUserMobile(imUserId) : null;
                default -> null;
            };
        } catch (Exception e) {
            log.debug("[IM] 获取手机号异常: platform={}, userId={}", platform, imUserId, e);
            return null;
        }
    }

    /** 补充身份记录中的手机号 */
    private void tryUpdateMobile(ImUserIdentity identity, String platform, String imUserId) {
        try {
            String mobile = fetchMobile(platform, imUserId);
            if (mobile != null && !mobile.isBlank()) {
                identity.setMobile(mobile);
                identityMapper.updateById(identity);
                log.info("[IM] 补充手机号: identityId={}, hasMobile=true", identity.getId());
            }
        } catch (Exception e) {
            log.debug("[IM] 补充手机号失败: identityId={}", identity.getId(), e);
        }
    }

    /** 构建 IM 用户的 KB 用户名 */
    private String buildUsername(String platform, String imUserId) {
        String safeId = imUserId.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (safeId.length() > 30) {
            safeId = safeId.substring(safeId.length() - 30);
        }
        return USERNAME_PREFIX + platform + "_" + safeId;
    }

    /** 懒更新已有用户的部门 */
    private void tryUpdateDepartment(SysUser user, String platform, String imUserId) {
        try {
            Long kbDeptId = deptResolver.resolveKbDeptId(platform, imUserId);
            if (kbDeptId != null) {
                user.setDepartmentId(kbDeptId);
                userMapper.updateById(user);
                log.info("[IM] 懒更新用户部门: kbUserId={}, deptId={}", user.getId(), kbDeptId);
            }
        } catch (Exception e) {
            log.warn("[IM] 懒更新部门失败: kbUserId={}", user.getId(), e);
        }
    }
}
