package com.knowbrain.evaluation;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowbrain.common.Result;
import com.knowbrain.evaluation.entity.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * RAG 评测管理控制器 — 仅 ADMIN 可访问（AuthInterceptor 拦截）。
 */
@Tag(name = "RAG 评测管理", description = "评测数据集 + 评测运行 + 结果查询（ADMIN 权限）")
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/evaluation")
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationService evaluationService;

    // ==================== 数据集管理 ====================

    @Operation(summary = "数据集列表")
    @GetMapping("/datasets")
    public Result<IPage<EvaluationDataset>> listDatasets(
            @RequestParam(required = false) String scenario,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(evaluationService.listDatasets(scenario, page, size));
    }

    @Operation(summary = "创建数据集")
    @PostMapping("/datasets")
    public Result<EvaluationDataset> createDataset(@RequestBody EvaluationDataset dataset) {
        if (dataset.getName() == null || dataset.getName().isBlank()) {
            return Result.badRequest("数据集名称不能为空");
        }
        return Result.ok(evaluationService.createDataset(dataset));
    }

    @Operation(summary = "删除数据集（级联删除问题+结果）")
    @DeleteMapping("/datasets/{id}")
    public Result<Void> deleteDataset(@PathVariable Long id) {
        evaluationService.deleteDataset(id);
        return Result.ok(null);
    }

    @Operation(summary = "更新数据集")
    @PutMapping("/datasets/{id}")
    public Result<Void> updateDataset(@PathVariable Long id,
                                       @RequestBody EvaluationDataset dataset) {
        evaluationService.updateDataset(id, dataset);
        return Result.ok(null);
    }

    // ==================== 问题管理 ====================

    @Operation(summary = "数据集问题列表")
    @GetMapping("/datasets/{id}/questions")
    public Result<IPage<EvaluationQuestion>> listQuestions(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        return Result.ok(evaluationService.listQuestions(id, page, size));
    }

    @Operation(summary = "添加单条问题")
    @PostMapping("/datasets/{id}/questions")
    public Result<EvaluationQuestion> addQuestion(
            @PathVariable Long id,
            @RequestBody EvaluationQuestion question) {
        if (question.getQuestion() == null || question.getQuestion().isBlank()) {
            return Result.badRequest("问题内容不能为空");
        }
        question.setDatasetId(id);
        return Result.ok(evaluationService.addQuestion(question));
    }

    @Operation(summary = "批量导入问题")
    @PostMapping("/datasets/{id}/questions/batch")
    public Result<Map<String, Integer>> batchImportQuestions(
            @PathVariable Long id,
            @RequestBody List<EvaluationQuestion> questions) {
        if (questions == null || questions.isEmpty()) {
            return Result.badRequest("导入列表不能为空");
        }
        int count = evaluationService.importQuestions(id, questions);
        return Result.ok(Map.of("imported", count));
    }

    @Operation(summary = "更新问题")
    @PutMapping("/datasets/{datasetId}/questions/{questionId}")
    public Result<Void> updateQuestion(
            @PathVariable Long datasetId,
            @PathVariable Long questionId,
            @RequestBody EvaluationQuestion question) {
        evaluationService.updateQuestion(questionId, question);
        return Result.ok(null);
    }

    @Operation(summary = "删除问题")
    @DeleteMapping("/datasets/{datasetId}/questions/{questionId}")
    public Result<Void> deleteQuestion(
            @PathVariable Long datasetId,
            @PathVariable Long questionId) {
        evaluationService.deleteQuestion(questionId);
        return Result.ok(null);
    }

    // ==================== 评测运行 ====================

    @Operation(summary = "启动评测")
    @PostMapping("/runs")
    public Result<EvaluationRun> startRun(@RequestBody Map<String, Long> body) {
        Long datasetId = body.get("datasetId");
        if (datasetId == null) {
            return Result.badRequest("datasetId 不能为空");
        }
        try {
            EvaluationRun run = evaluationService.startRun(datasetId);
            return Result.ok(run);
        } catch (IllegalArgumentException e) {
            return Result.badRequest(e.getMessage());
        }
    }

    @Operation(summary = "运行历史列表")
    @GetMapping("/runs")
    public Result<IPage<EvaluationRun>> listRuns(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(evaluationService.listRuns(page, size));
    }

    @Operation(summary = "运行详情（含汇总指标）")
    @GetMapping("/runs/{id}")
    public Result<EvaluationRun> getRun(@PathVariable Long id) {
        return Result.ok(evaluationService.getRun(id));
    }

    @Operation(summary = "删除运行记录（级联删除结果）")
    @DeleteMapping("/runs/{id}")
    public Result<Void> deleteRun(@PathVariable Long id) {
        evaluationService.deleteRun(id);
        return Result.ok(null);
    }

    @Operation(summary = "单题结果列表")
    @GetMapping("/runs/{id}/results")
    public Result<IPage<EvaluationResult>> getRunResults(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        return Result.ok(evaluationService.getRunResults(id, page, size));
    }
}
