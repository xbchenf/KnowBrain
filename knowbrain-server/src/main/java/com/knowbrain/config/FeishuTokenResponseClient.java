package com.knowbrain.config;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 飞书 OAuth2 V3 Token 客户端。
 *
 * <p>飞书 V3 OAuth2 Token 端点：
 * <pre>{@code POST https://accounts.feishu.cn/oauth/v3/token}</pre>
 * 请求体 JSON：{@code grant_type, client_id, client_secret, code, redirect_uri}
 *
 * <p>响应为扁平 JSON（不再嵌套 data）：
 * <pre>{@code {"code":0, "access_token":"...", "expires_in":7200, ...}}</pre>
 *
 * <p>文档：https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/authentication-management/access-token/get-user-access-token-v3
 */
@Slf4j
public class FeishuTokenResponseClient
        implements OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> {

    private static final String TOKEN_URL = "https://accounts.feishu.cn/oauth/v3/token";

    @Override
    public OAuth2AccessTokenResponse getTokenResponse(
            OAuth2AuthorizationCodeGrantRequest grantRequest) {

        var registration = grantRequest.getClientRegistration();
        String code = grantRequest.getAuthorizationExchange().getAuthorizationResponse().getCode();
        String redirectUri = grantRequest.getAuthorizationExchange().getAuthorizationRequest().getRedirectUri();

        Map<String, Object> reqBody = new HashMap<>();
        reqBody.put("grant_type", "authorization_code");
        reqBody.put("client_id", registration.getClientId());
        reqBody.put("client_secret", registration.getClientSecret());
        reqBody.put("code", code);
        reqBody.put("redirect_uri", redirectUri);

        log.debug("[OIDC] 请求飞书 V3 Token: clientId={}", registration.getClientId());

        JSONObject json;
        try (var resp = HttpRequest.post(TOKEN_URL)
                .header("Content-Type", "application/json; charset=utf-8")
                .body(JSONUtil.toJsonStr(reqBody))
                .timeout(10000)
                .execute()) {
            json = JSONUtil.parseObj(resp.body());
        }

        if (json.getInt("code", -1) != 0) {
            log.error("[OIDC] 飞书 V3 Token 失败: code={}", json.getInt("code"));
            throw new RuntimeException("获取飞书 user_access_token 失败");
        }

        String accessToken = json.getStr("access_token");
        long expiresIn = json.getLong("expires_in", 7200L);
        String refreshToken = json.getStr("refresh_token");

        if (accessToken == null || accessToken.isEmpty()) {
            throw new RuntimeException("获取飞书 user_access_token 失败");
        }

        log.debug("[OIDC] 飞书 V3 Token 获取成功: expires_in={}", expiresIn);

        var builder = OAuth2AccessTokenResponse.withToken(accessToken)
                .tokenType(org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType.BEARER)
                .expiresIn(expiresIn)
                .scopes(Collections.emptySet());
        if (refreshToken != null && !refreshToken.isEmpty()) {
            builder.refreshToken(refreshToken);
        }
        return builder.build();
    }
}
