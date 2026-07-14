# KnowBrain 运维手册

日常运维操作指南，适用于已部署的生产/测试环境。

---

## 日常操作

### 服务管理

```bash
# 启动全部服务
docker compose up -d

# 查看运行状态
docker compose ps

# 查看日志（全部服务）
docker compose logs -f --tail=200

# 查看特定服务日志
docker compose logs -f server
docker compose logs -f web

# 重启单个服务（不中断整体）
docker compose restart server
docker compose restart web

# 停止全部服务
docker compose down
```

### 优雅停机

```bash
# 优雅停机（等待请求处理完毕，最多 30s）
docker compose stop server

# 立即强制停止（可能中断正在处理的请求）
docker compose kill server
```

---

## 监控

### 健康检查

```bash
# 应用健康检查
curl http://localhost:8080/api/v1/health

# 返回示例
# {"status":"UP","milvus":"connected","mysql":"connected","redis":"connected"}
```

### Prometheus 指标

关键指标（`http://localhost:8080/actuator/prometheus`）：

| 指标 | 含义 | 告警阈值 |
|------|------|---------|
| `rag_requests_total{result="success"}` | 问答成功次数 | - |
| `rag_requests_total{result="fallback"}` | LLM 降级次数 | > 5/min |
| `rag_requests_total{result="empty"}` | 检索无结果次数 | > 20/min |
| `rag_cache_hits_total` | 缓存命中次数 | - |
| `rag_search_duration_seconds` | 检索耗时 P95 | > 2s |
| `rag_llm_duration_seconds` | LLM 耗时 P95 | > 30s |
| `http_login_failures_total` | 登录失败次数 | > 10/min |

### Grafana 仪表盘

预置仪表盘位于 `docker/grafana/dashboards/`，导入后监控：
- **JVM 概览**：堆内存、GC 频率、线程数
- **RAG 性能**：QPS、检索耗时、LLM 耗时、缓存命中率
- **业务指标**：问答成功率、降级率、检索空结果率

---

## 备份

### MySQL 数据库

```bash
# 导出全库（每日建议）
docker compose exec mysql mysqldump -u root -p knowbrain > backup_$(date +%Y%m%d).sql

# 导入恢复
docker compose exec -T mysql mysql -u root -p knowbrain < backup_20260710.sql
```

### MinIO 文档

```bash
# MinIO 数据目录位于 Docker Volume
docker volume ls | grep minio

# 备份（停止 MinIO 后复制数据目录）
docker compose stop minio
cp -r /var/lib/docker/volumes/knowbrain_minio_data/_data ./minio_backup_$(date +%Y%m%d)/
docker compose start minio
```

### 自动化备份（每日凌晨 3 点）

```bash
# 在 crontab 中添加：
# 0 3 * * * cd /opt/knowbrain && docker compose exec -T mysql mysqldump -u root -p knowbrain > backups/db_$(date +\%Y\%m\%d).sql && find backups/ -name 'db_*.sql' -mtime +30 -delete
```

---

## 升级

### 小版本升级（仅代码变更，无数据库变更）

```bash
git pull
docker compose build server web
docker compose up -d server web
```

### 大版本升级（含数据库迁移）

```bash
git pull
docker compose build server web
# Flyway 会自动执行未应用的迁移脚本
docker compose up -d
# 检查迁移状态
docker compose logs server | grep "Flyway"
```

### 回滚

```bash
# 回退到指定 commit
git checkout <commit-hash>
docker compose build server web
docker compose up -d server web
# 注意：数据库迁移不可逆（Flyway 默认不允许回退）
```

---

## 日志

### 日志位置

| 日志 | 位置 |
|------|------|
| 应用日志 | `docker compose logs server` |
| 审计日志 | `kb_audit_log` 表（MySQL） |
| 搜索日志 | `kb_search_log` 表（MySQL） |
| Nginx 访问日志 | `docker compose logs nginx` |

### 日志级别调整

无需重启，通过环境变量临时调整：
```bash
# 开启 DEBUG（开发调试）
LOGGING_LEVEL_COM_KNOWBRAIN=DEBUG docker compose up -d server

# 恢复正常（INFO）
docker compose up -d server
```

---

## 容量规划

### 关键指标

| 指标 | 预警 | 扩容建议 |
|------|------|---------|
| 文档存储 (MinIO) | > 80% | 增加 Volume 或对象存储 |
| 向量条目 (Milvus) | > 100 万 | 增加 Milvus 内存 |
| 问答 QPS | > 50/s | 增加 server 实例数 |
| LLM 调用延迟 P95 | > 30s | 升级 LLM 配额或降低模型 |
| 数据库连接池使用率 | > 80% | 增加 HikariCP `maximum-pool-size` |

---

## 故障恢复

| 故障 | 恢复步骤 |
|------|---------|
| LLM API Key 过期 | 更新 `.env` 中 `SPRING_AI_OPENAI_API_KEY`，重启 server |
| Milvus Collection 损坏 | 设 `MILVUS_RECREATE_ON_STARTUP=true` 重启 → 重建 Collection → 重新上传文档 |
| MySQL 数据损坏 | 从备份恢复 → 重启全部服务 |
| 磁盘满 | 清理旧日志 → 清理 Docker 镜像 (`docker system prune -a`) → 扩展磁盘 |
