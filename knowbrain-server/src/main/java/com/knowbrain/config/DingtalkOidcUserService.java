package com.knowbrain.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * 钉钉 OIDC 用户信息服务（桥接层）。
 *
 * <p>由于钉钉 registration 配置了 scope=openid，Spring Security 会走 OIDC 流程，
 * 调用 {@code OAuth2UserService<OidcUserRequest, OidcUser>} 加载用户。
 * 本类将 OIDC 请求桥接到现有的 {@link DingtalkOAuth2UserService}，并将结果包装为 OidcUser。
 */
@Slf4j
public class DingtalkOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final DingtalkOAuth2UserService delegate = new DingtalkOAuth2UserService();

    @Override
    public OidcUser loadUser(OidcUserRequest r) {
        log.debug("[OIDC] 钉钉 OIDC 用户加载");

        // 桥接 OidcUserRequest → OAuth2UserRequest
        OAuth2AccessToken accessToken = r.getAccessToken();
        ClientRegistration cr = r.getClientRegistration();
        OAuth2UserRequest oauth2Request = new OAuth2UserRequest(
                cr, accessToken, r.getAdditionalParameters());
        OAuth2User oauth2User = delegate.loadUser(oauth2Request);

        // 包装为 OidcUser（Spring Security OIDC 流程要求返回此类型）
        OidcIdToken idToken = r.getIdToken();
        OidcUserInfo userInfo = new OidcUserInfo(oauth2User.getAttributes());

        return new DefaultOidcUser(oauth2User.getAuthorities(), idToken, userInfo);
    }
}
