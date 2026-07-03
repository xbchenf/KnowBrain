package com.knowbrain.scenario;

import com.knowbrain.scenario.entity.ScenarioGlossary;
import com.knowbrain.scenario.mapper.ScenarioGlossaryMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 术语词典服务 — 从 DB 读取，首次启动从 JSON 种子导入
 */
@Slf4j
@Service
public class GlossaryService {

    private final ScenarioGlossaryMapper mapper;

    /** term/synonym → formal 的快速映射（内存缓存） */
    private final Map<String, String> lookup = new HashMap<>();
    private List<ScenarioGlossary> cachedEntries = Collections.emptyList();

    public GlossaryService(ScenarioGlossaryMapper mapper) {
        this.mapper = mapper;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    /** 从 DB 重新加载术语词典 */
    public synchronized void reload() {
        List<ScenarioGlossary> rows = mapper.selectList(null);
        cachedEntries = rows;
        lookup.clear();

        for (ScenarioGlossary g : rows) {
            lookup.put(g.getTerm().toLowerCase(), g.getFormal());
            if (g.getSynonyms() != null && !g.getSynonyms().isBlank()) {
                for (String syn : g.getSynonyms().split(",")) {
                    lookup.putIfAbsent(syn.trim().toLowerCase(), g.getFormal());
                }
            }
        }
        log.info("术语词典加载完成: {} 条术语, {} 个映射", rows.size(), lookup.size());
    }

    /**
     * 查询改写
     */
    public String rewrite(String query) {
        if (query == null || query.isEmpty()) return query;

        String result = query;
        List<String> terms = new ArrayList<>(lookup.keySet());
        terms.sort((a, b) -> Integer.compare(b.length(), a.length()));

        for (String term : terms) {
            if (result.toLowerCase().contains(term.toLowerCase())) {
                result = result.replaceAll("(?i)" + Pattern.quote(term), lookup.get(term));
            }
        }
        return result;
    }

    /** 获取全部术语条目（兼容旧 API） */
    public List<GlossaryEntry> listAll() {
        return cachedEntries.stream().map(g -> {
            GlossaryEntry e = new GlossaryEntry();
            e.setTerm(g.getTerm());
            e.setFormal(g.getFormal());
            e.setSynonyms(g.getSynonyms() != null
                    ? Arrays.asList(g.getSynonyms().split(",")) : List.of());
            return e;
        }).toList();
    }
}
