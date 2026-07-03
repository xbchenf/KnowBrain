package com.knowbrain.document.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 语义文本切片器 — 递归按分隔符优先级切分
 * 分隔符优先级：\n\n → \n → 。→ ！→ ？→ ；→ ，
 * 目标 350 字/片，重叠 80 字
 */
@Slf4j
@Component
public class SmartChunker {

    // 分隔符优先级（按语义边界从高到低）
    private static final String[] SEPARATORS = {"\n\n", "\n", "。", "！", "？", "；", "，"};

    private static final int CHUNK_SIZE = 350;   // 目标切片大小（字符数）
    private static final int OVERLAP = 80;        // 重叠字符数

    public List<String> chunk(String text) {
        if (text == null || text.isEmpty()) return List.of();
        List<String> chunks = new ArrayList<>();
        splitRecursive(text, chunks, 0);
        log.info("文本切片完成: 原文{}字 → {}片", text.length(), chunks.size());
        return chunks;
    }

    private void splitRecursive(String text, List<String> chunks, int depth) {
        if (text.length() <= CHUNK_SIZE) {
            if (!text.isBlank()) chunks.add(text.trim());
            return;
        }

        // 选当前深度的分隔符
        String sep = depth < SEPARATORS.length ? SEPARATORS[depth] : null;
        if (sep == null) {
            // 到最底层还没切完，强制按长度硬切
            hardSplit(text, chunks);
            return;
        }

        String[] parts = text.split(sep, -1);
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            // 非最后一部分时，加上分隔符
            String segment = i < parts.length - 1 ? part + sep : part;

            if (current.length() + segment.length() <= CHUNK_SIZE) {
                current.append(segment);
            } else {
                // 当前 buffer 满了，先输出
                if (current.length() > 0) {
                    chunks.add(current.toString().trim());
                }

                // 重叠处理：上一片的末尾若干字作为新片的开头
                if (current.length() > OVERLAP && depth > 0) {
                    String overlapText = takeLastChars(current.toString(), OVERLAP);
                    current = new StringBuilder(overlapText + segment);
                } else {
                    current = new StringBuilder(segment);
                }

                // 如果单个片段超过切片大小，递归用下一级分隔符切
                if (current.length() > CHUNK_SIZE && depth < SEPARATORS.length - 1) {
                    splitRecursive(current.toString(), chunks, depth + 1);
                    current = new StringBuilder();
                }
            }
        }

        // 收尾
        if (current.length() > 0) {
            if (current.length() > CHUNK_SIZE && depth < SEPARATORS.length - 1) {
                splitRecursive(current.toString(), chunks, depth + 1);
            } else {
                chunks.add(current.toString().trim());
            }
        }
    }

    /** 取字符串末尾的 n 个字符 */
    private String takeLastChars(String s, int n) {
        if (s.length() <= n) return s + " ";
        return s.substring(s.length() - n) + " ";
    }

    /** 强制按长度硬切（最底层兜底） */
    private void hardSplit(String text, List<String> chunks) {
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + CHUNK_SIZE, text.length());
            chunks.add(text.substring(start, end).trim());
            start = end - OVERLAP;
        }
    }
}
