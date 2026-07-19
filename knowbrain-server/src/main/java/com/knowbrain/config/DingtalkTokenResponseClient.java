package com.knowbrain.config;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;

import java.util.HashMap;
import java.util.Map;

/**
 * 钉钉 OAuth2 Token 客户端。
 *
 * <p>钉钉 V2 Token 端点：
 * <pre>{@code POST https://api.dingtalk.com/v1.0/oauth2/userAccessToken}</pre>
 *
 * <p>特点：
 * <ul>
 *   <li>请求体为驼峰 JSON：{@code clientId, clientSecret, grantType=authorization_code, code}</li>
 *   <li>响应为扁平 JSON：{@code {"accessToken":"...", "expireIn":7200, ...}}</li>
 * </ul>
 */
@Slf4j
public class DingtalkTokenResponseClient
        implements OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> {

    private static final String TOKEN_URL = "https://api.dingtalk.com/v1.0/oauth2/userAccessToken";

    @Override
    public OAuth2AccessTokenResponse getTokenResponse(
            OAuth2AuthorizationCodeGrantRequest grantRequest) {

        var registration = grantRequest.getClientRegistration();
        String code = grantRequest.getAuthorizationExchange().getAuthorizationResponse().getCode();

        Map<String, Object> reqBody = new HashMap<>();
        reqBody.put("clientId", registration.getClientId());
        reqBody.put("clientSecret", registration.getClientSecret());
        reqBody.put("code", code);
        reqBody.put("grantType", "authorization_code");

        log.debug("[OIDC] 请求钉钉 Token: clientId={}", registration.getClientId());

        JSONObject json;
        try (var resp = HttpRequest.post(TOKEN_URL)
                .header("Content-Type", "application/json; charset=utf-8")
                .body(JSONUtil.toJsonStr(reqBody))
                .timeout(10000)
                .execute()) {
            json = JSONUtil.parseObj(resp.body());
        }

        String accessToken = json.getStr("accessToken");
        if (accessToken == null || accessToken.isEmpty()) {
            log.error("[OIDC] 钉钉 Token 获取失败, errCode={}, errMsg={}",
                    json.getStr("code"), json.getStr("msg"));
            throw new RuntimeException("获取钉钉 access_token 失败");
        }

        long expiresIn = json.getLong("expireIn", 7200L);
        String refreshToken = json.getStr("refreshToken");

        log.debug("[OIDC] 钉钉 Token 获取成功: expires_in={}", expiresIn);

        var builder = OAuth2AccessTokenResponse.withToken(accessToken)
                .tokenType(org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType.BEARER)
                .expiresIn(expiresIn);
        if (refreshToken != null && !refreshToken.isEmpty()) {
            builder.refreshToken(refreshToken);
        }
        return builder.build();
    }
}
