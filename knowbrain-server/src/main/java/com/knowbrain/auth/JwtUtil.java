package com.knowbrain.auth;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * JWT 令牌工具类 — 基于 Hutool JWTUtil (HS256)
 * 用于用户登录认证，生成/校验 Bearer Token
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expire-hours}")
    private int expireHours;

    /**
     * 生成 JWT Token
     */
    public String createToken(Long userId, String username, String role) {
        long now = System.currentTimeMillis() / 1000;
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("username", username);
        payload.put("role", role);
        payload.put("iat", now);
        payload.put("exp", now + expireHours * 3600L);
        return JWTUtil.createToken(payload, secret.getBytes());
    }

    /**
     * 校验 Token 并返回 claims，校验失败返回 null
     */
    public Map<String, Object> verifyToken(String token) {
        try {
            if (!JWTUtil.verify(token, secret.getBytes())) {
                log.debug("JWT 签名验证失败");
                return null;
            }

            JWT jwt = JWTUtil.parseToken(token);

            // 检查过期
            Object expObj = jwt.getPayload("exp");
            if (expObj == null) {
                log.debug("JWT 缺少 exp 字段");
                return null;
            }
            long exp = ((Number) expObj).longValue();
            if (exp < System.currentTimeMillis() / 1000) {
                log.debug("JWT 已过期: exp={}, now={}", exp, System.currentTimeMillis() / 1000);
                return null;
            }

            // 提取业务 claims
            Map<String, Object> claims = new HashMap<>();
            Object uidVal = jwt.getPayload("userId");
            claims.put("userId", uidVal instanceof Number ? ((Number) uidVal).longValue() : uidVal);
            claims.put("username", jwt.getPayload("username"));
            claims.put("role", jwt.getPayload("role"));
            return claims;

        } catch (Exception e) {
            log.warn("JWT 校验异常: {}", e.getMessage());
            return null;
        }
    }
}
