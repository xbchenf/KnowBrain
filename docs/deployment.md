# KnowBrain（知脑）部署文档

## 环境要求

| 资源 | 最低配置 | 推荐配置 |
|------|---------|---------|
| CPU | 4 核 | 8 核 |
| 内存 | 8 GB | 16 GB |
| 磁盘 | 20 GB（不含文档存储）| 50 GB+ |
| Docker | 24.0+ | 26.0+ |
| Docker Compose | 2.0+ | 2.20+ |

> **注意**：Milvus 向量数据库需要 AVX2 指令集。不支持老旧的 CPU（2014 年之前的 Intel / 2016 年之前的 AMD）。

---

## 快速开始

### 1. 克隆项目

```bash
git clone <repository-url> KnowBrain
cd KnowBrain
```

### 2. 配置环境变量

```bash
cp .env.example .env
vim .env  # 修改 SPRING_AI_OPENAI_API_KEY 为你的 LLM API Key
```

**必填项**：
- `SPRING_AI_OPENAI_API_KEY` — LLM 大模型 API Key（阿里云 DashScope / DeepSeek）

**建议修改**：
- `JWT_SECRET` — 生产环境请更换为随机字符串（如 `openssl rand -hex 32`）
- `MYSQL_ROOT_PASSWORD` — 数据库 root 密码
- `REDIS_PASSWORD` — Redis 密码

### 3. 一键启动

```bash
# Linux / macOS
chmod +x bin/start.sh
./bin/start.sh

# Windows
bin\start.bat
```

首次启动需要拉取镜像 + 构建项目（约 5-10 分钟），之后启动约 30 秒。

### 4. 访问

| 地址 | 说明 |
|------|------|
| http://localhost | 问答界面（员工使用） |
| http://localhost/admin | 管理后台（管理员用） |
| http://localhost:9001 | MinIO 控制台（文件存储管理） |
| http://localhost:3000 | Grafana 监控看板（需启用 monitoring profile） |

**默认管理员账户**：`admin` / `admin123`

---

## 服务架构

```
                    ┌──────────────┐
                    │   Nginx:80   │  网关（反向代理）
                    └──┬───┬───────┘
                       │   │
          ┌────────────┼───┼────────────┐
          │            │   │            │
     ┌────▼────┐ ┌────▼───▼────┐ ┌────▼──────────┐
     │ Web UI  │ │  Server :8080│ │ Prometheus    │
     │(Q&A+管理)│ │  Spring Boot│ │ Grafana       │
     │   :80   │ └──┬──┬──┬──┬──┘ │ (profile启用)│
     └─────────┘    │  │  │  │    └───────────────┘
                    │  │  │  │
          ┌─────────┘  │  │  └─────────┐
          │            │  │            │
     ┌────▼────┐ ┌────▼──▼──┐  ┌──────▼──────┐
     │  MySQL  │ │  Milvus  │  │    MinIO    │
     │  :3306  │ │  :19530  │  │    :9000    │
     └─────────┘ └──────────┘  └─────────────┘
          │                          │
     ┌────▼────┐                     │
     │  Redis  │  文档存储 ←─────────┘
     │  :6379  │
     └─────────┘
```

共 7 个容器（不含监控），9 个容器（含监控）：

| 容器 | 技术栈 | 端口 |
|------|--------|------|
| knowbrain-nginx | Nginx Alpine | 80 |
| knowbrain-server | Spring Boot 3.3 + Java 17 | 8080 |
| knowbrain-web | Nginx + Vue 3 | (内部) |
| knowbrain-mysql | MySQL 8.0 | 3306 |
| knowbrain-redis | Redis 7 | 6379 |
| knowbrain-milvus | Milvus 2.4 | 19530 |
| knowbrain-minio | MinIO | 9000 / 9001 |
| knowbrain-prometheus | Prometheus | 9090（profile） |
| knowbrain-grafana | Grafana | 3000（profile） |

---

## 常用命令

### 服务管理

```bash
# 启动
docker compose up -d

# 停止
docker compose down

# 重启单个服务
docker compose restart server

# 查看日志
docker compose logs -f server          # 只看后端
docker compose logs -f --tail=100      # 全部服务，最近 100 行
```

### 数据管理

```bash
# 重新初始化数据库（会删除所有数据！）
docker compose down -v
docker compose up -d
```

### 镜像更新

```bash
# 拉取最新基础镜像
docker compose pull mysql redis minio milvus

# 重新构建应用镜像
docker compose build --no-cache server web

# 重启
docker compose up -d
```

---

## 配置说明

所有配置通过 `.env` 文件的环境变量控制：

### LLM 大模型

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `SPRING_AI_OPENAI_API_KEY` | **必填** LLM API Key | (空) |
| `SPRING_AI_OPENAI_BASE_URL` | API 地址 | DashScope |
| `SPRING_AI_OPENAI_CHAT_MODEL` | 对话模型 | qwen-max |
| `SPRING_AI_OPENAI_EMBEDDING_MODEL` | 向量化模型 | text-embedding-v4 |

支持的 LLM 服务商：

| 服务商 | BASE_URL |
|--------|----------|
| 阿里云 DashScope | `https://dashscope.aliyuncs.com/compatible-mode` |
| DeepSeek | `https://api.deepseek.com` |
| 月之暗面 Moonshot | `https://api.moonshot.cn` |
| 自定义 OpenAI 兼容接口 | 填写你的地址 |

### 数据库

| 变量 | 默认值 |
|------|--------|
| `MYSQL_ROOT_PASSWORD` | knowbrain123 |
| `REDIS_PASSWORD` | knowbrain_redis_2024 |

### 对象存储

| 变量 | 默认值 |
|------|--------|
| `MINIO_ACCESS_KEY` | minioadmin |
| `MINIO_SECRET_KEY` | minioadmin |
| `MINIO_BUCKET` | knowbrain-documents |

### 向量数据库

| 变量 | 默认值 |
|------|--------|
| `MILVUS_USERNAME` | (空) |
| `MILVUS_PASSWORD` | (空) |

### 安全

| 变量 | 默认值 |
|------|--------|
| `JWT_SECRET` | change-me-in-production |
| `JWT_EXPIRE_HOURS` | 24 |

### 端口

| 变量 | 默认值 |
|------|--------|
| `NGINX_PORT` | 80 |
| `SERVER_PORT` | 8080 |
| `PROMETHEUS_PORT` | 9090 |
| `GRAFANA_PORT` | 3000 |

### 监控（可选）

```bash
# 启用 Prometheus + Grafana 监控
docker compose --profile monitoring up -d
```

- **Prometheus**：`http://localhost:9090` — 采集 JVM 指标（heap、GC、线程）+ RAG 性能指标
- **Grafana**：`http://localhost:3000` — 内置 JVM 概览 + RAG 检索看板

---

## 离线部署（无互联网环境）

### 1. 在有网环境导出镜像

```bash
# 拉取并保存基础镜像
docker pull mysql:8.0
docker pull redis:7-alpine
docker pull minio/minio:latest
docker pull minio/mc:latest
docker pull milvusdb/milvus:v2.4.0
docker pull nginx:alpine

docker save mysql:8.0 redis:7-alpine minio/minio:latest minio/mc:latest \
  milvusdb/milvus:v2.4.0 nginx:alpine -o knowbrain-images.tar

# 构建应用镜像
docker compose build server web
docker save knowbrain-server:latest knowbrain-web:latest \
  -o knowbrain-app.tar

# 复制到离线机器
# knowbrain-images.tar + knowbrain-app.tar + 项目源码
```

### 2. 在离线环境导入

```bash
docker load -i knowbrain-images.tar
docker load -i knowbrain-app.tar
cp .env.example .env
vim .env  # 配置 API Key
./bin/start.sh
```

---

## 常见问题

### Q: 启动后无法访问？

```bash
docker compose ps           # 检查各容器状态
docker compose logs nginx   # 查看网关日志
docker compose logs server  # 查看后端日志
```

### Q: Milvus 启动失败？

1. 检查 CPU 是否支持 AVX2：`grep avx2 /proc/cpuinfo`（Linux）
2. 给 Docker 分配至少 4GB 内存
3. 确认防火墙未阻止 19530 端口

### Q: 上传文档后问答无结果？

1. 确认 LLM API Key 已正确配置
2. 确认 Embedding 模型可用（`text-embedding-v4` 需要 DashScope API Key）
3. 检查文档是否解析成功：管理后台 → 文档管理 → 查看状态

### Q: 如何备份数据？

```bash
# MySQL
docker exec knowbrain-mysql mysqldump -u root -p knowbrain > backup.sql

# 文档文件
docker compose cp minio:/data ./minio-backup/

# 完整备份
docker compose down
tar -czf knowbrain-backup.tar.gz \
  backup.sql \
  ./minio-backup/ \
  ./milvus-data/ \
  .env
```

### Q: 如何升级 KnowBrain？

```bash
git pull
docker compose build --no-cache server web
docker compose up -d
```
