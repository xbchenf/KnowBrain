package com.knowbrain.im;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowbrain.auth.RoleEnum;
import com.knowbrain.auth.SysUser;
import com.knowbrain.auth.SysUserMapper;
import com.knowbrain.im.adapter.WecomBotAdapter;
import com.knowbrain.im.entity.ImUserIdentity;
import com.knowbrain.im.mapper.ImUserIdentityMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * IM 用户映射 — 多平台身份统一 单元测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IM 用户映射")
class ImUserMappingTest {

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private ImDeptResolver deptResolver;

    @Mock
    private ImUserIdentityMapper identityMapper;

    @Mock
    private WecomBotAdapter wecomAdapter;

    @InjectMocks
    private ImUserMapping userMapping;

    /** 自增计数器，模拟 MyBatis-Plus 自动生成 ID */
    private long nextUserId = 100L;

    @BeforeEach
    void setUp() {
        // @InjectMocks 不会注入 @Autowired(required=false) 字段，需手动反射注入
        setField(userMapping, "wecomAdapter", wecomAdapter);
    }

    // ==================== Step 1: 身份关联表命中 ====================

    @Test
    @DisplayName("身份关联表命中 → 直接返回 kbUserId")
    void identityHit() {
        ImUserIdentity identity = identity(1L, "wecom", "user001", "张三", "13800000001");
        when(identityMapper.selectOne(any())).thenReturn(identity);
        when(userMapper.selectById(1L)).thenReturn(sysUser(1L, "张三", 8L));

        Long result = userMapping.resolveUserId("wecom", "user001", "张三");

        assertEquals(1L, result);
        verify(identityMapper, never()).insert(any(ImUserIdentity.class));
    }

    @Test
    @DisplayName("身份关联表命中 + deptId 为空 → 懒更新部门")
    void identityHitLazyDeptUpdate() {
        ImUserIdentity identity = identity(1L, "wecom", "user001", "张三", null);
        when(identityMapper.selectOne(any())).thenReturn(identity);
        when(userMapper.selectById(1L)).thenReturn(sysUser(1L, "张三", null));
        when(deptResolver.resolveKbDeptId("wecom", "user001")).thenReturn(8L);

        Long result = userMapping.resolveUserId("wecom", "user001", "张三");

        assertEquals(1L, result);
        verify(userMapper).updateById(argThat((SysUser u) -> u.getDepartmentId() != null && u.getDepartmentId() == 8L));
    }

    @Test
    @DisplayName("身份关联表命中 + 手机号为空 → 尝试补充")
    void identityHitLazyMobileUpdate() {
        ImUserIdentity identity = identity(1L, "wecom", "user001", "张三", null);
        when(identityMapper.selectOne(any())).thenReturn(identity);
        when(userMapper.selectById(1L)).thenReturn(sysUser(1L, "张三", 8L));
        when(wecomAdapter.fetchUserMobile("user001")).thenReturn("13800000001");

        Long result = userMapping.resolveUserId("wecom", "user001", "张三");

        assertEquals(1L, result);
        verify(identityMapper).updateById(argThat((ImUserIdentity i) -> "13800000001".equals(i.getMobile())));
    }

    @Test
    @DisplayName("身份关联表命中但用户已禁用 → warn + 仍返回 id")
    void identityHitButDisabled() {
        ImUserIdentity identity = identity(2L, "wecom", "user002", "李四", null);
        when(identityMapper.selectOne(any())).thenReturn(identity);
        SysUser disabled = sysUser(2L, "李四", null);
        disabled.setStatus("DISABLED");
        when(userMapper.selectById(2L)).thenReturn(disabled);

        Long result = userMapping.resolveUserId("wecom", "user002", "李四");

        assertEquals(2L, result);
    }

    @Test
    @DisplayName("身份关联表指向已删除用户 → 清理烂数据 + 走后续流程")
    void identityStaleRecord() {
        ImUserIdentity identity = identity(99L, "wecom", "ghost", "Ghost", null);
        // Step 1: identity 命中
        when(identityMapper.selectOne(any())).thenReturn(identity);
        when(userMapper.selectById(99L)).thenReturn(null);

        // 回到 Step 2: mobile 为 null (wecomAdapter 默认不返回 mobile)
        // 回到 Step 3: fallback username
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        // Step 4: 创建新用户
        mockUserInsert();
        when(identityMapper.insert(any(ImUserIdentity.class))).thenReturn(1);

        Long result = userMapping.resolveUserId("wecom", "ghost", "Ghost");
        assertNotNull(result);
        verify(identityMapper).deleteById(99L);
    }

    // ==================== Step 2: 手机号跨平台匹配 ====================

    @Test
    @DisplayName("手机号匹配 — 新平台绑定到已有用户")
    void phoneMatchBindsNewPlatform() {
        // Step 1: 身份关联表未命中 → null
        // Step 2: mobile 匹配 → 查到已有身份
        ImUserIdentity existing = identity(1L, "dingtalk", "ding001", "张三", "13800000001");
        when(identityMapper.selectOne(any()))
                .thenReturn(null)       // Step 1: 查 identity 未命中
                .thenReturn(existing);   // Step 2: 查 mobile 命中

        when(wecomAdapter.fetchUserMobile("new-user")).thenReturn("13800000001");
        when(userMapper.selectById(1L)).thenReturn(sysUser(1L, "张三", 8L));
        when(identityMapper.insert(any(ImUserIdentity.class))).thenReturn(1);

        Long result = userMapping.resolveUserId("wecom", "new-user", "张三");

        assertEquals(1L, result);
        verify(identityMapper).insert(argThat((ImUserIdentity i) ->
                "wecom".equals(i.getPlatform()) && "new-user".equals(i.getPlatformUid())));
    }

    @Test
    @DisplayName("手机号匹配 — 未匹配到 → 创建新用户")
    void phoneMatchMiss() {
        when(identityMapper.selectOne(any())).thenReturn(null); // Step 1: 未命中
        when(wecomAdapter.fetchUserMobile("user003")).thenReturn("13800000003");

        // mobile 匹配也返回 null（无其他平台身份）→ 走 Step 3 fallback
        // Step 3: username 也返回 null → Step 4 创建
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        mockUserInsert();
        when(identityMapper.insert(any(ImUserIdentity.class))).thenReturn(1);

        Long result = userMapping.resolveUserId("wecom", "user003", "王五");

        assertNotNull(result);
        verify(userMapper).insert(any(SysUser.class));

        ArgumentCaptor<ImUserIdentity> captor = ArgumentCaptor.forClass(ImUserIdentity.class);
        verify(identityMapper, atLeastOnce()).insert(captor.capture());
        assertEquals("13800000003", captor.getValue().getMobile());
    }

    // ==================== Step 3: 旧命名 Fallback ====================

    @Test
    @DisplayName("旧命名规则命中 → 回填关联表")
    void oldUsernameFallback() {
        when(identityMapper.selectOne(any())).thenReturn(null);
        when(wecomAdapter.fetchUserMobile(any())).thenReturn(null);

        SysUser old = sysUser(3L, "老用户", 8L);
        old.setUsername("im_wecom_olduser");
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(old);
        when(identityMapper.insert(any(ImUserIdentity.class))).thenReturn(1);

        Long result = userMapping.resolveUserId("wecom", "olduser", "老用户");

        assertEquals(3L, result);
        verify(identityMapper).insert(argThat((ImUserIdentity i) ->
                "wecom".equals(i.getPlatform()) && "olduser".equals(i.getPlatformUid())));
    }

    // ==================== 异常降级 ====================

    @Test
    @DisplayName("fetchMobile 抛异常 → 降级跳过手机号匹配")
    void phoneApiExceptionDoesNotBlock() {
        when(identityMapper.selectOne(any())).thenReturn(null);
        when(wecomAdapter.fetchUserMobile("user004")).thenThrow(new RuntimeException("网络超时"));

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        mockUserInsert();
        when(identityMapper.insert(any(ImUserIdentity.class))).thenReturn(1);

        Long result = userMapping.resolveUserId("wecom", "user004", "赵六");
        assertNotNull(result);
        verify(userMapper).insert(any(SysUser.class));
    }

    @Test
    @DisplayName("deptResolver 抛异常 → 降级创建无部门用户")
    void deptExceptionDoesNotBlock() {
        when(identityMapper.selectOne(any())).thenReturn(null);
        when(wecomAdapter.fetchUserMobile(any())).thenReturn(null);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(deptResolver.resolveKbDeptId("wecom", "user005")).thenThrow(new RuntimeException("超时"));
        mockUserInsert();
        when(identityMapper.insert(any(ImUserIdentity.class))).thenReturn(1);

        Long result = userMapping.resolveUserId("wecom", "user005", "钱七");
        assertNotNull(result);
    }

    // ==================== 非企微平台 ====================

    @Test
    @DisplayName("非企微平台 — 无 wecomAdapter → 跳过手机号，直接创建")
    void nonWecomPlatform() {
        setField(userMapping, "wecomAdapter", null);

        when(identityMapper.selectOne(any())).thenReturn(null);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        mockUserInsert();
        when(identityMapper.insert(any(ImUserIdentity.class))).thenReturn(1);

        Long result = userMapping.resolveUserId("dingtalk", "dt001", "钉钉用户");
        assertNotNull(result);

        ArgumentCaptor<ImUserIdentity> captor = ArgumentCaptor.forClass(ImUserIdentity.class);
        verify(identityMapper).insert(captor.capture());
        assertNull(captor.getValue().getMobile());
    }

    // ==================== 工具方法 ====================

    /** 配置 userMapper.insert() 模拟 MyBatis-Plus 自动生成主键 ID */
    private void mockUserInsert() {
        doAnswer(inv -> {
            SysUser u = inv.getArgument(0);
            if (u.getId() == null) {
                u.setId(nextUserId++);
            }
            return 1;
        }).when(userMapper).insert(any(SysUser.class));
    }

    private SysUser sysUser(Long id, String name, Long deptId) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername("im_wecom_test");
        user.setName(name);
        user.setPhone("");
        user.setRole(RoleEnum.USER.getCode());
        user.setStatus("ACTIVE");
        user.setDepartmentId(deptId);
        return user;
    }

    private ImUserIdentity identity(Long kbUserId, String platform, String platformUid,
                                     String platformName, String mobile) {
        ImUserIdentity identity = new ImUserIdentity();
        identity.setId(kbUserId);
        identity.setKbUserId(kbUserId);
        identity.setPlatform(platform);
        identity.setPlatformUid(platformUid);
        identity.setPlatformName(platformName);
        identity.setMobile(mobile);
        return identity;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("反射设置字段失败: " + fieldName, e);
        }
    }
}
