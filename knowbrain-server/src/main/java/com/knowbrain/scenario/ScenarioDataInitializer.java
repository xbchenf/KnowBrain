package com.knowbrain.scenario;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

import java.util.List;

/**
 * 场景数据初始化器 — 首次启动时从 JSON 种子文件导入到数据库
 *
 * 策略：
 * - 遍历 classpath:scenarios/ 下所有子目录
 * - 每行按业务主键去重（category=key, glossary=term, faq=question）
 * - 已存在的行跳过（保留运行时编辑、支持多场景包叠加）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScenarioDataInitializer {

    private final ScenarioCategoryMapper categoryMapper;
    private final ScenarioGlossaryMapper glossaryMapper;
    private final ScenarioFaqMapper faqMapper;
    private final ResourceLoader resourceLoader;
    private final ScenarioConfig scenarioConfig;
    private final GlossaryService glossaryService;
    private final FaqService faqService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 所有场景包目录（新增场景时在此注册） */
    private static final String[] SCENARIO_DIRS = {"it-helpdesk", "hr-policy"};

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        boolean changed = false;
        for (String dir : SCENARIO_DIRS) {
            log.info(">>> 加载场景: {}", dir);
            String base = "classpath:scenarios/" + dir + "/";
            if (seedCategories(base, dir)) changed = true;
            if (seedGlossary(base, dir)) changed = true;
            if (seedFaq(base, dir)) changed = true;
        }

        // 有新数据入库 → 刷新所有内存缓存
        if (changed) {
            scenarioConfig.reload();
            glossaryService.reload();
            faqService.reload();
            log.info("场景内存缓存已刷新");
        }
    }

    // ==================== 分类 ====================

    /** @return true 如果有新数据入库 */
    private boolean seedCategories(String basePath, String scenario) {
        try {
            Resource res = resourceLoader.getResource(basePath + "categories.json");
            if (!res.exists()) {
                log.info("  [分类] 无数据文件，跳过");
                return false;
            }
            List<Category> list = objectMapper.readValue(res.getInputStream(),
                    new TypeReference<List<Category>>() {});

            int inserted = 0, skipped = 0;
            for (Category cat : list) {
                if (insertCategoryIfAbsent(cat, null)) inserted++; else skipped++;
                if (cat.getChildren() != null) {
                    for (Category child : cat.getChildren()) {
                        if (insertCategoryIfAbsent(child, cat.getKey())) inserted++; else skipped++;
                    }
                }
            }
            log.info("  [分类] 新增 {} 条, 跳过 {} 条 (场景={})", inserted, skipped, scenario);
            return inserted > 0;
        } catch (Exception e) {
            log.error("  [分类] 导入失败 (场景={}): {}", scenario, e.getMessage());
            return false;
        }
    }

    private boolean insertCategoryIfAbsent(Category cat, String parentKey) {
        Long count = categoryMapper.selectCount(
                new LambdaQueryWrapper<ScenarioCategory>()
                        .eq(ScenarioCategory::getKey, cat.getKey()));
        if (count > 0) return false;

        ScenarioCategory sc = new ScenarioCategory();
        sc.setName(cat.getName());
        sc.setKey(cat.getKey());
        sc.setParentKey(parentKey);
        categoryMapper.insert(sc);
        return true;
    }

    // ==================== 术语 ====================

    /** @return true 如果有新数据入库 */
    private boolean seedGlossary(String basePath, String scenario) {
        try {
            Resource res = resourceLoader.getResource(basePath + "glossary.json");
            if (!res.exists()) {
                log.info("  [术语] 无数据文件，跳过");
                return false;
            }
            List<GlossaryEntry> list = objectMapper.readValue(res.getInputStream(),
                    new TypeReference<List<GlossaryEntry>>() {});

            int inserted = 0, skipped = 0;
            for (GlossaryEntry e : list) {
                Long count = glossaryMapper.selectCount(
                        new LambdaQueryWrapper<ScenarioGlossary>()
                                .eq(ScenarioGlossary::getTerm, e.getTerm()));
                if (count > 0) { skipped++; continue; }

                ScenarioGlossary g = new ScenarioGlossary();
                g.setTerm(e.getTerm());
                g.setFormal(e.getFormal());
                g.setSynonyms(e.getSynonyms() != null ? String.join(",", e.getSynonyms()) : null);
                glossaryMapper.insert(g);
                inserted++;
            }
            log.info("  [术语] 新增 {} 条, 跳过 {} 条 (场景={})", inserted, skipped, scenario);
            return inserted > 0;
        } catch (Exception ex) {
            log.error("  [术语] 导入失败 (场景={}): {}", scenario, ex.getMessage());
            return false;
        }
    }

    // ==================== FAQ ====================

    /** @return true 如果有新数据入库 */
    private boolean seedFaq(String basePath, String scenario) {
        try {
            Resource res = resourceLoader.getResource(basePath + "faq.json");
            if (!res.exists()) {
                log.info("  [FAQ] 无数据文件，跳过");
                return false;
            }
            List<FaqEntry> list = objectMapper.readValue(res.getInputStream(),
                    new TypeReference<List<FaqEntry>>() {});

            int inserted = 0, skipped = 0;
            for (FaqEntry e : list) {
                Long count = faqMapper.selectCount(
                        new LambdaQueryWrapper<ScenarioFaq>()
                                .eq(ScenarioFaq::getQuestion, e.getQuestion()));
                if (count > 0) { skipped++; continue; }

                ScenarioFaq f = new ScenarioFaq();
                f.setKeywords(e.getKeywords() != null ? String.join(",", e.getKeywords()) : null);
                f.setQuestion(e.getQuestion());
                f.setAnswer(e.getAnswer());
                f.setCategory(e.getCategory());
                f.setEnabled(true);
                faqMapper.insert(f);
                inserted++;
            }
            log.info("  [FAQ] 新增 {} 条, 跳过 {} 条 (场景={})", inserted, skipped, scenario);
            return inserted > 0;
        } catch (Exception ex) {
            log.error("  [FAQ] 导入失败 (场景={}): {}", scenario, ex.getMessage());
            return false;
        }
    }
}
