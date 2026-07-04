package com.knowbrain.auth;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowbrain.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

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
            return Result.fail(401, "用户名或密码错误");
        }

        rateLimiter.clearFailure(ip);
        String token = jwtUtil.createToken(user.getId(), user.getUsername(), user.getRole());
        log.info("登录成功: username={}, role={}", username, user.getRole());

        return Result.ok(Map.of(
                "token", token,
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
        String phone = request.get("phone") != null ? request.get("phone").toString() : "";
        Long departmentId = null;
        if (request.containsKey("departmentId") && request.get("departmentId") != null) {
            try {
                departmentId = Long.valueOf(request.get("departmentId").toString());
            } catch (NumberFormatException ignored) {
                // 非法值忽略，保持 null
            }
        }

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return Result.badRequest("用户名和密码不能为空");
        }
        if (username.length() < 3 || username.length() > 50) {
            return Result.badRequest("用户名需 3-50 个字符");
        }
        if (password.length() < 6) {
            return Result.badRequest("密码至少 6 位");
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
        user.setRole("USER");
        user.setDepartmentId(departmentId);
        user.setStatus("ACTIVE");
        userMapper.insert(user);

        String token = jwtUtil.createToken(user.getId(), user.getUsername(), "USER");
        log.info("新用户注册: username={}, id={}", username, user.getId());

        return Result.ok(Map.of(
                "token", token,
                "userId", user.getId(),
                "name", user.getName(),
                "role", "USER"
        ));
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
