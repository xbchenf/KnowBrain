# KnowBrain Agent 智能体技术方案

> 调研日期：2026-07-14 | 基于 Dify / FastGPT / Glean / Microsoft Copilot Studio / ServiceNow 对标分析
> 
> **当前状态**：检索智能体已交付 + A/B 评测完成（详见 [Agent-Phase1-技术方案](Agent-Phase1-技术方案.md)）。Agent V1（Function Calling + SQL）评估为价值不大已暂缓。后续进化方向详见 [Agent-产品进化指南](Agent-产品进化指南.md)：低成本增强 AI 感知 + 知识全生命周期 Agent + 不越界做通用平台。

---

## 一、业界对标分析

### 1.1 五家标杆产品

| 产品 | 定位 | 技术栈 | 核心亮点 |
|------|------|--------|---------|
| **Dify** | 开源 AI 应用开发平台 | Python (LangChain) | Workflow + Agent + 插件市场，GitHub 142k Stars |
| **FastGPT** | 开源 AI Agent 构建平台 | Python | Agent V2 自主规划 + 代码沙箱 + MCP |
| **Glean** | 企业 Work AI 平台 | 自研 | Agentic Reasoning Engine + Enterprise Graph + Reflection |
| **Copilot Studio** | 微软低代码 Agent 平台 | 微软生态 | 200+ 连接器 + 多 Agent A2A + 12 项治理模式 |
| **ServiceNow** | 企业 AI 平台 | 自研 | AI Agent Fabric + Knowledge Graph + AI Control Tower |

### 1.2 能力全景图

```
                        ┌──────────────────────────┐
                        │   Tier 3: 自治智能体       │
                        │   多Agent协作 + 知识图谱    │
                        │   + 自进化 + A2A 协议       │
                        ├──────────────────────────┤
                        │   Tier 2: 编排智能体       │
                        │   工作流编排 + 沙箱执行      │
                        │   + MCP + 多模态           │
                        ├──────────────────────────┤
      ← 每家都有 →       │   Tier 1: 基础 Agent       │
                        │   Function Calling         │
                        │   + RAG + 工具调用          │
                        └──────────────────────────┘
```

### 1.3 能力对照详表

| 能力 | Dify | FastGPT | Glean | Copilot Studio | ServiceNow |
|------|:----:|:-------:|:-----:|:-------------:|:----------:|
| Function Calling（调 API/DB） | ✅ | ✅ | ✅ | ✅ | ✅ |
| RAG + Agent 联动 | ✅ | ✅ | ✅ | ✅ | ✅ |
| 工作流编排（分支/循环/异常） | ✅ | ✅ | ✅ | ✅ | ✅ |
| 代码沙箱（安全执行生成代码） | ❌ | ✅ | ✅ | ❌ | ❌ |
| MCP 协议（标准化工具接入） | ✅ | ✅ | ✅ | ✅ | — |
| 多 Agent 协作（A2A） | ❌ | ❌ | ❌ | ✅ | ✅ |
| 知识图谱（实体+关系） | ❌ | ❌ | ✅ | ❌ | ✅ |
| Agent Skills（可复用技能包） | ❌ | ✅ | ✅ | ❌ | — |
| 自评估/反思（Reflection） | ❌ | ❌ | ✅ | ❌ | ❌ |
| 可视化 Agent Builder | ✅ | ✅ | ✅ | ✅ | ❌ |
| 治理/审计/权限感知 | ⚠️ | ⚠️ | ✅ | ✅ | ✅ |

### 1.4 KnowBrain 相对于业界的独特基础

| 能力 | 业界 | KnowBrain |
|------|:----:|:---------:|
| 空间级细粒度权限 | ❌ 最多应用级 | ✅ Owner/Editor/Viewer + 部门可见 |
| 内置 AOP 审计日志 | ⚠️ 需第三方 | ✅ 原生 |
| FAQ 精确匹配短路 | ⚠️ 无 | ✅ 倒排索引零延迟 |
| 场景种子数据驱动 | ❌ | ✅ categories + glossary + FAQ |
| Java 技术栈 | ❌ 全 Python | ✅ Spring Boot 3.3 + Spring AI |

### 1.5 关键结论

1. **Tier 1（Function Calling）是入场券，不是优势** — 五家全部具备，缺失意味着不在同一赛道竞争
2. **KnowBrain 的权限 + 审计 + FAQ 短路是独特基础** — 业界产品在这些方面反而薄弱
3. **业界 Agent 正快速向「自治」方向演进** — Glean 的 Reflection、Copilot Studio 的 A2A 是前沿
4. **MCP 协议正在成为工具接入的行业标准** — Dify/FastGPT/Glean/Copilot Studio 全部支持

---

## 二、KnowBrain Agent 三阶段规划

### Phase 1 — Agent V1：基础工具调用（3 周）⚠️ 价值不大，暂缓

对标 Dify/FastGPT Tier 1。**目标**：让 KnowBrain 不仅搜文档内容，也能查结构化数据。

```
Agent V1 = Function Calling 框架（Spring AI 原生）
         + SQL 查询工具（覆盖统计 + 文档元数据搜索）
         + 日期工具（解决「上个月」「下周」等相对时间）
         + 安全边界（仅 SELECT + 表白名单 + 行数限制 + 工具白名单）
         + 降级策略（FC 失败 → RAG 回退）
```

> **决策（2026-07-19）**：暂缓。SQL 统计查询与现有 Dashboard 统计看板高度重叠（热门问题、每日趋势、FAQ 命中率、文档排行等均已在管理后台可视化呈现），自然语言查询结构化数据的增量价值有限。日期工具 5 行代码，不值得为此搭建整套 Function Calling 基础设施。未来如有明确的跨表组合查询需求（如「哪些文档被标记为无用最多」需要 JOIN feedback + document），再重新评估。

### Phase 2 — Agent V2：工作流编排 + MCP（3-5 周，V1 发布后启动）

对标 Dify Workflow + FastGPT Flow。**目标**：支持多步骤复杂任务。

- 工作流编排：顺序节点 + 条件分支 + 异常处理
- MCP 接入：Server 端暴露 KnowBrain 能力 + Client 端接入企业 MCP 服务
- 配置化管理界面：管理后台可视化配置

### Phase 3 — Agent V3：自治智能体（长期，V2 稳定后按需求启动）

对标 Glean Agentic Engine。**目标**：Agent 自评估、自修正、跨领域推理。

- 反思机制：Agent 自评输出质量 → 自动重试 → 选最优结果
- Agent Skills：可复用领域技能包（IT 运维技能包 / HR 技能包）
- 知识图谱：文档 → 实体 → 关系，跨文档推理
- 多 Agent 协作：路由 Agent → 专项 Agent → 汇总 Agent

---

## 三、Phase 1 功能逐一分析

### 原始 7 项 → 精简为 4 项

| # | 功能 | 必要性 | 决策 |
|---|------|:------:|------|
| 1 | Function Calling 基础设施 | 🟢 必须 | ✅ 保留（Spring AI 原生，零额外依赖） |
| 2 | SQL 查询工具 | 🟢 必须 | ✅ 保留（一个工具覆盖统计 + 文档元数据搜索） |
| 3 | 统计工具（独立） | 🔴 非必要 | ✂️ 合并入 #2 |
| 4 | 文档搜索工具（独立） | 🔴 非必要 | ✂️ 合并入 #2 |
| 5 | 日期工具 | 🟡 小成本高收益 | ✅ 保留（5 行代码） |
| 6 | 安全边界 | 🟢 必须 | ✅ 保留 |
| 7 | 降级策略 | 🟡 必须 | ✅ 保留 |

### 关键决策

**为什么一个 SQL 工具就够了**：LLM 天然擅长 Text-to-SQL，「上个月上传最多的部门」「张三昨天的合同」「问答准确率趋势」都是同一个能力的不同查询——用户自然语言 → LLM 生成 SELECT → 执行 → LLM 翻译为自然语言。不需要为每种查询写独立工具。

**为什么安全边界必须内建**：
- SQL 仅允许 SELECT（拒绝 DROP/DELETE/UPDATE/INSERT）
- 表白名单（`kb_document`, `kb_evaluation_result`, `kb_feedback` 等，禁止 `kb_user`）
- 结果行数限制（默认 LIMIT 100）
- 工具白名单（仅注册过的 Function 可被 LLM 调用）

**为什么日期工具只有 5 行代码**：
```java
@Bean
public FunctionCallback currentTimeTool() {
    return FunctionCallback.builder()
        .function("getCurrentDateTime", () -> LocalDateTime.now().toString())
        .description("获取当前日期和时间，用于计算相对时间")
        .build();
}
```
LLM 拿到当前日期后自己算「上个月」= 2026-06-01 ~ 2026-06-30。

### 工时估算

```
Function Calling 框架    ████████████ 1 周
SQL 查询工具             ██████████████████ 1.5 周
安全边界                 ██████ 0.5 周
日期工具 + 降级策略       ██ 1 天
─────────────────────────────────────
合计                     ~3 周
```

---

## 附录：数据来源

- Dify 官方文档 (docs.dify.ai), 2026
- FastGPT 官方文档 (doc.fastgpt.cn), V4.11-V4.15, 2025
- Glean 官网 (glean.com), Agentic Reasoning Engine, 2025-2026
- Microsoft Copilot Studio 2026 Release Wave 1
- ServiceNow Knowledge 2025 大会
