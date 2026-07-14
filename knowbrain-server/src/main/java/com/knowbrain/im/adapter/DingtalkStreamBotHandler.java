package com.knowbrain.im.adapter;

import com.dingtalk.open.app.api.callback.OpenDingTalkCallbackListener;
import com.dingtalk.open.app.api.chatbot.BotReplier;
import com.dingtalk.open.app.api.message.GenericOpenDingTalkEvent;
import com.dingtalk.open.app.api.models.bot.ChatbotMessage;
import com.dingtalk.open.app.api.models.bot.MessageContent;
import com.knowbrain.common.RateLimiter;
import com.knowbrain.im.ImMessageDedup;
import com.knowbrain.im.ImUserMapping;
import com.knowbrain.permission.PermissionService;
import com.knowbrain.retrieval.engine.RAGService;
import com.knowbrain.retrieval.engine.SearchResult;
import com.knowbrain.retrieval.engine.StreamContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 钉钉 Stream 模式 Bot 消息处理器。
 *
 * <p>由 {@link DingtalkStreamConfig} 注册到 SDK Client。
 * SDK 在收到机器人消息回调时调用 {@link #execute(ChatbotMessage)}。
 *
 * <h3>处理流程</h3>
 * <ol>
 *   <li>提取文本内容（仅处理 text 消息）</li>
 *   <li>消息去重</li>
 *   <li>IM 用户 → KB 用户映射</li>
 *   <li>限流检查</li>
 *   <li>异步 RAG + 流式/非流式回复</li>
 * </ol>
 */
@Slf4j
@Component
public class DingtalkStreamBotHandler
        implements OpenDingTalkCallbackListener<ChatbotMessage, GenericOpenDingTalkEvent> {

    private final RAGService ragService;
    private final PermissionService permissionService;
    private final RateLimiter rateLimiter;
    private final ImUserMapping userMapping;
    private final ImMessageDedup dedup;

    public DingtalkStreamBotHandler(RAGService ragService,
                                     PermissionService permissionService,
                                     RateLimiter rateLimiter,
                                     ImUserMapping userMapping,
                                     ImMessageDedup dedup) {
        this.ragService = ragService;
        this.permissionService = permissionService;
        this.rateLimiter = rateLimiter;
        this.userMapping = userMapping;
        this.dedup = dedup;
    }

    @Override
    public GenericOpenDingTalkEvent execute(ChatbotMessage msg) {
        // ---- Step 1: 提取文本 ----
        if (!"text".equals(msg.getMsgtype())) {
            log.debug("[钉钉Stream] 非文本消息忽略: msgtype={}", msg.getMsgtype());
            return null;
        }
        MessageContent textObj = msg.getText();
        if (textObj == null || textObj.getContent() == null || textObj.getContent().isBlank()) {
            return null;
        }
        String content = textObj.getContent().trim();

        // ---- Step 2: 消息去重 ----
        if (!dedup.markAndCheck("dingtalk", msg.getMsgId())) {
            return null;
        }

        // ---- Step 3: IM 用户 → KB 用户 ----
        String userId = staffId(msg);
        String userName = msg.getSenderNick();
        Long kbUserId = userMapping.resolveUserId("dingtalk", userId, userName);

        // ---- Step 4: 限流 ----
        if (!rateLimiter.tryAcquire(kbUserId)) {
            replyQuick(msg, "提问太频繁了，请稍后再试 ⏳");
            return null;
        }

        // ---- Step 5: 异步 RAG + 回复 ----
        String webhook = msg.getSessionWebhook();
        List<Long> spaceIds = permissionService.getAccessibleSpaceIds(kbUserId);
        CompletableFuture.runAsync(() -> executeRagAndReply(webhook, content, spaceIds));

        return null;
    }

    // ==================== RAG 执行 ====================

    private void executeRagAndReply(String webhook, String question, List<Long> spaceIds) {
        BotReplier bot = BotReplier.fromWebhook(webhook);
        try {
            StreamContext ctx = ragService.chatStream(question, spaceIds);
            List<String> allTokens = ctx.tokens().collectList().block(Duration.ofSeconds(60));
            if (allTokens == null || allTokens.isEmpty()) {
                bot.replyMarkdown("KnowBrain", "抱歉，系统处理出错了。");
                return;
            }

            String fullAnswer = String.join("", allTokens);
            List<SearchResult> sources = ctx.sources();

            if (allTokens.size() == 1) {
                // 非流式：FAQ/兜底
                String md = fullAnswer;
                if (sources != null && !sources.isEmpty()) {
                    md += "\n\n---\n**📖 参考来源**  \n"
                            + buildSourceLinks(sources);
                }
                bot.replyMarkdown("KnowBrain 回答", md);
            } else {
                // 流式：逐段推送
                bot.replyMarkdown("KnowBrain 回答", "🔍 正在检索知识库…\n\n> ⏳ 回答生成中…");

                StringBuilder buffer = new StringBuilder();
                for (int i = 0; i < allTokens.size(); i++) {
                    buffer.append(allTokens.get(i));
                    if ((i + 1) % 80 == 0) {
                        bot.replyMarkdown("KnowBrain 回答(续)", buffer.toString());
                    }
                }

                String finalMd = buffer.toString();
                if (sources != null && !sources.isEmpty()) {
                    finalMd += "\n\n---\n**📖 参考来源**  \n"
                            + buildSourceLinks(sources);
                }
                bot.replyMarkdown("KnowBrain 回答", finalMd);
            }
        } catch (Exception e) {
            log.error("[钉钉Stream] RAG 失败", e);
            try {
                bot.replyMarkdown("KnowBrain", "抱歉，系统处理出错了，请稍后重试。");
            } catch (Exception ignored) { /* 回复失败不处理 */ }
        }
    }

    // ==================== 辅助 ====================

    /** 优先 senderStaffId，回退 senderId */
    private String staffId(ChatbotMessage msg) {
        String sid = msg.getSenderStaffId();
        return (sid != null && !sid.isBlank()) ? sid : msg.getSenderId();
    }

    private void replyQuick(ChatbotMessage msg, String text) {
        try {
            BotReplier.fromWebhook(msg.getSessionWebhook())
                    .replyMarkdown("KnowBrain", text);
        } catch (Exception e) {
            log.debug("[钉钉Stream] 快捷回复失败", e);
        }
    }

    private String buildSourceLinks(List<SearchResult> sources) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(sources.size(), 5); i++) {
            SearchResult s = sources.get(i);
            sb.append(i + 1).append(". **").append(s.getDocumentTitle())
                    .append("**（").append(Math.round(s.getScore() * 100)).append("%）  \n");
        }
        return sb.toString();
    }
}
