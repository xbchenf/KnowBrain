# KnowBrain — RAG 评测体系技术方案

> 版本：V1.0 | 日期：2026-07-12 | 状态：待实施

---

## 一、背景与目标

### 1.1 当前问题

KnowBrain 目前没有任何自动化评测能力。以下问题无法回答：

- 「当前检索准确率多少？」
- 「改分块策略后，答案质量变好还是变差？」
- 「升级 Embedding 模型后，召回率提升多少？」
- 「新 Prompt 模板比旧版好在哪里？」

### 1.2 目标

构建一套 **RAG 评测体系**，支持：

1. **测试数据集管理**：管理员创建数据集，添加 Q&A 对（含预期答案/预期文档）
2. **一键运行评测**：选中数据集 → 逐题调用 RAG 管线 → LLM-as-Judge 自动打分
3. **三项核心指标**：Faithfulness（忠实度）、Answer Relevance（答案相关性）、Context Recall（上下文召回率）
4. **历史趋势追踪**：对比多次运行结果，量化每次变更的效果

### 1.3 优先级

| 维度 | 评级 |
|------|:----:|
| 商业化紧迫度 | 🟡 P1（产品就绪阶段必须完成） |
| 预估工时 | 2-3 周 |
| 依赖 | 无（可直接开始） |

---

## 二、评测指标体系

### 2.1 Faithfulness（忠实度，0-1）

**定义**：AI 回答中的每个事实性陈述，是否都能在检索到的参考资料中找到明确依据。

**为什么重要**：这是 RAG 系统最核心的指标。忠实度低 = 幻觉（hallucination），是企业场景绝对不能接受的。

**评测方式**：LLM-as-Judge — 将「问题 + 检索上下文 + AI 回答」提交给 LLM，让其逐条判断回答中的陈述是否有上下文依据。

```
评分标准：
- 1.0: 回答中的所有事实性陈述都能在参考资料中找到明确依据
- 0.7-0.9: 大部分陈述有依据，个别推断合理
- 0.4-0.6: 约一半陈述有依据，存在无依据的推断
- 0.1-0.3: 大部分陈述在参考资料中找不到依据
- 0.0: 回答与参考资料完全无关或完全编造
```

### 2.2 Answer Relevance（答案相关性，0-1）

**定义**：AI 回答是否直接、完整地回应用户问题。

**为什么重要**：检索对了但回答偏了 = 用户体验差。高声望系统 = 高检索质量 + 高答案相关性。

**评测方式**：LLM-as-Judge — 将「问题 + AI 回答」提交给 LLM，判断回答是否切题。

```
评分标准：
- 1.0: 回答完全切题，直接回应用户问题的核心诉求
- 0.7-0.9: 基本切题，但略有偏差或不够完整
- 0.4-0.6: 部分相关，但遗漏了关键信息或包含大量无关内容
- 0.1-0.3: 勉强沾边，大部分内容不相关
- 0.0: 完全答非所问
```

### 2.3 Context Recall（上下文召回率，0-1）

**定义**：检索到的参考资料中，是否包含了回答用户问题所需的完整信息。

**为什么重要**：检索是 RAG 的上游瓶颈——检索不到，生成一定不好。这个指标独立衡量检索质量。

**评测方式**：LLM-as-Judge — 将「问题 + 检索到的上下文」提交给 LLM，判断信息完整性。

```
评分标准：
- 1.0: 参考资料包含了回答问题的全部必要信息
- 0.7-0.9: 包含了大部分必要信息，缺少少量细节
- 0.4-0.6: 包含了部分信息，但关键信息缺失
- 0.1-0.3: 仅包含少量相关信息，无法有效回答问题
- 0.0: 参考资料与问题完全无关
```

### 2.4 辅助指标

| 指标 | 计算方式 | 用途 |
|------|---------|------|
| 平均延迟 | 从查询到返回答案的耗时 | 性能回归检测 |
| 降级率 | fallback 次数 / 总问题数 | 系统健康度 |
| FAQ 命中率 | FAQ 匹配次数 / 总问题数 | 短路效率 |
| 置信度分布 | high/medium/low 占比 | 质量一致性 |

### 2.5 维度选型说明

#### 2.5.1 RAGAS 标准四维度分析

[RAGAS](https://docs.ragas.io/)（RAG Assessment）是业界最广泛使用的 RAG 评测框架，其原始论文定义了四个核心指标：

| RAGAS 指标 | 测量目标 | 评测方式 | 是否需要人工标注 |
|:---------:|---------|---------|:---:|
| **Faithfulness** | 答案是否忠实于检索到的上下文？（反幻觉，更准确的中文叫法是"忠实度"） | LLM 将答案拆成原子陈述，逐一在上下文中查证 | ❌ 不需要 |
| **Answer Relevancy** | 答案是否切题？ | LLM 基于答案逆向生成问题，计算与原始问题的语义相似度 | ❌ 不需要 |
| **Context Recall** | 检索上下文是否覆盖了回答所需的全部信息？ | LLM 基于上下文提取关键信息点，判断是否足以回答问题 | ❌ 不需要 |
| **Context Precision** | 相关的检索片段是否排在前面？（排序质量） | 计算 `relevant_chunks_in_top_k / k` | ✅ **需要**逐 chunk 标注相关性 |

#### 2.5.2 MVP 阶段的三维度选择

本方案选择 **Faithfulness + Answer Relevance + Context Recall** 作为 MVP 指标，**暂不纳入 Context Precision**。理由如下：

**1. 零标注成本（最关键差异）**

Context Precision 的计算依赖 chunk 级别的相关性标注——你必须先知道每个问题的检索结果中「哪些 chunk 是相关的、哪些不是」。对于一个 50 题的数据集，HybridSearch 每问返回 5 个片段，就需要 **250 条人工标注**。而 Faithfulness、Answer Relevance、Context Recall 全部由 LLM-as-Judge 自动完成，管理员只需提供「问题」文本，连「预期答案」都是可选的。

这种标注成本差异决定了 MVP 阶段的优先级：**先跑通零标注的三维度，后续再建设标注基础设施补上 Context Precision**。

**2. 三维度已覆盖 RAG 管线的三个关键节点**

```
检索阶段              生成阶段              最终输出
───────              ───────              ────────
                      Context ──→ LLM ──→ Answer
                        ↑                      ↑
                        │                      │
                Context Recall           Faithfulness
                "检索到没？"             "编造了没？"
                
                                  Answer Relevance
                                  "答对题了没？"
```

- **Context Recall** → 衡量**检索质量**（上游 — 信息是否入池）
- **Faithfulness** → 衡量**生成质量**（下游 — 是否忠实于上下文，杜绝幻觉）
- **Answer Relevance** → 衡量**最终输出质量**（用户视角 — 有没有答所问）

三个指标形成一条质量链：**检索到 → 不编造 → 答对题**。对 RAG 系统来说，只要这条链没断，质量就是过关的。

**3. Context Precision 的适用场景**

Context Precision 回答的是另一个问题——「检索结果排对了顺序吗？」。这属于**检索调优**范畴，更适合以下场景时引入：

- 切换 Embedding 模型后的 A/B 对比
- 调整 RRF 融合权重（dense/sparse 配比）
- Reranker 精排效果验证

这些场景将在本方案的「八、后续扩展」中通过**检索专项评测**（recall@K / MRR / NDCG / Context Precision）统一覆盖。

#### 2.5.3 指标对比总结

| 考量 | Faithfulness | Answer Relevance | Context Recall | Context Precision |
|------|:---:|:---:|:---:|:---:|
| 衡量目标 | 反幻觉 | 是否切题 | 检索覆盖度 | 排序质量 |
| 标注成本 | 零 | 零 | 零 | 需要 chunk 级标注 |
| RAG 管线节点 | 生成阶段 | 最终输出 | 检索阶段 | 检索阶段 |
| MVP 纳入 | ✅ | ✅ | ✅ | ⏸️ 后续版本 |
| 故障示例 | 「VPN 用 3389 端口」（上下文写的是 443） | 问「VPN 怎么配」答「VPN 是虚拟专用网络的定义」 | 问「年假几天」但检索到的全是加班制度文档 | 正确答案排在第 5 位，用户没耐心往下翻 |

### 2.6 LLM-as-Judge 模型选型分析

#### 2.6.1 核心问题：评测模型和被评测模型要不要分开？

本项目中，**生成答案的 LLM**（默认 Qwen-Max）和**评测答案的 LLM** 是同一个模型。这意味着存在「既当运动员又当裁判」的潜在偏差风险。业界对此有两种做法：

| 策略 | 做法 | 适用场景 |
|------|------|---------|
| **同模型评测** | 用一个 ChatClient 既生成又评分 | MVP 快速起步，资源有限，模型能力足够强 |
| **交叉评测** | 用不同的模型作为 Judge（如 Claude 评 Qwen、GPT-4 评 DeepSeek） | 生产级评测，需要消除自我偏好偏差 |

#### 2.6.2 同模型评测的可行性分析

RAGAS 官方在其 [Metrics 文档](https://docs.ragas.io/en/latest/concepts/metrics/available_metrics/) 中指出：Faithfulness 和 Context Recall 属于**事实核查型指标**（判断依据是否存在于给定文本中），这类任务对 LLM 的「公正性」要求较低，更考验**文本理解能力**。Qwen-Max 在这类任务上的表现已被验证为可靠。

Answer Relevance 属于**语义判断型指标**，理论上存在自我偏好风险（模型可能对自己生成的答案更宽容），但可以通过以下方式缓解：

- **结构化输出约束**：Prompt 要求返回 `{"score": 0.X, "reason": "..."}` JSON，强制给出评分理由
- **评分标准锚定**：每个指标都有从 1.0 到 0.0 的五级锚定描述，降低主观偏差
- **人工抽检校准**：定期对比 LLM 评分与人工评分，计算 Judge 准确率

#### 2.6.3 实现方案：YAML 动态配置（从 MVP 即支持）

**设计原则**：Judge 模型的独立配置不是「后续扩展」，而是**从 MVP 阶段就直接实现**。用户通过 YAML 配置自由选择评测模型——可以配成和主模型相同（同模型评测），也可以配成不同（交叉评测）。

**配置设计**（`application.yml` 新增）：

```yaml
# RAG 评测配置
evaluation:
  judge:
    # 评测专用 LLM 配置（OpenAI 兼容协议）
    # 不配置时自动复用主 ChatClient（与 RAG 生成使用同一模型）
    base-url: ${EVAL_JUDGE_BASE_URL:}        # 如 https://api.deepseek.com/v1
    api-key: ${EVAL_JUDGE_API_KEY:}          # 独立 API Key
    model: ${EVAL_JUDGE_MODEL:}              # 如 deepseek-v4-pro / qwen-max / claude-sonnet-5
```

**三种典型配置场景**：

```bash
# 场景 1: 同模型评测（MVP 默认）—— 不配置任何 EVAL_JUDGE_*，自动复用主 ChatClient
# → Judge 使用 Qwen-Max（与 RAG 生成同一模型）

# 场景 2: 交叉评测（推荐生产级）—— 用 DeepSeek 评测 Qwen-Max 的生成结果
EVAL_JUDGE_BASE_URL=https://api.deepseek.com/v1
EVAL_JUDGE_API_KEY=sk-deepseek-xxx
EVAL_JUDGE_MODEL=deepseek-v4-pro

# 场景 3: 复用主 API 但不同模型 —— 同一个 DashScope 账号，用不同模型评测
EVAL_JUDGE_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode
EVAL_JUDGE_API_KEY=sk-dashscope-xxx      # 可与主 API Key 相同
EVAL_JUDGE_MODEL=qwen-max                # 可指定与主模型不同的版本
```

**核心实现**（`EvaluationJudgeConfig` + `EvaluationService`）：

```java
// === EvaluationJudgeConfig.java ===
@ConfigurationProperties(prefix = "evaluation.judge")
public class EvaluationJudgeConfig {
    private String baseUrl;   // 为空则复用主 ChatClient
    private String apiKey;
    private String model;
    // getters/setters...
}

// === EvaluationService.java ===
@Service
public class EvaluationService {

    private final ChatClient primaryClient;   // 主 ChatClient（RAG 生成用）
    private final ChatClient judgeClient;     // 评测专用 ChatClient（可独立配置）

    public EvaluationService(ChatClient.Builder primaryBuilder,
                             EvaluationJudgeConfig judgeConfig) {
        // 主 ChatClient：用于 RAG 生成（不变）
        this.primaryClient = primaryBuilder.build();

        // Judge ChatClient：根据配置决定复用还是新建
        if (isNotBlank(judgeConfig.getBaseUrl()) 
                && isNotBlank(judgeConfig.getApiKey())
                && isNotBlank(judgeConfig.getModel())) {
            // 独立 Judge 模型 → 创建独立 ChatClient
            this.judgeClient = createJudgeClient(judgeConfig);
        } else {
            // 未配置 → 复用主 ChatClient（同模型评测）
            this.judgeClient = primaryBuilder.build();
        }
    }

    private ChatClient createJudgeClient(EvaluationJudgeConfig config) {
        var api = new org.springframework.ai.openai.api.OpenAiApi(
                config.getBaseUrl(), config.getApiKey());
        var chatModel = new org.springframework.ai.openai.OpenAiChatModel(api,
                org.springframework.ai.openai.OpenAiChatOptions.builder()
                        .withModel(config.getModel())
                        .withTemperature(0.0)  // Judge 需要确定性输出
                        .build());
        return ChatClient.builder(chatModel).build();
    }

    // evaluateSingle() 中使用 judgeClient 而非 primaryClient
    private float judgeFaithfulness(String question, String answer, String context) {
        String prompt = buildJudgePrompt(FAITHFULNESS_TEMPLATE, question, answer, context);
        String response = judgeClient.prompt().user(prompt).call().content();
        return parseScore(response);
    }
}
```

**关键设计细节**：

| 设计点 | 决策 | 原因 |
|--------|------|------|
| Judge 的 temperature | 固定为 **0.0** | 评测需要确定性，不能每次分数不一样 |
| Judge 的超时 | 默认 60s（可配置） | 单次评测比生成更简单，通常更快 |
| Judge 失败重试 | **0 次**（不重试） | 评测失败记录到 `llm_eval_raw`，不阻塞整体运行 |
| 配置变更生效 | 重启生效 | 无需运行时切换 |

#### 2.6.4 渐进式升级路径

```
MVP（即日起）              生产级推荐                  深度评测（后续）
────────────────────────────────────────────────────────────────────
同模型评测                  交叉模型评测                  双轨并行
EVAL_JUDGE_* 不配置        EVAL_JUDGE_MODEL=          Python RAGAS 交叉验证
→ 复用 Qwen-Max            deepseek-v4-pro             LLM-Judge 结果 vs
                           → DeepSeek 评 Qwen           RAGAS 结果对比
```

**升级指引**：

- **MVP 先跑**：不配置 EVAL_JUDGE_*，Qwen-Max 自评，零额外成本
- **觉得自评不可信时**：配一个不同厂商的模型做 Judge（如 DeepSeek-V3/Claude），交叉验证
- **需要对外展示时**：深度评测模式，LLM-Judge + RAGAS 双轨并行，产出可信报告

---

## 三、数据模型

### 3.1 ER 图

```
┌──────────────────────────┐
│  kb_evaluation_dataset    │
│  ─────────────────────── │
│  id (PK)                  │
│  name                     │
│  description              │
│  scenario                 │──┐
│  question_count           │  │
│  create_time              │  │
│  update_time              │  │
└──────────┬───────────────┘  │
           │ 1:N              │
           ▼                  │
┌──────────────────────────┐  │
│  kb_evaluation_question   │  │
│  ─────────────────────── │  │
│  id (PK)                  │  │
│  dataset_id (FK) ─────────┘  │
│  question (TEXT)             │
│  expected_answer (TEXT)      │
│  expected_doc_ids (VARCHAR)  │
│  create_time                 │
└──────────────────────────┘

┌──────────────────────────┐
│  kb_evaluation_run        │
│  ─────────────────────── │
│  id (PK)                  │
│  dataset_id (FK)          │
│  status                   │
│  total_questions          │
│  completed_questions      │
│  avg_faithfulness         │
│  avg_relevance            │
│  avg_context_recall       │
│  avg_latency_ms           │
│  started_at               │
│  completed_at             │
│  error_message            │
│  create_time              │
└──────────┬───────────────┘
           │ 1:N
           ▼
┌──────────────────────────┐
│  kb_evaluation_result     │
│  ─────────────────────── │
│  id (PK)                  │
│  run_id (FK)              │
│  question_id (FK)         │
│  question_text (TEXT)     │
│  actual_answer (TEXT)     │
│  retrieved_chunks (TEXT)  │
│  faithfulness (DECIMAL)   │
│  answer_relevance         │
│  context_recall           │
│  latency_ms               │
│  llm_eval_raw (TEXT)      │
│  create_time              │
└──────────────────────────┘
```

### 3.2 表结构详情

#### kb_evaluation_dataset（评测数据集）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK AUTO_INCREMENT | 主键 |
| name | VARCHAR(100) | NOT NULL | 数据集名称 |
| description | VARCHAR(500) | — | 描述说明 |
| scenario | VARCHAR(50) | — | 关联场景：it-helpdesk / hr-policy / general |
| question_count | INT | DEFAULT 0 | 问题数量（冗余计数，列表展示用） |
| create_time | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

#### kb_evaluation_question（评测问题）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK AUTO_INCREMENT | 主键 |
| dataset_id | BIGINT | NOT NULL, INDEX | 所属数据集 |
| question | TEXT | NOT NULL | 测试问题 |
| expected_answer | TEXT | — | 预期答案（可选，人工标注的参考答案） |
| expected_doc_ids | VARCHAR(500) | — | 预期命中文档 ID（逗号分隔，用于召回率验证） |
| create_time | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |

#### kb_evaluation_run（评测运行记录）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK AUTO_INCREMENT | 主键 |
| dataset_id | BIGINT | NOT NULL | 所用数据集 |
| status | VARCHAR(20) | NOT NULL | RUNNING / COMPLETED / FAILED |
| total_questions | INT | — | 总问题数 |
| completed_questions | INT | DEFAULT 0 | 已完成数 |
| avg_faithfulness | DECIMAL(4,3) | — | 平均忠实度 |
| avg_relevance | DECIMAL(4,3) | — | 平均答案相关性 |
| avg_context_recall | DECIMAL(4,3) | — | 平均上下文召回率 |
| avg_latency_ms | INT | — | 平均延迟（毫秒） |
| started_at | DATETIME | — | 开始时间 |
| completed_at | DATETIME | — | 完成时间 |
| error_message | TEXT | — | 失败原因 |
| create_time | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |

#### kb_evaluation_result（单题评测结果）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK AUTO_INCREMENT | 主键 |
| run_id | BIGINT | NOT NULL, INDEX | 所属运行 |
| question_id | BIGINT | NOT NULL | 对应问题 |
| question_text | TEXT | — | 问题文本（冗余存储，避免 JOIN） |
| actual_answer | TEXT | — | RAG 实际返回的答案 |
| retrieved_chunks | TEXT | — | 检索到的文档片段（JSON 数组） |
| faithfulness | DECIMAL(4,3) | — | 忠实度 0.000-1.000 |
| answer_relevance | DECIMAL(4,3) | — | 答案相关性 0.000-1.000 |
| context_recall | DECIMAL(4,3) | — | 上下文召回率 0.000-1.000 |
| latency_ms | INT | — | 本次查询延迟（毫秒） |
| llm_eval_raw | TEXT | — | LLM 评测原始输出（JSON，调试用） |
| create_time | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |

---

## 四、后端设计

### 4.1 包结构

```
com.knowbrain.evaluation/
├── entity/
│   ├── EvaluationDataset.java      -- @TableName("kb_evaluation_dataset")
│   ├── EvaluationQuestion.java     -- @TableName("kb_evaluation_question")
│   ├── EvaluationRun.java          -- @TableName("kb_evaluation_run")
│   └── EvaluationResult.java       -- @TableName("kb_evaluation_result")
├── mapper/
│   ├── EvaluationDatasetMapper.java  -- extends BaseMapper<EvaluationDataset>
│   ├── EvaluationQuestionMapper.java -- extends BaseMapper<EvaluationQuestion>
│   ├── EvaluationRunMapper.java      -- extends BaseMapper<EvaluationRun>
│   └── EvaluationResultMapper.java   -- extends BaseMapper<EvaluationResult>
├── EvaluationService.java           -- 核心业务逻辑
└── EvaluationController.java        -- REST API
```

### 4.2 EvaluationService — 核心方法

```java
@Service
public class EvaluationService {

    // ===== 数据集管理 =====
    IPage<EvaluationDataset> listDatasets(String scenario, int page, int size);
    EvaluationDataset createDataset(EvaluationDataset dataset);
    void deleteDataset(Long id);  // 级联删除 questions + results

    // ===== 问题管理 =====
    IPage<EvaluationQuestion> listQuestions(Long datasetId, int page, int size);
    EvaluationQuestion addQuestion(EvaluationQuestion question);
    void updateQuestion(Long id, EvaluationQuestion question);
    void deleteQuestion(Long id);
    int importQuestions(Long datasetId, List<EvaluationQuestion> questions);

    // ===== 评测运行 =====
    EvaluationRun startRun(Long datasetId);  // 异步执行，立即返回 run 对象
    IPage<EvaluationRun> listRuns(int page, int size);
    EvaluationRun getRun(Long id);
    IPage<EvaluationResult> getRunResults(Long runId, int page, int size);

    // ===== 内部方法 =====
    void executeRun(EvaluationRun run);           // @Async 逐题执行
    EvaluationResult evaluateSingle(EvaluationQuestion q);  // 单题评测
}
```

### 4.3 评测流水线（executeRun 内部逻辑）

```
输入: EvaluationRun run（含 datasetId）

1. 加载数据集 → List<EvaluationQuestion> questions
2. 更新 run.status = RUNNING, total_questions = questions.size()

3. 对每题循环：
   ┌──────────────────────────────────────────────────────┐
   │ a. 记录 startMs                                       │
   │                                                       │
   │ b. 调用 RAG 管线：                                     │
   │    ChatResponse cr = ragService.chat(                 │
   │        q.getQuestion(),                               │
   │        Collections.emptyList(),  // 不限空间            │
   │        Collections.emptyList(),  // 无历史             │
   │        null                      // 不限分类            │
   │    );                                                 │
   │                                                       │
   │ c. 捕获数据：                                          │
   │    - actualAnswer = cr.getAnswer()                    │
   │    - retrievedChunks = JSON.serialize(cr.getSources())│
   │    - latencyMs = System.currentTimeMillis() - startMs │
   │                                                       │
   │ d. LLM-as-Judge 评分（见 4.4）：                       │
   │    - faithfulness = judge(FAITHFULNESS_PROMPT, ...)   │
   │    - answerRelevance = judge(RELEVANCE_PROMPT, ...)   │
   │    - contextRecall = judge(CONTEXT_RECALL_PROMPT, ...)│
   │                                                       │
   │ e. 写入 EvaluationResult                              │
   │ f. 更新 run.completed_questions++                     │
   └──────────────────────────────────────────────────────┘

4. 计算平均值 → 更新 run（status=COMPLETED, 各项均值）
5. 异常处理 → run.status=FAILED, error_message=异常信息
```

### 4.4 LLM-as-Judge 实现

**设计决策**：复用项目已有的 Spring AI `ChatClient`（Qwen-Max），无需引入额外依赖。

**Prompt 模板**：三个指标各一个 Prompt 文件，放在 `resources/prompts/` 下。

**解析策略**：从 LLM 返回文本中提取 JSON 对象（正则匹配 `\{[^}]+\}`），解析 `score` 和 `reason` 字段。解析失败时 fallback 为 `score=0, reason="解析失败"`。

**性能优化**：三项指标的 Judge 调用互不依赖，可使用 `CompletableFuture.allOf()` 并行执行，将评测延迟从 3×LLM 延迟压缩到 1×LLM 延迟。

**容错**：单题评测异常不中断整体运行，记录异常信息到 `EvaluationResult.llm_eval_raw` 字段。

### 4.5 EvaluationController — REST API

```
Base: /api/v1/admin/evaluation

数据集管理
├── GET    /datasets                    列表（?scenario=&page=&size=）
├── POST   /datasets                    创建
└── DELETE /datasets/{id}               删除（级联）

问题管理
├── GET    /datasets/{id}/questions     列表（分页）
├── POST   /datasets/{id}/questions     添加单题
├── POST   /datasets/{id}/questions/batch  批量导入（JSON 数组）
├── PUT    /datasets/{id}/questions/{qid}  更新
└── DELETE /datasets/{id}/questions/{qid}  删除

评测运行
├── POST   /runs                        启动评测 { datasetId: N }
├── GET    /runs                        运行历史（分页）
├── GET    /runs/{id}                   运行详情（含汇总指标）
└── GET    /runs/{id}/results           单题结果列表（分页）
```

**权限**：所有端点位于 `/admin/*` 下，通过 AuthInterceptor 自动拦截，仅 ADMIN 可操作。

---

## 五、前端设计

### 5.1 页面结构

```
┌──────────────────────────────────────────────────────────────┐
│  评测管理                                                     │
│                                                              │
│  ┌──────────────┬────────────────────────────────┐           │
│  │ 数据集        │  评测运行                        │           │
│  └──────────────┴────────────────────────────────┘           │
│                                                              │
│  【Tab 1: 数据集】                                            │
│  ┌─ 工具栏 ───────────────────────────────────────────────┐  │
│  │ [场景筛选: 全部▾]                    [+ 新建数据集]     │  │
│  └────────────────────────────────────────────────────────┘  │
│  ┌─ 表格 ─────────────────────────────────────────────────┐  │
│  │ # │ 名称 │ 场景 │ 问题数 │ 创建时间 │ 操作              │  │
│  │───┼──────┼──────┼───────┼─────────┼──────────────────│  │
│  │ 1 │ IT基线│ IT  │  50   │ 07-12   │ [问题] [删除]     │  │
│  │ 2 │ HR题库│ HR  │  30   │ 07-10   │ [问题] [删除]     │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                              │
│  【Tab 2: 评测运行】                                          │
│  ┌─ 工具栏 ───────────────────────────────────────────────┐  │
│  │                         [选择数据集▾] [▶ 开始评测]      │  │
│  └────────────────────────────────────────────────────────┘  │
│  ┌─ 运行历史 ─────────────────────────────────────────────┐  │
│  │ # │ 数据集 │ 状态 │ Faith.│ Relev.│ Recall│ 延迟│ 时间  │  │
│  │───┼───────┼──────┼───────┼───────┼───────┼─────┼──────│  │
│  │ 3 │ IT基线│ ✅   │ 0.82  │ 0.88  │ 0.75  │2.1s │07-12 │  │
│  │   │       │      │        [查看结果]                    │  │
│  │ 2 │ IT基线│ ✅   │ 0.79  │ 0.85  │ 0.72  │2.4s │07-10 │  │
│  │   │       │      │        [查看结果]                    │  │
│  └────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

### 5.2 弹窗设计

#### 数据集表单

```
┌──────────────────────────────────┐
│  新建数据集                  [×]  │
│                                  │
│  名称:   [___________________]   │
│  场景:   [IT运维 / HR制度 / 通用]  │
│  描述:   [___________________]   │
│                                  │
│              [取消]  [保存]       │
└──────────────────────────────────┘
```

#### 问题管理子表

```
┌──────────────────────────────────────────────┐
│  数据集: IT基准测试 — 共 50 题           [×]  │
│                                              │
│  [+ 添加问题]  [批量导入]                     │
│  ┌─────────────────────────────────────────┐ │
│  │ # │ 问题(截断) │ 预期答案(截断) │ 操作   │ │
│  │───┼───────────┼───────────────┼────────│ │
│  │ 1 │ VPN怎么配  │ 先打开...     │ [编][删]│ │
│  └─────────────────────────────────────────┘ │
└──────────────────────────────────────────────┘
```

#### 运行结果详情

```
┌──────────────────────────────────────────────────────┐
│  运行结果: IT基准测试 #3                          [×]  │
│                                                      │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐    │
│  │ 0.82    │ │ 0.88    │ │ 0.75    │ │ 2.1s    │    │
│  │ 忠实度  │ │ 相关性  │ │ 召回率  │ │ 平均延迟│    │
│  └─────────┘ └─────────┘ └─────────┘ └─────────┘    │
│                                                      │
│  ┌────────────────────────────────────────────────┐  │
│  │ # │ 问题 │ Faith.│ Relev.│ Recall│ 延迟        │  │
│  │───┼──────┼───────┼───────┼───────┼─────────────│  │
│  │ 1 │ VPN. │ 0.90  │ 0.95  │ 0.80  │ 1.8s        │  │
│  │   │ [展开详情: 答案 + 检索上下文 + 评测理由]    │  │
│  └────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────┘
```

### 5.3 需修改的文件清单

| 文件 | 操作 |
|------|------|
| `knowbrain-web/src/api/index.ts` | 新增 11 个 evaluation API 函数 |
| `knowbrain-web/src/views/EvaluationView.vue` | **新建** — 主页面（约 500 行） |
| `knowbrain-web/src/router/index.ts` | 新增 `/admin/evaluation` 路由 |
| `knowbrain-web/src/views/AdminLayout.vue` | 新增菜单项 + pageTitles |

---

## 六、实施步骤

### Step 1: 数据库层（0.5 天）
- 创建 `V6__evaluation.sql` Flyway 迁移（4 张表）
- 同步更新 `docker/mysql/init/04-evaluation.sql`（Docker 首次部署用）
- 创建 4 个 Entity + 4 个 Mapper（纯模板代码）

### Step 2: Prompt 模板（0.5 天）
- `prompts/eval-faithfulness.txt`
- `prompts/eval-relevance.txt`
- `prompts/eval-context-recall.txt`

### Step 3: EvaluationService（1.5 天）
- 数据集/问题 CRUD
- `executeRun()` — 异步评测流水线（`@Async`）
- LLM-as-Judge 调用 + JSON 解析
- 三项指标并行评分（CompletableFuture）

### Step 4: EvaluationController（0.5 天）
- 11 个 REST 端点
- 入参校验 + 分页支持

### Step 5: 前端（1.5 天）
- `api/index.ts` 新增 API 函数
- `EvaluationView.vue` 页面（Tab + Table + Dialog 模式）
- `router/index.ts` + `AdminLayout.vue` 菜单

### Step 6: 端到端验证（0.5 天）
- 创建 IT 运维测试数据集（5-10 条问题）
- 启动评测 → 验证结果合理性
- 检查管理后台页面交互

---

## 七、关键设计决策

### 7.1 为什么用 LLM-as-Judge 而不引入 RAGAS？

| 方案 | 优点 | 缺点 |
|------|------|------|
| **RAGAS（Python 库）** | 业界标准，指标齐全 | 需要 Python 服务，增加运维复杂度；Java 项目调用需跨进程 |
| **LLM-as-Judge（本方案）** | 纯 Java 实现，零额外依赖；复用现有 ChatClient | 评测结果依赖 LLM 质量；每次评测消耗 LLM token |

**结论**：MVP 阶段采用 LLM-as-Judge，后续可引入 RAGAS 作为交叉验证。

### 7.2 为什么评测时禁用缓存？

`RAGServiceImpl` 在 pipeline stage 0 会先查 Redis 缓存。评测时需要 **实时测量** 检索 + 生成质量，不能返回缓存结果。处理方式：

```java
// executeRun() 中暂时将 rag.cache.enabled 设为 false
// 或在调用 ragService.chat() 前清空相关 key
```

**推荐方案**：在 `EvaluationService` 中注入 `RAGCacheService`，评测前调用 `invalidateAll()` 清空缓存。

### 7.3 为什么使用 @Async 异步执行？

评测一个 50 题的数据集可能需要 3-5 分钟（每题 LLM 调用 4 次 = 1 次生成 + 3 次 Judge）。异步执行避免 HTTP 请求超时，用户提交后立即返回 runId，通过轮询或刷新查看进度。

---

## 八、后续扩展

| 扩展项 | 说明 | 优先级 |
|--------|------|:----:|
| 检索专项评测 | 独立评估 HybridSearch 的 recall@K / MRR / NDCG | P2 |
| RAGAS 集成 | 引入 Python RAGAS 作为第二评测引擎，与 LLM-as-Judge 交叉验证 | P2 |
| 人工标注对比 | 支持人工打分与自动评分对比，计算 Judge 准确率 | P2 |
| CI 集成 | 每次 PR 自动跑评测，阻断指标下降的变更 | P2 |
| 多模型对比 | 同一数据集在不同的 LLM/Embedding 配置下跑分对比 | P3 |
| 评测数据版本管理 | 数据集快照 + 版本 diff | P3 |

---

## 九、参考

- [RAGAS: Evaluation for RAG](https://docs.ragas.io/)
- [DeepEval: The Evaluation Framework for LLMs](https://docs.confident-ai.com/)
- [LangSmith Evaluation](https://docs.smith.langchain.com/evaluation)
- 项目内参考：`RAGServiceImpl.java` — RAG 管线实现；`ImIntegrationView.vue` — 管理后台页面模板
