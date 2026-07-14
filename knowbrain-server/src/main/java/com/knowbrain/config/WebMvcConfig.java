package com.knowbrain.config;

import com.knowbrain.auth.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置 — 注册认证拦截器
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(
                        // 健康检查
                        "/api/v1/health",
                        // 认证接口（登录/注册）
                        "/api/v1/auth/**",
                        // IM Bot 回调（企微/钉钉/飞书自有签名验证机制，不走 JWT）
                        "/api/v1/im/**"
                );
    }
}
