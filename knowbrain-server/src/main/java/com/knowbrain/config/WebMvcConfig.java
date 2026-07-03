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
                        // 认证接口
                        "/api/v1/auth/**",
                        // RAG 问答（无需登录即可提问）
                        "/api/v1/rag/chat",
                        "/api/v1/rag/chat/stream",
                        "/api/v1/rag/search",
                        // 知识分类（公开读取）
                        "/api/v1/categories/**",
                        // 术语词典（公开读取）
                        "/api/v1/glossary/**",
                        // FAQ 预设库（公开读取）
                        "/api/v1/faq/**",
                        // 答案反馈（无需登录）
                        "/api/v1/feedback"
                );
    }
}
