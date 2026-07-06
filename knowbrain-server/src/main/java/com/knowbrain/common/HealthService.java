package com.knowbrain.common;

import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 组件健康检查服务 — 逐项探测所有外部依赖
 */
@Slf4j
@Service
public class HealthService {

    private final JdbcTemplate jdbcTemplate;
    private final MinioClient minioClient;
    private final MilvusClientV2 milvusClient;
    private final RedisConnectionFactory redisConnectionFactory;
    private final EmbeddingModel embeddingModel;

    public HealthService(JdbcTemplate jdbcTemplate,
                         MinioClient minioClient,
                         MilvusClientV2 milvusClient,
                         RedisConnectionFactory redisConnectionFactory,
                         EmbeddingModel embeddingModel) {
        this.jdbcTemplate = jdbcTemplate;
        this.minioClient = minioClient;
        this.milvusClient = milvusClient;
        this.redisConnectionFactory = redisConnectionFactory;
        this.embeddingModel = embeddingModel;
    }

    @Value("${minio.bucket}")
    private String minioBucket;

    @Value("${spring.ai.vectorstore.milvus.collection-name}")
    private String milvusCollection;

    /**
     * 返回健康检查结果
     */
    public HealthResult check() {
        Map<String, ComponentStatus> components = new LinkedHashMap<>();
        boolean allUp = true;

        allUp &= checkComponent(components, "mysql", this::checkMysql);
        allUp &= checkComponent(components, "redis", this::checkRedis);
        allUp &= checkComponent(components, "minio", this::checkMinio);
        allUp &= checkComponent(components, "milvus", this::checkMilvus);
        allUp &= checkComponent(components, "llm", this::checkLlm);

        long uptime = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;

        return new HealthResult(
                allUp ? "UP" : "DOWN",
                components,
                uptime
        );
    }

    // ==================== 逐项检查 ====================

    private ComponentStatus checkMysql() {
        long start = System.currentTimeMillis();
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return ComponentStatus.up(latency(start));
        } catch (Exception e) {
            log.error("MySQL 健康检查失败: {}", e.getMessage());
            return ComponentStatus.down("connect failed");
        }
    }

    private ComponentStatus checkRedis() {
        long start = System.currentTimeMillis();
        try {
            if (redisConnectionFactory == null) {
                return ComponentStatus.unknown("not configured");
            }
            String pong = redisConnectionFactory.getConnection().ping();
            if (!"PONG".equals(pong)) {
                log.error("Redis PING 返回异常: {}", pong);
                return ComponentStatus.down("unexpected ping response");
            }
            return ComponentStatus.up(latency(start));
        } catch (Exception e) {
            log.error("Redis 健康检查失败: {}", e.getMessage());
            return ComponentStatus.down("connect failed");
        }
    }

    private ComponentStatus checkMinio() {
        long start = System.currentTimeMillis();
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(minioBucket).build());
            if (!exists) {
                log.error("MinIO Bucket 不存在: {}", minioBucket);
                return ComponentStatus.down("bucket not found");
            }
            return ComponentStatus.up(latency(start));
        } catch (Exception e) {
            log.error("MinIO 健康检查失败: {}", e.getMessage());
            return ComponentStatus.down("connect failed");
        }
    }

    private ComponentStatus checkMilvus() {
        long start = System.currentTimeMillis();
        try {
            milvusClient.hasCollection(HasCollectionReq.builder()
                    .collectionName(milvusCollection).build());
            return ComponentStatus.up(latency(start));
        } catch (Exception e) {
            log.error("Milvus 健康检查失败: {}", e.getMessage());
            return ComponentStatus.down("connect failed");
        }
    }

    private ComponentStatus checkLlm() {
        long start = System.currentTimeMillis();
        try {
            float[] vec = embeddingModel.embed("health_check");
            if (vec == null || vec.length == 0) {
                log.error("LLM Embedding 返回空向量");
                return ComponentStatus.down("empty embedding");
            }
            return ComponentStatus.up(latency(start));
        } catch (Exception e) {
            log.error("LLM 健康检查失败: {}", e.getMessage());
            return ComponentStatus.down("connect failed");
        }
    }

    // ==================== 辅助方法 ====================

    private boolean checkComponent(Map<String, ComponentStatus> map,
                                   String name,
                                   java.util.function.Supplier<ComponentStatus> checker) {
        ComponentStatus status = checker.get();
        map.put(name, status);
        return "UP".equals(status.status());
    }

    private int latency(long start) {
        return (int) (System.currentTimeMillis() - start);
    }

    // ==================== 数据类 ====================

    public record HealthResult(String status, Map<String, ComponentStatus> components, long uptimeSeconds) {}

    public record ComponentStatus(String status, int latencyMs, String error) {
        public static ComponentStatus up(int latencyMs) {
            return new ComponentStatus("UP", latencyMs, null);
        }

        public static ComponentStatus down(String error) {
            return new ComponentStatus("DOWN", 0, error);
        }

        public static ComponentStatus unknown(String reason) {
            return new ComponentStatus("UNKNOWN", 0, reason);
        }
    }
}
