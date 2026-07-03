package com.knowbrain.scenario;

import com.knowbrain.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识分类 API
 */
@Tag(name = "知识分类", description = "获取文档分类树")
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final ScenarioConfig scenarioConfig;

    @Operation(summary = "获取全部分类")
    @GetMapping
    public Result<List<Category>> list() {
        return Result.ok(scenarioConfig.getCategories());
    }

    @Operation(summary = "根据 key 查找分类名")
    @GetMapping("/lookup")
    public Result<String> lookup(@RequestParam("key") String key) {
        String name = scenarioConfig.findCategoryName(key);
        return name != null ? Result.ok(name) : Result.notFound("未知分类");
    }
}
