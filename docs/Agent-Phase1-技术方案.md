# KnowBrain Agent Phase 1 — 检索智能体技术方案

> 版本：V2.0 | 日期：2026-07-14 | 重写：从 SQL 工具方向转为多步检索方向

---

## 目录

- [一、为什么做检索智能体](#一为什么做检索智能体)
- [二、总体架构](#二总体架构)
- [三、searchKnowledge 工具设计](#三searchknowledge-工具设计)
- [四、Prompt 设计](#四prompt-设计)
- [五、反思机制](#五反思机制)
- [六、Fast Path / Agentic Path 路由](#六fast-path--agentic-path-路由)
- [七、集成实现](#七集成实现)
- [八、配置设计](#八配置设计)
- [九、测试策略](#九测试策略)
- [十、文件变更清单](#十文件变更清单)
- [十一、风险与对策](#十一风险与对策)
- [附录：后续演进](#附录后续演进)

---

## 一、为什么做检索智能体

### 1.1 当前 RAG 的隐性瓶颈

现有管线假设**一次检索就能找到所有需要的信息**：

```
用户问题 → QueryPreprocessor → HybridSearch → BuildPrompt → LLM生成 → 返回
                │                   │
                ▼                   ▼
          一次改写              一次搜索（top-K 个片段）
```

当这个假设不成立时，系统静默失败：

| 失败场景 | 根因 | 当前表现 | 发生频率 |
|---------|------|---------|:--:|
| **对比问题**：「全职和外包远程办公政策有什么区别」 | 检索偏向「全职」，外包信息排在 top-10 之外被截断 | 回答偏向全职，外包只字不提 | **高** |
| **多跳问题**：「VPN 连不上影响远程桌面怎么办」 | 单次检索难以同时覆盖 VPN 排障 + 远程桌面配置 | 只回答 VPN 或只回答远程桌面 | **高** |
| **不完整检索**：「年会请假流程是什么」 | 检索到《考勤制度》但没有《年会管理制度》 | 答非所问或用考勤制度硬套 | 中 |
| **歧义问题**：「密码忘了」 | 不知道是 WiFi 密码还是邮箱密码还是 VPN 密码 | 默认搜到哪个算哪个，可能全错 | 中 |

### 1.2 Agent 怎么解决

```
当前（一枪流）：「全职和外包远程办公区别」
  → search("全职 外包 远程办公 区别")
  → top-5 全是全职相关内容，外包信息排在 #12
  → LLM 只看到全职，回答残缺

Agent（多步流）：
  → LLM 分析："这需要对比两类人群，分别搜索"
  → Step 1: searchKnowledge("全职员工 远程办公政策")
  → Step 2: searchKnowledge("外包人员 远程办公政策")
  → LLM 拿到双方信息，完整对比回答
```

**Agent 的价值不在调用新工具，而在掌握检索策略。**

### 1.3 边界声明

**Phase 1 做什么**：让 LLM 自主决定「搜什么、搜几次、搜完怎么判断够不够」。

**Phase 1 不做什么**：
- ❌ 不调用外部 API（无 SQL、无 HTTP 请求）
- ❌ 不修改现有检索算法（HybridSearchService 不动）
- ❌ 不新增数据库查询
- ❌ 不引入新依赖（Spring AI Function Calling 已在 classpath）

---

## 二、总体架构

### 2.1 双路径模型

```
                         用户问题
                             │
                    ┌────────▼────────┐
                    │ QueryPreprocessor │  ← 不变
                    └────────┬────────┘
                             │
                    ┌────────▼────────┐
                    │   FAQ 匹配？     │  ← 不变，命中直接返回
                    └───┬────────┬────┘
                        │ 命中    │ 未命中
                        ▼         ▼
                  直接返回    ┌──────────────┐
                             │ Agent 管线    │  ← 新增
                             │              │
                             │ LLM 分析问题  │
                             │   ├─ 简单 → 一次搜索
                             │   ├─ 对比 → 多次搜索
                             │   ├─ 歧义 → 反问用户
                             │   └─ 不够 → 换措辞重试
                             │              │
                             │ 生成最终答案  │
                             └──────────────┘
```

### 2.2 Agent 循环（Spring AI 自动处理）

```
chatClient.prompt()
  .system(agentPrompt)     ← "你是企业知识库助手，有 searchKnowledge 工具"
  .user(question)
  .functions(searchKnowledge)
  .call()

Spring AI 框架自动处理以下循环：
  ┌─────────────────────────────────────────┐
  │                                         │
  │  LLM 思考 → 决定是否调用工具             │
  │    ├─ 不调用 → 直接生成答案 → 返回       │
  │    └─ 调用 searchKnowledge("query")      │
  │         → 框架执行 Java 方法              │
  │         → 结果返回 LLM                   │
  │         → LLM 再次思考                   │
  │              ├─ 够了 → 生成答案           │
  │              └─ 不够 → 再次调用工具       │
  │                                         │
  └─────────────────────────────────────────┘
```

对业务代码透明——我们只需要注册一个 FunctionCallback，Spring AI 处理循环。

### 2.3 与现有管线的关系

| 步骤 | 变化 |
|------|------|
| 0. Redis 缓存检查 | **不变** |
| 1. QueryPreprocessor | **不变**（Glossary 改写 + FAQ 匹配继续生效） |
| 2. FAQ 短路 | **不变**（命中直接返回，零延迟） |
| 3. HybridSearch | **不再固定调用**——被包装为 searchKnowledge 工具，由 LLM 决定何时调用 |
| 4. BuildPrompt | **改为 Agent 专用 Prompt**（含搜索策略 + 反思规则） |
| 5. LLM 调用 | 从 `.stream().content()` 改为 `.functions(searchKnowledge).call().content()` |
| 6. BuildResponse | 新增：汇总所有 searchKnowledge 返回的文档作为 sources |

---

## 三、searchKnowledge 工具设计

### 3.1 唯一工具

整个 Phase 1 只注册**一个** FunctionCallback：

```
函数名: searchKnowledge
入参:   query (String) — 搜索关键词
出参:   results (List<SearchResultItem>) + totalHits (int)
```

### 3.2 Java 实现

```java
package com.knowbrain.agent;

import com.knowbrain.retrieval.engine.HybridSearchService;
import com.knowbrain.retrieval.engine.SearchResult;

import java.util.*;
import java.util.function.Function;

/**
 * 检索智能体的唯一工具 —— 包装 HybridSearchService。
 * 每次请求创建新实例，携带当前请求的 spaceIds/category。
 */
public class SearchKnowledgeTool implements Function<
        SearchKnowledgeTool.Request, SearchKnowledgeTool.Response> {

    // ---- 入参 / 出参记录 ----

    public record Request(String query) {
        public Request {
            if (query == null || query.isBlank()) {
                throw new IllegalArgumentException("query 不能为空");
            }
            if (query.length() > 500) {
                throw new IllegalArgumentException("query 过长（最多 500 字符）");
            }
        }
    }

    public record Response(
        List<SearchResultItem> results,
        int totalHits
    ) {}

    public record SearchResultItem(
        String title,
        String text,
        double score
    ) {}

    // ---- 实例字段 ----

    private final HybridSearchService searchService;
    private final List<Long> spaceIds;
    private final String category;

    /** 跨多次调用的累计结果（用于最终组装 sources） */
    private final Set<Long> seenDocIds = new HashSet<>();

    public SearchKnowledgeTool(HybridSearchService searchService,
                               List<Long> spaceIds, String category) {
        this.searchService = searchService;
        this.spaceIds = (spaceIds != null) ? spaceIds : List.of();
        this.category = (category != null && !category.isBlank()) ? category : null;
    }

    @Override
    public Response apply(Request request) {
        // 执行检索（复用现有混合检索，保留空间过滤 + 自适应 TopK）
        List<SearchResult> hits = searchService.search(
            request.query(),
            adaptiveTopK(request.query()),
            spaceIds,
            category
        );

        List<SearchResultItem> items = new ArrayList<>();
        for (SearchResult sr : hits) {
            items.add(new SearchResultItem(
                sr.getTitle(),
                truncate(sr.getContent(), 600),   // 截断内容，避免 Token 爆炸
                sr.getScore()
            ));
            seenDocIds.add(sr.getDocumentId());
        }

        return new Response(items, items.size());
    }

    /** 返回本次所有检索中出现过的文档 ID 集合 */
    public Set<Long> getSeenDocumentIds() {
        return Collections.unmodifiableSet(seenDocIds);
    }

    // ---- 内部方法 ----

    /**
     * 自适应 TopK：简单短词给 5 个，复杂长句给 10 个。
     * LLM 生成的搜索词通常较短，统一 8 个较为合理。
     */
    private int adaptiveTopK(String query) {
        return query.length() > 30 ? 10 : 8;
    }

    private String truncate(String text, int maxChars) {
        if (text == null) return "";
        return text.length() <= maxChars ? text : text.substring(0, maxChars) + "…";
    }
}
```

### 3.3 设计要点

**为什么 per-request new 而不是 @Bean**：

工具需要知道当前请求的 `spaceIds` 和 `category`（权限过滤），这些是请求级数据。Spring Bean 是单例，无法携带。在 `RAGServiceImpl.chat()` 中每次 new 一个，注入当前的 spaceIds。

**为什么截断内容到 600 字符**：

完整 chunk 可能 1000+ 字符，多次搜索后上下文会膨胀。600 字符足以让 LLM 判断相关性，最终生成答案时才需要完整上下文（由 BuildPrompt 步骤补全）。

**为什么累计文档 ID**：

Agent 可能搜索 3 次，每次结果不同。最终 ChatResponse 的 sources 需要汇总全部 3 次的去重文档列表，而非仅最后一次。

---

## 四、Prompt 设计

### 4.1 新增文件

`resources/prompts/agent-system.txt`：

```markdown
你是企业智能知识库助手。你的知识来源于企业内部的文档和制度，你必须基于检索结果回答，不得凭空编造。

## 核心能力

你拥有 searchKnowledge 工具，用于检索企业内部文档。你需要根据问题类型制定检索策略。

## 检索策略

根据问题类型选择策略：

### 类型 1：简单事实问题（"VPN怎么配置""年假有几天"）
→ 调用 searchKnowledge 一次，直接回答。

### 类型 2：对比问题（"A和B有什么区别""全职和外包的远程政策"）
→ 分别搜索每个对比对象，确保双方信息完整后再进行对比。
→ 示例：用户问"全职和外包远程办公政策区别"
  · searchKnowledge("全职员工 远程办公 政策")
  · searchKnowledge("外包人员 远程办公 政策")
→ 拿到双方结果后，逐项对比，不得遗漏任一方。

### 类型 3：多步问题（"VPN连不上导致远程桌面无法使用"）
→ 第一步搜索主要问题，若结果中没有涵盖所有相关方面，则补充搜索缺失部分。
→ 最终答案应覆盖问题的所有子话题。

### 类型 4：歧义问题（"密码忘了"——WiFi密码？邮箱密码？VPN密码？）
→ 不要猜测。直接反问用户："您是指哪方面的密码？WiFi密码、企业邮箱密码、还是VPN密码？"
→ 用户明确后再搜索回答。

## 反思规则

每次 searchKnowledge 返回结果后，你必须自我检查：

1. **结果是否相关？** 看 title 和 text 是否在回答用户的问题。
   → 如果明显不相关（如问 VPN 却返回打印机内容），换一种措辞重新搜索。
2. **信息是否完整？** 对比问题是否两边都有结果？多步问题是否各方面都覆盖？
   → 如果有缺失，针对缺失部分补充搜索。
3. **结果是否为空？** 
   → 换一种更通用的措辞再试一次。仍为空则告知用户未找到。

## 约束

- 最多调用 searchKnowledge 3 次（避免无限循环）
- 检索不到信息时，诚实告知"未找到相关文档"，不得编造
- 每次搜索使用最精确的关键词，用多个相关词而非完整句子
- 答案必须基于检索到的文档内容，引用时标注文档标题
```

### 4.2 原有 Prompt 的处理

原有 `rag-system.txt` 保留不动。Agent 启用时用 `agent-system.txt` 替代，不启用时用原来的。两者不混合。

---

## 五、反思机制

### 5.1 设计思路

不是单独的代码模块，而是**通过 Prompt 让 LLM 内化反思行为**：

```
LLM 收到 searchKnowledge 结果
  │
  ├─→ 自检：「这些结果能完整回答用户问题吗？」
  │     ├─ 能 → 生成答案
  │     ├─ 部分能（缺某些方面）→ 补充搜索 → 合并 → 生成
  │     └─ 不能（完全不相关/为空）→ 换措辞 → 重试
  │
  └─→ 3 次后仍不够 → 诚实告知用户
```

对比 Glean 的做法：

| 维度 | Glean | KnowBrain Phase 1 |
|------|-------|-------------------|
| 实现方式 | 独立 Reflection 模型 | Prompt 驱动的 LLM 内省 |
| 延迟 | 两次模型调用 | 单次推理中的思考步骤 |
| 成本 | 双倍 Token | 仅增加 reasoning tokens |
| 适合规模 | 百万 DAU | MVP 阶段 |

Glean 的独立 Reflection 是在规模化之后才需要的优化。当前阶段用 Prompt 内化足够。

### 5.2 效果预期

| 场景 | 无反思 | 有反思 |
|------|--------|--------|
| 「全职和外包远程区别」 | 只答全职，不提外包 | 分别搜两边，完整对比 |
| 「VPN连不上怎么办」但搜到了打印机文档 | 硬着头皮回答打印 | 发现不相关，换词重搜 |
| 「年会请假」搜到《考勤制度》但缺少《年会管理制度》 | 用考勤制度回答年假 | 发现没找到年会相关文档，告知用户「未找到年会制度相关文档」而非硬套 |

---

## 六、Fast Path / Agentic Path 路由

### 6.1 设计

当前设计没有显式的「分类器」路由。FAQ 是唯一的 Fast Path：

```
用户问题
  → FAQ 命中（score ≥ 2）→ Fast Path：直接返回预设答案，零 LLM 延迟
  → FAQ 未命中 → Agentic Path：进入 Agent 循环
```

### 6.2 为什么不需要复杂度分类器

Agent 模式下，LLM 自己对简单问题也只会搜一次就回答。额外分类器带来的收益（省一次工具调用决策）远小于它的成本（增加复杂度和误判风险）。

```
简单问题「年假有几天」：
  LLM: "简单事实问题" → searchKnowledge("年假 天数") → 拿到结果 → 回答
  额外开销：一次工具调用决策（约 500ms）

对比问题「全职和外包远程区别」：
  LLM: "需要分别搜索" → searchKnowledge("全职 远程") → searchKnowledge("外包 远程") → 对比
  额外开销：两次搜索 + 一次合成推理
```

简单问题多花 500ms 可以接受，没必要引入分类器增加复杂度。

### 6.3 唯一的路由优化：FAQ 短路

FAQ 短路保留在 Agent 之前。如果 FAQ 命中，直接返回，完全不进入 Agent 循环。这条路径零延迟、零 LLM 成本。

---

## 七、集成实现

### 7.1 RAGServiceImpl 改造

改动集中在 `chat()` 方法的步骤 5（LLM 调用）。以下标注了具体变更点：

```java
// ==================== 新增字段 ====================

private final boolean agentEnabled;
private final Environment environment;

// ==================== 构造函数新增参数 ====================

public RAGServiceImpl(
        HybridSearchService hybridSearchService,
        ChatClient.Builder chatClientBuilder,
        QueryPreprocessor preprocessor,
        RAGCacheService cacheService,
        SearchLogMapper searchLogMapper,
        RequestContext requestContext,
        RAGMetrics metrics,
        Environment environment                          // ← 新增
) {
    // ... 现有赋值 ...
    this.environment = environment;
    this.agentEnabled = Boolean.parseBoolean(
        environment.getProperty("rag.agent.enabled", "false"));
}

// ==================== chat() 方法改动 ====================

@Override
public ChatResponse chat(String question, List<Long> spaceIds,
                         List<Map<String, String>> history,
                         String category, boolean skipFaq) {

    // === 步骤 0-2 完全不变 ===
    // 缓存检查、QueryPreprocessor、FAQ 短路 保持不变

    // === 步骤 3：检索（Agent 模式下跳过固定检索）===

    String prompt;
    List<SearchResult> sources;

    if (agentEnabled && !skipFaq) {
        // ─────── Agent 路径 ───────
        return agentChat(question, spaceIds, history, category);
    } else {
        // ─────── 原有路径（不变）───
        sources = hybridSearchService.search(processed, adaptiveTopK(question),
                                              spaceIds, category);
        prompt = buildPrompt(context.toString(), question, history);
        answer = callLLM(prompt);
        return buildResponse(answer, sources);
    }
}

// ==================== 新增方法 ====================

private ChatResponse agentChat(String question, List<Long> spaceIds,
                               List<Map<String, String>> history,
                               String category) {
    // 1. 创建本次请求的检索工具（携带 spaceIds）
    var searchTool = new SearchKnowledgeTool(
        hybridSearchService, spaceIds, category);

    var callback = FunctionCallback.builder()
        .function("searchKnowledge", searchTool)
        .description("检索企业内部文档。输入搜索关键词，返回相关文档片段列表（含标题、内容、相关度评分）。适用场景：需要从公司文档、制度、手册中查找信息时使用。")
        .build();

    // 2. 加载 Agent 专用 Prompt
    String systemPrompt = loadTemplate("classpath:prompts/agent-system.txt");
    String userPrompt = buildUserPrompt(question, history);

    // 3. 调用 LLM（带 Function Calling）
    String answer;
    long t0 = System.currentTimeMillis();
    try {
        answer = chatClient.prompt()
            .system(systemPrompt)
            .user(userPrompt)
            .functions(callback)
            .call()
            .content();
    } catch (Exception e) {
        log.warn("Agent 调用失败，降级到标准 RAG: {}", e.getMessage());
        // 降级：走标准路径
        return fallbackChat(question, spaceIds, history, category);
    }

    // 4. 汇总 sources（Agent 可能搜索了多次）
    List<SearchResult> allSources = collectSources(searchTool);

    // 5. 置信度评估
    String confidence = allSources.isEmpty() ? "low"
        : allSources.size() >= 3 ? "high" : "medium";

    // 6. 组装响应
    var sourceInfos = allSources.stream()
        .map(s -> new ChatResponse.SourceInfo(
            s.getTitle(), s.getDocumentId(), s.getChunkIndex(), s.getContent()))
        .toList();

    return new ChatResponse(answer, sourceInfos,
        allSources.isEmpty(), confidence);
}

/**
 * 从 SearchKnowledgeTool 中提取所有搜索中出现的文档
 */
private List<SearchResult> collectSources(SearchKnowledgeTool tool) {
    Set<Long> docIds = tool.getSeenDocumentIds();
    if (docIds.isEmpty()) return List.of();

    // 从 HybridSearchService 缓存 / DB 中获取完整文档信息
    // 或直接在 SearchKnowledgeTool 中累积完整的 SearchResult 对象
    // 具体实现取决于 SearchKnowledgeTool 的内部缓存设计
    return tool.getCachedResults();
}
```

### 7.2 流式接口处理

`chatStream()` 暂不启用 Agent。Spring AI 1.0.0-M4 的流式 Function Calling 返回 `Flux<ChatResponse>`，与现有 SSE 模型兼容性待验证。Phase 2 再处理。

```java
// chatStream() → 统一不走 Agent，保持现有行为
```

### 7.3 SearchKnowledgeTool 优化：缓存完整结果

上一版的 `SearchKnowledgeTool` 只存储了 `docIds`。需要改为存储完整 `SearchResult`：

```java
public class SearchKnowledgeTool implements Function<...> {

    // 新增：跨调用的完整结果缓存
    private final Map<Long, SearchResult> resultCache = new LinkedHashMap<>();

    @Override
    public Response apply(Request request) {
        List<SearchResult> hits = searchService.search(...);

        for (SearchResult sr : hits) {
            seenDocIds.add(sr.getDocumentId());
            resultCache.putIfAbsent(sr.getDocumentId(), sr);
        }

        // ... 返回 Response items
    }

    /** 供 RAGServiceImpl 组装最终 sources */
    public List<SearchResult> getCachedResults() {
        return new ArrayList<>(resultCache.values());
    }
}
```

---

## 八、配置设计

### 8.1 application.yml

```yaml
rag:
  agent:
    enabled: ${RAG_AGENT_ENABLED:false}

spring:
  ai:
    openai:
      chat:
        options:
          max-tool-calls: 3          # Agent 最多 3 轮工具调用
```

### 8.2 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `RAG_AGENT_ENABLED` | `false` | 启用检索智能体模式 |

### 8.3 Profile 建议

```yaml
# application-dev.yml
rag:
  agent:
    enabled: true          # 开发环境默认开启，方便调试
```

```yaml
# application-prod.yml
rag:
  agent:
    enabled: ${RAG_AGENT_ENABLED:false}   # 生产环境默认关闭，渐进式上线
```

---

## 九、测试策略

### 9.1 单元测试

| 测试对象 | 测试点 |
|---------|--------|
| `SearchKnowledgeTool` | 正常搜索返回结果、空结果处理、query 长度校验、结果截断、文档 ID 累计 |
| `SearchKnowledgeTool.adaptiveTopK` | 短 query → 8，长 query → 10 |

### 9.2 集成测试

| 场景 | 预期 Agent 行为 |
|------|---------------|
| **简单事实**：「年假有几天」 | searchKnowledge 一次 → 直接回答 |
| **对比问题**：「全职和外包远程办公区别」 | searchKnowledge("全职 远程") → searchKnowledge("外包 远程") → 对比回答 |
| **歧义问题**：「密码忘了」 | 不搜索，反问用户「您指哪种密码？」 |
| **无结果**：「火星移民政策」 | 第一次无结果 → 换词再试 → 仍无结果 → 告知用户 |
| **不相关结果**：问 VPN 但检索返回打印机文档 | 发现不相关 → 换措辞重搜 |
| **FAQ 命中**：「打卡失败怎么办」 | FAQ 短路返回（不走 Agent） |
| **Agent 关闭**：`rag.agent.enabled=false` | 行为完全不变，回归全绿 |
| **Agent 调用失败**：LLM 超时 | 降级到标准 RAG 管线 |

### 9.3 评测回归

```bash
# 启用 Agent 后跑现有评测数据集，对比分数
# 预期：简单问题分数持平，对比/多跳问题分数显著提升
curl -X POST /api/v1/admin/evaluation/runs -d '{"datasetId": 1}'
```

增加评测数据集时，应包含对比型和多跳型问题，验证 Agent 在复杂场景下的提升。

---

## 十、文件变更清单

### 新增文件

| 文件 | 说明 | 行数 |
|------|------|:----:|
| `agent/SearchKnowledgeTool.java` | 检索工具实现 | ~80 |
| `resources/prompts/agent-system.txt` | Agent 系统 Prompt | ~50 |

### 修改文件

| 文件 | 变更内容 | 行数 |
|------|---------|:----:|
| `retrieval/engine/RAGServiceImpl.java` | 注入 Environment + 新增 agentChat()/fallbackChat() + chat() 分叉 | +80 |
| `resources/application.yml` | 新增 `rag.agent.enabled` + `max-tool-calls` | +5 |
| `resources/application-dev.yml` | 开发环境默认开启 Agent | +4 |

### 不动文件

| 文件 | 原因 |
|------|------|
| `RAGService.java` | 接口不变，Agent 是实现细节 |
| `RAGController.java` | API 不变 |
| `ChatResponse.java` | 返回结构不变 |
| `HybridSearchService.java` | 被包装，不动 |
| `QueryPreprocessor.java` | 预处理逻辑不变 |
| `RAGCacheService.java` | 缓存逻辑不变 |
| `pom.xml` | 零新依赖 |

**总计：2 新文件 + 3 修改文件，~150 行业务代码 + ~50 行 Prompt。**

---

## 十一、风险与对策

| 风险 | 概率 | 影响 | 对策 |
|------|:----:|:----:|------|
| **Agent 延迟增加**（多轮搜索） | 高 | 中 | 默认关闭；FAQ 短路不受影响；`max-tool-calls=3` 限制 |
| **Token 消耗增加** | 高 | 低 | 内容截断 600 字符；对比评测验证 Token 增幅 |
| **LLM 不遵循搜索策略**（Prompt 注入类对抗） | 低 | 中 | max-tool-calls 兜底；降级到标准 RAG |
| **Spring AI M4 Function Calling 稳定性** | 中 | 中 | 默认关闭；try-catch 降级保护 |
| **评测分数短期波动** | 中 | 低 | 评测时关闭 Agent；A/B 对比验证 |
| **检索结果内容截断导致错失关键信息** | 低 | 中 | 600 字符对匹配判断足够；最终生成时用完整 chunk |

---

## 附录：后续演进

Phase 1 交付后的方向，详见 [Agent技术方案.md](Agent技术方案.md)：

```
Phase 1（本次）── 检索智能体
  ├── 多步检索 + 反思（LLM 内化）
  ├── 歧义澄清
  └── 零新依赖

Phase 2 ── 场景智能 + MCP
  ├── Agent Skills（基于场景种子数据的角色 Prompt）
  ├── 场景路由（IT vs HR 自动分流）
  └── MCP Server（对外暴露 KnowBrain 检索能力）

Phase 3 ── 工作流 + 外部接入
  ├── 检索策略配置（管理员可调）
  └── MCP Client（接入外部数据源）

明确不做：
  ✂️ 知识图谱 — 混合检索 + Agent 多步检索已覆盖
  ✂️ 多 Agent 协作 — 场景路由 + 单 Agent 更实际
  ✂️ 工作流编排 — 定位不匹配（KnowBrain 不是通用 AI 应用平台）
  ✂️ Text-to-SQL — 仪表盘已覆盖统计需求
```
