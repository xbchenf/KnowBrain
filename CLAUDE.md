# KnowBrain（知脑）— 企业私有知识大脑

> 私有化部署的企业智能知识库——把公司所有文档投喂进去，员工用自然语言提问，AI 精准回答 + 溯源原文。

---

## 一、产品定位

### 一句话

**企业第二大脑：上传文档 → 自然语言提问 → AI 精准回答 + 溯源原文段落。**

### 核心价值

| 痛点 | KnowBrain 解决方案 |
|------|-------------------|
| 海量文档检索难（制度/合同/技术文档/项目资料） | 混合检索（语义 + 关键词），自然语言提问 |
| 新人培训周期长，追着老员工问 | AI 7×24 自动答疑，新人自助入职 |
| 专家离职经验流失 | 文档知识永久沉淀，人不在了知识还在 |
| SaaS 数据出境风险 | 纯私有化部署，数据不出企业内网 |

### 一套代码，开箱即用

- **平台引擎**：通用文档解析 → 向量化 → 混合检索 → RAG 生成，不绑定行业
- **场景配置（种子数据）**：IT 运维 + HR 制度模板，含术语词典 + 高频问答对，启动时加载

**不是两套系统。** 场景包是数据，不是代码。装不同种子数据 = 不同场景效果。

---

## 二、目标用户与场景

| 角色 | 诉求 |
|------|------|
| 普通员工 | 「VPN 怎么配」「年假几天」→ 快速找到答案 |
| 新入职员工 | 自助学习制度流程，不用追着老员工问 |
| IT 运维 | 减少重复问题，AI 引导自助排查故障 |
| HR/行政 | 制度政策 AI 代答，减少 50% 重复咨询 |
| 知识管理员 | 文档上传/分类/权限管控，知识质量监控 |

---

## 三、技术栈

| 层 | 技术 | 说明 |
|----|------|------|
| 后端框架 | Java 17 + Spring Boot 3.3.x | 企业级标准 |
| AI 编排 | Spring AI 1.0+ | 统一 LLM 调用抽象（含 Function Calling） |
| LLM | Qwen3.7-Plus（默认）/ DeepSeek-V3 | DashScope/DeepSeek API，OpenAI 兼容协议 |
| 向量数据库 | Milvus 2.4 | 语义检索 + BM25 关键词检索（RRF 融合） |
| 关系数据库 | MySQL 8.0 | 文档元数据、用户、权限、反馈（Flyway 迁移） |
| 缓存 | Redis 7 | 高频问答缓存、会话状态、Token 黑名单 |
| 对象存储 | MinIO | 原始文档存储 |
| 文档解析 | Qwen-VL + POI + Tika 三层架构 | PDF 视觉解析 + Office 原生解析 + 通用格式兜底 |
| 熔断降级 | Resilience4j | LLM/Milvus 故障隔离，防止级联失败 |
| 前端 | Vue 3 + Element Plus | 统一前端（Q&A 问答 + 管理后台） |
| 监控 | Prometheus + Grafana | JVM 指标 / RAG 性能看板（可选，profile 启用） |
| 部署 | Docker Compose | 一键部署 |

---

## 四、核心检索架构

```
用户问题
   → 查询预处理（LLM 改写 + 术语映射 + 敏感词过滤）
   → FAQ 精确匹配
        ├─ 命中 → 短路返回预设答案（零延迟、零 LLM 成本）
        └─ 未命中
              ├─ Agent 路径（默认关闭，RAG_AGENT_ENABLED=true 开启）
              │     └─ LLM 自主多步检索：searchKnowledge → 反思 → 补充 → 合成
              └─ 标准路径：混合检索（Milvus 语义 + BM25 关键词）
                              → 粗排 → Reranker 精排
                              → LLM 生成 + 引用标注 + 置信度判断
                              → 答案 + 溯源 → 返回用户
```

---

## 五、代码结构规划

```
knowbrain-server/
├── src/main/java/com/knowbrain/
│   ├── KnowBrainApplication.java
│   ├── config/              # 框架配置
│   ├── common/              # Result、全局异常处理
│   ├── auth/                # JWT 认证、权限拦截
│   ├── audit/                # 审计日志（AOP 操作记录）
│   ├── agent/               # Agent 检索智能体（多步检索 + 反思机制）
│   ├── document/            # 文档上传、解析、分块、管理
│   │   ├── entity/
│   │   ├── mapper/
│   │   ├── service/
│   │   └── controller/
│   ├── retrieval/           # 检索引擎（混合检索 + Reranker）
│   │   ├── engine/          # RAG 管线编排（含 Agent 分叉）
│   │   ├── vector/          # Milvus 向量检索
│   │   ├── keyword/         # BM25 关键词检索
│   │   └── rerank/          # 重排序
│   ├── generation/          # LLM 生成（Prompt 拼装 + 溯源 + 后检）
│   ├── evaluation/          # RAG 评测（数据集管理 + LLM-as-Judge）
│   ├── permission/          # 文档级权限管控
│   ├── space/               # 文档空间（Space → Document → Chunk）
│   ├── feedback/            # 答案反馈（有用/无用）
│   ├── statistics/          # 使用统计
│   ├── scenario/            # 场景配置加载（种子数据、术语词典）
│   ├── im/                   # 企业 IM 集成（企微/钉钉/飞书 Bot）
│   │   ├── adapter/          # 平台适配器（Wecom/Dingtalk/Feishu）
│   │   ├── entity/           # IM 实体（部门映射、用户身份）
│   │   ├── mapper/           # MyBatis-Plus Mapper
│   │   └── model/            # 统一消息模型
│   └── websocket/           # WebSocket（可选，流式输出）
├── src/main/resources/
│   ├── application.yml
│   ├── prompts/             # LLM Prompt 模板（rag-system + agent-system）
│   └── scenarios/           # 场景种子数据
│       ├── it-helpdesk/     # IT 运维场景包
│       │   ├── categories.json
│       │   ├── glossary.json
│       │   └── faq.json
│       └── hr-policy/       # HR 制度场景包
└── docker/
    └── mysql/init/          # 数据库初始化 SQL

knowbrain-web/
├── src/
│   ├── api/                 # 统一 API 层（问答 + 管理）
│   ├── layouts/
│   │   ├── WebLayout.vue    # Q&A 问答布局
│   └── views/               # 全部视图页面
│       ├── ChatView.vue     # 对话问答
│       ├── AdminLayout.vue  # 管理后台布局
│       ├── DocBrowseView.vue # 文档浏览
│       ├── DashboardView.vue # 管理仪表盘
│       └── ...              # 其他管理页面
├── Dockerfile
└── nginx.conf

docker/
├── nginx/nginx.conf         # 网关反向代理
├── prometheus/prometheus.yml # Prometheus 指标采集配置
└── grafana/dashboards/      # Grafana 仪表盘（JVM + RAG）
```

---

## 六、开发阶段（已完成）

### 阶段 1：平台核心引擎 ✅
- 项目脚手架搭建
- 文档上传 API + MinIO 存储
- 文档解析：Qwen-VL(PDF) + POI(Office) + Tika(兜底) 三层架构
- 智能分块引擎（ElementAwareChunker + SmartChunker）
- Milvus 向量化入库 + BM25 关键词索引
- 混合检索 → LLM 生成 → 溯源
- 基础 Web 问答界面

### 阶段 2：权限 + 管理后台 ✅
- 用户认证（JWT + BCrypt + refresh token rotation）
- 文档空间模型（Space → Document → Chunk）
- 空间级权限（Owner/Editor/Viewer + 部门可见范围）
- 检索时权限过滤
- 管理后台 UI（13 个页面）
- 使用统计看板

### 阶段 3：IT 运维 + HR 制度场景包 ✅
- IT 知识分类模板 + HR 制度分类模板
- IT 术语词典 + HR 术语词典
- 高频问题预设答案库（FAQ 精确匹配 + 关键词倒排索引）
- 检索质量调优
- Demo 环境 + 演示数据

### 阶段 4：打包发布 ✅
- Docker Compose 编排
- 一键初始化脚本（bin/start.sh / start.bat）
- 安装部署文档
- GitHub Release

### 超出 MVP 的新增功能（Phase 5+）

| 功能 | 说明 |
|------|------|
| 🧠 **Agent 检索智能体** | LLM 自主多步检索 + 反思，解决对比/多跳/歧义问题；Spring AI Function Calling 集成，零新依赖 |
| 📊 **RAG 评测体系** | 数据集管理 + LLM-as-Judge 自动评分（Faithfulness/Relevance/Context Recall）+ 历史趋势 |
| 💬 **企业 IM 集成** | 企微/钉钉/飞书 Bot，双通道架构（HTTP 回调 + WebSocket Stream）+ 部门映射 + 用户身份关联 |
| 🛡️ **安全加固** | 敏感词过滤 + JWT refresh token 拒绝机制 + Mass Assignment 防护 |
| 📝 **审计日志** | AOP 全操作审计，支持按用户/操作/资源/时间查询 |
| 🔄 **LLM 查询改写** | 短查询自动扩展、口语规范化，提升检索命中率 |
| 🗄️ **数据库迁移** | Flyway 版本化 SQL，启动时自动增量升级 |
| ⚡ **熔断降级** | Resilience4j 熔断器（LLM/Milvus 故障隔离），LLM 超时→降级返回检索原文 |

---

## 七、从 EICS V2.0 可复用的代码

> 参考仓库：`d:\github\EICS\EICS-V2.0\eics-server\`

| 组件 | 来源路径 | 复用方式 |
|------|---------|---------|
| Spring Boot 项目结构 + pom.xml | `eics-server/` | 参考依赖版本和结构 |
| JWT 认证 | `com.eics.auth.JwtUtil` `AuthInterceptor` | 直接迁移 |
| MinIO 配置 | `com.eics.config.MinioConfig` | 直接迁移 |
| Tika 文档解析 | `DocumentServiceImpl` 解析逻辑 | 抽离迁移 |
| 智能分块 | `com.eics.service.SmartChunker` | 直接迁移 |
| Milvus 配置 | `com.eics.config.*` | 参考 Spring AI Milvus 集成方式 |
| 敏感词过滤 | `com.eics.security.SensitiveWordFilter` | 直接迁移 |
| Result 统一返回体 | `com.eics.common.Result` | 直接迁移 |
| 前端脚手架 | `eics-web/` | 参考 Pinia + Element Plus + Axios 配置 |

**禁止直接复制粘贴整个模块。** 逐一审查，去掉 EICS 特有的工单/坐席逻辑，只保留知识库相关的通用能力。

---

## 七.五、场景数据设计（categories / glossary / faq）

> **设计说明**：这三类数据采用「JSON 种子 + DB 存储 + 内存缓存」的混合架构。
> 详见下方各自用途说明，**不要删除 JSON 文件**——它们是首次部署时的种子数据源。

### categories.json → kb_scenario_category

**作用**：知识分类树，文档上传时选分类，检索时可按分类缩小范围。

**数据结构**：树形（一级分类 → 子分类），`key` 为唯一标识，`parentKey` 表示父子关系。

**加载策略**：首次启动从 JSON 导入 DB → 内存组装成树 → ScenarioConfig 缓存。

### glossary.json → kb_scenario_glossary

**作用**：查询改写。用户口语 → 正式技术术语，提升检索精度。

**数据结构**：`term`（口语）→ `formal`（正式术语），`synonyms` 逗号分隔同义词。

**匹配逻辑**：按术语长度降序匹配（避免短词误匹配），`Pattern.quote` 安全替换。

### faq.json → kb_scenario_faq

**作用**：高频问题预设答案库。命中 ≥ 2 个关键词时直接返回预设答案，**跳过 LLM 调用**（零延迟零成本）。

**匹配逻辑**：倒排索引（keyword → FAQ 列表），QueryPreprocessor 中发生在向量检索之前。

### 三者协作流水线

```
用户输入 → glossary 改写 → sensitiveWord 过滤 → FAQ 精确匹配
                                                    ├─ 命中 → 短路返回
                                                    └─ 未命中 → 向量检索 → LLM 生成
categories 用于：文档上传分类 + 检索范围限定
```

### 运行时管理

- **DB 优先**：启动时检查 DB，有数据则直接用；无数据则从 JSON 种子导入
- **热更新**：管理后台 CRUD → 写 DB + 调 `reload()` 刷新内存缓存
- **管理 API**：`/api/v1/admin/scenario/**`（需 ADMIN 角色）

---

## 八、编码规范

- 基础包名：`com.knowbrain`
- 统一返回体 `Result<T>`（参考 EICS 实现）
- LLM Prompt 模板放在 `resources/prompts/` 目录
- 场景种子数据放在 `resources/scenarios/` 目录
- 回复文本禁止硬编码，统一在配置或常量类中定义
- 数据库表前缀：`kb_`（knowledge base）
- API 路径前缀：`/api/v1/`

---

## 九、关键设计原则

1. **检索优先于生成**：能检索到的才回答，检索不到就诚实说"未找到"，绝不编造
2. **溯源强制要求**：每个答案必须标注来源文档 + 段落位置
3. **权限在检索层过滤**：Milvus 查询时注入权限表达式，而非生成后过滤
4. **LLM 是增强不是替代**：LLM 超时/失败 → 降级返回检索结果原文
5. **高频问题缓存**：Redis 缓存热门问答，避免重复调 LLM
6. **反馈闭环**：用户标记「无用」→ 记录 → 统计 → 驱动知识库改进
7. **Agent 渐进式上线**：默认关闭（`RAG_AGENT_ENABLED=false`），通过 Feature Flag 控制，异常时自动降级到标准管线
8. **Agent 单工具原则**：Phase 1 只暴露一个 `searchKnowledge` 工具，让 LLM 专注检索策略而非工具选择

---

## 十、技术设计文档索引

重要技术方案的详细设计存放于 `docs/` 目录：

| 文档 | 内容 |
|------|------|
| [文档解析技术方案](docs/文档解析技术方案.md) | 文档解析入库链路设计：Qwen-VL(PDF主引擎)+POI(Office)+Tika(兜底) 三层架构、表格提取、结构感知分块、业界对标、MinerU/LibreOffice 未来增强评估 |
| [企业 IM 集成技术方案](docs/企业IM集成技术方案.md) | 企微/钉钉/飞书 Bot 集成：双通道架构（HTTP回调+WebSocket Stream）、加解密、消息处理流水线、用户映射、部门映射、管理后台 |
| [Agent 智能体技术方案](docs/Agent技术方案.md) | 业界对标分析（Dify/FastGPT/Glean/Copilot Studio/ServiceNow）+ 三阶段规划 |
| [Agent Phase 1 技术方案](docs/Agent-Phase1-技术方案.md) | 检索智能体详细设计 + 实现记录：SearchKnowledgeTool + Prompt 反思 + 非流式/流式双路径 + 降级策略 |
| [RAG 评测体系技术方案](docs/RAG评测体系技术方案.md) | 评测数据集管理 + LLM-as-Judge 自动评分（Faithfulness/Relevance/Context Recall）+ 历史趋势追踪 |
| [E2E 自动化测试方案](docs/E2E测试方案.md) | Playwright + TypeScript 全模块端到端测试：Smoke/Regression/API 三层 + CI 集成 + Skill 触发 |
| [商业化可行性分析报告](docs/KnowBrain-商业化可行性分析报告.md) | 市场分析、竞争格局、商业模式、能力缺口、财务测算 |
| [产品战略规划](docs/KnowBrain-产品战略规划.md) | 产品定位、架构、路线图 |
| [部署文档](docs/deployment.md) | Docker Compose 部署指南 |

**解析链路实施前必读**：[文档解析技术方案](docs/文档解析技术方案.md)——包含完整的设计决策、业界对比和未来演进路径。
