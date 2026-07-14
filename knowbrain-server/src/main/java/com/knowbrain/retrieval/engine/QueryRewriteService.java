package com.knowbrain.retrieval.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;

/**
 * LLM 查询改写服务 — 将模糊/口语化查询改写为规范化检索查询
 *
 * 仅在检测到需要时触发（短查询、口语化查询），避免对所有查询增加延迟。
 * 改写失败时静默降级返回 null，由调用方回退到原查询。
 */
@Slf4j
@Service
public class QueryRewriteService {

    private final ChatClient chatClient;

    /** 改写系统指令（启动时加载，避免每次请求读文件） */
    private final String systemPrompt;

    public QueryRewriteService(ChatClient.Builder chatClientBuilder,
                               @Value("classpath:prompts/query-rewrite.txt") Resource promptTemplate) {
        this.chatClient = chatClientBuilder.build();
        this.systemPrompt = loadSystemPrompt(promptTemplate);
    }

    /** 需要 LLM 改写的最小查询长度（≤ 此值判定为模糊简短查询） */
    private static final int VAGUE_LENGTH_THRESHOLD = 4;

    /** 口语化特征词（包含任一即触发改写） */
    private static final Set<String> COLLOQUIAL_MARKERS = Set.of(
            "咋", "啥", "咋整", "咋弄", "咋设", "咋办", "咋配置",
            "弄一下", "破电脑", "转圈", "圈圈", "卡死",
            "怎么弄", "怎么搞", "搞一下", "设一下",
            "那个", "哪个", "这玩意", "那玩意"
    );

    /** LLM 改写超时时间 */
    private static final Duration REWRITE_TIMEOUT = Duration.ofSeconds(3);

    /**
     * 改写查询（如果需要）。
     *
     * @param originalQuery 原始用户输入（glossary 改写前）
     * @return 改写后的查询，如果不需要改写或改写失败返回 null
     */
    public String rewrite(String originalQuery) {
        if (originalQuery == null || originalQuery.isBlank()) {
            return null;
        }

        if (!needsLlmRewrite(originalQuery)) {
            return null;
        }

        try {
            // system 放改写指令，user 放原始查询 — 防止用户输入覆盖系统指令（Prompt Injection）
            String rewritten = chatClient.prompt()
                    .system(systemPrompt)
                    .user(originalQuery)
                    .stream()
                    .content()
                    .collectList()
                    .map(list -> String.join("", list))
                    .block(REWRITE_TIMEOUT);

            if (rewritten == null || rewritten.isBlank()) {
                log.warn("LLM 查询改写返回空，回退到原查询: \"{}\"", safePreview(originalQuery));
                return null;
            }

            // 清理可能的多余空白和换行
            rewritten = rewritten.trim().replaceAll("\\s+", " ");

            // 改写结果与原查询相同 → 不需要改写
            if (rewritten.equals(originalQuery.trim())) {
                log.debug("LLM 查询改写结果与原查询相同，跳过: \"{}\"", safePreview(originalQuery));
                return null;
            }

            // 改为 DEBUG：生产环境不输出用户查询原文，避免泄露 PII/密钥
            log.debug("LLM 查询改写: \"{}\" → \"{}\"", safePreview(originalQuery), rewritten);
            return rewritten;

        } catch (Exception e) {
            log.warn("LLM 查询改写失败，回退到原查询: safePreview={}", safePreview(originalQuery));
            return null;
        }
    }

    /**
     * 检测是否需要 LLM 改写。
     *
     * 条件（满足任一即触发）：
     * 1. 短查询：中文 ≤ 4 字
     * 2. 口语特征：包含非正式口语词
     */
    boolean needsLlmRewrite(String query) {
        // 条件 1：短查询（中文 ≤ 4 个字符，排除纯英文短查询如"VPN"）
        String stripped = query.strip();
        // 计算中文字符数
        long chineseChars = stripped.codePoints()
                .filter(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN)
                .count();
        if (chineseChars > 0 && chineseChars <= VAGUE_LENGTH_THRESHOLD) {
            return true;
        }

        // 条件 2：包含口语特征词
        for (String marker : COLLOQUIAL_MARKERS) {
            if (stripped.contains(marker)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 启动时加载 system prompt，I/O 失败时用内置兜底。
     */
    private String loadSystemPrompt(Resource resource) {
        try {
            return new String(resource.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("查询改写 Prompt 模板加载失败，使用内置默认模板: {}", e.getMessage());
            return "你是一个搜索查询优化器。将用户的原始查询改写为更适合企业知识库检索的规范化查询。"
                    + "如果查询很短很模糊，扩展为可能相关的搜索主题词。"
                    + "如果查询包含口语/非正式表达，转换为正式技术术语。"
                    + "如果查询本身已经很规范，直接原样返回。"
                    + "只返回改写后的查询文本，不要任何解释。";
        }
    }

    /**
     * 日志安全截断：防止用户查询中的 PII/密钥被明文记录到日志。
     * 超过 30 字符时截断并加省略标记。
     */
    private static String safePreview(String text) {
        if (text == null) return "null";
        return text.length() <= 30 ? text : text.substring(0, 30) + "…";
    }
}
