package com.knowbrain.scenario;

import com.knowbrain.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * FAQ 预设问答 API
 */
@Tag(name = "FAQ 预设问答", description = "高频问答预设库查询")
@RestController
@RequestMapping("/api/v1/faq")
@RequiredArgsConstructor
public class FaqController {

    private final FaqService faqService;

    @Operation(summary = "获取全部 FAQ")
    @GetMapping
    public Result<List<FaqEntry>> list() {
        return Result.ok(faqService.getEntries());
    }

    @Operation(summary = "按分类获取 FAQ")
    @GetMapping("/category/{category}")
    public Result<List<FaqEntry>> listByCategory(@PathVariable String category) {
        return Result.ok(faqService.getByCategory(category));
    }

    @Operation(summary = "FAQ 匹配测试")
    @PostMapping("/match")
    public Result<Object> match(@RequestBody java.util.Map<String, String> body) {
        String query = body.get("query");
        if (query == null || query.isBlank()) {
            return Result.badRequest("查询文本不能为空");
        }
        var result = faqService.match(query);
        if (result == null) return Result.ok("未匹配到 FAQ", null);
        return Result.ok(java.util.Map.of(
                "question", result.entry().getQuestion(),
                "answer", result.entry().getAnswer(),
                "score", result.score()
        ));
    }
}
