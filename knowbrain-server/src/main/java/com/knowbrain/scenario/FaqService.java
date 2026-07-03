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
     * 精确匹配：关键词命中 ≥ 2 个时返回最佳匹配
     */
    public FaqMatchResult match(String query) {
        if (query == null || query.isEmpty() || cachedEntries.isEmpty()) return null;

        String q = query.toLowerCase();
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
            log.info("FAQ 命中: score={}, question=\"{}\"", bestScore, faq.getQuestion());
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
