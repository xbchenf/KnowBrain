# KnowBrain 安全加固清单

生产环境部署前的安全检查与配置指南。

---

## 部署前检查

### 1. 默认密钥修改（必做）

| 配置项 | 默认值 | 操作 |
|--------|--------|------|
| `JWT_SECRET` | `change-me-in-production` | 生成随机 64 字符密钥：`openssl rand -base64 48` |
| `MYSQL_PASSWORD` | `knowbrain123` | 修改为强密码 |
| `REDIS_PASSWORD` | `knowbrain_redis_2024` | 修改为强密码 |
| `MINIO_ACCESS_KEY` | `minioadmin` | 修改为自定义 Access Key |
| `MINIO_SECRET_KEY` | `minioadmin` | 修改为随机 Secret Key |

```bash
# 在 .env 中修改以上所有值
vim .env
```

### 2. TLS / HTTPS（推荐）

当前默认 HTTP。生产环境建议前面加一层 Nginx 反向代理或使用 Let's Encrypt：

```nginx
# 在 docker/nginx/nginx.conf 的 server 块中添加：
listen 443 ssl;
ssl_certificate     /etc/nginx/certs/fullchain.pem;
ssl_certificate_key /etc/nginx/certs/privkey.pem;
ssl_protocols       TLSv1.2 TLSv1.3;
ssl_ciphers         HIGH:!aNULL:!MD5;
```

### 3. CORS 白名单

限制允许跨域访问的来源：

```bash
# .env
CORS_ALLOWED_ORIGINS=https://kb.yourcompany.com
```

默认值为 `http://localhost`，生产环境必须改为实际域名。

---

## 运行时加固

### 已启用的安全机制

| 机制 | 配置 | 状态 |
|------|------|------|
| JWT Token 认证 | HS256 签名，24h 过期 | ✅ |
| BCrypt 密码哈希 | `BCrypt.gensalt()` (log-rounds=10) | ✅ |
| Token 黑名单 (Redis) | 退出登录立即失效 | ✅ |
| Refresh Token 轮换 | 用过的 Refresh Token 立即作废 | ✅ |
| 密码强度校验 | 最少 8 位，必须含大小写字母+数字+特殊字符 | ✅ |
| 登录限速 (Redis) | 同 IP 5 分钟 5 次失败锁 15 分钟 | ✅ |
| CSRF 防护 | Bearer Token 认证（无 Cookie），CSRF 风险天然低 | ✅ |
| SQL 注入防护 | MyBatis-Plus 参数化查询 | ✅ |
| 用户级 Token 失效 | 禁用账号/改密码后所有旧 Token 立即失效 | ✅ |
| 请求 Trace ID | 每个请求生成 `X-Request-Id` 响应头 | ✅ |
| 审计日志 | 所有写操作记录操作人+IP+时间+结果 | ✅ |
| 敏感词过滤 | 输入预处理时过滤 | ✅ |

### 可选加固

| 措施 | 说明 | 优先级 |
|------|------|--------|
| VPN/内网部署 | 整个系统部署在企业内网，不暴露公网 | ⭐ 高 |
| API 限流 | 已启用 RAG 问答限流（默认 20/min/用户），可调整为更严格的策略 | ⭐ 中 |
| IP 白名单 | 在 Nginx 层加 IP 限制，仅允许办公网段访问 | ⭐ 中 |
| 数据库加密 | 对 MySQL 启用 TDE（透明数据加密）或磁盘加密 | ⭐ 中 |
| WAF | 前面加 Web 应用防火墙（如 ModSecurity） | ⭐ 低 |
| 访问日志审计 | 接入 SIEM 系统分析 Nginx 访问日志 | ⭐ 低 |

---

## 安全响应头（已配置 Nginx）

当前已在 Nginx 中注入以下响应头：

```
X-Frame-Options: SAMEORIGIN
X-Content-Type-Options: nosniff
X-XSS-Protection: 1; mode=block
Referrer-Policy: strict-origin-when-cross-origin
Strict-Transport-Security: max-age=31536000; includeSubDomains
```

---

## 权限模型

### 系统角色

| 角色 | 权限 |
|------|------|
| `ADMIN` | 全局管理：用户管理、空间管理、系统配置、查看所有文档 |
| `MANAGER` | 知识管理：创建/管理空间、上传/编辑文档、查看同部门文档 |
| `USER` | 基础使用：在有权空间内查看文档 + 问答 |

### 空间可见性

| 可见性 | 谁能看 |
|--------|--------|
| PUBLIC | 所有登录用户 |
| TEAM | 指定部门（含父部门层级） |
| PRIVATE | Owner + 显式成员 |

---

## 数据隔离

| 组件 | 隔离方式 |
|------|---------|
| Milvus 检索 | `space_id` 字段在查询时注入权限过滤表达式 |
| MySQL 文档 | 按 `space_id` 过滤 + PermissionService 权限校验 |
| MinIO 文件 | 通过文档 ID 关联，无直接访问入口（应用层读取） |

---

## 密码策略

在 `PasswordValidator.java` 中定义：

- 最少 8 个字符
- 必须包含大写字母
- 必须包含小写字母
- 必须包含数字
- 必须包含特殊字符（`@$!%*?&`）

---

## 备份安全

- 数据库备份文件包含用户密码哈希（BCrypt），泄漏后难以直接破解但建议加密存储备份
- MinIO 备份包含原始文档内容，备份文件应加密或存储在安全位置

---

## 安全事件响应

### 疑似账号被盗

1. 管理员立即禁用该账号：管理后台 → 用户管理 → 点击禁用
2. 所有现有 Token 自动失效（`kb:token:invalid_before:{userId}`）
3. 检查审计日志确认是否有异常操作：`kb_audit_log` 表
4. 重置该用户密码

### API Key 泄漏

1. 立即在 LLM 平台吊销旧 Key
2. 生成新 Key 并更新 `.env`
3. `docker compose restart server`
4. 检查审计日志确认泄漏期间是否有异常调用

### 发现异常流量

1. 限流器会在 20 QPM 后拒绝请求（返回 HTTP 429）
2. 在 Nginx 层添加 `limit_req_zone` 做额外层限流
3. 检查 `docker compose logs nginx | grep <异常IP>` 确认攻击来源
4. 在防火墙层封禁该 IP
