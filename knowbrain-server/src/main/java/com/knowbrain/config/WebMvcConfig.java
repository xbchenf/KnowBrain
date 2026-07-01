package com.knowbrain.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 * 阶段 2 将在此注册 AuthInterceptor
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    // 预留：阶段 2 注册 AuthInterceptor 到 /api/v1/**
    // 预留：阶段 2 添加白名单路径排除
}
