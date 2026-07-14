package com.knowbrain.auth;

import com.knowbrain.TestMockConfig;
import com.knowbrain.common.RAGMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 认证流程集成测试 — 登录 / 注册 / 退出 / Token 刷新
 *
 * 覆盖场景：
 * - 登录成功 → 200 + JWT Token
 * - 密码错误 → 401
 * - 限速触发 → 429
 * - 注册成功 → 200 + Token
 * - 重复用户名 → 400
 * - 弱密码 → 400
 * - 退出 + Token 黑名单
 * - Refresh Token 轮换
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("mock")
@Import(TestMockConfig.class)
@DisplayName("AuthController — 认证接口集成测试")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SysUserMapper userMapper;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private LoginRateLimiter rateLimiter;

    @MockBean
    private TokenBlacklistService blacklistService;

    @MockBean
    private RAGMetrics metrics;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setUp() {
        when(rateLimiter.allow(anyString())).thenReturn(true);
    }

    // ==================== 登录 ====================

    @Nested
    @DisplayName("POST /api/v1/auth/login")
    class Login {

        @Test
        @DisplayName("正确凭证 → 200 + Token")
        void loginSuccess() throws Exception {
            SysUser user = new SysUser();
            user.setId(1L);
            user.setUsername("admin");
            user.setPasswordHash(cn.hutool.crypto.digest.BCrypt.hashpw("Admin@123"));
            user.setName("管理员");
            user.setRole(RoleEnum.ADMIN.getCode());
            user.setStatus("ACTIVE");
            when(userMapper.selectOne(any())).thenReturn(user);
            when(jwtUtil.createToken(eq(1L), eq("admin"), eq("ADMIN")))
                    .thenReturn("fake-access-token");
            when(jwtUtil.createRefreshToken(eq(1L), eq("admin"), eq("ADMIN")))
                    .thenReturn("fake-refresh-token");

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.token").value("fake-access-token"))
                    .andExpect(jsonPath("$.data.name").value("管理员"))
                    .andExpect(jsonPath("$.data.role").value("ADMIN"));
        }

        @Test
        @DisplayName("密码错误 → 401")
        void loginWrongPassword() throws Exception {
            SysUser user = new SysUser();
            user.setId(1L);
            user.setUsername("admin");
            user.setPasswordHash(cn.hutool.crypto.digest.BCrypt.hashpw("Admin@123"));
            user.setStatus("ACTIVE");
            when(userMapper.selectOne(any())).thenReturn(user);

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"admin\",\"password\":\"WrongPassword1\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(401))
                    .andExpect(jsonPath("$.message").value("用户名或密码错误"));
        }

        @Test
        @DisplayName("限速触发 → 429")
        void loginRateLimited() throws Exception {
            when(rateLimiter.allow(anyString())).thenReturn(false);
            when(rateLimiter.remainingLockSeconds(anyString())).thenReturn(600L);

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(429));
        }
    }

    // ==================== 注册 ====================

    @Nested
    @DisplayName("POST /api/v1/auth/register")
    class Register {

        @Test
        @DisplayName("合法注册 → 200 + Token")
        void registerSuccess() throws Exception {
            when(userMapper.selectCount(any())).thenReturn(0L);
            // 模拟 MyBatis-Plus insert 后自动回填 ID
            org.mockito.Mockito.doAnswer(inv -> {
                SysUser u = inv.getArgument(0, SysUser.class);
                u.setId(100L);
                return 1;
            }).when(userMapper).insert(any(SysUser.class));
            when(jwtUtil.createToken(eq(100L), eq("newuser"), eq("USER")))
                    .thenReturn("new-user-token");
            when(jwtUtil.createRefreshToken(anyLong(), eq("newuser"), eq("USER")))
                    .thenReturn("new-refresh-token");

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"newuser\",\"password\":\"Strong@123\",\"name\":\"新用户\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.token").value("new-user-token"))
                    .andExpect(jsonPath("$.data.name").value("新用户"));
        }

        @Test
        @DisplayName("用户名已存在 → 400")
        void registerDuplicateUsername() throws Exception {
            when(userMapper.selectCount(any())).thenReturn(1L);

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"admin\",\"password\":\"Strong@123\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("用户名已存在"));
        }

        @Test
        @DisplayName("弱密码 → 400")
        void registerWeakPassword() throws Exception {
            when(userMapper.selectCount(any())).thenReturn(0L);

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"newuser\",\"password\":\"123456\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }
    }

    // ==================== 退出登录 ====================

    @Nested
    @DisplayName("POST /api/v1/auth/logout")
    class Logout {

        @Test
        @DisplayName("退出 → 200 + Token 入黑名单")
        void logoutWithToken() throws Exception {
            when(jwtUtil.getJti("test-token")).thenReturn("jti-abc123");
            when(jwtUtil.getExp("test-token")).thenReturn(
                    System.currentTimeMillis() / 1000 + 3600);

            mockMvc.perform(post("/api/v1/auth/logout")
                            .header("Authorization", "Bearer test-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(blacklistService).add(eq("jti-abc123"), anyLong());
        }

        @Test
        @DisplayName("无 Token 退出 → 200（幂等）")
        void logoutWithoutToken() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    // ==================== Token 刷新 ====================

    @Nested
    @DisplayName("POST /api/v1/auth/refresh")
    class Refresh {

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("合法 Refresh Token → 200 + 新 Token 对")
        void refreshSuccess() throws Exception {
            when(jwtUtil.getJti("valid-refresh-token")).thenReturn("jti-refresh");
            when(blacklistService.isBlacklisted("jti-refresh")).thenReturn(false);

            java.util.Map<String, Object> claims = new java.util.HashMap<>();
            claims.put("userId", 1L);
            claims.put("username", "admin");
            claims.put("role", "ADMIN");
            claims.put("exp", System.currentTimeMillis() / 1000 + 7200L);
            claims.put("iat", System.currentTimeMillis() / 1000 - 3600L);
            claims.put("type", "refresh");
            when(jwtUtil.verifySignature("valid-refresh-token")).thenReturn(claims);

            SysUser user = new SysUser();
            user.setId(1L);
            user.setUsername("admin");
            user.setName("管理员");
            user.setRole("ADMIN");
            user.setStatus("ACTIVE");
            when(userMapper.selectById(1L)).thenReturn(user);

            when(jwtUtil.createToken(1L, "admin", "ADMIN")).thenReturn("new-access");
            when(jwtUtil.createRefreshToken(1L, "admin", "ADMIN")).thenReturn("new-refresh");

            // Refresh 端点会检查 kb:token:invalid_before:{userId} 是否需作废 Token
            @SuppressWarnings("unchecked")
            ValueOperations<String, String> mockOps = org.mockito.Mockito.mock(ValueOperations.class);
            when(mockOps.get(eq("kb:token:invalid_before:1"))).thenReturn(null);
            when(stringRedisTemplate.opsForValue()).thenReturn(mockOps);

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"valid-refresh-token\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.token").value("new-access"))
                    .andExpect(jsonPath("$.data.refreshToken").value("new-refresh"));
        }

        @Test
        @DisplayName("黑名单中的 Refresh Token → 401")
        void refreshBlacklisted() throws Exception {
            when(jwtUtil.getJti("blacklisted-token")).thenReturn("jti-black");
            when(blacklistService.isBlacklisted("jti-black")).thenReturn(true);

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"blacklisted-token\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(401));
        }
    }
}
