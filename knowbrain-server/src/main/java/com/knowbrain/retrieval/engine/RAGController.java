package com.knowbrain.retrieval.engine;

import com.knowbrain.common.GlobalExceptionHandler.BizException;
import com.knowbrain.common.RateLimiter;
import com.knowbrain.common.Result;
import com.knowbrain.permission.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RAG 问答控制器
 *
 * 支持认证可选模式：
 * - 带 Authorization Header → 仅搜索用户可访问的空间
 * - 无 Authorization Header → 仅搜索 PUBLIC 空间
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/rag")
@RequiredArgsConstructor
public class RAGController {

    private final RAGService ragService;
    private final PermissionService permissionService;
    private final RateLimiter rateLimiter;

    /**
     * RAG 问答：检索 + 生成（带空间权限过滤 + 可选对话历史）
     */
    @PostMapping("/chat")
    public Result<ChatResponse> chat(@RequestBody Map<String, Object> body,
                                      HttpServletRequest request) {
        checkRateLimit(request);
        String question = (String) body.get("question");
        if (question == null || question.isBlank()) {
            return Result.badRequest("问题不能为空");
        }
        List<Long> spaceIds = resolveSpaceIds(request, body);
        List<Map<String, String>> history = extractHistory(body);
        String category = body.get("category") instanceof String s && !s.isBlank() ? s : null;
        ChatResponse response = ragService.chat(question, spaceIds, history, category);
        return Result.ok(response);
    }

    /**
     * 纯检索（不调用 LLM，返回 Top-K 文档片段，带空间权限过滤）
     */
    @GetMapping("/search")
    public Result<List<SearchResult>> search(
            @RequestParam("q") String question,
            @RequestParam(value = "topK", defaultValue = "5") int topK,
            HttpServletRequest request) {
        checkRateLimit(request);
        if (question == null || question.isBlank()) {
            return Result.badRequest("搜索关键词不能为空");
        }
        List<Long> spaceIds = getAccessibleSpaceIds(request);
        List<SearchResult> results = ragService.search(question, topK, spaceIds);
        return Result.ok(results);
    }

    /**
     * RAG 流式问答（SSE）— token 逐字推送 + 溯源元数据
     *
     * SSE 事件类型：
     * - thinking : Agent 思考链（analyze / search / synthesize，token 流之前）
     * - token    : LLM 生成文本片段（逐字/逐词）
     * - sources  : 检索溯源列表（JSON 数组）
     * - done     : 完成信号，包含 confidence / fallback
     * - error    : 异常信号
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody Map<String, Object> body,
                                  HttpServletRequest request) {
        checkRateLimit(request);
        String question = (String) body.get("question");
        if (question == null || question.isBlank()) {
            SseEmitter bad = new SseEmitter();
            bad.completeWithError(new IllegalArgumentException("问题不能为空"));
            return bad;
        }

        List<Long> spaceIds = resolveSpaceIds(request, body);
        List<Map<String, String>> history = extractHistory(body);
        String category = body.get("category") instanceof String s && !s.isBlank() ? s : null;
        SseEmitter emitter = new SseEmitter(300_000L); // 5 分钟超时

        // 在独立线程中桥接 Flux → SSE
        Thread worker = new Thread(() -> {
            try {
                log.info("SSE: 开始流式处理 question={} historyRounds={} category={}", question, history.size() / 2, category);
                StreamContext ctx = ragService.chatStream(question, spaceIds, history, category);
                log.info("SSE: StreamContext 就绪 sources={} fallback={}", ctx.sources().size(), ctx.fallback());
                Flux<String> tokens = ctx.tokens();

                // ★ 订阅思考链 Flux（Agent 模式下为实时流，非 Agent 模式为空 Flux）
                //    思考链完成后自动订阅 token 流，形成"思考 → 回答"的流水线
                ctx.thinkingFlux()
                        .doOnNext(event -> {
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("thinking")
                                        .data(event));
                                log.debug("SSE: thinking 事件已推送 type={}", event.get("type"));
                            } catch (Exception ex) {
                                log.warn("SSE thinking 事件推送失败: {}", ex.getMessage());
                            }
                        })
                        .doOnComplete(() -> {
                            log.info("SSE: thinkingFlux 完成，开始订阅 token 流");
                            // 思考链完成 → 订阅 token 流
                            ctx.tokens().doOnNext(token -> {
                                try {
                                    emitter.send(SseEmitter.event().name("token").data(token));
                                } catch (Exception e) {
                                    log.warn("SSE token 推送失败: {}", e.getMessage());
                                }
                            }).doOnComplete(() -> {
                                log.info("SSE: token 流完成，推送元数据");
                                try {
                                    // 推送溯源列表（Agent 路径通过 Supplier 延迟计算）
                                    List<SearchResult> sources = ctx.sources();
                                    emitter.send(SseEmitter.event()
                                            .name("sources")
                                            .data(sources != null ? sources : List.of()));
                                    // 推送完成信号
                                    Map<String, Object> done = Map.of(
                                            "confidence", ctx.confidence(),
                                            "fallback", ctx.fallback()
                                    );
                                    emitter.send(SseEmitter.event()
                                            .name("done")
                                            .data(done));
                                    emitter.complete();
                                    log.info("SSE: 流式问答完成");
                                } catch (Exception e) {
                                    log.error("SSE 元数据推送失败", e);
                                    emitter.completeWithError(e);
                                }
                            }).doOnError(e -> {
                                log.error("LLM 流式调用异常", e);
                                try {
                                    emitter.send(SseEmitter.event()
                                            .name("error")
                                            .data("服务暂时不可用，请稍后重试。"));
                                } catch (Exception ignored) {}
                                emitter.completeWithError(e);
                            }).subscribe();
                        })
                        .doOnError(e -> {
                            log.warn("SSE: thinkingFlux 异常，仍尝试订阅 token 流: {}", e.getMessage());
                            // Agent 思考链异常时仍尝试推送 token 流（降级回答）
                            ctx.tokens().doOnNext(token -> {
                                try {
                                    emitter.send(SseEmitter.event().name("token").data(token));
                                } catch (Exception ex) {
                                    log.warn("SSE token 推送失败: {}", ex.getMessage());
                                }
                            }).doOnComplete(() -> {
                                try {
                                    Map<String, Object> done = Map.of(
                                            "confidence", "low",
                                            "fallback", true
                                    );
                                    emitter.send(SseEmitter.event().name("done").data(done));
                                    emitter.complete();
                                } catch (Exception ex) {
                                    emitter.completeWithError(ex);
                                }
                            }).doOnError(ex -> emitter.completeWithError(ex)).subscribe();
                        })
                        .subscribe();

            } catch (Exception e) {
                log.error("流式问答初始化失败", e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("服务暂时不可用，请稍后重试。"));
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
            }
        });
        worker.setName("sse-worker");
        worker.setDaemon(true);
        worker.start();

        return emitter;
    }

    /**
     * 从请求体中提取对话历史
     */
    private List<Map<String, String>> extractHistory(Map<String, Object> body) {
        Object historyObj = body.get("history");
        if (!(historyObj instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<Map<String, String>> history = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> raw) {
                Object roleObj = raw.get("role");
                Object contentObj = raw.get("content");
                if (roleObj instanceof String role && contentObj instanceof String content
                        && !role.isBlank() && !content.isBlank()) {
                    history.add(Map.of("role", role, "content", content));
                }
            }
        }
        return history;
    }

    /**
     * 从请求中提取用户 ID，查询可访问空间列表
     * 未登录用户只能看 PUBLIC 空间
     */
    private List<Long> getAccessibleSpaceIds(HttpServletRequest request) {
        Long userId = extractUserId(request);
        return permissionService.getAccessibleSpaceIds(userId);
    }

    private Long extractUserId(HttpServletRequest request) {
        Object uid = request.getAttribute("userId");
        if (uid instanceof Number n) return n.longValue();
        throw new IllegalStateException("未登录用户不应到达此处（AuthInterceptor 拦截）");
    }

    /**
     * 限流检查：超出配额抛出 BizException(429)，由 GlobalExceptionHandler 转换为 HTTP 429
     */
    private void checkRateLimit(HttpServletRequest request) {
        Long userId = extractUserId(request);
        if (!rateLimiter.tryAcquire(userId)) {
            throw new BizException(429, "请求过于频繁，请稍后再试");
        }
    }

    /**
     * 解析客户端传入的 spaceIds（可选），与用户可访问空间取交集。
     * 未传或为空时使用全部可访问空间。
     */
    private List<Long> resolveSpaceIds(HttpServletRequest request, Map<String, Object> body) {
        List<Long> accessible = getAccessibleSpaceIds(request);

        Object clientIds = body.get("spaceIds");
        if (!(clientIds instanceof List<?> list) || list.isEmpty()) {
            return accessible;
        }

        // 取交集：客户端传入 ∩ 用户可访问
        List<Long> requested = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Number n) {
                requested.add(n.longValue());
            }
        }
        if (requested.isEmpty()) return accessible;

        requested.retainAll(accessible);
        return requested.isEmpty() ? accessible : requested;
    }
}
