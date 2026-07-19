package com.knowbrain.config;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 钉钉 OAuth2 UserInfo 服务。
 *
 * <p>钉钉 UserInfo 端点使用自定义 Header {@code x-acs-dingtalk-access-token}
 * 而非标准 {@code Authorization: Bearer}。
 *
 * <p>响应为扁平 JSON：{@code {"nick":"张三", "openId":"xxx", "unionId":"xxx", ...}}
 */
@Slf4j
public class DingtalkOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private static final String USER_INFO_URL = "https://api.dingtalk.com/v1.0/contact/users/me";

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        String accessToken = userRequest.getAccessToken().getTokenValue();

        JSONObject json;
        try (var resp = HttpRequest.get(USER_INFO_URL)
                .header("x-acs-dingtalk-access-token", accessToken)
                .timeout(10000)
                .execute()) {
            json = JSONUtil.parseObj(resp.body());
        }

        // 钉钉 user info 是扁平 JSON，直接转为 attributes
        Map<String, Object> attributes = new HashMap<>();
        for (String key : json.keySet()) {
            attributes.put(key, json.get(key));
        }

        // 钉钉昵称字段是 "nick"，统一映射为 "name" 以便 OAuth2SuccessHandler 提取
        if (attributes.containsKey("nick") && !attributes.containsKey("name")) {
            attributes.put("name", attributes.get("nick"));
        }

        if (attributes.isEmpty()) {
            throw new RuntimeException("获取钉钉用户信息失败");
        }

        // openId 是用户在应用内的唯一标识
        String nameAttr = attributes.containsKey("openId") ? "openId" : "nick";

        log.debug("[OIDC] 钉钉用户加载成功");

        return new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
                attributes,
                nameAttr);
    }
}
