package com.knowbrain.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 敏感词过滤器 — 基于 DFA（确定性有限自动机）算法
 * 从 EICS V2.0 直接迁移
 */
@Slf4j
@Component
public class SensitiveWordFilter {

    /** DFA 根节点 */
    private final Map<Character, Object> dfaRoot = new HashMap<>();

    private static final String REPLACE_CHAR = "*";

    @Value("${security.sensitive-word.enabled:true}")
    private boolean enabled;

    @Value("${security.sensitive-word.custom-words:}")
    private String customWords;

    /** 内置敏感词库（示例，生产环境应替换为完整词库） */
    private static final Set<String> BUILTIN_WORDS = Set.of(
            "敏感词1", "敏感词2"
    );

    @PostConstruct
    public void init() {
        Set<String> allWords = new HashSet<>(BUILTIN_WORDS);
        if (customWords != null && !customWords.isBlank()) {
            Arrays.stream(customWords.split(","))
                    .map(String::trim)
                    .filter(w -> !w.isEmpty())
                    .forEach(allWords::add);
        }

        // 构建 DFA 字典树
        for (String word : allWords) {
            Map<Character, Object> current = dfaRoot;
            for (int i = 0; i < word.length(); i++) {
                char c = word.charAt(i);
                @SuppressWarnings("unchecked")
                Map<Character, Object> child = (Map<Character, Object>) current.get(c);
                if (child == null) {
                    child = new HashMap<>();
                    current.put(c, child);
                }
                current = child;
            }
            current.put('$', Boolean.TRUE); // 结束标记
        }
        log.info("敏感词过滤器初始化完成: {} 个词条 (内置 {} + 自定义 {})",
                allWords.size(), BUILTIN_WORDS.size(), allWords.size() - BUILTIN_WORDS.size());
    }

    /** 是否包含敏感词 */
    public boolean contains(String text) {
        return !findAll(text).isEmpty();
    }

    /** 查找所有敏感词 */
    public List<String> findAll(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) return result;

        for (int i = 0; i < text.length(); i++) {
            Map<Character, Object> current = dfaRoot;
            for (int j = i; j < text.length(); j++) {
                char c = text.charAt(j);
                @SuppressWarnings("unchecked")
                Map<Character, Object> child = (Map<Character, Object>) current.get(c);
                if (child == null) break;
                current = child;
                if (current.containsKey('$')) {
                    result.add(text.substring(i, j + 1));
                    break;
                }
            }
        }
        return result;
    }

    /** 将敏感词替换为 * */
    public String replace(String text) {
        if (text == null || text.isEmpty()) return text;
        String result = text;
        for (String word : findAll(text)) {
            result = result.replace(word, REPLACE_CHAR.repeat(word.length()));
        }
        return result;
    }

    /**
     * 完整脱敏：敏感词替换 + PII 掩码
     */
    public String sanitize(String text) {
        if (!enabled || text == null || text.isEmpty()) return text;
        return maskEmail(maskPhone(maskIdCard(replace(text))));
    }

    /** 手机号掩码：138****5678 */
    public static String maskPhone(String text) {
        if (text == null) return null;
        return text.replaceAll("(1[3-9]\\d)\\d{4}(\\d{4})", "$1****$2");
    }

    /** 邮箱掩码：a***@***.com */
    public static String maskEmail(String text) {
        if (text == null) return null;
        return text.replaceAll("(?<=.{1}).(?=.*@)", "*");
    }

    /** 身份证号掩码 */
    public static String maskIdCard(String text) {
        if (text == null) return null;
        return text.replaceAll("(\\d{6})\\d{8}(\\d{4})", "$1****$2");
    }
}
