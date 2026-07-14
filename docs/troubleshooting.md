# KnowBrain 故障排查指南

常见问题诊断与解决步骤。

---

## 问答问题

### 问题：RAG 问答返回"服务暂时不可用"

**症状**：提问后系统回复"服务暂时不可用，请稍后重试"，回答标记为低置信度 + 降级。

**可能原因**：

| 原因 | 排查方式 | 解决 |
|------|---------|------|
| LLM API Key 未配置或过期 | `docker compose logs server \| grep "401\|403\|unauthorized"` | 更新 `.env` 中 `SPRING_AI_OPENAI_API_KEY` |
| LLM API 超时 | `docker compose logs server \| grep "timeout\|TimeoutException"` | 检查网络到 LLM API 的连通性，或增加 `spring.ai.openai.chat.options.timeout` |
| LLM 额度耗尽 | 检查 LLM 平台控制台余量 | 充值或切换模型 |
| 熔断器打开 | `docker compose logs server \| grep "CircuitBreaker"` | 30s 后自动恢复，频繁触发需排查上游问题 |

**验证**：
```bash
# 手动测试 LLM 连接
curl -X POST "${SPRING_AI_OPENAI_BASE_URL}/v1/chat/completions" \
  -H "Authorization: Bearer ${SPRING_AI_OPENAI_API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{"model":"qwen-max","messages":[{"role":"user","content":"hello"}]}'
```

### 问题：总是返回"未找到相关文档"

**症状**：任何问题都回复"未找到与您问题相关的文档"。

**可能原因**：

| 原因 | 排查方式 | 解决 |
|------|---------|------|
| 没有上传文档 | 检查管理后台文档列表 | 上传文档 |
| Milvus Collection 为空 | 查看 Milvus Attu 控制台 `http://localhost:9091` | 重新上传文档触发向量化 |
| 空间权限不足 | 确认用户有对应空间的读权限 | 在管理后台调整空间权限 |
| 查询与文档语言不匹配 | 中文文档用英文查询可能检索不到 | 使用文档语言提问 |

### 问题：低置信度/回答不准确

**症状**：返回了答案但标记为"低置信度"，内容可能不相关。

**解决**：
1. 补充更多覆盖该主题的文档
2. 在管理后台添加 FAQ 预设答案
3. 在术语词典中添加相关口语→正式术语映射
4. 检查检索到的文档片段是否正确（查看溯源）

---

## 文档上传问题

### 问题：上传失败/处理失败

**症状**：上传后文档状态显示"FAILED"。

**排查**：
```bash
docker compose logs server | grep "文档处理失败"
```

**常见原因**：

| 日志特征 | 原因 | 解决 |
|---------|------|------|
| `QwenVl 返回空内容` | Qwen-VL API Key 未配置 | Qwen-VL 仅 PDF 图片提取用，会自动降级到 Tika，不影响最终结果 |
| `MinIO 上传失败` | MinIO 服务不可用 | `docker compose restart minio` |
| `Embedding 调用失败` | Embedding API 不可用 | 检查 API Key 和模型名称 |
| `Milvus 写入失败` | Milvus 连接错误 | `docker compose restart milvus-standalone` |

### 问题：PDF 解析出乱码

**症状**：PDF 文档上传后解析内容为乱码或为空。

**排查**：
1. 确认 PDF 是文本型（非扫描件）。扫描件需要 Qwen-VL OCR
2. 配置 `QWEN_API_KEY` 环境变量启用视觉解析
3. 检查 PDF 是否加密或有 DRM 保护

---

## 认证问题

### 问题：登录失败

**排查**：
```bash
# 检查用户是否存在
docker compose exec mysql mysql -u root -p -e "SELECT id,username,status,role FROM knowbrain.kb_sys_user WHERE username='admin'"

# 检查登录失败日志
docker compose logs server | grep "登录失败\|login failure"
```

**限速锁定**：同一 IP 5 分钟内失败 5 次，锁定 15 分钟。
```bash
# 手动清除锁定
docker compose exec redis redis-cli DEL "kb:rate:login:lock:<IP地址>"
```

### 问题：Token 突然失效

**症状**：操作中突然跳回登录页。

**可能原因**：
- 管理员重置了你的密码（所有旧 Token 立即失效）
- 管理员禁用了你的账号
- Token 超过 24 小时过期（Access Token）

---

## 服务连接问题

### MySQL 连接失败

```bash
docker compose logs server | grep "CommunicationsException\|拒绝连接"
```

**常见原因**：
- MySQL 未完全启动就启动了 server → `docker compose restart server`
- 磁盘满，MySQL 无法写入 → 清理磁盘
- MySQL root 密码与配置不匹配

### Redis 连接失败

```bash
docker compose logs server | grep "RedisConnectionFailure\|Unable to connect"
```

**影响**：
- 登录限速降级为不限制（安全风险）
- RAG 缓存不可用（性能下降，功能不中断）
- 会话状态丢失

**解决**：`docker compose restart redis`

### Milvus 连接失败

```bash
docker compose logs server | grep "MilvusException\|UNAVAILABLE"
```

**影响**：检索功能不可用，所有问答降级返回"未找到"。

**解决**：Milvus 启动较慢（1-2 分钟），等待后重试：
```bash
docker compose restart milvus-standalone
# 等待 2 分钟后
docker compose restart server
```

---

## 性能问题

### 问答响应慢（> 10s）

```bash
# 查看耗时分布
docker compose logs server | grep "RAG耗时"
```

每个阶段参考耗时：
- 缓存检查：< 10ms
- 查询预处理（术语改写）：< 50ms
- FAQ 匹配：< 10ms
- 混合检索（Milvus）：100-500ms
- LLM 生成：2-30s（取决于模型和输出长度）

如果 LLM 耗时 > 30s，考虑：
1. 切换到更快的模型（如 qwen-turbo）
2. 降低 `temperature` 减少随机性
3. 检查 LLM API 网络延迟

### 内存持续增长

```bash
docker stats knowbrain-server
```

**排查**：
- 查看 JVM 堆使用：Prometheus → `jvm_memory_used_bytes`
- 如果持续增长不回收 → 可能存在内存泄漏，查看 heap dump
- 临时缓解：`docker compose restart server`

---

## 获取帮助

排查时收集以下信息：
```bash
# 1. 服务状态
docker compose ps

# 2. 最近 200 行日志
docker compose logs --tail=200 server > server_recent.log

# 3. 系统资源
docker stats --no-stream

# 4. 健康检查
curl http://localhost:8080/api/v1/health
```
