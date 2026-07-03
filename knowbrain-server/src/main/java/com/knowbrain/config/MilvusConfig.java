package com.knowbrain.config;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Milvus 客户端配置 — 注册 MilvusClientV2 Bean 供 BM25 混合检索使用
 */
@Configuration
public class MilvusConfig {

    @Value("${spring.ai.vectorstore.milvus.client.host}")
    private String host;

    @Value("${spring.ai.vectorstore.milvus.client.port}")
    private int port;

    @Value("${spring.ai.vectorstore.milvus.client.username:}")
    private String username;

    @Value("${spring.ai.vectorstore.milvus.client.password:}")
    private String password;

    @Bean
    public MilvusClientV2 milvusClientV2() {
        var builder = ConnectConfig.builder()
                .uri("http://" + host + ":" + port);

        if (username != null && !username.isEmpty()) {
            builder.username(username)
                   .password(password != null ? password : "");
        }

        return new MilvusClientV2(builder.build());
    }
}
