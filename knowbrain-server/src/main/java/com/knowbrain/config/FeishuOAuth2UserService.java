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
 * 飞书 OIDC UserInfo 服务。
 *
 * <p>飞书 user_info 返回嵌套 JSON（{@code {"code":0, "data":{...}}}），
 * 需要先解包 data 再交给 Spring Security 处理。
 */
@Slf4j
public class FeishuOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private static final String USER_INFO_URL = "https://open.feishu.cn/open-apis/authen/v1/user_info";

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        String accessToken = userRequest.getAccessToken().getTokenValue();

        JSONObject json;
        try (var resp = HttpRequest.get(USER_INFO_URL)
                .header("Authorization", "Bearer " + accessToken)
                .timeout(10000)
                .execute()) {
            json = JSONUtil.parseObj(resp.body());
        }

        if (json.getInt("code", -1) != 0) {
            log.error("[OIDC] user_info 失败: code={}", json.getInt("code"));
            throw new RuntimeException("获取飞书用户信息失败");
        }

        JSONObject data = json.getJSONObject("data");
        if (data == null) {
            throw new RuntimeException("获取飞书用户信息失败");
        }

        Map<String, Object> attributes = new HashMap<>();
        for (String key : data.keySet()) {
            attributes.put(key, data.get(key));
        }

        // open_id 作为 OIDC 用户唯一标识
        String nameAttr = attributes.containsKey("open_id") ? "open_id" : "name";

        log.debug("[OIDC] 飞书用户加载成功: open_id={}", attributes.get("open_id"));

        return new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
                attributes,
                nameAttr);
    }
}
