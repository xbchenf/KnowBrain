# KnowBrain（知脑）— 企业私有知识大脑

> 私有化部署的企业智能知识库——把公司所有文档投喂进去，员工用自然语言提问，AI 精准回答 + 溯源原文。

[![Java 17](https://img.shields.io/badge/Java-17-orange)](https://adoptium.net/)
[![Spring Boot 3.3](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen)](https://spring.io/projects/spring-boot)
[![Vue 3](https://img.shields.io/badge/Vue-3.x-4FC08D)](https://vuejs.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue)](LICENSE)

---

## 目录

- [产品简介](#产品简介)
- [核心能力](#核心能力)
- [技术架构](#技术架构)
- [快速开始](#快速开始)
- [项目结构](#项目结构)
- [API 概览](#api-概览)
- [场景数据](#场景数据)
- [默认账号](#默认账号)
- [开发指南](#开发指南)

---

## 产品简介

KnowBrain 是一个**纯私有化部署**的企业智能知识库平台。上传公司各类文档（制度、合同、技术文档、项目资料），员工用自然语言提问，AI 精准回答并标注原文出处。

| 痛点 | KnowBrain 解决方案 |
|------|-------------------|
| 海量文档检索难 | 混合检索（语义 + 关键词），自然语言提问 |
| 新人培训周期长 | AI 7×24 自动答疑，新人自助入职 |
| 专家离职经验流失 | 文档知识永久沉淀，人不在了知识还在 |
| SaaS 数据出境风险 | 纯私有化部署，数据不出企业内网 |

### 目标用户

| 角色 | 诉求 |
|------|------|
| 普通员工 | 「VPN 怎么配」「年假几天」→ 快速找到答案 |
| 新入职员工 | 自助学习制度流程，不用追着老员工问 |
| IT 运维 | 减少重复问题，AI 引导自助排查故障 |
| HR/行政 | 制度政策 AI 代答，减少 50% 重复咨询 |
| 知识管理员 | 文档上传/分类/权限管控，知识质量监控 |

---

## 核心能力

### 智能检索流水线

```
用户问题
  → 查询预处理（LLM 改写 + 术语映射 + 敏感词过滤）
  → FAQ 精确匹配（命中则直接返回，零延迟）
  → 混合检索（Milvus 语义 + BM25 关键词）
  → Reranker 精排
  → LLM 生成 + 引用标注 + 置信度判断
  → 答案 + 溯源 → 返回用户
```

### 功能矩阵

- **文档管理** — 上传/解析/分块，支持 PDF、Word、TXT、Markdown
- **混合检索** — 向量语义检索 + BM25 关键词检索，RRF 融合排序
- **FAQ 匹配** — 高频问题预设答案，精准命中直接返回，节省 LLM 成本
- **RAG 生成** — 检索增强生成，答案可溯源到原始文档段落
- **权限管控** — 空间级权限（Owner/Editor/Viewer）+ 部门可见范围
- **反馈闭环** — 用户标记有用/无用 → 统计 → 驱动知识库改进
- **管理后台** — 用户管理、部门管理、文档空间管理、数据统计
- **审计日志** — 全操作审计记录，AOP 自动采集，支持按用户/操作/时间查询
- **场景配置** — IT 运维/HR 制度等场景包，术语词典 + 分类模板 + FAQ 种子数据
- **系统监控** — Prometheus + Grafana（可选），JVM 指标 + RAG 性能看板

---

## 技术架构

| 层 | 技术 | 说明 |
|----|------|------|
| 后端框架 | Java 17 + Spring Boot 3.3.x | 企业级标准 |
| AI 编排 | Spring AI 1.0+ | 统一 LLM 调用抽象 |
| LLM | Qwen-Max / DeepSeek-V3 | OpenAI 兼容协议，可替换 |
| 向量数据库 | Milvus 2.4 | 语义检索（支持 BM25 分词器） |
| 关系数据库 | MySQL 8.0 | 文档元数据、用户、权限、反馈 |
| 缓存 | Redis 7 | 高频问答缓存、会话状态 |
| 对象存储 | MinIO | 原始文档存储 |
| 文档解析 | Apache Tika 2.x | PDF/Word/TXT/Markdown 多格式解析 |
| 前端 | Vue 3 + Element Plus | 问答界面 + 管理后台 |
| 监控 | Prometheus + Grafana | JVM / RAG 指标（可选，profile 启用） |
| 部署 | Docker Compose | 一键部署 |

---

## 快速开始

### 前置要求

- **Docker** & **Docker Compose** v2+
- 4 GB 以上可用内存
- LLM API Key（阿里云 DashScope 或兼容 OpenAI 协议的 API）

### 1. 克隆项目

```bash
git clone https://github.com/your-org/knowbrain.git
cd knowbrain
```

### 2. 配置环境变量

```bash
cp .env.example .env
```

编辑 `.env`，填入 LLM API Key：

```bash
# 必填：LLM API Key（阿里云 DashScope 或兼容服务）
SPRING_AI_OPENAI_API_KEY=sk-your-api-key-here

# 可选：修改默认密码
MYSQL_ROOT_PASSWORD=your-mysql-password
REDIS_PASSWORD=your-redis-password
JWT_SECRET=your-jwt-secret
```

### 3. 一键启动

```bash
# Linux / macOS
bash bin/start.sh

# Windows
bin\start.bat
```

首次运行会自动构建镜像（约 3-5 分钟），后续启动使用缓存。

### 4. 访问系统

| 入口 | 地址 | 说明 |
|------|------|------|
| 问答界面 | `http://localhost` | 员工问答入口 |
| 管理后台 | `http://localhost/admin` | 知识库管理 |
| Grafana 监控 | `http://localhost:3000` | 系统监控看板（需 `--profile monitoring`） |
| MinIO 控制台 | `http://localhost:9001` | 对象存储管理 |

### 5. 停止服务

```bash
docker compose down          # 停止（保留数据）
docker compose down -v       # 停止并清除所有数据
```

---

## 项目结构

```
knowbrain/
├── knowbrain-server/            # Spring Boot 后端
│   ├── src/main/java/com/knowbrain/
│   │   ├── auth/                # JWT 认证、权限拦截
│   │   ├── audit/                # 审计日志（AOP 切面 + 异步入库）
│   │   ├── common/              # 统一返回体、全局异常、健康检查、限流
│   │   ├── config/              # 框架配置
│   │   ├── document/            # 文档上传、解析、分块
│   │   ├── feedback/            # 答案反馈（有用/无用）
│   │   ├── generation/          # LLM 生成（Prompt 拼装 + 溯源）
│   │   ├── permission/          # 文档级权限
│   │   ├── retrieval/           # 检索引擎（混合检索 + Reranker）
│   │   ├── scenario/            # 场景配置（种子数据 + 术语词典 + FAQ）
│   │   ├── security/            # 敏感词过滤
│   │   ├── space/               # 文档空间（Space → Document → Chunk）
│   │   ├── statistics/          # 使用统计
│   │   └── websocket/           # WebSocket 流式输出
│   ├── src/main/resources/
│   │   ├── application.yml      # 应用配置
│   │   ├── prompts/             # LLM Prompt 模板
│   │   └── scenarios/           # 场景种子数据
│   │       ├── it-helpdesk/     # IT 运维场景包
│   │       └── hr-policy/       # HR 制度场景包
│   └── docker/
│       └── mysql/init/          # 数据库初始化 SQL
│
├── knowbrain-web/               # 统一前端（Vue 3 — Q&A 问答 + 管理后台）
│   ├── src/
│   │   ├── layouts/
│   │   │   ├── WebLayout.vue     # Q&A 问答布局（对话/文档库侧边栏）
│   │   │   └── AdminLayout.vue   # 管理后台布局
│   │   └── views/
│   │       ├── ChatView.vue         # 对话问答页
│   │       ├── DocBrowseView.vue    # 文档浏览页
│   │       ├── DashboardView.vue    # 统计看板
│   │       ├── UserView.vue         # 用户管理
│   │       ├── DepartmentView.vue   # 部门管理
│   │       ├── SpaceDetailView.vue  # 文档空间管理
│   │       ├── ScenarioView.vue     # 场景配置
│   │       ├── FeedbackView.vue     # 反馈管理
│   │       ├── AuditLogView.vue     # 审计日志
│   │       └── StatsView.vue        # 数据统计
│   ├── Dockerfile
│   └── nginx.conf
│
├── docker/
│   ├── nginx/
│   │   └── nginx.conf           # Nginx 反向代理配置
│   ├── prometheus/
│   │   └── prometheus.yml       # Prometheus 采集配置
│   └── grafana/
│       ├── datasources.yml      # 数据源配置
│       └── dashboards/          # 仪表盘（JVM + RAG）
│
├── bin/
│   ├── start.sh                 # Linux/macOS 一键启动
│   └── start.bat                # Windows 一键启动
│
├── docs/                        # 文档
├── test-docs/                   # 测试文档
├── docker-compose.yml           # 服务编排
├── .env.example                 # 环境变量模板
└── CLAUDE.md                    # 项目开发说明
```

---

## API 概览

| 模块 | 路径前缀 | 说明 |
|------|---------|------|
| 健康检查 | `/api/v1/health` | 服务健康状态 |
| 认证 | `/api/v1/auth/**` | 登录、注册、Token 刷新 |
| 文档 | `/api/v1/documents/**` | 上传、解析、CRUD |
| 检索问答 | `/api/v1/rag/**` | RAG 问答、流式输出 |
| 文档空间 | `/api/v1/spaces/**` | 空间管理 |
| 反馈 | `/api/v1/feedback/**` | 答案评价 |
| 场景管理 | `/api/v1/admin/scenario/**` | 分类/术语/FAQ 管理（需 ADMIN） |
| 用户管理 | `/api/v1/admin/users/**` | 用户 CRUD（需 ADMIN） |
| 部门管理 | `/api/v1/admin/departments/**` | 部门 CRUD（需 ADMIN） |
| 统计 | `/api/v1/admin/statistics/**` | 使用统计（需 ADMIN） |
| 审计日志 | `/api/v1/admin/audit-logs/**` | 操作审计记录（需 ADMIN） |

---

## 场景数据

KnowBrain 内置 IT 运维和 HR 制度两个场景包。场景包是**数据**而非代码，通过种子数据在首次启动时导入数据库。

| 数据文件 | 用途 |
|---------|------|
| `categories.json` | 知识分类树，上传文档时选分类，检索时可缩小范围 |
| `glossary.json` | 术语词典，用户口语 → 正式术语，提升检索精度 |
| `faq.json` | 高频问答对，命中则直接返回预设答案，跳过 LLM |

三者协作流水线：

```
用户输入 → glossary 改写 → 敏感词过滤 → FAQ 精确匹配
                                          ├─ 命中 → 短路返回
                                          └─ 未命中 → 向量检索 → LLM 生成
```

---

## 默认账号

| 角色 | 用户名 | 密码 |
|------|--------|------|
| 管理员 | `admin` | `KnowBrain@2026` |

> ⚠️ 生产环境请立即修改默认密码。

---

## 开发指南

### 后端开发

```bash
cd knowbrain-server

# 启动依赖服务（MySQL、Redis、MinIO、Milvus）
docker compose up -d mysql redis minio minio-init milvus

# 启动开发服务器
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### 前端开发

统一前端（Q&A 问答 + 管理后台在同一项目中，按路由切换布局）：

```bash
cd knowbrain-web
npm install
npm run dev     # 启动后访问 / 为问答界面，/admin 为管理后台
```

### 关键设计原则

1. **检索优先于生成** — 能检索到的才回答，检索不到就诚实说"未找到"，绝不编造
2. **溯源强制要求** — 每个答案必须标注来源文档 + 段落位置
3. **权限在检索层过滤** — 检索时注入权限表达式，而非生成后过滤
4. **LLM 降级兜底** — LLM 超时/失败时，降级返回检索结果原文
5. **高频问题缓存** — Redis 缓存热门问答，避免重复调用 LLM
6. **反馈闭环** — 用户标记「无用」→ 记录 → 统计 → 驱动知识库改进

### 编码规范

- 基础包名：`com.knowbrain`
- 统一返回体 `Result<T>`
- 数据库表前缀：`kb_`
- API 路径前缀：`/api/v1/`
- LLM Prompt 模板放在 `resources/prompts/` 目录
- 场景种子数据放在 `resources/scenarios/` 目录

---

## License

[Apache License 2.0](LICENSE)
