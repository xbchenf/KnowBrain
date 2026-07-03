package com.knowbrain.retrieval.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG 答案缓存 — Redis 热点问答缓存
 *
 * 设计：
 * - 缓存 Key: kb:rag:cache:{question_hash}:{space_hash}
 * - 缓存 TTL: 默认 1 小时（可配置）
 * - 仅缓存非降级、置信度 medium+ 的回答
 * - 问题归一化后 MD5 哈希，避免 Key 过长
 *
 * 缓存命中时跳过检索+LLM，直接返回缓存答案（零延迟零成本）。
 */
@Slf4j
@Service
public class RAGCacheService {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Value("${rag.cache.ttl-minutes:60}")
    private long ttlMinutes;

    @Value("${rag.cache.enabled:true}")
    private boolean enabled;

    private static final String KEY_PREFIX = "kb:rag:cache";

    public RAGCacheService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询缓存
     *
     * @return 缓存的 CacheEntry（含 ChatResponse 序列化数据），未命中返回 null
     */
    public ChatResponse get(String question, List<Long> spaceIds) {
        if (!enabled) return null;

        String key = buildKey(question, spaceIds);
        try {
            String json = redis.opsForValue().get(key);
            if (json == null) return null;

            CacheEntry entry = objectMapper.readValue(json, CacheEntry.class);
            log.info("RAG 缓存命中: key={}", key);
            return entry.toChatResponse();
        } catch (Exception e) {
            log.warn("RAG 缓存读取失败: key={}", key, e);
            return null;
        }
    }

    /**
     * 写入缓存
     */
    public void put(String question, List<Long> spaceIds, ChatResponse response) {
        if (!enabled) return;

        // 不缓存降级回答
        if (response.isFallback()) return;

        // 不缓存低置信度回答
        if ("low".equals(response.getConfidence())) return;

        String key = buildKey(question, spaceIds);
        try {
            CacheEntry entry = CacheEntry.from(response);
            String json = objectMapper.writeValueAsString(entry);
            redis.opsForValue().set(key, json, Duration.ofMinutes(ttlMinutes));
            log.info("RAG 缓存写入: key={}, ttl={}min", key, ttlMinutes);
        } catch (Exception e) {
            log.warn("RAG 缓存写入失败: key={}", key, e);
        }
    }

    /**
     * 清除所有 RAG 缓存（文档更新/删除时调用）
     */
    public void invalidateAll() {
        if (!enabled) return;
        var keys = redis.keys(KEY_PREFIX + ":*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
            log.info("RAG 缓存已全部清除: {} 条", keys.size());
        }
    }

    // ==================== 内部方法 ====================

    private String buildKey(String question, List<Long> spaceIds) {
        String qHash = md5(normalize(question));
        String sHash = spaceIds != null && !spaceIds.isEmpty()
                ? md5(spaceIds.stream().sorted().map(String::valueOf)
                        .collect(Collectors.joining(",")))
                : "public";
        return KEY_PREFIX + ":" + qHash + ":" + sHash;
    }

    /** 问题归一化：去首尾空白 + 全小写 + 合并连续空格 */
    static String normalize(String question) {
        if (question == null) return "";
        return question.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not available", e);
        }
    }

    /**
     * Redis 缓存条目 — ChatResponse 的可序列化形态
     */
    @SuppressWarnings("unused") // Jackson 需要无参构造 + getter/setter
    public static class CacheEntry {
        private String answer;
        private List<SourceInfo> sources;
        private boolean fallback;
        private String confidence;

        public CacheEntry() {}

        CacheEntry(String answer, List<SourceInfo> sources, boolean fallback, String confidence) {
            this.answer = answer;
            this.sources = sources;
            this.fallback = fallback;
            this.confidence = confidence;
        }

        static CacheEntry from(ChatResponse r) {
            List<SourceInfo> infos = r.getSources() != null
                    ? r.getSources().stream()
                        .map(s -> new SourceInfo(s.getTitle(), s.getDocumentId(),
                                s.getChunkIndex(), s.getText()))
                        .toList()
                    : List.of();
            return new CacheEntry(r.getAnswer(), infos, r.isFallback(), r.getConfidence());
        }

        ChatResponse toChatResponse() {
            List<ChatResponse.SourceInfo> infos = sources != null
                    ? sources.stream()
                        .map(s -> new ChatResponse.SourceInfo(
                                s.title, s.documentId, s.chunkIndex, s.text))
                        .toList()
                    : List.of();
            return new ChatResponse(answer, infos, fallback, confidence);
        }

        public String getAnswer() { return answer; }
        public void setAnswer(String answer) { this.answer = answer; }
        public List<SourceInfo> getSources() { return sources; }
        public void setSources(List<SourceInfo> sources) { this.sources = sources; }
        public boolean isFallback() { return fallback; }
        public void setFallback(boolean fallback) { this.fallback = fallback; }
        public String getConfidence() { return confidence; }
        public void setConfidence(String confidence) { this.confidence = confidence; }
    }

    /** 轻量 SourceInfo（避免循环依赖 ChatResponse.SourceInfo 的 JSON 序列化问题） */
    public static class SourceInfo {
        private String title;
        private Long documentId;
        private Integer chunkIndex;
        private String text;

        public SourceInfo() {}

        SourceInfo(String title, Long documentId, Integer chunkIndex, String text) {
            this.title = title;
            this.documentId = documentId;
            this.chunkIndex = chunkIndex;
            this.text = text;
        }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public Long getDocumentId() { return documentId; }
        public void setDocumentId(Long documentId) { this.documentId = documentId; }
        public Integer getChunkIndex() { return chunkIndex; }
        public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
    }
}
