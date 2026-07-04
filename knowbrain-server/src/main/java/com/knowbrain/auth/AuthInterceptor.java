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
        Map<String, Object> claims = jwtUtil.verifyToken(token);
        if (claims == null) {
            writeJson(response, 401, "Token无效或已过期");
            return false;
        }

        // 注入用户信息到 request attributes
        request.setAttribute("userId", claims.get("userId"));
        request.setAttribute("username", claims.get("username"));
        request.setAttribute("role", claims.get("role"));

        // /api/v1/admin/** 仅 ADMIN 角色可访问
        String path = request.getRequestURI();
        if (path.startsWith("/api/v1/admin/")) {
            String role = (String) claims.get("role");
            if (!"ADMIN".equals(role)) {
                writeJson(response, 403, "需要系统管理员权限");
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
