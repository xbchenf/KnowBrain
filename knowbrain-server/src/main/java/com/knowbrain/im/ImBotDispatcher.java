package com.knowbrain.im;

import com.knowbrain.common.GlobalExceptionHandler.BizException;
import com.knowbrain.common.RateLimiter;
import com.knowbrain.im.adapter.ImBotAdapter;
import com.knowbrain.im.model.ImIncomingMessage;
import com.knowbrain.im.model.ImOutgoingMessage;
import com.knowbrain.permission.PermissionService;
import com.knowbrain.retrieval.engine.RAGService;
import com.knowbrain.retrieval.engine.SearchResult;
import com.knowbrain.retrieval.engine.StreamContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * IM Bot 核心调度器 — 接收回调 → RAG → 回复的完整流水线。
 *
 * <h3>处理流程</h3>
 * <ol>
 *   <li>URL 验证识别（首次配置回调地址时触发）</li>
 *   <li>签名验证（防止伪造请求）</li>
 *   <li>解密 + 解析为统一消息模型</li>
 *   <li>消息去重（防止平台重试导致重复回答）</li>
 *   <li>IM 用户 → KB 用户映射</li>
 *   <li>限流检查</li>
 *   <li>异步执行 RAG + 流式回复</li>
 * </ol>
 *
 * <p>全部走异步主动回复 — 企微虽有 5 秒被动回复窗口，但 RAG 生成
 * 通常超过 5 秒，统一用"立即返回 ACK + 异步推送结果"模式。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImBotDispatcher {

    private final List<ImBotAdapter> adapters;
    private final RAGService ragService;
    private final PermissionService permissionService;
    private final RateLimiter rateLimiter;
    private final ImUserMapping userMapping;
    private final ImMessageDedup dedup;

    /**
     * 处理 IM 回调请求。
     *
     * @param platform 平台标识（URL 路径中提取）
     * @param request  HTTP 原始请求
     * @param body     原始请求体（密文或明文）
     * @return URL 验证时返回验证响应字符串；消息回调时返回空字符串
     */
    public String handle(String platform, HttpServletRequest request, String body) {
        ImBotAdapter adapter = findAdapter(platform);

        // ---- Step 1: URL 验证（首次配置回调地址时触发） ----
        String verificationResponse = adapter.handleUrlVerification(request, body);
        if (verificationResponse != null) {
            log.info("[IM-{}] URL 验证成功", platform);
            return verificationResponse;
        }

        // ---- Step 2: 签名验证 ----
        if (!adapter.verifySignature(request, body)) {
            log.warn("[IM-{}] 签名验证失败, remoteIP={}", platform, request.getRemoteAddr());
            throw new BizException(403, "签名验证失败");
        }

        // ---- Step 3: 解密 + 解析 ----
        ImIncomingMessage msg = adapter.parse(request, body);
        if (msg == null || isEmptyMessage(msg)) {
            return ""; // 非文本消息或空内容，静默忽略
        }
        msg.setPlatform(platform); // 确保 platform 字段已设置

        // ---- Step 4: 消息去重 ----
        if (!dedup.markAndCheck(platform, msg.getMessageId())) {
            return "";
        }

        // ---- Step 5: IM 用户 → KB 用户映射 ----
        Long kbUserId = userMapping.resolveUserId(platform, msg.getFromUserId(), msg.getFromUserName());

        // ---- Step 6: 限流（按 KB 用户 ID） ----
        if (!rateLimiter.tryAcquire(kbUserId)) {
            log.info("[IM-{}] 限流触发: kbUserId={}", platform, kbUserId);
            adapter.sendReply(
                    ImOutgoingMessage.single("提问太频繁了，请稍后再试 ⏳", msg.getMessageId()),
                    msg);
            return "";
        }

        // ---- Step 7: 获取可访问空间 ----
        List<Long> spaceIds = permissionService.getAccessibleSpaceIds(kbUserId);

        // ---- Step 8: 异步执行 RAG + 流式回复 ----
        CompletableFuture.runAsync(() -> executeRagAndReply(adapter, msg, spaceIds));

        return "";
    }

    // ==================== RAG 执行 ====================

    /**
     * 异步执行 RAG 问答并通过适配器流式推送回复。
     *
     * <p>非流式响应（FAQ 命中 / 兜底 / 单 token）直接发一条完整消息，不带"思考中"提示。
     * 流式响应（LLM 生成）先发"思考中"，再逐段推送，最后发完整结果。
     */
    private void executeRagAndReply(ImBotAdapter adapter, ImIncomingMessage msg, List<Long> spaceIds) {
        try {
            StreamContext ctx = ragService.chatStream(msg.getContent(), spaceIds);

            // 收集所有 token
            List<String> allTokens = ctx.tokens().collectList().block(Duration.ofSeconds(60));
            if (allTokens == null || allTokens.isEmpty()) {
                adapter.sendReply(
                        ImOutgoingMessage.error("抱歉，系统处理出错了。", msg.getMessageId()),
                        msg);
                return;
            }

            String fullAnswer = String.join("", allTokens);
            List<SearchResult> sources = ctx.sources();

            // 非流式（单 token）：FAQ/兜底 → 直接发一条完整消息
            if (allTokens.size() == 1) {
                String content = fullAnswer;
                if (sources != null && !sources.isEmpty()) {
                    content += buildSourceLinks(sources);
                }
                adapter.sendReply(
                        ImOutgoingMessage.single(content, msg.getMessageId()),
                        msg);
                log.info("[IM-{}] RAG 完成(非流式): msgId={}, answerLen={}",
                        msg.getPlatform(), msg.getMessageId(), fullAnswer.length());
                return;
            }

            // 流式（多 token）：LLM 生成 → 先发"思考中"，再逐段推送
            adapter.sendReply(
                    ImOutgoingMessage.streamFirst("正在检索知识库… 🔍", msg.getMessageId()),
                    msg);

            StringBuilder buffer = new StringBuilder();
            int[] seq = {1};
            for (int i = 0; i < allTokens.size(); i++) {
                buffer.append(allTokens.get(i));
                // 每 80 个 token 或最后一个 token 时推送
                if ((i + 1) % 80 == 0 || i == allTokens.size() - 1) {
                    adapter.sendReply(
                            ImOutgoingMessage.streamSegment(buffer.toString(), seq[0]++, msg.getMessageId()),
                            msg);
                }
            }

            // 最终消息（含溯源）
            String finalContent = buffer.toString();
            if (sources != null && !sources.isEmpty()) {
                finalContent += buildSourceLinks(sources);
            }
            adapter.sendReply(
                    ImOutgoingMessage.streamFinal(
                            finalContent,
                            sources != null
                                    ? sources.stream().map(SearchResult::getDocumentTitle).toList()
                                    : List.of(),
                            seq[0], msg.getMessageId()),
                    msg);

            log.info("[IM-{}] RAG 完成(流式): msgId={}, answerLen={}",
                    msg.getPlatform(), msg.getMessageId(), fullAnswer.length());
        } catch (Exception e) {
            log.error("[IM-{}] RAG 执行失败: msgId={}", msg.getPlatform(), msg.getMessageId(), e);
            adapter.sendReply(
                    ImOutgoingMessage.error("抱歉，系统处理出错了，请稍后重试。", msg.getMessageId()),
                    msg);
        }
    }

    // ==================== 辅助方法 ====================

    /** 构建溯源链接（追加在回答末尾） */
    private String buildSourceLinks(List<SearchResult> sources) {
        StringBuilder sb = new StringBuilder("\n\n---\n📖 **参考来源**：\n");
        for (int i = 0; i < Math.min(sources.size(), 5); i++) {
            SearchResult s = sources.get(i);
            sb.append(String.format("%d. **%s**（相关度: %.0f%%）\n",
                    i + 1, s.getDocumentTitle(), s.getScore() * 100));
        }
        return sb.toString();
    }

    /** 判断消息是否为空（无需回复） */
    private boolean isEmptyMessage(ImIncomingMessage msg) {
        return msg.getContent() == null || msg.getContent().isBlank();
    }

    /** 查找平台对应的适配器 */
    private ImBotAdapter findAdapter(String platform) {
        return adapters.stream()
                .filter(a -> a.platform().equalsIgnoreCase(platform))
                .findFirst()
                .orElseThrow(() -> new BizException(400, "不支持的 IM 平台: " + platform));
    }
}
