package com.knowbrain.auth;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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

    @Value("${jwt.refresh-expire-hours:168}")
    private int refreshExpireHours;

    /**
     * 生成 JWT Token（含唯一 jti，用于黑名单机制）
     */
    public String createToken(Long userId, String username, String role) {
        long now = System.currentTimeMillis() / 1000;
        String jti = UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> payload = new HashMap<>();
        payload.put("jti", jti);
        payload.put("userId", userId);
        payload.put("username", username);
        payload.put("role", role);
        payload.put("iat", now);
        payload.put("exp", now + expireHours * 3600L);
        payload.put("type", "access");
        return JWTUtil.createToken(payload, secret.getBytes());
    }

    /**
     * 从 Token 中提取 jti（不验证有效期，用于提取黑名单标识）
     */
    public String getJti(String token) {
        try {
            JWT jwt = JWTUtil.parseToken(token);
            Object jti = jwt.getPayload("jti");
            return jti != null ? jti.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 Token 中提取过期时间（秒级时间戳），解析失败返回 0
     */
    public long getExp(String token) {
        try {
            JWT jwt = JWTUtil.parseToken(token);
            Object exp = jwt.getPayload("exp");
            return exp instanceof Number ? ((Number) exp).longValue() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 生成 Refresh Token（长期有效，用于换取新的 Access Token）
     */
    public String createRefreshToken(Long userId, String username, String role) {
        long now = System.currentTimeMillis() / 1000;
        String jti = UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> payload = new HashMap<>();
        payload.put("jti", jti);
        payload.put("userId", userId);
        payload.put("username", username);
        payload.put("role", role);
        payload.put("iat", now);
        payload.put("exp", now + refreshExpireHours * 3600L);
        payload.put("type", "refresh");
        return JWTUtil.createToken(payload, secret.getBytes());
    }

    /**
     * 仅验证签名并提取所有 claims（不检查过期），用于 refresh 端点
     * 过期检查由调用方自行决定
     */
    public Map<String, Object> verifySignature(String token) {
        try {
            if (!JWTUtil.verify(token, secret.getBytes())) {
                return null;
            }
            JWT jwt = JWTUtil.parseToken(token);
            Map<String, Object> claims = new HashMap<>();
            Object uidVal = jwt.getPayload("userId");
            claims.put("userId", uidVal instanceof Number ? ((Number) uidVal).longValue() : uidVal);
            claims.put("username", jwt.getPayload("username"));
            claims.put("role", jwt.getPayload("role"));
            Object iatVal = jwt.getPayload("iat");
            claims.put("iat", iatVal instanceof Number ? ((Number) iatVal).longValue() : 0L);
            Object expVal = jwt.getPayload("exp");
            claims.put("exp", expVal instanceof Number ? ((Number) expVal).longValue() : 0L);
            claims.put("jti", jwt.getPayload("jti"));
            claims.put("type", jwt.getPayload("type"));  // "refresh" vs null（access token 不带 type）
            return claims;
        } catch (Exception e) {
            return null;
        }
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
            // iat 用于用户级 Token 失效检测（禁用/改密后生效）
            Object iatVal = jwt.getPayload("iat");
            claims.put("iat", iatVal instanceof Number ? ((Number) iatVal).longValue() : 0L);
            return claims;

        } catch (Exception e) {
            log.warn("JWT 校验异常: {}", e.getMessage());
            return null;
        }
    }
}
