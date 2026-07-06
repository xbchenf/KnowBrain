package com.knowbrain;

import com.knowbrain.document.service.QwenVisionService;
import io.milvus.v2.client.MilvusClientV2;
import io.minio.MinioClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.mockito.Mockito.mock;

/**
 * 测试用 Mock 配置 — 替换所有外部依赖（Milvus / MinIO / LLM / Redis / Vision）
 */
@TestConfiguration
public class TestMockConfig {

    @Bean
    @Primary
    public MilvusClientV2 mockMilvusClient() {
        return mock(MilvusClientV2.class);
    }

    @Bean
    @Primary
    public MinioClient mockMinioClient() {
        return mock(MinioClient.class);
    }

    @Bean
    @Primary
    public EmbeddingModel mockEmbeddingModel() {
        return mock(EmbeddingModel.class);
    }

    @Bean
    @Primary
    public ChatClient.Builder mockChatClientBuilder() {
        return mock(ChatClient.Builder.class);
    }

    @Bean
    @Primary
    public StringRedisTemplate mockStringRedisTemplate() {
        return mock(StringRedisTemplate.class);
    }

    @Bean
    @Primary
    public RedisConnectionFactory mockRedisConnectionFactory() {
        return mock(RedisConnectionFactory.class);
    }

    @Bean
    @Primary
    public QwenVisionService mockVisionService() {
        return mock(QwenVisionService.class);
    }
}
