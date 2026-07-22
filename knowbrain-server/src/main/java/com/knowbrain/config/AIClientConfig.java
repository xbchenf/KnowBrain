package com.knowbrain.config;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.time.Duration;

/**
 * Spring AI / OpenAI 客户端超时配置
 *
 * 默认 JdkClientHttpRequestFactory read timeout 为 10s，
 * Agent 的 Function Calling 多步调用远超市，需增加到 120s。
 */
@Configuration
public class AIClientConfig {

    @Bean
    public RestClientCustomizer timeoutRestClientCustomizer() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(120));
        return restClientBuilder -> restClientBuilder.requestFactory(factory);
    }
}
