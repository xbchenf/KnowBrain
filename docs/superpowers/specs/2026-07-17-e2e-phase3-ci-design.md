# E2E Phase 3 — CI 集成 + 报告美化 设计文档

> 版本：V1.0 | 日期：2026-07-17 | 状态：待实施
>
> 基于用户确认的方案编写。

---

## 一、背景

KnowBrain E2E 测试体系已就绪（46 条测试、POM 架构、storageState 复用），但缺少 CI 自动触发机制。当前测试只能开发者本地手动运行，无法作为 PR 门禁或质量监控手段。

Phase 3 目标：**让测试从"能跑"升级为"自动跑 + 结果可视"**。

---

## 二、设计决策

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 触发策略 | PR smoke / Merge 全量 / 定时全量 | PR 反馈快（~8min），Merge 保质量，定时兜底 |
| 报告方案 | Playwright 原生 HTML | 零新依赖，GitHub Pages 直接托管，10 分钟配完 |
| CI 环境 | Docker Compose 自包含 | 与本地完全物理隔离（GitHub 云 runner），无需维护额外测试服务器 |
| 数据隔离 | 每次 run `docker compose down -v` | 数据卷随 run 销毁，零残留，不影响任何外部环境 |

### CI 与本地开发隔离

```
你的电脑（Docker Desktop）       GitHub 云 (Actions Runner)
┌─────────────────────┐          ┌─────────────────────┐
│ 本地开发 / 手工测试  │          │ PR/Merge/Cron 自动触发│
│ docker compose up    │    ≠     │ docker compose up     │
│ 数据持久保留         │          │ docker down -v（销毁）│
└─────────────────────┘          └─────────────────────┘
        物理隔离，互不影响
```

唯一共享项：GitHub Secret `DASHSCOPE_API_KEY`（LLM API 密钥，不涉及数据）。

---

## 三、CI 流水线设计

### 3.1 Workflow 一览

```
pr-e2e.yml       PR → smoke(11条) → Artifact报告    ~8min
merge-e2e.yml    Merge main → 全量(46条) → Pages报告 ~12min
nightly-e2e.yml  Cron 03:00 → 全量(46条) → 失败通知  ~12min
```

### 3.2 pr-e2e.yml — PR 门禁

```
触发: pull_request (opened, synchronize, reopened)
      paths: knowbrain-server/** | knowbrain-web/** | docker-compose.yml

Job: e2e-smoke
├── Checkout 代码
├── 设置 Docker Buildx（层缓存加速）
├── 注入 GitHub Secrets → .env 文件
│     DASHSCOPE_API_KEY → SPRING_AI_OPENAI_API_KEY
│     JWT_SECRET → JWT_SECRET
├── docker compose up -d（后台启动全栈）
├── 等待健康检查（轮询 /health → 200，最长 3 分钟）
├── 安装 Playwright + Chromium
├── npx playwright test --project=chromium --grep @smoke
├── 上传 Playwright HTML Report → Artifact（保留 7 天）
└── docker compose down -v（清理，含数据卷）
```

### 3.3 merge-e2e.yml — Merge 门禁 + 报告发布

```
触发: push to main
      paths: 同 pr-e2e

Job: e2e-full
├── （同 pr-e2e 的环境启动 + 等待）
├── npx playwright test（全量，chromium + api）
├── 上传 Artifact
├── docker compose down -v
└── Deploy to GitHub Pages
      └── 把 HTML Report 部署到 gh-pages 分支
```

### 3.4 nightly-e2e.yml — 定时回归

```
触发: schedule (cron: "0 3 * * *")   // 北京时间 11:00 = UTC 03:00
      workflow_dispatch（手动触发）

Job: e2e-full
├── 同 merge-e2e 的全量测试
├── 失败时：创建 GitHub Issue（含失败摘要 + 报告链接）
```

---

## 四、环境配置

### 4.1 复用现有 docker-compose.yml

不创建单独的 `docker-compose.ci.yml`。生产 compose 文件已包含所有必须服务，CI 通过环境变量差异适配：

| 差异点 | 生产 | CI |
|--------|------|----|
| LLM API Key | `.env` 文件 | GitHub Secret → CI 环境变量 |
| server/web 构建 | 本地 Docker build | CI Docker layer cache 加速 |
| 端口暴露 | 对外暴露 | 仅容器间通信（compose 内网） |
| 健康检查 | Docker healthcheck | 额外 `curl` 轮询 `/api/v1/health` |
| 持久化 | 数据卷保留 | `down -v` 销毁 |

### 4.2 关键环境变量

```yaml
# CI .env（运行时注入）
SPRING_AI_OPENAI_API_KEY: ${{ secrets.DASHSCOPE_API_KEY }}
JWT_SECRET: ci-test-jwt-secret-not-for-production
MYSQL_ROOT_PASSWORD: ci-test-mysql
REDIS_PASSWORD: ci-test-redis
MINIO_ACCESS_KEY: ci-minio
MINIO_SECRET_KEY: ci-minio-key
```

### 4.3 等待服务就绪

```bash
# CI 中的健康检查循环
for i in $(seq 1 30); do
  if curl -s http://localhost/api/v1/health | grep -q '"status":"UP"'; then
    echo "Server ready"
    break
  fi
  sleep 10
done
```

---

## 五、报告方案

### 5.1 两层报告策略

| 场景 | 方式 | 保留期 |
|------|------|:---:|
| PR | GitHub Artifact（HTML Report zip） | 7 天 |
| Merge | GitHub Artifact + GitHub Pages 部署 | 永久（覆盖） |

### 5.2 Playwright Reporter 配置

```typescript
// playwright.config.ts — CI 模式
reporter: [
  ['html', { outputFolder: 'playwright-report', open: 'never' }],
  ['list'],
  ['junit', { outputFile: 'test-results/junit.xml' }],  // CI 摘要用
],
```

### 5.3 GitHub Pages 报告入口

```
https://<org>.github.io/KnowBrain/e2e-report/
```

每次 merge 到 main 后自动更新。页面包含：
- 测试通过/失败计数
- 各 spec 耗时
- 失败用例截图 + trace（`trace: 'on-first-retry'`）
- 运行元信息（commit SHA、时间戳）

---

## 六、文件变更清单

| 类型 | 文件 | 说明 |
|------|------|------|
| 新增 | `.github/workflows/pr-e2e.yml` | PR → smoke |
| 新增 | `.github/workflows/merge-e2e.yml` | Merge → full + deploy |
| 新增 | `.github/workflows/nightly-e2e.yml` | Cron → full + notify |
| 新增 | `knowbrain-web/e2e/scripts/ci-wait.sh` | 等待服务健康检查脚本 |
| 修改 | `knowbrain-web/e2e/playwright.config.ts` | 添加 junit reporter（CI 摘要） |
| 修改 | `docs/E2E测试方案.md` | 更新 Phase 3 状态 |

---

## 七、成功标准

- [ ] PR 提测 → smoke 自动运行 → < 10 分钟内出结果 → 失败阻断 merge
- [ ] Merge 到 main → 全量自动运行 → 报告部署到 GitHub Pages
- [ ] 每天凌晨 → 全量自动跑 → 失败自动创建 Issue
- [ ] CI 每次 run 环境隔离，不产生脏数据
- [ ] 报告可公开访问（GitHub Pages），含失败截图/trace

---

## 八、不复用的内容

- ❌ 不创建单独的 `docker-compose.ci.yml`
- ❌ 不引入 Allure / Mochawesome 等新报告依赖
- ❌ 不需要外部测试服务器
- ❌ 不修改后端代码
- ❌ 不修改前端代码
