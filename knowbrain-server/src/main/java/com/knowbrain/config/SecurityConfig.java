package com.knowbrain.config;

import com.knowbrain.auth.OAuth2SuccessHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 最小化配置。
 *
 * <p>Security 在此项目中仅负责 OAuth2/OIDC 登录流程（/oauth2/authorization/** → IdP → 回调），
 * JWT 认证仍由 {@link com.knowbrain.auth.AuthInterceptor} 通过 WebMvcConfigurer 处理。
 * 因此所有请求 {@code permitAll()}，Security 不拦截任何业务请求。
 *
 * <p>当未配置任何 OAuth2 Client 时，oauth2Login 不会注册，
 * 此时登录页仅显示用户名密码登录，不影响系统运行。
 */
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
                // API 服务无需 CSRF（无 Session/Cookie 认证）
                .csrf(AbstractHttpConfigurer::disable)

                // 无需表单登录和 HTTP Basic
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                // 无状态 Session（JWT 认证由 AuthInterceptor 处理）
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 全部放行 — JWT 认证交给 AuthInterceptor
                .authorizeHttpRequests(a -> a.anyRequest().permitAll());

        // OAuth2 登录：仅在配置了 OAuth2 Client 时才启用
        // （未配置时 ClientRegistrationRepository bean 不存在，oauth2Login 无法注册）
        if (clientRegistrationRepo.getIfAvailable() != null) {
            http.oauth2Login(o -> o.successHandler(oAuth2SuccessHandler));
        }

        return http.build();
    }
}
