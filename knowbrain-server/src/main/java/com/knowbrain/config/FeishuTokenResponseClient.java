package com.knowbrain.config;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;

import java.util.Collections;
import java.util.Map;

/**
 * 飞书 OIDC Token 客户端。
 *
 * <p>飞书新版 OIDC 需要两步认证：
 * <ol>
 *   <li>用 app_id + app_secret 换取 app_access_token</li>
 *   <li>用 app_access_token 作 Bearer 换取 user_access_token</li>
 * </ol>
 *
 * <p>文档：https://open.feishu.cn/document/sso/web-application-sso/login-overview
 */
@Slf4j
public class FeishuTokenResponseClient
        implements OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> {

    private static final String APP_TOKEN_URL = "https://open.feishu.cn/open-apis/auth/v3/app_access_token/internal";
    private static final String USER_TOKEN_URL = "https://open.feishu.cn/open-apis/authen/v1/oidc/access_token";

    @Override
    public OAuth2AccessTokenResponse getTokenResponse(
            OAuth2AuthorizationCodeGrantRequest grantRequest) {

        var registration = grantRequest.getClientRegistration();
        String code = grantRequest.getAuthorizationExchange().getAuthorizationResponse().getCode();
        String clientId = registration.getClientId();
        String clientSecret = registration.getClientSecret();

        // Step 1: 获取 app_access_token
        log.debug("[OIDC] 获取 app_access_token: clientId={}", clientId);
        JSONObject tokenJson;
        try (var resp = HttpRequest.post(APP_TOKEN_URL)
                .header("Content-Type", "application/json; charset=utf-8")
                .body(JSONUtil.toJsonStr(Map.of("app_id", clientId, "app_secret", clientSecret)))
                .timeout(10000)
                .execute()) {
            tokenJson = JSONUtil.parseObj(resp.body());
        }
        if (tokenJson.getInt("code", -1) != 0) {
            log.error("[OIDC] 获取 app_access_token 失败: code={}", tokenJson.getInt("code"));
            throw new RuntimeException("获取飞书 app_access_token 失败");
        }
        String appAccessToken = tokenJson.getStr("app_access_token");
        if (appAccessToken == null || appAccessToken.isEmpty()) {
            throw new RuntimeException("获取飞书 app_access_token 失败");
        }

        // Step 2: 用 app_access_token 换取 user_access_token
        log.debug("[OIDC] 换取 user_access_token");
        JSONObject userJson;
        try (var resp = HttpRequest.post(USER_TOKEN_URL)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Authorization", "Bearer " + appAccessToken)
                .body(JSONUtil.toJsonStr(Map.of(
                        "grant_type", "authorization_code",
                        "code", code)))
                .timeout(10000)
                .execute()) {
            userJson = JSONUtil.parseObj(resp.body());
        }
        if (userJson.getInt("code", -1) != 0) {
            log.error("[OIDC] 换取 user_access_token 失败: code={}", userJson.getInt("code"));
            throw new RuntimeException("换取飞书 user_access_token 失败");
        }

        JSONObject data = userJson.getJSONObject("data");
        if (data == null) {
            throw new RuntimeException("换取飞书 user_access_token 失败");
        }

        String accessToken = data.getStr("access_token");
        long expiresIn = data.getLong("expires_in", 3600L);
        String refreshToken = data.getStr("refresh_token");

        if (accessToken == null || accessToken.isEmpty()) {
            throw new RuntimeException("换取飞书 user_access_token 失败");
        }

        log.debug("[OIDC] 飞书 Token 获取成功: expires_in={}", expiresIn);

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
