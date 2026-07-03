package com.knowbrain.retrieval.engine;

import com.google.gson.JsonObject;

import java.util.*;

/**
 * 轻量混合文本分词器（中英文）
 *
 * - 中文: unigrams + bigrams（字符级）
 * - 英文: 空格分词 + 小写
 * - 输出: term_hash → 词频（用于构建 Milvus SparseFloatVector）
 *
 * Milvus 2.4 standalone 不支持 runAnalyzer API，因此使用 Java 端分词。
 */
public final class TextTokenizer {

    private TextTokenizer() {}

    /**
     * 分词并构建 term-hash → 词频 的稀疏向量
     */
    public static JsonObject tokenize(String text) {
        Map<Long, Integer> freq = new LinkedHashMap<>();

        // 1. 英文词：连续字母序列
        for (String word : extractEnglishWords(text)) {
            if (word.length() >= 2) {
                long hash = hash(word);
                freq.merge(hash, 1, Integer::sum);
            }
        }

        // 2. 中文 unigrams（单字）
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isChinese(c)) {
                long hash = hash(String.valueOf(c));
                freq.merge(hash, 1, Integer::sum);
            }
        }

        // 3. 中文 bigrams（连续两字）
        for (int i = 0; i < text.length() - 1; i++) {
            char c1 = text.charAt(i);
            char c2 = text.charAt(i + 1);
            if (isChinese(c1) && isChinese(c2)) {
                long hash = hash(String.valueOf(c1) + c2);
                freq.merge(hash, 1, Integer::sum);
            }
        }

        // 构建 JsonObject
        JsonObject result = new JsonObject();
        if (freq.isEmpty()) {
            // 兜底：占位项
            result.addProperty("0", 1.0f);
        } else {
            for (var entry : freq.entrySet()) {
                result.addProperty(String.valueOf(entry.getKey()), (float) entry.getValue());
            }
        }
        return result;
    }

    private static List<String> extractEnglishWords(String text) {
        List<String> words = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isAsciiAlpha(c)) {
                buf.append(Character.toLowerCase(c));
            } else {
                if (buf.length() > 0) {
                    words.add(buf.toString());
                    buf.setLength(0);
                }
            }
        }
        if (buf.length() > 0) words.add(buf.toString());
        return words;
    }

    private static boolean isChinese(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF)
                || (c >= 0x3400 && c <= 0x4DBF)
                || (c >= 0xF900 && c <= 0xFAFF);
    }

    private static boolean isAsciiAlpha(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    /**
     * 字符串哈希到 unsigned 32-bit Long（Milvus 稀疏向量索引范围 [0, 2^32-1]）
     */
    private static long hash(String s) {
        // 使用 j.l.String 的 hashCode，转为 unsigned 32-bit
        return Integer.toUnsignedLong(s.hashCode());
    }
}
