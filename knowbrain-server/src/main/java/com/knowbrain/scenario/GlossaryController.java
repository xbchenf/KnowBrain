package com.knowbrain.scenario;

import com.knowbrain.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 术语词典 API
 */
@Tag(name = "术语词典", description = "术语查询与改写测试")
@RestController
@RequestMapping("/api/v1/glossary")
@RequiredArgsConstructor
public class GlossaryController {

    private final GlossaryService glossaryService;

    @Operation(summary = "获取全部术语")
    @GetMapping
    public Result<List<GlossaryEntry>> list() {
        return Result.ok(glossaryService.listAll());
    }

    @Operation(summary = "查询改写测试")
    @PostMapping("/rewrite")
    public Result<String> rewrite(@RequestBody java.util.Map<String, String> body) {
        String query = body.get("query");
        if (query == null || query.isBlank()) {
            return Result.badRequest("查询文本不能为空");
        }
        String rewritten = glossaryService.rewrite(query);
        return Result.ok(rewritten);
    }
}
