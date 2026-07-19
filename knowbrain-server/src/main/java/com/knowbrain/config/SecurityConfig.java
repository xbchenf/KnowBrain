package com.knowbrain.config;

import com.knowbrain.auth.OAuth2SuccessHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrationRepo;

    public SecurityConfig(OAuth2SuccessHandler oAuth2SuccessHandler,
                          ObjectProvider<ClientRegistrationRepository> clientRegistrationRepo) {
        this.oAuth2SuccessHandler = oAuth2SuccessHandler;
        this.clientRegistrationRepo = clientRegistrationRepo;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().permitAll());

        var repo = clientRegistrationRepo.getIfAvailable();
        if (repo != null) {
            // 钉钉 OAuth2 要求 scope=openid + prompt=consent
            // 但不能在 registration.scopes 中设置 openid（否则 Spring Security 会走 OIDC 流程）
            // 通过 additionalParameters 注入，会直接出现在授权 URL 的 QueryString 中
            var authResolver = new DefaultOAuth2AuthorizationRequestResolver(
                    repo, "/oauth2/authorization");
            authResolver.setAuthorizationRequestCustomizer(c ->
                    c.additionalParameters(p -> {
                        p.put("prompt", "consent");
                        p.put("scope", "openid");
                    }));

            http.oauth2Login(o -> o
                    .successHandler(oAuth2SuccessHandler)
                    .authorizationEndpoint(a -> a
                            .authorizationRequestResolver(authResolver))
                    .tokenEndpoint(t -> t
                            .accessTokenResponseClient(new DelegatingTokenClient()))
                    .userInfoEndpoint(u -> u
                            .userService(new DelegatingUserService())
                            .oidcUserService(new DelegatingOidcUserService())));
        }

        return http.build();
    }

    // ===== Token 响应：按 registrationId 路由到不同实现 =====

    private static class DelegatingTokenClient
            implements OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> {
        private final FeishuTokenResponseClient feishu = new FeishuTokenResponseClient();
        private final DingtalkTokenResponseClient dingtalk = new DingtalkTokenResponseClient();

        @Override
        public OAuth2AccessTokenResponse getTokenResponse(OAuth2AuthorizationCodeGrantRequest r) {
            String id = r.getClientRegistration().getRegistrationId();
            if ("feishu".equals(id)) return feishu.getTokenResponse(r);
            if ("dingtalk".equals(id)) return dingtalk.getTokenResponse(r);
            throw new IllegalStateException("Unknown OAuth2 provider: " + id);
        }
    }

    // ===== UserInfo：按 registrationId 路由到不同实现（OAuth2 路径）=====

    private static class DelegatingUserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {
        private final FeishuOAuth2UserService feishu = new FeishuOAuth2UserService();
        private final DingtalkOAuth2UserService dingtalk = new DingtalkOAuth2UserService();

        @Override
        public OAuth2User loadUser(OAuth2UserRequest r) {
            String id = r.getClientRegistration().getRegistrationId();
            if ("feishu".equals(id)) return feishu.loadUser(r);
            if ("dingtalk".equals(id)) return dingtalk.loadUser(r);
            throw new IllegalStateException("Unknown OAuth2 provider: " + id);
        }
    }

    // ===== UserInfo：按 registrationId 路由到不同实现（OIDC 路径）=====

    private static class DelegatingOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {
        private final DingtalkOidcUserService dingtalkOidc = new DingtalkOidcUserService();

        @Override
        public OidcUser loadUser(OidcUserRequest r) {
            String id = r.getClientRegistration().getRegistrationId();
            if ("dingtalk".equals(id)) return dingtalkOidc.loadUser(r);
            throw new IllegalStateException("Unknown OIDC provider: " + id);
        }
    }
}
