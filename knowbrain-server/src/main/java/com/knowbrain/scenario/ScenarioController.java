package com.knowbrain.scenario;

import com.knowbrain.audit.Auditable;
import com.knowbrain.auth.RoleEnum;
import com.knowbrain.common.GlobalExceptionHandler.BizException;
import com.knowbrain.common.Result;
import com.knowbrain.scenario.entity.ScenarioCategory;
import com.knowbrain.scenario.entity.ScenarioFaq;
import com.knowbrain.scenario.entity.ScenarioGlossary;
import com.knowbrain.scenario.mapper.ScenarioCategoryMapper;
import com.knowbrain.scenario.mapper.ScenarioFaqMapper;
import com.knowbrain.scenario.mapper.ScenarioGlossaryMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 场景数据管理 API — 管理后台使用（需 ADMIN 角色）
 */
@Tag(name = "场景数据管理", description = "分类/术语/FAQ 的 CRUD 管理（需 ADMIN 角色）")
@RestController
@RequestMapping("/api/v1/admin/scenario")
@RequiredArgsConstructor
public class ScenarioController {

    private final ScenarioCategoryMapper categoryMapper;
    private final ScenarioGlossaryMapper glossaryMapper;
    private final ScenarioFaqMapper faqMapper;
    private final ScenarioConfig scenarioConfig;
    private final GlossaryService glossaryService;
    private final FaqService faqService;

    /** 所有写操作统一校验 ADMIN 角色 */
    private void assertAdmin(HttpServletRequest request) {
        Object role = request.getAttribute("role");
        if (role == null || !RoleEnum.ADMIN.matches(role.toString())) {
            throw new BizException(403, "需要管理员权限");
        }
    }

    // ==================== 分类管理 ====================

    @Operation(summary = "全部分类（树形）")
    @GetMapping("/categories")
    public Result<List<Category>> listCategories() {
        return Result.ok(scenarioConfig.getCategories());
    }

    @Operation(summary = "新增分类")
    @Auditable(operation = "CREATE", resourceType = "SCENARIO_CATEGORY",
               resourceName = "#category.name", description = "新增知识分类")
    @PostMapping("/categories")
    public Result<Void> addCategory(@RequestBody ScenarioCategory category,
                                     HttpServletRequest request) {
        assertAdmin(request);
        categoryMapper.insert(category);
        scenarioConfig.reload();
        return Result.ok("添加成功", null);
    }

    @Operation(summary = "删除分类")
    @Auditable(operation = "DELETE", resourceType = "SCENARIO_CATEGORY",
               resourceId = "#id", description = "删除知识分类")
    @DeleteMapping("/categories/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id,
                                        HttpServletRequest request) {
        assertAdmin(request);
        categoryMapper.deleteById(id);
        scenarioConfig.reload();
        return Result.ok("删除成功", null);
    }

    // ==================== 术语管理 ====================

    @Operation(summary = "全部术语")
    @GetMapping("/glossary")
    public Result<List<ScenarioGlossary>> listGlossary() {
        return Result.ok(glossaryMapper.selectList(null));
    }

    @Operation(summary = "新增术语")
    @Auditable(operation = "CREATE", resourceType = "SCENARIO_GLOSSARY",
               resourceName = "#glossary.term", description = "新增术语")
    @PostMapping("/glossary")
    public Result<Void> addGlossary(@RequestBody ScenarioGlossary glossary,
                                     HttpServletRequest request) {
        assertAdmin(request);
        glossaryMapper.insert(glossary);
        glossaryService.reload();
        return Result.ok("添加成功", null);
    }

    @Operation(summary = "删除术语")
    @Auditable(operation = "DELETE", resourceType = "SCENARIO_GLOSSARY",
               resourceId = "#id", description = "删除术语")
    @DeleteMapping("/glossary/{id}")
    public Result<Void> deleteGlossary(@PathVariable Long id,
                                        HttpServletRequest request) {
        assertAdmin(request);
        glossaryMapper.deleteById(id);
        glossaryService.reload();
        return Result.ok("删除成功", null);
    }

    // ==================== FAQ 管理 ====================

    @Operation(summary = "全部 FAQ")
    @GetMapping("/faq")
    public Result<List<ScenarioFaq>> listFaq() {
        return Result.ok(faqMapper.selectList(null));
    }

    @Operation(summary = "新增 FAQ")
    @Auditable(operation = "CREATE", resourceType = "SCENARIO_FAQ",
               resourceName = "#faq.question", description = "新增FAQ")
    @PostMapping("/faq")
    public Result<Void> addFaq(@RequestBody ScenarioFaq faq,
                                HttpServletRequest request) {
        assertAdmin(request);
        faq.setEnabled(true);
        faqMapper.insert(faq);
        faqService.reload();
        return Result.ok("添加成功", null);
    }

    @Operation(summary = "更新 FAQ")
    @Auditable(operation = "UPDATE", resourceType = "SCENARIO_FAQ",
               resourceId = "#id", description = "更新FAQ")
    @PutMapping("/faq/{id}")
    public Result<Void> updateFaq(@PathVariable Long id,
                                   @RequestBody ScenarioFaq faq,
                                   HttpServletRequest request) {
        assertAdmin(request);
        faq.setId(id);
        faqMapper.updateById(faq);
        faqService.reload();
        return Result.ok("更新成功", null);
    }

    @Operation(summary = "删除 FAQ")
    @Auditable(operation = "DELETE", resourceType = "SCENARIO_FAQ",
               resourceId = "#id", description = "删除FAQ")
    @DeleteMapping("/faq/{id}")
    public Result<Void> deleteFaq(@PathVariable Long id,
                                   HttpServletRequest request) {
        assertAdmin(request);
        faqMapper.deleteById(id);
        faqService.reload();
        return Result.ok("删除成功", null);
    }
}
