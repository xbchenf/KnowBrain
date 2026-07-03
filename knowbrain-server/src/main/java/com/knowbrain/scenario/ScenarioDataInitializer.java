package com.knowbrain.scenario;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowbrain.scenario.entity.ScenarioCategory;
import com.knowbrain.scenario.entity.ScenarioFaq;
import com.knowbrain.scenario.entity.ScenarioGlossary;
import com.knowbrain.scenario.mapper.ScenarioCategoryMapper;
import com.knowbrain.scenario.mapper.ScenarioFaqMapper;
import com.knowbrain.scenario.mapper.ScenarioGlossaryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

/**
 * 场景数据初始化器 — 首次启动时从 JSON 种子文件导入到数据库
 *
 * 策略：DB 有数据 → 跳过（保留运行时编辑）；DB 为空 → 从 JSON 导入
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScenarioDataInitializer {

    private final ScenarioCategoryMapper categoryMapper;
    private final ScenarioGlossaryMapper glossaryMapper;
    private final ScenarioFaqMapper faqMapper;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SCENARIO_PATH = "classpath:scenarios/it-helpdesk/";

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        seedCategories();
        seedGlossary();
        seedFaq();
    }

    private void seedCategories() {
        if (categoryMapper.selectCount(null) > 0) {
            log.info("分类数据已存在 ({} 条)，跳过种子导入", categoryMapper.selectCount(null));
            return;
        }
        try {
            Resource res = resourceLoader.getResource(SCENARIO_PATH + "categories.json");
            List<Category> list = objectMapper.readValue(res.getInputStream(),
                    new TypeReference<List<Category>>() {});

            int order = 0;
            for (Category cat : list) {
                ScenarioCategory parent = toEntity(cat, null, order++);
                categoryMapper.insert(parent);
                if (cat.getChildren() != null) {
                    int childOrder = 0;
                    for (Category child : cat.getChildren()) {
                        categoryMapper.insert(toEntity(child, cat.getKey(), childOrder++));
                    }
                }
            }
            log.info("分类种子导入完成: {} 条", categoryMapper.selectCount(null));
        } catch (Exception e) {
            log.error("分类种子导入失败: {}", e.getMessage());
        }
    }

    private void seedGlossary() {
        if (glossaryMapper.selectCount(null) > 0) {
            log.info("术语数据已存在 ({} 条)，跳过种子导入", glossaryMapper.selectCount(null));
            return;
        }
        try {
            Resource res = resourceLoader.getResource(SCENARIO_PATH + "glossary.json");
            List<GlossaryEntry> list = objectMapper.readValue(res.getInputStream(),
                    new TypeReference<List<GlossaryEntry>>() {});
            for (GlossaryEntry e : list) {
                ScenarioGlossary g = new ScenarioGlossary();
                g.setTerm(e.getTerm());
                g.setFormal(e.getFormal());
                g.setSynonyms(e.getSynonyms() != null ? String.join(",", e.getSynonyms()) : null);
                glossaryMapper.insert(g);
            }
            log.info("术语种子导入完成: {} 条", glossaryMapper.selectCount(null));
        } catch (Exception ex) {
            log.error("术语种子导入失败: {}", ex.getMessage());
        }
    }

    private void seedFaq() {
        if (faqMapper.selectCount(null) > 0) {
            log.info("FAQ 数据已存在 ({} 条)，跳过种子导入", faqMapper.selectCount(null));
            return;
        }
        try {
            Resource res = resourceLoader.getResource(SCENARIO_PATH + "faq.json");
            List<FaqEntry> list = objectMapper.readValue(res.getInputStream(),
                    new TypeReference<List<FaqEntry>>() {});
            for (FaqEntry e : list) {
                ScenarioFaq f = new ScenarioFaq();
                f.setKeywords(e.getKeywords() != null ? String.join(",", e.getKeywords()) : null);
                f.setQuestion(e.getQuestion());
                f.setAnswer(e.getAnswer());
                f.setCategory(e.getCategory());
                f.setEnabled(true);
                faqMapper.insert(f);
            }
            log.info("FAQ 种子导入完成: {} 条", faqMapper.selectCount(null));
        } catch (Exception ex) {
            log.error("FAQ 种子导入失败: {}", ex.getMessage());
        }
    }

    private ScenarioCategory toEntity(Category cat, String parentKey, int order) {
        ScenarioCategory sc = new ScenarioCategory();
        sc.setName(cat.getName());
        sc.setKey(cat.getKey());
        sc.setParentKey(parentKey);
        sc.setSortOrder(order);
        return sc;
    }
}
