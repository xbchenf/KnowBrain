package com.knowbrain.auth;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowbrain.audit.Auditable;
import com.knowbrain.common.RAGMetrics;
import com.knowbrain.common.Result;
import com.knowbrain.im.entity.ImUserIdentity;
import com.knowbrain.im.mapper.ImUserIdentityMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 认证控制器 — 登录 / 注册
 */
@Tag(name = "认证", description = "用户登录 / 注册 / Token 管理")
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SysUserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final LoginRateLimiter rateLimiter;
    private final TokenBlacklistService blacklistService;
    private final RAGMetrics metrics;
    private final StringRedisTemplate stringRedisTemplate;
    private final OAuth2UserService oAuth2UserService;
    private final UserService userService;
    private final ImUserIdentityMapper identityMapper;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private OAuth2ClientProperties oauth2ClientProperties;

    @Operation(summary = "登录", description = "用户名密码登录，返回 JWT Token")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> request,
                                              HttpServletRequest servletRequest) {
        String username = request.get("username");
        String password = request.get("password");
        String ip = getClientIp(servletRequest);

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return Result.badRequest("用户名和密码不能为空");
        }

        // 限速检查
        if (!rateLimiter.allow(ip)) {
            long remaining = rateLimiter.remainingLockSeconds(ip);
            return Result.fail(429, "登录尝试过于频繁，请 " + (remaining / 60 + 1) + " 分钟后再试");
        }

        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, username)
                        .eq(SysUser::getStatus, "ACTIVE")
        );

        if (user == null || !BCrypt.checkpw(password, user.getPasswordHash())) {
            rateLimiter.recordFailure(ip);
            metrics.recordLoginFailure();
            return Result.fail(401, "用户名或密码错误");
        }

        rateLimiter.clearFailure(ip);
        String token = jwtUtil.createToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtUtil.createRefreshToken(user.getId(), user.getUsername(), user.getRole());
        log.info("登录成功: username={}, role={}", username, user.getRole());

        return Result.ok(Map.of(
                "token", token,
                "refreshToken", refreshToken,
                "userId", user.getId(),
                "name", user.getName(),
                "role", user.getRole()
        ));
    }

    @Operation(summary = "注册", description = "用户自助注册，默认 USER 角色")
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody Map<String, Object> request) {
        String username = request.get("username") != null ? request.get("username").toString() : null;
        String password = request.get("password") != null ? request.get("password").toString() : null;
        String name = request.get("name") != null ? request.get("name").toString() : username;
        String phone = request.get("phone") != null ? request.get("phone").toString() : null;
        Long departmentId = null;
        if (request.containsKey("departmentId") && request.get("departmentId") != null) {
            try {
                departmentId = Long.valueOf(request.get("departmentId").toString());
            } catch (NumberFormatException ignored) {}
        }
        // 手机号必填校验
        if (phone == null || phone.isBlank()) {
            return Result.badRequest("手机号不能为空");
        }
        if (!phone.matches("^1[3-9]\\d{9}$")) {
            return Result.badRequest("手机号格式不正确");
        }
        // 手机号唯一性
        if (userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getPhone, phone)) > 0) {
            return Result.fail(400, "该手机号已被注册");
        }

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return Result.badRequest("用户名和密码不能为空");
        }
        if (username.length() < 3 || username.length() > 50) {
            return Result.badRequest("用户名需 3-50 个字符");
        }
        try {
            PasswordValidator.validate(password);
        } catch (IllegalArgumentException e) {
            return Result.badRequest(e.getMessage());
        }
        if (userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username)) > 0) {
            return Result.fail(400, "用户名已存在");
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(BCrypt.hashpw(password, BCrypt.gensalt()));
        user.setName(name);
        user.setPhone(phone);
        user.setRole(RoleEnum.USER.getCode());
        user.setDepartmentId(departmentId);
        user.setStatus("ACTIVE");
        userMapper.insert(user);

        String token = jwtUtil.createToken(user.getId(), user.getUsername(), RoleEnum.USER.getCode());
        String refreshToken = jwtUtil.createRefreshToken(user.getId(), user.getUsername(), RoleEnum.USER.getCode());
        log.info("新用户注册: username={}, id={}", username, user.getId());

        return Result.ok(Map.of(
                "token", token,
                "refreshToken", refreshToken,
                "userId", user.getId(),
                "name", user.getName(),
                "role", RoleEnum.USER.getCode()
        ));
    }

    @Operation(summary = "退出登录", description = "将当前 Access Token 和 Refresh Token 加入黑名单")
    @Auditable(operation = "OTHER", resourceType = "AUTH", description = "用户退出登录")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest servletRequest, @RequestBody(required = false) Map<String, Object> body) {
        String authHeader = servletRequest.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            blacklistToken(token);
        }
        // 如果请求体中有 refreshToken，一并加入黑名单
        if (body != null && body.containsKey("refreshToken")) {
            blacklistToken(body.get("refreshToken").toString());
        }
        return Result.ok("已退出登录", null);
    }

    @Operation(summary = "刷新 Token", description = "使用 Refresh Token 换取新的 Access Token")
    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh(@RequestBody Map<String, Object> body) {
        String refreshToken = body.get("refreshToken") != null ? body.get("refreshToken").toString() : null;
        if (refreshToken == null || refreshToken.isBlank()) {
            return Result.badRequest("refreshToken 不能为空");
        }

        // 1. 黑名单检查
        String jti = jwtUtil.getJti(refreshToken);
        if (jti != null && blacklistService.isBlacklisted(jti)) {
            return Result.fail(401, "Refresh Token 已失效（已退出登录）");
        }

        // 2. 验证签名 + token type（防止 access token 冒充 refresh token）
        Map<String, Object> claims = jwtUtil.verifySignature(refreshToken);
        if (claims == null) {
            return Result.fail(401, "Refresh Token 无效");
        }

        // 2b. Token 类型校验：只接受 refresh 类型的 token
        if (!"refresh".equals(claims.get("type"))) {
            return Result.fail(401, "仅支持 Refresh Token 刷新，请使用登录接口返回的 refreshToken");
        }

        // 3. 检查过期
        long exp = claims.get("exp") instanceof Number ? ((Number) claims.get("exp")).longValue() : 0;
        if (exp > 0 && exp < System.currentTimeMillis() / 1000) {
            return Result.fail(401, "Refresh Token 已过期，请重新登录");
        }

        // 4. 用户状态验证
        Long userId = claims.get("userId") instanceof Number ? ((Number) claims.get("userId")).longValue() : null;
        if (userId == null) {
            return Result.fail(401, "Token 格式错误");
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            return Result.fail(401, "账户已被禁用");
        }

        // 5. 用户级 Token 失效检查
        String invalidBeforeKey = "kb:token:invalid_before:" + userId;
        String invalidBeforeStr = stringRedisTemplate.opsForValue().get(invalidBeforeKey);
        if (invalidBeforeStr != null) {
            long invalidBefore = Long.parseLong(invalidBeforeStr);
            long iat = claims.get("iat") instanceof Number ? ((Number) claims.get("iat")).longValue() : 0;
            if (iat < invalidBefore) {
                return Result.fail(401, "Token 已失效，请重新登录（密码已变更）");
            }
        }

        // 6. 颁发新 Token + 旧 Refresh Token 轮换（Rotation：用过的 refresh token 立即作废）
        String newToken = jwtUtil.createToken(userId, user.getUsername(), user.getRole());
        String newRefreshToken = jwtUtil.createRefreshToken(userId, user.getUsername(), user.getRole());

        // Rotation: 将旧 refresh token 加入黑名单，防止重放攻击
        long oldExp = claims.get("exp") instanceof Number ? ((Number) claims.get("exp")).longValue() : 0;
        if (jti != null && oldExp > 0) {
            blacklistService.add(jti, oldExp);
        }
        log.info("Token 刷新成功: userId={}", userId);

        return Result.ok(Map.of(
                "token", newToken,
                "refreshToken", newRefreshToken,
                "userId", userId,
                "name", user.getName(),
                "role", user.getRole()
        ));
    }

    // ========== OAuth2 绑定（无手机号场景） ==========

    @Operation(summary = "OAuth2 绑定", description = "OAuth2 登录后未返回手机号时，补充手机号完成绑定")
    @PostMapping("/bind-oauth2")
    public Result<Map<String, Object>> bindOAuth2(@RequestBody Map<String, String> body) {
        String code = body.get("code");
        String phone = body.get("phone");
        String name = body.get("name");

        if (code == null || code.isBlank()) return Result.badRequest("缺少绑定码");
        if (!code.matches("^[a-zA-Z0-9]+$")) return Result.badRequest("无效的绑定码格式");
        if (phone == null || phone.isBlank()) return Result.badRequest("手机号不能为空");
        if (!phone.matches("^1[3-9]\\d{9}$")) return Result.badRequest("手机号格式不正确");
        if (name == null || name.isBlank()) return Result.badRequest("姓名不能为空");

        String redisKey = "kb:oidc:bind:" + code;
        String redisValue = stringRedisTemplate.opsForValue().get(redisKey);
        if (redisValue == null) return Result.fail(401, "绑定码无效或已过期，请重新登录");
        stringRedisTemplate.delete(redisKey);

        String[] parts = redisValue.split("\n", 3);
        if (parts.length < 2) return Result.fail(500, "绑定码数据异常");
        String platform = parts[0];
        String platformUid = parts[1];
        String oauth2Name = parts.length > 2 ? parts[2] : name;
        if (name == null || name.isBlank()) name = oauth2Name;

        SysUser user;
        try {
            user = oAuth2UserService.completeBinding(platform, platformUid, phone, name);
        } catch (IllegalStateException e) {
            return Result.fail(403, e.getMessage());
        }

        String token = jwtUtil.createToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtUtil.createRefreshToken(user.getId(), user.getUsername(), user.getRole());
        log.info("OAuth2 绑定完成: platform={}, platformUid={}, kbUserId={}", platform, platformUid, user.getId());

        return Result.ok(Map.of(
                "token", token,
                "refreshToken", refreshToken,
                "userId", user.getId(),
                "name", user.getName(),
                "role", user.getRole()
        ));
    }

    // ========== 自服务（需认证但不需要 ADMIN 角色） ==========

    @Operation(summary = "个人资料", description = "获取当前登录用户的个人资料和关联身份")
    @GetMapping("/profile")
    public Result<Map<String, Object>> getProfile(HttpServletRequest servletRequest) {
        Long userId = (Long) servletRequest.getAttribute("userId");
        return Result.ok(userService.getProfile(userId));
    }

    @Operation(summary = "修改个人资料", description = "当前用户修改自己的姓名和手机号")
    @PutMapping("/profile")
    public Result<Map<String, Object>> updateProfile(@RequestBody Map<String, String> body,
                                                      HttpServletRequest servletRequest) {
        Long userId = (Long) servletRequest.getAttribute("userId");
        String name = body.get("name");
        String phone = body.get("phone");
        if (name == null && phone == null) return Result.badRequest("至少需要提供 name 或 phone");
        SysUser user = userService.updateProfile(userId, name, phone);
        return Result.ok("修改成功", userService.getProfile(user.getId()));
    }

    @Operation(summary = "关联身份列表", description = "获取当前用户关联的 OAuth2 身份列表")
    @GetMapping("/identities")
    public Result<List<ImUserIdentity>> listMyIdentities(HttpServletRequest servletRequest) {
        Long userId = (Long) servletRequest.getAttribute("userId");
        List<ImUserIdentity> list = identityMapper.selectList(
                new LambdaQueryWrapper<ImUserIdentity>()
                        .eq(ImUserIdentity::getKbUserId, userId));
        return Result.ok(list);
    }

    @Operation(summary = "解除关联", description = "解绑指定的 OAuth2 身份")
    @DeleteMapping("/identities/{id}")
    public Result<Void> unbindMyIdentity(@PathVariable Long id, HttpServletRequest servletRequest) {
        Long userId = (Long) servletRequest.getAttribute("userId");
        ImUserIdentity identity = identityMapper.selectById(id);
        if (identity == null) return Result.fail(404, "关联记录不存在");
        if (!identity.getKbUserId().equals(userId)) return Result.fail(403, "只能解绑自己的账号关联");
        identityMapper.deleteById(id);
        log.info("解除关联: userId={}, platform={}, platformUid={}", userId, identity.getPlatform(), identity.getPlatformUid());
        return Result.ok("解绑成功", null);
    }

    @Operation(summary = "准备关联账号", description = "为关联企业账号做准备，设置 Cookie 后前端跳转到 OAuth2 授权页")
    @PostMapping("/prepare-link")
    public Result<Void> prepareLink(@RequestBody Map<String, String> body,
                                     HttpServletRequest servletRequest,
                                     HttpServletResponse response) {
        Long userId = (Long) servletRequest.getAttribute("userId");
        String platform = body.get("platform");
        if (platform == null || platform.isBlank()) return Result.badRequest("平台不能为空");
        if (!java.util.Set.of("feishu", "dingtalk").contains(platform))
            return Result.badRequest("不支持的平台");

        String linkToken = java.util.UUID.randomUUID().toString().replace("-", "");
        stringRedisTemplate.opsForValue().set("kb:link:" + linkToken,
                userId + "\n" + platform, java.time.Duration.ofMinutes(5));

        Cookie cookie = new Cookie("kb_link_token", linkToken);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(300);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);

        log.info("准备关联: userId={}, platform={}", userId, platform);
        return Result.ok("success", null);
    }

    // ========== OIDC ==========

    @Operation(summary = "OIDC 提供商列表", description = "返回已启用的 OAuth2/OIDC 登录入口，供前端动态渲染登录按钮")
    @GetMapping("/oidc-providers")
    public Result<List<Map<String, String>>> getOidcProviders() {
        List<Map<String, String>> providers = new ArrayList<>();
        if (oauth2ClientProperties == null) {
            return Result.ok(providers);
        }
        oauth2ClientProperties.getRegistration().forEach((id, reg) -> {
            // 仅返回已配置 client-id 的 provider
            if (reg.getClientId() != null && !reg.getClientId().isBlank()) {
                Map<String, String> item = new LinkedHashMap<>();
                item.put("id", id);
                item.put("name", reg.getClientName() != null ? reg.getClientName() : id);
                item.put("url", "/oauth2/authorization/" + id);
                providers.add(item);
            }
        });
        return Result.ok(providers);
    }

    @Operation(summary = "OIDC 令牌交换", description = "用一次性 code 换取 JWT token。OIDC 登录成功后使用，防止 token 出现在 URL 中")
    @PostMapping("/oidc-exchange")
    public Result<Map<String, Object>> oidcExchange(@RequestBody Map<String, String> body) {
        String code = body.get("code");
        if (code == null || code.isBlank()) {
            return Result.badRequest("缺少交换码");
        }
        // 只允许字母数字
        if (!code.matches("^[a-zA-Z0-9]+$")) {
            return Result.badRequest("无效的交换码格式");
        }
        String redisKey = "kb:oidc:code:" + code;
        String redisValue = stringRedisTemplate.opsForValue().get(redisKey);
        if (redisValue == null) {
            return Result.fail(401, "交换码无效或已过期，请重新登录");
        }
        // 一次性使用：立即删除
        stringRedisTemplate.delete(redisKey);

        String[] parts = redisValue.split("\n", 4);
        if (parts.length < 4) {
            return Result.fail(500, "交换码数据异常");
        }

        return Result.ok(Map.of(
                "token", parts[0],
                "refreshToken", parts[1],
                "name", parts[2],
                "role", parts[3]
        ));
    }

    /** 将单个 token 加入黑名单 */
    private void blacklistToken(String token) {
        String jti = jwtUtil.getJti(token);
        long exp = jwtUtil.getExp(token);
        if (jti != null && exp > 0) {
            blacklistService.add(jti, exp);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
