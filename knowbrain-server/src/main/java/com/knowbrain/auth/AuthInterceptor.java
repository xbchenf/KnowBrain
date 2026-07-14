package com.knowbrain.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

/**
 * 认证拦截器 — 校验 Bearer Token
 * 仅拦截 WebMvcConfig 中指定的路径，白名单路径由 WebMvcConfig 排除
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final TokenBlacklistService blacklistService;
    private final UserService userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        // 放行 OPTIONS 预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeJson(response, 401, "未登录或Token已过期");
            return false;
        }

        String token = authHeader.substring(7);

        // 0. 黑名单检查（退出登录后 Token 立即失效）
        String jti = jwtUtil.getJti(token);
        if (blacklistService.isBlacklisted(jti)) {
            writeJson(response, 401, "Token已失效，请重新登录");
            return false;
        }

        Map<String, Object> claims = jwtUtil.verifyToken(token);
        if (claims == null) {
            writeJson(response, 401, "Token无效或已过期");
            return false;
        }

        // 注入用户信息到 request attributes
        request.setAttribute("userId", claims.get("userId"));
        request.setAttribute("username", claims.get("username"));
        request.setAttribute("role", claims.get("role"));

        // 0.5 用户级 Token 失效检查（禁用 / 改密后所有旧 Token 立即失效）
        Object uidObj = claims.get("userId");
        if (uidObj != null) {
            long userId = uidObj instanceof Number ? ((Number) uidObj).longValue() : 0;
            long invalidBefore = userService.getTokenInvalidBefore(userId);
            if (invalidBefore > 0) {
                Object iatObj = claims.get("iat");
                long iat = iatObj instanceof Number ? ((Number) iatObj).longValue() : 0;
                if (iat > 0 && iat < invalidBefore) {
                    writeJson(response, 401, "账号状态已变更，请重新登录");
                    return false;
                }
            }
        }

        // /api/v1/admin/** 权限控制：
        //   ADMIN   — 全部方法可访问
        //   MANAGER — 仅 GET（读）可访问（创建/修改/删除需 ADMIN）
        String path = request.getRequestURI();
        if (path.startsWith("/api/v1/admin/")) {
            String role = (String) claims.get("role");
            if (RoleEnum.ADMIN.matches(role)) {
                // ADMIN 全部放行
            } else if (RoleEnum.MANAGER.matches(role)) {
                if (!"GET".equalsIgnoreCase(request.getMethod())) {
                    writeJson(response, 403, "知识管理员仅有只读权限，如需写操作请联系系统管理员");
                    return false;
                }
            } else {
                writeJson(response, 403, "需要管理员权限");
                return false;
            }
        }

        return true;
    }

    private void writeJson(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + status + ",\"message\":\"" + message + "\"}");
    }
}
