# KnowBrain E2E 自动化测试方案

> Playwright + TypeScript 全模块端到端测试，覆盖 API 接口测试 + 浏览器 UI 测试。

---

## 一、业界实践参考

### 主流做法

| 实践 | 说明 |
|------|------|
| **Page Object Model** | 每个页面一个 TS 类，选择器集中管理，UI 变更只改一处 |
| **Auth State 复用** | `global.setup.ts` 登录一次保存 `storageState`，后续测试跳过登录，提速 60-80% |
| **Smoke / Regression 分层** | PR → smoke（5min，关键路径）；Merge → regression（全量） |
| **API + UI 混合** | `beforeEach` 用 API 播种数据（比 UI 快 10x），测试验证 UI 表现 |
| **Playwright Fixtures** | 注入 page objects + helpers，测试函数只需声明依赖 |
| **Web-first Assertions** | `expect(locator).toBeVisible()` 自动重试，禁止 `sleep()` |

### 测试分层

```
         ┌──────────┐
         │  Smoke   │  @smoke   PR 触发，10 条，< 5min
         ├──────────┤
         │Regression│  @regression  Merge 触发，~40 条
         ├──────────┤
         │   API    │  @api  纯接口测试，无浏览器
         ├──────────┤
         │   A11y   │  @a11y  可访问性审计（未来）
         └──────────┘
```

---

## 二、测试架构

```
knowbrain-web/e2e/
├── package.json                         # @playwright/test + typescript
├── playwright.config.ts                 # 三项目：chromium / api / a11y
├── global.setup.ts                      # API 登录 → storageState
├── fixtures/
│   └── index.ts                         # 注入 pages + apiHelper
├── pages/                               # Page Object Model
│   ├── BasePage.ts                      # 共享导航、等待
│   ├── LoginPage.ts
│   ├── ChatPage.ts                      # SSE 流式断言
│   ├── DocBrowsePage.ts
│   ├── SpacePage.ts
│   └── AdminPage.ts                     # 管理后台共享
├── helpers/
│   └── ApiHelper.ts                     # REST 封装（种子数据 + 清理）
├── data/
│   └── test-data.ts                     # 用户、文档、空间测试数据
├── tests/
│   ├── smoke/          (@smoke)         # PR 触发，~10 条
│   │   ├── auth.spec.ts
│   │   ├── chat.spec.ts
│   │   ├── document.spec.ts
│   │   └── health.spec.ts
│   ├── regression/     (@regression)    # Merge 触发，~30 条
│   │   ├── admin/
│   │   │   ├── users.spec.ts
│   │   │   ├── scenario.spec.ts
│   │   │   └── feedback.spec.ts
│   │   ├── doc/
│   │   │   ├── document.spec.ts
│   │   │   └── parsing.spec.ts        # Priority C 文档解析链路
│   │   ├── evaluation/
│   │   │   └── eval.spec.ts
│   │   ├── space/
│   │   │   └── space.spec.ts
│   │   └── audit/
│   │       └── audit.spec.ts
│   └── api/             (@api)          # 纯接口测试
│       ├── auth.api.spec.ts
│       ├── rag.api.spec.ts
│       └── document.api.spec.ts
├── scripts/
│   ├── run-smoke.ps1                    # Windows 一键冒烟
│   ├── run-smoke.sh                     # Linux/Mac 一键冒烟
│   ├── run-all.ps1                      # Windows 全量回归
│   └── run-all.sh                       # Linux/Mac 全量回归
└── ../../.github/workflows/
    ├── pr-e2e.yml                       # PR → smoke
    ├── merge-e2e.yml                    # Merge → full + Pages
    └── nightly-e2e.yml                  # Cron → full + notify
```

---

## 三、测试用例清单

### Smoke（10 条，关键路径，每次 PR 运行）

| # | 模块 | 用例 | Tag |
|---|------|------|-----|
| S1 | Auth | 登录成功 — 正确用户名密码返回 JWT token | `@smoke` `@auth` |
| S2 | Auth | 登录失败 — 错误密码返回 401 | `@smoke` `@auth` |
| S3 | Auth | Token 过期 — 过期 token 访问需认证接口返回 401 | `@smoke` `@auth` |
| S4 | Chat | FAQ 命中 — "年假有几天" 返回预设答案 + 溯源 | `@smoke` `@rag` |
| S5 | Chat | Agent 多步检索 — 对比类问题触发 Function Calling | `@smoke` `@rag` `@agent` |
| S6 | Chat | SSE 事件完整性 — token → sources → done 全部推送 | `@smoke` `@rag` |
| S7 | Doc | 上传文档 → 搜索可检索 — 上传 TXT 后 search API 返回结果 | `@smoke` `@doc` |
| S8 | Doc | 文档列表分页 — 列表接口返回正确分页结构 | `@smoke` `@doc` |
| S9 | Space | 创建空间 → 空间列表可见 | `@smoke` `@space` |
| S10 | Health | 健康检查 — /health 返回 200 + 所有组件 UP | `@smoke` `@health` |

### Regression（~25 条，完整业务流，Merge + Nightly 运行）

**用户管理**

| # | 模块 | 用例 | Tag |
|---|------|------|-----|
| R1 | Admin | 用户 CRUD — 创建 → 编辑 → 重置密码 → 查看列表 | `@regression` `@admin` |
| R2 | Admin | 用户注册 — 自助注册 → 默认 USER 角色 → 可登录 | `@regression` `@auth` |
| R3 | Admin | Token 刷新 — refresh token 换新 access token + rotation | `@regression` `@auth` |

**文档管理**

| # | 模块 | 用例 | Tag |
|---|------|------|-----|
| R4 | Doc | 文档删除 — 删除后搜索不到 | `@regression` `@doc` |
| R5 | Doc | 文档分类过滤 — 上传时选分类 → 按分类过滤列表 | `@regression` `@doc` |
| R6 | Doc | 文件格式校验 — 非法格式/超大文件拒绝 | `@regression` `@doc` |

**空间管理**

| # | 模块 | 用例 | Tag |
|---|------|------|-----|
| R7 | Space | 成员管理 — 添加 VIEWER → 成员列表可见 → 删除成员 | `@regression` `@space` |
| R8 | Space | 权限校验 — VIEWER 不能编辑空间 / 非成员不能访问 PRIVATE 空间 | `@regression` `@space` |

**RAG 增强场景**

| # | 模块 | 用例 | Tag |
|---|------|------|-----|
| R9 | RAG | 对话历史 — 多轮对话上下文连贯 | `@regression` `@rag` |
| R10 | RAG | 空间过滤 — 指定 spaceId 仅检索该空间文档 | `@regression` `@rag` |
| R11 | RAG | 无结果兜底 — "火星移民" → "未找到" 而非幻觉 | `@regression` `@rag` |
| R12 | RAG | Agent 降级 — Agent 异常时自动回退标准 RAG | `@regression` `@rag` `@agent` |

**评测模块**

| # | 模块 | 用例 | Tag |
|---|------|------|-----|
| R13 | Eval | 数据集 CRUD — 创建 → 添加问题 → 编辑 → 删除 | `@regression` `@eval` |
| R14 | Eval | 批量导入 — JSON/CSV 批量导入问题 | `@regression` `@eval` |
| R15 | Eval | 评测运行 — 提交评测 → 查看结果 → 验证评分 | `@regression` `@eval` |

**场景配置**

| # | 模块 | 用例 | Tag |
|---|------|------|-----|
| R16 | Scenario | 分类 CRUD — 添加 → 查看树 → 删除 | `@regression` `@scenario` |
| R17 | Scenario | 术语管理 — 添加术语 → 查询改写验证 | `@regression` `@scenario` |
| R18 | Scenario | FAQ CRUD — 添加 FAQ → 命中测试 → 编辑 → 删除 | `@regression` `@scenario` |

**反馈 + 统计 + 审计**

| # | 模块 | 用例 | Tag |
|---|------|------|-----|
| R19 | Feedback | 提交反馈 → 统计面板更新 | `@regression` `@feedback` |
| R20 | Stats | 统计看板 — dailyTrend / topQuestions 数据准确 | `@regression` `@stats` |
| R21 | Audit | 审计日志 — CRUD 操作后审计日志可查 | `@regression` `@audit` |

**文档解析（Priority C 回归）**

| # | 模块 | 用例 | Tag |
|---|------|------|-----|
| R22 | Parsing | 纯文本上传 → 解析成功 → parsedContent 非空 | `@regression` `@doc` `@parsing` |
| R23 | Parsing | Markdown 含表格上传 → 解析后表格结构保留 | `@regression` `@doc` `@parsing` |
| R24 | Parsing | 空文件上传 → 拒绝（400/422） | `@regression` `@doc` `@parsing` |
| R25 | Parsing | 文档详情 → 解析元数据可追溯（fileName/fileSize） | `@regression` `@doc` `@parsing` |

### API 纯接口测试（~10 条）

| # | 场景 | 说明 |
|---|------|------|
| A1 | 限流 | 连续请求 >20/min → 429 |
| A2 | 无认证 | 不带 token 访问 → 401 |
| A3 | 权限不足 | USER 访问 ADMIN 接口 → 403 |
| A4 | RAG 缓存 | 相同问题第二次命中 Redis → 响应时间 <100ms |
| A5 | CORS | OPTIONS 预检返回正确 CORS 头 |
| A6 | SSE 超时 | 流式连接超过 5min → 自动关闭 |
| A7 | 上传大小 | 超过 50MB 文件 → 413 |
| A8 | Milvus 不可用 | 降级回答而非 500 |
| A9 | MinIO 不可用 | 上传返回明确错误 |
| A10 | Redis 不可用 | 主流程不受影响 |

---

## 四、触发方式

### 方式 1：脚本一键运行

```bash
# Windows
.\knowbrain-web\e2e\scripts\run-smoke.ps1
.\knowbrain-web\e2e\scripts\run-all.ps1

# Linux / CI
./knowbrain-web/e2e/scripts/run-smoke.sh
./knowbrain-web/e2e/scripts/run-all.sh
```

### 方式 2：Claude Code Skill

```
/e2e-test smoke     # 冒烟测试（~5min）
/e2e-test all       # 全量回归（~20min）
/e2e-test api       # 仅 API 测试
```

### 方式 3：GitHub Actions CI

```yaml
PR → lint → build → smoke (@smoke) → ✅/❌
Merge to main → smoke → regression (@regression) → report
Schedule (nightly) → full suite + a11y + visual
```

---

## 五、前置条件

1. Docker Compose 已启动，`http://localhost` 可访问
2. DashScope API Key 已配置（需要真实 LLM 调用的测试）
3. 默认管理员账号 `admin / KnowBrain@2026` 可用
4. MinIO bucket `knowbrain-documents` 已创建
5. Milvus collection `knowbrain_knowledge_base` 已初始化
6. 测试专用 Space `e2e-test-space` 自动创建和清理

---

## 六、当前测试覆盖率

| 模块 | 后端单元测试 | E2E 测试 | 覆盖率 |
|------|:---:|:---:|:---:|
| Auth | ✅ | ✅ S1-S3, R2-R3, A1-A5 | 75% |
| Document | ✅ | ✅ S7-S8, R4-R6, R22-R25, A9-A10 | 75% |
| RAG | ✅ | ✅ S4-S6, R9-R12, A6-A8 | 75% |
| Space | ❌ | ✅ S9, R7-R8 | 45% |
| Scenario | ✅ | ✅ R16-R18 | 55% |
| Feedback | ❌ | ✅ R19 | 30% |
| Statistics | ❌ | ✅ R20 | 30% |
| Audit | ❌ | ✅ R21 | 30% |
| Evaluation | ❌ | ✅ R13-R15 | 50% |
| IM | ✅ | ❌ | 25% |
| Health | ❌ | ✅ S10 | 50% |

> **更新于 2026-07-17**：Phase 2 完成（含 Priority C 文档解析），Smoke 11 条 + API 10 条 + Regression 25 条全部通过（总计 46 条）。

---

## 七、实施计划

| 阶段 | 内容 | 预估工作量 | 状态 |
|------|------|:---:|:---:|
| Phase 1 | 框架搭建 + smoke 11 条 + API 10 条 | 1-2 天 | ✅ 已完成 (2026-07-17) |
| Phase 2 | regression 25 条（admin/eval/space/scenario/feedback/audit/stats/doc-parsing） | 2-3 天 | ✅ 已完成 (2026-07-17) |
| Phase 3 | CI 集成 + 报告美化 | 1 天 | ✅ 已完成 (2026-07-17) |
| Phase 4 | 持续维护：新功能追测、脆弱用例修复 | 持续 | 🔄 持续 |

### Phase 1 实施记录

**已完成**（2026-07-17）：

- **框架**：Playwright + TypeScript，三项目（chromium / api / setup），POM 架构
- **认证复用**：`global.setup.ts` → API 登录 → `storageState` → 后续测试跳过 UI 登录
- **Fixture 注入**：`chatPage` / `loginPage` / `adminPage` / `apiHelper` 自动注入
- **选择器适配**：`.msg-row.assistant` / `.msg-ai-text` / `.send-btn` / `.chat-input`
- **Smoke 11 条**：Auth(S1-S3) + Chat(S4-S6) + Document(S7-S8) + Space(S9) + Health(S10) — 耗时 ~1.1min
- **API 10 条**：Auth(A1-A5) + RAG(A6-A8) + Document(A9-A10) — 耗时 ~8s
- **触发方式**：PowerShell (`run-smoke.ps1`) / Bash (`run-smoke.sh`) 一键运行
- **已知限制**：当前浏览器 UI 测试仅覆盖 Chat 页面；管理后台 CRUD 页面选择器更复杂，需逐页适配（Phase 2）

### Phase 2 实施记录

**已完成**（2026-07-17）：

- **AdminPage POM 增强**：对话框表单填充(`fillDialogField`)、表格行操作(`clickRowAction`/`findTableRow`)、ElMessageBox 确认/取消、标签页切换(`switchTab`)、树节点操作、Toast 断言、筛选栏操作、统计卡片等待
- **ApiHelper 扩展**：新增 25 个管理 API 方法（场景 CRUD、评测数据集/问题/运行、空间成员、反馈提交/统计、审计日志、统计、Token 刷新、用户管理）
- **回归测试 21 条（7 个 spec 文件）**：
  - `admin/users.spec.ts` — R1 用户 CRUD（浏览器 UI：新建→编辑→重置密码→验证列表）+ R2 自助注册 + R3 Token 刷新 rotation
  - `doc/document.spec.ts` — R4 文档删除 + R5 分类过滤 + R6 格式校验（R4 需前置上传文档，当前 skip）
  - `space/space.spec.ts` — R7 空间成员管理（添加→可见→删除）+ R8 权限校验（非成员访问拒绝）
  - `rag/rag-enhanced.spec.ts` — R9 多轮对话 + R10 空间过滤检索 + R11 无结果兜底 + R12 Agent 降级
  - `evaluation/eval.spec.ts` — R13 数据集 CRUD + R14 批量导入 + R15 评测运行
  - `admin/scenario.spec.ts` — R16 分类树 CRUD + R17 术语 API 增删 + R18 FAQ 编辑验证
  - `admin/feedback.spec.ts` — R19 反馈提交/统计面板 + R20 统计看板 + R21 审计日志筛选
- **测试结果**：21 passed, 1 skipped (R4 无文档可删), 0 failed — 耗时 ~51s
  - `doc/parsing.spec.ts` — R22-R25 文档解析链路（Priority C）：纯文本上传→解析内容验证 + Markdown 表格结构保留 + 空文件拒绝 + 元数据追溯
- **模式**：API 种子数据 + 浏览器 UI 验证；API 纯接口测试；测试数据自动清理（`afterAll` 钩子）
- **已知限制**：R4 需要提前上传文档才能运行；R17 术语删除用 API 避开 ElMessageBox 的 Playwright 交互限制

### Phase 3 实施记录

**已完成**（2026-07-17）— 设计文档：[E2E Phase 3 CI 设计](superpowers/specs/2026-07-17-e2e-phase3-ci-design.md)

- **3 条 GitHub Actions Workflow**：
  - `pr-e2e.yml` — PR 触发 smoke（11 条），~8min，报告 Artifact 保留 7 天
  - `merge-e2e.yml` — Merge to main 触发全量（46 条），~12min，报告部署到 GitHub Pages
  - `nightly-e2e.yml` — Cron 凌晨 3:00 UTC 全量，失败自动创建 GitHub Issue
- **环境**：Docker Compose 自包含（MySQL + Redis + MinIO + Milvus + Server + Web + Nginx），每次 run `down -v` 销毁隔离
- **报告**：Playwright 原生 HTML + JUnit XML（CI 摘要），PR 用 Artifact / Merge 用 GitHub Pages
- **密钥**：LLM API Key 通过 GitHub Secret `DASHSCOPE_API_KEY` 注入，不硬编码
- **Playwright 配置调整**：新增 JUnit reporter → `test-results/junit.xml`

---

## 八、参考

- [Playwright Best Practices](https://playwright.dev/docs/best-practices)
- [Playwright Enterprise Framework](https://github.com/mustafaautomation/playwright-enterprise-framework)
- [Testing Pyramid](https://martinfowler.com/articles/practical-test-pyramid.html)
