package com.knowbrain.scenario;

import com.knowbrain.scenario.entity.ScenarioFaq;
import com.knowbrain.scenario.mapper.ScenarioFaqMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * FAQ 预设问答服务 — 从 DB 读取，首次启动从 JSON 种子导入
 */
@Slf4j
@Service
public class FaqService {

    private final ScenarioFaqMapper mapper;

    private List<ScenarioFaq> cachedEntries = Collections.emptyList();
    /** keyword → 对应 Faq 索引列表（倒排索引） */
    private final Map<String, List<Integer>> keywordIndex = new HashMap<>();

    public FaqService(ScenarioFaqMapper mapper) {
        this.mapper = mapper;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    /** 从 DB 重新加载 FAQ 并重建倒排索引 */
    public synchronized void reload() {
        List<ScenarioFaq> rows = mapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ScenarioFaq>()
                        .eq(ScenarioFaq::getEnabled, true));
        cachedEntries = rows;
        keywordIndex.clear();

        for (int i = 0; i < rows.size(); i++) {
            ScenarioFaq faq = rows.get(i);
            if (faq.getKeywords() != null && !faq.getKeywords().isBlank()) {
                for (String kw : faq.getKeywords().split(",")) {
                    keywordIndex.computeIfAbsent(kw.trim().toLowerCase(),
                            k -> new ArrayList<>()).add(i);
                }
            }
        }
        log.info("FAQ 预设库加载完成: {} 条, {} 个关键词索引", rows.size(), keywordIndex.size());
    }

    /**
     * 匹配 FAQ：先问题文本匹配，再关键词计数。
     *
     * <p>包含多问句检测：如用户一次问了多个问题（≥2 个 "？"），
     * 跳过 FAQ 短路，交由标准 RAG/Agent 管线综合检索生成。</p>
     */
    public FaqMatchResult match(String query) {
        if (query == null || query.isEmpty() || cachedEntries.isEmpty()) return null;

        String q = query.toLowerCase().trim();

        // 0. 多问题检测：用户一次问多个问题 → 跳过 FAQ，走 RAG 综合回答
        long questionMarkCount = q.chars().filter(c -> c == '？' || c == '?').count();
        if (questionMarkCount >= 2) {
            log.info("FAQ 跳过：检测到 {} 个问号，疑似多问题综合查询", questionMarkCount);
            return null;
        }

        // 1. 优先：问题文本直接匹配（覆盖欢迎页推荐问题点击等场景）
        for (int i = 0; i < cachedEntries.size(); i++) {
            ScenarioFaq faq = cachedEntries.get(i);
            String faqQ = faq.getQuestion().toLowerCase();
            // 查询包含 FAQ 问题，或 FAQ 问题包含查询（允许标点/语气词差异）
            if (q.contains(faqQ) || faqQ.contains(q)) {
                log.info("FAQ 问题文本命中: \"{}\"", faq.getQuestion());
                return new FaqMatchResult(faq, 99);
            }
        }

        // 2. 兜底：关键词命中 ≥ 2 个时返回最佳匹配
        Map<Integer, Integer> hitScores = new HashMap<>();

        for (String kw : keywordIndex.keySet()) {
            if (q.contains(kw)) {
                for (int idx : keywordIndex.get(kw)) {
                    hitScores.merge(idx, 1, Integer::sum);
                }
            }
        }

        if (hitScores.isEmpty()) return null;

        int bestIdx = -1, bestScore = 0;
        for (Map.Entry<Integer, Integer> e : hitScores.entrySet()) {
            if (e.getValue() > bestScore) {
                bestScore = e.getValue();
                bestIdx = e.getKey();
            }
        }

        if (bestScore >= 2 && bestIdx >= 0) {
            ScenarioFaq faq = cachedEntries.get(bestIdx);
            log.info("FAQ 关键词命中: score={}, question=\"{}\"", bestScore, faq.getQuestion());
            return new FaqMatchResult(faq, bestScore);
        }
        return null;
    }

    /** 获取所有条目 */
    public List<FaqEntry> getEntries() {
        return cachedEntries.stream().map(f -> {
            FaqEntry e = new FaqEntry();
            e.setKeywords(f.getKeywords() != null
                    ? Arrays.asList(f.getKeywords().split(",")) : List.of());
            e.setQuestion(f.getQuestion());
            e.setAnswer(f.getAnswer());
            e.setCategory(f.getCategory());
            return e;
        }).toList();
    }

    /** 按分类获取 */
    public List<FaqEntry> getByCategory(String category) {
        return cachedEntries.stream()
                .filter(f -> category.equals(f.getCategory()))
                .map(f -> {
                    FaqEntry e = new FaqEntry();
                    e.setKeywords(f.getKeywords() != null
                            ? Arrays.asList(f.getKeywords().split(",")) : List.of());
                    e.setQuestion(f.getQuestion());
                    e.setAnswer(f.getAnswer());
                    e.setCategory(f.getCategory());
                    return e;
                }).toList();
    }

    public record FaqMatchResult(ScenarioFaq entry, int score) {}
}
