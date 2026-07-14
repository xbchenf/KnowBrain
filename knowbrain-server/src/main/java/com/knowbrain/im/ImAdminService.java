package com.knowbrain.im;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowbrain.auth.Department;
import com.knowbrain.auth.DepartmentMapper;
import com.knowbrain.auth.SysUser;
import com.knowbrain.auth.SysUserMapper;
import com.knowbrain.common.GlobalExceptionHandler.BizException;
import com.knowbrain.im.adapter.DingtalkBotAdapter;
import com.knowbrain.im.adapter.FeishuStreamBotHandler;
import com.knowbrain.im.adapter.WecomBotAdapter;
import com.knowbrain.im.entity.ImDeptMapping;
import com.knowbrain.im.entity.ImUserIdentity;
import com.knowbrain.im.mapper.ImDeptMappingMapper;
import com.knowbrain.im.mapper.ImUserIdentityMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImAdminService {

    private final ImDeptMappingMapper deptMappingMapper;
    private final ImUserIdentityMapper identityMapper;
    private final SysUserMapper userMapper;
    private final DepartmentMapper departmentMapper;

    @Autowired(required = false)
    private WecomBotAdapter wecomAdapter;

    @Autowired(required = false)
    private DingtalkBotAdapter dingtalkAdapter;

    @Autowired(required = false)
    private FeishuStreamBotHandler feishuHandler;

    // ==================== 部门映射 ====================

    public Page<DeptMappingVO> listDeptMappings(String platform, int page, int size) {
        LambdaQueryWrapper<ImDeptMapping> q = new LambdaQueryWrapper<>();
        if (platform != null && !platform.isBlank()) {
            q.eq(ImDeptMapping::getPlatform, platform);
        }
        q.orderByDesc(ImDeptMapping::getCreateTime);

        Page<ImDeptMapping> result = deptMappingMapper.selectPage(Page.of(page, size), q);

        // JOIN 部门名称
        Map<Long, String> deptNames = departmentMapper.selectList(null).stream()
                .collect(Collectors.toMap(Department::getId, Department::getName, (a, b) -> a));

        Page<DeptMappingVO> voPage = new Page<>(page, size, result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(m -> {
            DeptMappingVO vo = new DeptMappingVO();
            vo.setId(m.getId());
            vo.setPlatform(m.getPlatform());
            vo.setExternalDeptId(m.getExternalDeptId());
            vo.setKbDeptId(m.getKbDeptId());
            vo.setKbDeptName(deptNames.getOrDefault(m.getKbDeptId(), "—"));
            vo.setCreateTime(m.getCreateTime());
            return vo;
        }).toList());
        return voPage;
    }

    public ImDeptMapping createDeptMapping(ImDeptMapping mapping) {
        // 唯一性校验
        if (deptMappingMapper.selectCount(new LambdaQueryWrapper<ImDeptMapping>()
                .eq(ImDeptMapping::getPlatform, mapping.getPlatform())
                .eq(ImDeptMapping::getExternalDeptId, mapping.getExternalDeptId())) > 0) {
            throw new BizException(400, "该平台部门已存在映射");
        }
        deptMappingMapper.insert(mapping);
        return mapping;
    }

    public ImDeptMapping updateDeptMapping(Long id, ImDeptMapping mapping) {
        ImDeptMapping existing = deptMappingMapper.selectById(id);
        if (existing == null) throw new BizException(404, "映射记录不存在");
        if (!existing.getPlatform().equals(mapping.getPlatform())
                || !existing.getExternalDeptId().equals(mapping.getExternalDeptId())) {
            if (deptMappingMapper.selectCount(new LambdaQueryWrapper<ImDeptMapping>()
                    .eq(ImDeptMapping::getPlatform, mapping.getPlatform())
                    .eq(ImDeptMapping::getExternalDeptId, mapping.getExternalDeptId())
                    .ne(ImDeptMapping::getId, id)) > 0) {
                throw new BizException(400, "该平台部门已存在映射");
            }
        }
        mapping.setId(id);
        deptMappingMapper.updateById(mapping);
        return mapping;
    }

    public void deleteDeptMapping(Long id) {
        deptMappingMapper.deleteById(id);
    }

    // ==================== IM 身份 ====================

    public Page<IdentityVO> listIdentities(String platform, String keyword, int page, int size,
                                            boolean unboundOnly) {
        LambdaQueryWrapper<ImUserIdentity> q = new LambdaQueryWrapper<>();
        if (platform != null && !platform.isBlank()) {
            q.eq(ImUserIdentity::getPlatform, platform);
        }
        q.orderByDesc(ImUserIdentity::getLinkedAt);

        Page<ImUserIdentity> result = identityMapper.selectPage(Page.of(page, size), q);

        // JOIN 用户信息
        List<Long> userIds = result.getRecords().stream()
                .map(ImUserIdentity::getKbUserId).distinct().toList();
        Map<Long, SysUser> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));

        Page<IdentityVO> voPage = new Page<>(page, size, result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(i -> {
            SysUser u = userMap.get(i.getKbUserId());
            IdentityVO vo = new IdentityVO();
            vo.setId(i.getId());
            vo.setKbUserId(i.getKbUserId());
            vo.setKbUsername(u != null ? u.getUsername() : null);
            vo.setKbName(u != null ? u.getName() : null);
            vo.setPlatform(i.getPlatform());
            vo.setPlatformUid(i.getPlatformUid());
            vo.setPlatformName(i.getPlatformName());
            vo.setMobile(i.getMobile());
            vo.setLinkedAt(i.getLinkedAt());
            return vo;
        })
        .filter(vo -> keyword == null || keyword.isBlank()
                || matchesKeyword(vo, keyword))
        .filter(vo -> !unboundOnly || isAutoCreatedImUser(vo.getKbUsername()))
        .toList());
        return voPage;
    }

    /** 判断用户名是否匹配自动创建的 IM 用户模式：im_{platform}_{uid} */
    private boolean isAutoCreatedImUser(String username) {
        return username != null && username.startsWith("im_");
    }

    public ImUserIdentity bindIdentity(Long kbUserId, String platform, String platformUid) {
        // 校验 KB 用户存在
        if (userMapper.selectById(kbUserId) == null) {
            throw new BizException(400, "KB 用户不存在");
        }
        // 如果已存在同平台+uid的身份 → 更新绑定（支持管理员改绑）
        ImUserIdentity existing = identityMapper.selectOne(new LambdaQueryWrapper<ImUserIdentity>()
                .eq(ImUserIdentity::getPlatform, platform)
                .eq(ImUserIdentity::getPlatformUid, platformUid));
        if (existing != null) {
            if (existing.getKbUserId().equals(kbUserId)) {
                // 补充之前缺失的显示名
                if (existing.getPlatformName() == null || existing.getPlatformName().isBlank()) {
                    existing.setPlatformName(fetchPlatformName(platform, platformUid));
                    identityMapper.updateById(existing);
                }
                return existing; // 已经是这个用户，幂等
            }
            Long oldKbUserId = existing.getKbUserId();
            existing.setKbUserId(kbUserId);
            // 补充显示名（可能之前未获取到）
            if (existing.getPlatformName() == null || existing.getPlatformName().isBlank()) {
                existing.setPlatformName(fetchPlatformName(platform, platformUid));
            }
            identityMapper.updateById(existing);
            log.info("[IM管理] 改绑身份: platform={}, uid={}, {} → {}",
                    platform, platformUid, oldKbUserId, kbUserId);

            // 旧账号是自动创建的 IM 用户 → 自动禁用（不再被任何 identity 指向）
            disableOrphanImUser(oldKbUserId, platform, platformUid);
            return existing;
        }
        ImUserIdentity identity = new ImUserIdentity();
        identity.setKbUserId(kbUserId);
        identity.setPlatform(platform);
        identity.setPlatformUid(platformUid);
        identity.setPlatformName(fetchPlatformName(platform, platformUid));
        identityMapper.insert(identity);
        return identity;
    }

    /** 尝试从平台 API 获取用户显示名，失败返回 null */
    private String fetchPlatformName(String platform, String platformUid) {
        try {
            return switch (platform) {
                case "wecom" -> wecomAdapter != null ? wecomAdapter.fetchUserName(platformUid) : null;
                case "dingtalk" -> dingtalkAdapter != null ? dingtalkAdapter.fetchUserName(platformUid) : null;
                case "feishu" -> feishuHandler != null ? feishuHandler.fetchUserName(platformUid) : null;
                default -> null;
            };
        } catch (Exception e) {
            log.debug("[IM管理] 获取平台显示名失败: platform={}, uid={}", platform, platformUid, e);
            return null;
        }
    }

    /**
     * 改绑后，如果旧 KB 用户是自动创建的 IM 用户
     * （username 匹配 im_{platform}_{uid}）且不再被任何 identity 指向，则自动禁用。
     */
    private void disableOrphanImUser(Long oldKbUserId, String platform, String platformUid) {
        SysUser oldUser = userMapper.selectById(oldKbUserId);
        if (oldUser == null) return;

        String expectedUsername = "im_" + platform + "_" + platformUid;
        // 只处理自动创建的 IM 用户（用户名匹配模式），不误伤手动创建的用户
        if (!expectedUsername.equals(oldUser.getUsername())
                && !oldUser.getUsername().startsWith("im_" + platform + "_")) {
            return;
        }

        // 检查该用户是否还有其他 identity 指向
        Long remaining = identityMapper.selectCount(new LambdaQueryWrapper<ImUserIdentity>()
                .eq(ImUserIdentity::getKbUserId, oldKbUserId));
        if (remaining == 0) {
            oldUser.setStatus("DISABLED");
            userMapper.updateById(oldUser);
            log.info("[IM管理] 自动禁用孤立的自动创建用户: kbUserId={}, username={}", oldKbUserId, oldUser.getUsername());
        }
    }

    public void unbindIdentity(Long id) {
        identityMapper.deleteById(id);
    }

    // ==================== 聚合信息 ====================

    public DepartmentsInfoVO getDepartmentsInfo() {
        return getDepartmentsInfo(null);
    }

    /**
     * @param platform 可选，传 wecom/dingtalk 时会同时返回平台侧部门列表
     */
    public DepartmentsInfoVO getDepartmentsInfo(String platform) {
        List<Department> kbDepts = departmentMapper.selectList(
                new LambdaQueryWrapper<Department>().orderByAsc(Department::getSortOrder));

        // 已有映射关系（用于标注哪些平台部门已经映射）
        Set<String> mappedExternalIds = deptMappingMapper.selectList(null).stream()
                .filter(m -> platform == null || platform.equals(m.getPlatform()))
                .map(ImDeptMapping::getExternalDeptId)
                .collect(Collectors.toSet());

        // 拉取平台部门列表
        List<Map<String, Object>> platformDepts = fetchPlatformDepartments(platform);

        return new DepartmentsInfoVO(kbDepts, platformDepts, mappedExternalIds);
    }

    /**
     * 从平台 API 拉取完整部门树。
     */
    public List<Map<String, Object>> fetchPlatformDepartments(String platform) {
        if (platform == null || platform.isBlank()) return List.of();
        try {
            return switch (platform) {
                case "wecom" -> wecomAdapter != null
                        ? wecomAdapter.fetchDepartmentList() : List.of();
                case "dingtalk" -> dingtalkAdapter != null
                        ? dingtalkAdapter.fetchDepartmentList() : List.of();
                case "feishu" -> {
                    if (feishuHandler == null) {
                        log.warn("[IM管理] feishuHandler 为 null，无法拉取部门");
                        yield List.of();
                    }
                    yield feishuHandler.fetchDepartmentList();
                }
                default -> List.of();
            };
        } catch (Exception e) {
            log.warn("[IM管理] 拉取平台部门失败: platform={}, error={}", platform, e.toString());
            return List.of();
        }
    }

    // ==================== 内部方法 ====================

    private boolean matchesKeyword(IdentityVO vo, String keyword) {
        String kw = keyword.toLowerCase();
        return (vo.getKbUsername() != null && vo.getKbUsername().toLowerCase().contains(kw))
                || (vo.getKbName() != null && vo.getKbName().toLowerCase().contains(kw))
                || (vo.getPlatformUid() != null && vo.getPlatformUid().toLowerCase().contains(kw))
                || (vo.getMobile() != null && vo.getMobile().contains(kw));
    }

    // ==================== VO ====================

    @Data
    public static class DeptMappingVO {
        private Long id;
        private String platform;
        private String externalDeptId;
        private Long kbDeptId;
        private String kbDeptName;
        private java.time.LocalDateTime createTime;
    }

    @Data
    public static class IdentityVO {
        private Long id;
        private Long kbUserId;
        private String kbUsername;
        private String kbName;
        private String platform;
        private String platformUid;
        private String platformName;
        private String mobile;
        private java.time.LocalDateTime linkedAt;
    }

    @Data
    public static class DepartmentsInfoVO {
        private final List<Department> kbDepartments;
        /** 平台侧部门列表（仅当指定 platform 时有值） */
        private List<Map<String, Object>> platformDepartments;
        /** 已映射的平台部门 ID 集合 */
        private Set<String> mappedExternalIds;

        public DepartmentsInfoVO(List<Department> kbDepartments,
                                  List<Map<String, Object>> platformDepartments,
                                  Set<String> mappedExternalIds) {
            this.kbDepartments = kbDepartments;
            this.platformDepartments = platformDepartments;
            this.mappedExternalIds = mappedExternalIds;
        }

        public DepartmentsInfoVO(List<Department> kbDepartments) {
            this(kbDepartments, List.of(), Set.of());
        }
    }
}
