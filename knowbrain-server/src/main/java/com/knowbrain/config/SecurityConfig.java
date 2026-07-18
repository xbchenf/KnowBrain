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

        if (clientRegistrationRepo.getIfAvailable() != null) {
            var tokenClient = new FeishuTokenResponseClient();
            var userService = new FeishuOAuth2UserService();

            http.oauth2Login(o -> o
                    .successHandler(oAuth2SuccessHandler)
                    .tokenEndpoint(t -> t.accessTokenResponseClient(tokenClient))
                    .userInfoEndpoint(u -> u.userService(userService)));
        }

        return http.build();
    }
}
