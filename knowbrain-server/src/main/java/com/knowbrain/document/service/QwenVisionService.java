package com.knowbrain.document.service;

import com.knowbrain.common.GlobalExceptionHandler.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Qwen-VL 视觉模型服务 — 文档页面 → Markdown（表格/标题/列表结构保留）
 *
 * <p>通过 DashScope 原生多模态 API 调用 qwen-vl-max 模型，将 PDF 渲染页面转为
 * 结构化 Markdown。支持文本型 PDF、扫描件 PDF、含表格/图表的复杂排版。
 *
 * <p>API 协议：DashScope 原生多模态（非 OpenAI 兼容模式，兼容模式不支持 VL 模型）
 */
@Slf4j
@Service
public class QwenVisionService {

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${qwen-vl.model:qwen-vl-max}")
    private String model;

    private final RestTemplate restTemplate;

    private static final int MAX_RETRIES = 1;
    private static final int TIMEOUT_MS = 60_000;

    /** DashScope 原生多模态 API 端点 */
    private static final String API_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";

    public QwenVisionService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT_MS);
        factory.setReadTimeout(TIMEOUT_MS);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 对单张图片调用 Qwen-VL 提取文字
     */
    public String extractText(byte[] imageBytes, int pageIndex) {
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                return doExtract(base64Image);
            } catch (Exception e) {
                if (attempt < MAX_RETRIES) {
                    log.warn("Qwen-VL 第 {} 页第 {} 次调用失败，重试中: {}", pageIndex, attempt + 1, e.getMessage());
                } else {
                    log.error("Qwen-VL 第 {} 页提取失败: {}", pageIndex, e.getMessage());
                    return "";
                }
            }
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private String doExtract(String base64Image) {
        // 构造 DashScope 原生多模态 API 请求体
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);

        Map<String, Object> input = new LinkedHashMap<>();
        Map<String, Object> userMsg = new LinkedHashMap<>();
        userMsg.put("role", "user");

        List<Map<String, Object>> content = new ArrayList<>();

        // 图片（原生格式：{"image": "data:..."} 或 {"image": "http://..."}）
        content.add(Map.of("image", "data:image/png;base64," + base64Image));

        // 文字提示 — 文档 → Markdown（表格/标题/列表结构保留）
        content.add(Map.of("text",
                "将文档页面转为 Markdown 格式，保持原文结构和阅读顺序。" +
                "表格使用 | 列1 | 列2 | 格式，" +
                "标题使用 # / ## / ### 标记层级，" +
                "列表使用 - 或 1. 格式。" +
                "不要添加任何说明或注释。如果页面中没有文字内容，请回复「无文字内容」。"));
        userMsg.put("content", content);
        input.put("messages", List.of(userMsg));
        body.put("input", input);

        // 调用 API
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> responseEntity = restTemplate.postForEntity(
                API_URL, request, Map.class);

        Map<String, Object> response = responseEntity.getBody();
        if (response == null) {
            throw new BizException(502, "Qwen-VL API 返回空响应");
        }

        // DashScope 原生响应格式：
        // output.choices[0].message.content = [{"text": "..."}, ...]
        Map<String, Object> output = (Map<String, Object>) response.get("output");
        if (output == null) {
            // 可能有错误信息
            String errorMsg = Objects.toString(response.get("message"),
                    Objects.toString(response.get("code"), "unknown"));
            log.warn("Qwen-VL API 错误: {}", errorMsg);
            throw new BizException(502, "Qwen-VL API 错误: " + errorMsg);
        }

        List<Map<String, Object>> choices = (List<Map<String, Object>>) output.get("choices");
        if (choices == null || choices.isEmpty()) {
            log.warn("Qwen-VL 响应无 choices, output keys: {}", output.keySet());
            throw new BizException(502, "Qwen-VL API 返回无 choices");
        }

        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null) {
            throw new BizException(502, "Qwen-VL API 返回无 message");
        }

        // content 是数组：[{"text": "..."}]
        Object contentObj = message.get("content");
        if (contentObj instanceof List<?> parts) {
            StringBuilder sb = new StringBuilder();
            for (Object part : parts) {
                if (part instanceof Map<?, ?> m && m.get("text") instanceof String s) {
                    sb.append(s);
                }
            }
            String result = sb.toString().trim();
            if (!result.isEmpty()) return result;
        } else if (contentObj instanceof String s) {
            // 兼容纯文本返回
            if (!s.isBlank()) return s.trim();
        }

        log.warn("Qwen-VL content 为空, message keys: {}", message.keySet());
        return "";
    }
}
