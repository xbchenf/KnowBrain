# 企业 IM 集成技术方案

> 版本：3.0 | 最后更新：2026-07-12
> 状态：企微 ✅ HTTP 回调 / 钉钉 ✅ Stream 模式 / 飞书 ✅ Stream 模式

---

## 一、架构总览

### 1.1 设计原则

采用 **适配器模式（Adapter Pattern）** 屏蔽企微/钉钉/飞书在消息收发、加密方式、回复机制上的差异。

**双通道架构**：
- **HTTP 回调通道**：企微使用，`ImBotController` → `ImBotDispatcher` → `WecomBotAdapter`（ImBotAdapter 接口）
- **WebSocket Stream 通道**：钉钉/飞书使用，各自 Stream Handler 直接处理 RAG 流水线，无需公网回调地址

```
┌──────────────────────────────────────────────────────────────┐
│                        HTTP 回调通道（企微）                   │
│                                                              │
│  ImBotController                                             │
│  /api/v1/im/wecom/callback  (GET=验证, POST=消息)            │
│        │                                                     │
│        ▼                                                     │
│  ImBotDispatcher                                             │
│  ① URL验证 → ② 签名校验 → ③ 解密+解析 → ④ 去重              │
│  → ⑤ 用户映射 → ⑥ 限流 → ⑦ 异步RAG+流式回复                │
│        │                                                     │
│        ▼                                                     │
│  ImBotAdapter (接口) → WecomBotAdapter                       │
└──────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│                    WebSocket Stream 通道（钉钉 + 飞书）        │
│                                                              │
│  钉钉: DingtalkStreamConfig → OpenDingTalkClient (SDK)       │
│  飞书: FeishuStreamConfig → lark.ws.Client (SDK)             │
│        │                                                     │
│        ▼  onMessage / handleMessageEvent                     │
│                                                              │
│  DingtalkStreamBotHandler / FeishuStreamBotHandler            │
│  ① 提取文本 → ② 去重 → ③ 用户映射 → ④ 限流                  │
│  → ⑤ 异步 RAG + 回复（内置 RAG 流水线）                       │
│                                                              │
│  无需公网地址 · SDK 内置鉴权重连 · 消息不丢失                  │
└──────────────────────────────────────────────────────────────┘
```

### 1.2 各平台差异速查

| 维度 | 企业微信 | 钉钉 | 飞书 |
|------|---------|------|------|
| **接入模式** | HTTP 回调（需公网地址） | **WebSocket Stream**（无需公网） | **WebSocket Stream**（无需公网） |
| 签名位置 | URL Query (`msg_signature`) | HTTP Header (`timestamp` + `sign`) | SDK 内置（无需手动验签） |
| 消息加密 | AES-256-CBC（强制） | 明文（SDK 鉴权） | 明文（SDK 鉴权） |
| 签名算法 | SHA1 | HmacSHA256 | — |
| URL 验证 | GET — 解密 `echostr` 返回明文 | 无 | POST — 返回 `{"challenge":"xxx"}` |
| 主动回复 | POST `/cgi-bin/message/send` (access_token) | POST `sessionWebhook` (临时地址, ~90min) | POST `/open-apis/im/v1/messages/{id}/reply` (tenant_access_token) |
| 被动回复 | 5 秒内同步返回加密消息 | 不支持 | 不支持 |
| 消息格式 | XML（CDATA） | JSON | JSON v2.0 |
| Token 获取 | `/cgi-bin/gettoken` (corpid + secret) | `/gettoken` (appKey + appSecret) | `/open-apis/auth/v3/tenant_access_token/internal` |
| SDK | 无（裸 HTTP） | dingtalk-stream 1.3.2 | lark-oapi SDK |
| 部门 API 限制 | 无特殊限制 | dept_id 为数字 | page_size ≤ 50，需 open_department_id |

### 1.3 代码结构

```
knowbrain-server/src/main/java/com/knowbrain/im/
├── ImBotController.java          # 回调端点（仅企微：/api/v1/im/wecom/callback）
├── ImBotDispatcher.java          # 核心调度：校验→解密→去重→映射→限流→RAG（企微通道）
├── ImUserMapping.java            # IM用户 → KB用户（自动创建+部门解析+手机号跨平台匹配）
├── ImMessageDedup.java           # 消息去重（Redis SETNX, 8h TTL）
├── ImDeptResolver.java           # IM部门ID → KB部门ID 解析
├── ImAdminController.java        # 管理后台 API（/api/v1/admin/im/*）
├── ImAdminService.java           # 管理后台业务逻辑（部门映射CRUD + 身份绑定）
├── adapter/
│   ├── ImBotAdapter.java         # 适配器接口（仅企微使用此接口）
│   ├── WecomBotAdapter.java      # 企微实现（加解密+XML解析+主动回复API）
│   ├── DingtalkBotAdapter.java   # 钉钉 REST API 工具类（用户/部门查询，非消息通道）
│   ├── DingtalkStreamConfig.java # 钉钉 Stream WebSocket 配置（@ConditionalOnProperty）
│   ├── DingtalkStreamBotHandler.java # 钉钉 Stream 消息处理器（内置 RAG 流水线）
│   ├── FeishuStreamConfig.java   # 飞书 Stream WebSocket 配置（运行时 isConfigured() 判空）
│   └── FeishuStreamBotHandler.java  # 飞书 Stream 消息处理器 + API 工具（内置 RAG 流水线）
├── entity/
│   ├── ImDeptMapping.java        # kb_im_dept_mapping 实体
│   └── ImUserIdentity.java       # kb_user_identity 实体（多平台身份关联）
└── mapper/
    ├── ImDeptMappingMapper.java  # MyBatis-Plus BaseMapper
    └── ImUserIdentityMapper.java # MyBatis-Plus BaseMapper
```

---

## 二、真机调试基础设施

### 2.1 为什么需要隧道

企业 IM 回调要求 HTTPS + 公网可达。本地开发环境（内网开发机）无法直接接收回调，需要使用内网穿透隧道。

### 2.2 隧道方案对比

| 方案 | 优点 | 缺点 | 国内可用性 |
|------|------|------|-----------|
| **cloudflared** | 免费、无需注册、自动 HTTPS | 域名随机变化（每次重启）、有 TTL 限制 | ✅ 可用 |
| ngrok | 固定子域名（付费版） | **国内被墙**（ERR_NGROK_6024） | ❌ 不可用 |
| frp | 自建、可控 | 需要公网服务器 | ✅ 需自备服务器 |

**结论：开发测试阶段使用 cloudflared 快速隧道，生产环境使用 nginx 反向代理或 frp。**

### 2.3 cloudflared 操作步骤

#### 安装

```powershell
# 从 https://github.com/cloudflare/cloudflared/releases 下载 Windows amd64 版本
# 放置到：C:\Users\Administrator\AppData\Local\cloudflared\cloudflared.exe
```

#### 启动隧道

```bash
# 假设本地服务监听 8080 端口
./cloudflared tunnel --url http://localhost:8080
```

输出示例：

```
INF | Your quick Tunnel has been created! Visit it at:
INF | https://waves-bedding-weather-dating.trycloudflare.com
```

#### 验证隧道连通性

```bash
# 通过隧道访问回调端点
curl "https://{tunnel}.trycloudflare.com/api/v1/im/wecom/callback?msg_signature=test&timestamp=1&nonce=2&echostr=test"
# 预期: HTTP 403（签名不匹配）= 隧道 + 服务都正常
```

#### 注意事项

1. **域名随机变化**：每次 `cloudflared` 重启，`trycloudflare.com` 子域名都会变化。企微后台需要同步更新回调 URL
2. **隧道 TTL**：免费隧道无 uptime 保证，长时间闲置可能断开。表现为 Cloudflare 返回 `Error 1033`
3. **IP 白名单**：企微要求配置「企业可信 IP」。cloudflared 连接多个 Cloudflare 边缘节点（如 `211.139.143.165;211.139.143.166`），需要全部加入白名单
4. **企微 IP 白名单不支持网段**：需逐个 IP 添加，用 `;` 分隔

---

## 三、企业微信后台配置

### 3.1 前提条件

- 企业微信管理后台权限
- 已完成企业认证（才能使用自建应用消息推送）
- 自建应用已创建（AgentID + Secret 已分配）

### 3.2 配置项一览

| 配置项 | 后台位置 | 说明 | 示例值 |
|--------|---------|------|--------|
| CorpID | 我的企业 → 企业信息 | 企业唯一标识 | `wwbe2efe5b28ee2e11` |
| AgentID | 应用管理 → 自建应用 | 应用 ID | `1000002` |
| Secret | 应用管理 → 自建应用 → Secret | 应用密钥（获取 AccessToken） | `icX4uP-RxommIBNVnwCKHXNZlQybAIhFE3Hfzz9bHSs` |
| Token | 应用管理 → 自建应用 → 接收消息 | 回调签名 Token | `knowbrain2026`（自定义字符串） |
| EncodingAESKey | 应用管理 → 自建应用 → 接收消息 | 消息加解密密钥（43 字符） | 点击「随机生成」获取 |
| 回调 URL | 应用管理 → 自建应用 → 接收消息 | 服务器回调地址 | `https://{tunnel}.trycloudflare.com/api/v1/im/wecom/callback` |
| 企业可信 IP | 应用管理 → 自建应用 → 企业可信 IP | 回调来源 IP 白名单 | cloudflared 的多个出口 IP |

### 3.3 配置步骤

1. **获取 CorpID**：管理后台 → 我的企业 → 企业信息 → 复制企业 ID
2. **创建/打开自建应用**：管理后台 → 应用管理 → 自建 → 创建应用 → 记录 AgentID 和 Secret
3. **配置接收消息**：
   - URL：填入 cloudflared 隧道地址 + `/api/v1/im/wecom/callback`
   - Token：自定义任意字符串（如 `knowbrain2026`）
   - EncodingAESKey：点击「随机生成」按钮
   - 点击「保存」→ 企微立即向该 URL 发起 **GET 请求做 URL 验证**
4. **配置企业可信 IP**：填入 cloudflared 隧道出口 IP（可查询 `ifconfig.me`）
5. **设置可见范围**：应用管理 → 自建应用 → 可见范围 → 添加需要使用 Bot 的员工/部门
6. **员工在通讯录中查看**：进入「通讯录」→ 找到应用，发消息测试

### 3.4 URL 验证过程

```
企微后台                        cloudflared                    KnowBrain Server
   │                                │                                │
   │  GET /api/v1/im/wecom/callback │                                │
   │  ?msg_signature=xxx            │                                │
   │  &timestamp=xxx                │                                │
   │  &nonce=xxx                    │                                │
   │  &echostr=xxx                  │                                │
   │───────────────────────────────>│───────────────────────────────>│
   │                                │                                │
   │                                │  ① 验证签名:                   │
   │                                │     SHA1(sort(token, ts,       │
   │                                │          nonce, echostr))      │
   │                                │     == msg_signature ?         │
   │                                │                                │
   │                                │  ② AES解密 echostr → 明文      │
   │                                │                                │
   │  200 OK (明文)                 │                                │
   │<───────────────────────────────│<───────────────────────────────│
   │                                │                                │
   │  ✓ 保存成功                    │                                │
```

**关键点**：
- Token + EncodingAESKey **必须与服务器配置完全一致**，否则 URL 验证失败
- 验证成功后，后续 POST 消息才会被推送
- 如果修改了 Token/EncodingAESKey，需要**重新保存**触发验证

---

## 四、应用配置

### 4.1 application.yml 配置项

```yaml
# 企业 IM Bot 集成（企微/钉钉/飞书）
im:
  enabled: ${IM_ENABLED:false}
  wecom:
    enabled: ${WECOM_ENABLED:false}
    corp-id: ${WECOM_CORP_ID:}
    agent-id: ${WECOM_AGENT_ID:}
    secret: ${WECOM_SECRET:}
    callback-token: ${WECOM_TOKEN:}
    encoding-aes-key: ${WECOM_ENCODING_AES_KEY:}
  dingtalk:
    enabled: ${DINGTALK_ENABLED:false}
    app-key: ${DINGTALK_APP_KEY:}
    app-secret: ${DINGTALK_APP_SECRET:}
    robot-code: ${DINGTALK_ROBOT_CODE:}
  feishu:
    enabled: ${FEISHU_ENABLED:false}
    app-id: ${FEISHU_APP_ID:}
    app-secret: ${FEISHU_APP_SECRET:}
```

> 未配置的平台不会自动启用，不影响主服务启动。
> 钉钉/飞书使用 WebSocket Stream 模式，无需公网回调地址，无需 cloudflared 隧道。

### 4.2 本地开发启动命令

```bash
cd knowbrain-server

# --- 企微 Bot ---
export IM_ENABLED=true
export WECOM_ENABLED=true
export WECOM_CORP_ID=wwbe2efe5b28ee2e11
export WECOM_AGENT_ID=1000002
export WECOM_SECRET=icX4uP-RxommIBNVnwCKHXNZlQybAIhFE3Hfzz9bHSs
export WECOM_TOKEN=knowbrain2026
export WECOM_ENCODING_AES_KEY=ZRgcriIpIQVi43ZA7fzd39biSIu84ARzAeIUtMxv5qs

# --- 钉钉 Stream Bot（可选） ---
# export DINGTALK_ENABLED=true
# export DINGTALK_APP_KEY=dingxxxxxxxxxxxx
# export DINGTALK_APP_SECRET=your-secret
# export DINGTALK_ROBOT_CODE=your-robot-code

# --- 飞书 Stream Bot（可选） ---
# export FEISHU_ENABLED=true
# export FEISHU_APP_ID=cli_xxxxxxxxxxxx
# export FEISHU_APP_SECRET=your-secret

# --- LLM 服务配置（DashScope） ---
export SPRING_AI_OPENAI_API_KEY=sk-fdab3a3afeb147d6b939ea8bf04ba810
export SPRING_AI_OPENAI_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode
export SPRING_AI_OPENAI_CHAT_MODEL=qwen-max
export SPRING_AI_OPENAI_EMBEDDING_MODEL=text-embedding-v4

export JWT_SECRET=change-me-in-production
export CORS_ALLOWED_ORIGINS=http://localhost

mvn spring-boot:run -Dspring-boot.run.profiles=dev -DskipTests
```

> **安全提醒**：敏感配置（Secret、EncodingAESKey、API Key）**绝不提交到代码仓库**。生产环境通过 Docker 环境变量或 K8s Secret 注入。
> 钉钉/飞书使用 WebSocket Stream 模式，本地开发**无需启动 cloudflared 隧道**，开箱即用。

### 4.3 Docker Compose 环境变量（生产环境参考）

```yaml
# docker-compose.yml 中的 knowbrain-server 服务
environment:
  # 企微
  WECOM_ENABLED: "true"
  WECOM_CORP_ID: wwbe2efe5b28ee2e11
  WECOM_AGENT_ID: "1000002"
  WECOM_SECRET: ${WECOM_SECRET}
  WECOM_TOKEN: ${WECOM_TOKEN}
  WECOM_ENCODING_AES_KEY: ${WECOM_ENCODING_AES_KEY}
  # 钉钉
  DINGTALK_ENABLED: "true"
  DINGTALK_APP_KEY: ${DINGTALK_APP_KEY}
  DINGTALK_APP_SECRET: ${DINGTALK_APP_SECRET}
  DINGTALK_ROBOT_CODE: ${DINGTALK_ROBOT_CODE}
  # 飞书（环境变量 → Spring 双映射，绕过占位符解析时序问题）
  FEISHU_ENABLED: ${FEISHU_ENABLED}
  FEISHU_APP_ID: ${FEISHU_APP_ID}
  FEISHU_APP_SECRET: ${FEISHU_APP_SECRET}
  IM_FEISHU_ENABLED: ${FEISHU_ENABLED}
  IM_FEISHU_APP_ID: ${FEISHU_APP_ID}
  IM_FEISHU_APP_SECRET: ${FEISHU_APP_SECRET}
  # LLM
  SPRING_AI_OPENAI_API_KEY: ${DASHSCOPE_API_KEY}
  SPRING_AI_OPENAI_BASE_URL: https://dashscope.aliyuncs.com/compatible-mode
  SPRING_AI_OPENAI_CHAT_MODEL: qwen-max
  SPRING_AI_OPENAI_EMBEDDING_MODEL: text-embedding-v4
```

> **飞书双映射说明**：`FEISHU_*` 被 Spring 用于 `application.yml` 的 `${FEISHU_*}` 占位符，`IM_FEISHU_*` 直接映射到 `im.feishu.*` 属性。因为 `application.yml` 中 `${FEISHU_ENABLED}` 占位符在 Spring Condition 评估之前解析，需通过 Docker 环境变量同时注入两种形式以确保配置正确传递。
>
> 钉钉和飞书 Stream 模式在 Docker 中无需额外网络配置——出境 WebSocket 即可工作，无需开放入站端口或公网 IP。

---

## 五、加解密详解

### 5.1 企微加解密规范

企业微信使用 **AES-256-CBC + SHA1** 保护回调消息，格式如下：

```
加密结构（企微侧）:
  plain = random(16B) + msg_len(4B, 大端序/网络序) + msg + corpId
  → AES-256-CBC 加密（key=aesKey, iv=aesKey[0:16]）
  → Base64 编码
  → 放入 XML: <Encrypt><![CDATA[base64密文]]></Encrypt>

解密结构（服务端）:
  XML 中提取 <Encrypt> 内容
  → Base64 解码 → byte[] ciphertext
  → AES-256-CBC 解密（key=aesKey, iv=aesKey[0:16]） → byte[] plain
  → 按结构解析：
       bytes[0:16]           → 随机数（忽略）
       bytes[16:20]          → msgLen（大端序 int）
       bytes[20:20+msgLen]   → 消息明文（XML）
       bytes[20+msgLen:]     → corpId + 随机填充
```

### 5.2 AES 密钥派生

```java
// EncodingAESKey 为 43 字符的 Base64 字符串（不含尾随 '='）
String encodingAesKey = "ZRgcriIpIQVi43ZA7fzd39biSIu84ARzAeIUtMxv5qs"; // 43 chars
byte[] aesKey = Base64.decode(encodingAesKey + "=");               // → 32 bytes (AES-256)
// IV = aesKey 前 16 字节
```

### 5.3 SHA1 签名算法

```java
// 企微签名 = SHA1(按字典序排序后拼接的 token, timestamp, nonce, encrypt)
// 参与排序的是参数值本身（非 URL-encoded）
String[] items = { token, timestamp, nonce, encrypt };
Arrays.sort(items);                              // 字典序排序
String raw = String.join("", items);             // 直接拼接
String signature = sha1(raw);                    // SHA1 → 十六进制小写

// 验证: computed_signature == request.getParameter("msg_signature")
```

**关键细节**：
- encrypt 字段用于签名计算时取 **XML 中 `Encrypt` 节点的原始 Base64 文本**
- URL 验证时用 `echostr` 参数值替代 encrypt 参与签名

### 5.4 BadPaddingException 问题（实战踩坑）

#### 问题现象

- URL 验证（GET echostr 解密）正常 ✅
- 消息回调（POST Encrypt 解密）失败 ❌：`javax.crypto.BadPaddingException: Given final block not properly padded`

#### 排查过程

1. **排除密钥错误**：URL 验证使用同一把 EncodingAESKey 解密 echostr 成功 → 密钥正确
2. **排除 Base64 问题**：POST 消息的 Encrypt 值解码正常，384 字节
3. **核心诊断**：写独立 Java 测试，用 `AES/CBC/NoPadding` 直接解密 → **XML 完整可读！**

```java
// NoPadding 解密：384 字节 → 结构完整
// random(16) + msgLen(=321, 4B) + msg(321B, 有效XML) + corpId(18B) + padding(25B)
// padding 全部为 0x19 (= 25)
```

4. **根因定位**：`0x19 = 25 > 16 (AES blockSize)`。Java `PKCS5Padding` 强制要求 padding 值 ∈ [1, blockSize]。企微部分消息的 padding 值超出 16，触发 `BadPaddingException`。

#### 修复方案

**改用 `AES/CBC/NoPadding` + 按 `msgLen` 手动提取消息，不依赖 padding 验证：**

```java
String aesDecrypt(String encrypted, String receiveId) {
    byte[] ciphertext = Base64.decode(encrypted);

    // 关键修改：NoPadding 而非 PKCS5Padding
    Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
    SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
    IvParameterSpec ivSpec = new IvParameterSpec(aesKey, 0, 16);
    cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

    byte[] decrypted = cipher.doFinal(ciphertext);

    // 按 msgLen 手动定位消息边界（不依赖 padding）
    int msgLen = bytesToInt(decrypted, 16);   // 跳过 16 字节随机数
    String msg = new String(decrypted, 20, msgLen, StandardCharsets.UTF_8);
    String tail = new String(decrypted, 20 + msgLen,
            decrypted.length - 20 - msgLen, StandardCharsets.UTF_8);

    // tail 可能含 padding 字节，用 startsWith 验证 recevieId
    if (receiveId != null && !tail.startsWith(receiveId)) {
        log.warn("[企微] receiveid 不匹配: expected={}, tailStart={}",
                receiveId, tail.substring(0, Math.min(receiveId.length(), tail.length())));
    }

    return msg;
}
```

> **教训**：消息协议中定义了显式长度字段（`msgLen`）时，应优先使用长度字段定位边界，而非依赖 padding 验证。第三方加密实现不一定完全遵守 PKCS 规范。

#### 为什么 echostr 不出问题？

echostr（URL 验证）是随机短字符串，加密前 plaintext 短，padding 字节数 ≤ 16。消息回调的 plaintext（XML body + corpId）长度大，padding 字节数可能超过 16。

---

## 六、消息处理流水线

### 6.1 ImBotDispatcher 完整流程

```
ImBotController.wecomCallback(request, body)
  │
  ▼
ImBotDispatcher.handle("wecom", request, body)
  │
  ├─ Step ①: handleUrlVerification()
  │     request.method == "GET" + 有 echostr → 验证签名 + 解密 → 返回明文
  │     非 GET → 返回 null → 继续消息处理
  │
  ├─ Step ②: verifySignature()
  │     从 Query String: msg_signature, timestamp, nonce
  │     从 XML body: extractXmlField("Encrypt")
  │     SHA1(sort(token, timestamp, nonce, encrypt)) == msg_signature ?
  │     不匹配 → throw BizException(403)
  │
  ├─ Step ③: adapter.parse() — 解密 + XML → ImIncomingMessage
  │     见第五章加解密 + 5.6 XML 解析
  │
  ├─ Step ④: dedup.markAndCheck(platform, messageId)
  │     Redis SETNX "kb:im:dedup:wecom:{msgId}" TTL=8h
  │     key 已存在 → 返回 "" （平台重试消息，丢弃）
  │
  ├─ Step ⑤: userMapping.resolveUserId(platform, fromUserId, fromUserName)
  │     见第七章 IM 用户 → KB 用户映射
  │
  ├─ Step ⑥: rateLimiter.tryAcquire(kbUserId)
  │     Redis INCR "kb:rate:rag:{userId}" 窗口=60s
  │     超限 → sendReply("提问太频繁了") → return
  │
  ├─ Step ⑦: permissionService.getAccessibleSpaceIds(kbUserId)
  │     用户无部门 → 仅 PUBLIC
  │     用户有部门 → PUBLIC + 该部门可见的 TEAM 空间
  │
  └─ Step ⑧: CompletableFuture.runAsync → executeRagAndReply
        │
        ├─ ctx = ragService.chatStream(question, spaceIds)
        ├─ allTokens = ctx.tokens().collectList()（60s 超时）
        │
        ├─ size == 1: 非流式（FAQ/兜底）
        │     sendReply(ImOutgoingMessage.single(content))
        │
        └─ size > 1: 流式（LLM 生成）
              → streamFirst("正在检索知识库…")
              → 每 80 token: streamSegment(buffer)
              → streamFinal(content + 溯源链接)
```

### 6.2 非流式 vs 流式消息判断

```java
// ImBotDispatcher.executeRagAndReply()
List<String> allTokens = ctx.tokens().collectList().block(Duration.ofSeconds(60));

if (allTokens == null || allTokens.isEmpty()) {
    // 无 token → 错误
    adapter.sendReply(ImOutgoingMessage.error("系统处理出错了"), msg);
} else if (allTokens.size() == 1) {
    // 单 token = FAQ 预设答案 / LLM 兜底 / 模型不支持流式
    // → 直接发送一条完整消息，不显示"思考中"
    adapter.sendReply(ImOutgoingMessage.single(content + sources), msg);
} else {
    // 多 token = LLM 流式生成
    // → "思考中" → 逐段推送 → 最终完整结果 + 溯源
    adapter.sendReply(ImOutgoingMessage.streamFirst("正在检索知识库… 🔍", msgId), msg);
    // 每 80 token 推送一段
    adapter.sendReply(ImOutgoingMessage.streamSegment(buffer, seq++, msgId), msg);
    // 最后帧：完整答案 + 参考来源
    adapter.sendReply(ImOutgoingMessage.streamFinal(content + sources, docList, seq, msgId), msg);
}
```

> **关于重复回复 Bug**：旧实现使用 Reactor `doOnNext` + `doOnComplete` 发送，非流式场景下两者都触发发送，导致 FAQ/兜底消息重复。修复方式：先 `collectList()` 收集全部 token 再判断流式/非流式，非流式只调 `single()` 一次。

### 6.3 为什么统一异步回复

企微有 5 秒被动回复窗口（同步返回加密消息即可在客户端直接显示），但 RAG 生成通常超过 5 秒。KnowBrain 统一使用**异步主动推送**模式：
- `ImBotController` 收到消息后立即返回空字符串
- RAG 执行和回复在 `CompletableFuture` 中异步完成
- 通过 `POST /cgi-bin/message/send` 主动推送到企微客户端

### 6.4 XML 消息解析

```java
// WecomBotAdapter.parseMessageXml() — 解密后的 XML → ImIncomingMessage

// 企微 XML 格式:
// <xml>
//   <ToUserName><![CDATA[corpId]]></ToUserName>
//   <FromUserName><![CDATA[ChenXiaoBing]]></FromUserName>
//   <CreateTime>1783754331</CreateTime>
//   <MsgType><![CDATA[text]]></MsgType>
//   <Content><![CDATA[用户问题]]></Content>
//   <MsgId>7661166516751493203</MsgId>
//   <AgentID>1000002</AgentID>
// </xml>

// 关键处理:
// 1. MsgType != "text" → 忽略（不支持图片/语音/文件）
// 2. Content 去除 "@机器人名 " 前缀
// 3. CreateTime 秒级 → ×1000 转毫秒
```

---

## 七、IM 用户 → KB 用户映射

### 7.1 自动创建策略

```
IM 用户首次发消息
  │
  └─ ImUserMapping.resolveUserId(platform, userId, userName)
        │
        ├─ 查 kb_sys_user WHERE username = "im_{platform}_{userId}"
        │     ├─ 存在 + ACTIVE → 返回 kbUserId（若 deptId=null → 懒更新）
        │     └─ 存在 + DISABLED → 日志 warn，返回 kbUserId
        │
        └─ 不存在 → 自动创建
              │
              ├─ username = "im_wecom_ChenXiaoBing"
              ├─ passwordHash = ""（仅 IM 身份认证）
              ├─ role = USER
              ├─ 显示名: 优先 IM 消息中的 fromUserName → API 获取 → fallback 使用 username
              ├─ deptResolver.resolveKbDeptId(platform, userId)
              │     ├─ 命中 → departmentId = kbDeptId
              │     └─ 未命中/失败 → departmentId = null（PUBLIC only）
              └─ insert → 返回 kbUserId
```

### 7.2 命名规则

```java
// ImUserMapping.buildUsername()
String prefix = "im_";
String safeId = imUserId.replaceAll("[^a-zA-Z0-9_-]", "_");
// 截断过长 ID（保留后 30 字符）
if (safeId.length() > 30) safeId = safeId.substring(safeId.length() - 30);
// 结果示例: "im_wecom_ChenXiaoBing"
return prefix + platform + "_" + safeId;
```

### 7.3 显示名获取

企微消息回调 XML **不包含用户显示名**（只有 `FromUserName` = UserId），需要通过 API 获取：

```java
// WecomBotAdapter.fetchUserName(userId)
GET https://qyapi.weixin.qq.com/cgi-bin/user/get?access_token=TOKEN&userid={userId}
// 响应: { "errcode": 0, "name": "陈小兵", "department": [3], ... }
```

---

## 八、部门映射（IM 部门 → KB 部门）

### 8.1 数据库表

```sql
-- kb_im_dept_mapping（Flyway V4 迁移）
CREATE TABLE IF NOT EXISTS `kb_im_dept_mapping` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `platform`         VARCHAR(20)  NOT NULL COMMENT 'IM平台: wecom / dingtalk / feishu',
    `external_dept_id` VARCHAR(100) NOT NULL COMMENT 'IM平台侧部门ID',
    `kb_dept_id`       BIGINT       NOT NULL COMMENT '对应 KB 部门 ID (kb_department.id)',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_platform_dept` (`platform`, `external_dept_id`),
    KEY `idx_kb_dept_id` (`kb_dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IM平台部门→KB部门映射表';
```

### 8.2 解析流程

```
ImDeptResolver.resolveKbDeptId("wecom", "ChenXiaoBing")
  │
  ├─ ① 调企微 API: /cgi-bin/user/get?userid=ChenXiaoBing
  │     响应: { "department": [1, 2], "name": "陈小兵" }
  │     失败 → return null（不阻塞消息处理）
  │
  ├─ ② 遍历 department IDs [1, 2]
  │     SELECT * FROM kb_im_dept_mapping
  │     WHERE platform='wecom' AND external_dept_id='1'
  │       → 命中: kb_dept_id=8 → return 8
  │
  └─ ③ 全部未命中 → return null（仅 PUBLIC 空间可访问）
```

### 8.3 配置示例

```sql
-- 查询企微部门 ID（管理后台 → 通讯录 → 各部门 URL 中的 partyid）
-- 查询 KB 内部部门 ID（管理后台 → 组织管理 → 部门管理）

INSERT INTO kb_im_dept_mapping (platform, external_dept_id, kb_dept_id) VALUES
  ('wecom', '1',  1),   -- 企微部门1（公司根）    → KB部门1（总公司）
  ('wecom', '2',  9),   -- 企微部门2（人力资源部） → KB部门9（人力资源部）
  ('wecom', '3',  8),   -- 企微部门3（技术部）     → KB部门8（技术部）
  ('wecom', '4', 12),   -- 企微部门4（产品部）     → KB部门12（产品部）
  ('wecom', '5', 10),   -- 企微部门5（销售部）     → KB部门10（销售部）
  ('wecom', '6', 13);   -- 企微部门6（财务部）     → KB部门13（财务部）
```

### 8.4 权限效果

| 场景 | departmentId | 可访问空间 |
|------|-------------|-----------|
| 新用户 + 映射命中 | 8（技术部） | PUBLIC + 可见范围 =「技术部」的 TEAM 空间 |
| 新用户 + 映射未命中 | null | 仅 PUBLIC |
| 已有用户 + deptId=null + 懒更新成功 | 更新为映射值 | PUBLIC + 对应部门 TEAM 空间 |
| 已有用户 + 已有 deptId | 不变 | 不变 |

---

## 九、消息去重 + 限流

### 9.1 消息去重

各 IM 平台在以下场景会重复推送同一条消息：

| 平台 | 重试策略 |
|------|---------|
| 企微 | 回调 5 秒未响应 → 重试 |
| 钉钉 | HTTP 非 200 → 递增间隔重试 |
| 飞书 | 3s / 15s / 5min / 1h / 6h 共 5 次 |

**实现**：Redis `SETNX` 原子操作，Key = `kb:im:dedup:{platform}:{messageId}`，TTL = 8 小时（覆盖飞书最长重试窗口）。

```java
// ImMessageDedup.markAndCheck()
public boolean markAndCheck(String platform, String messageId) {
    String key = "kb:im:dedup:" + platform + ":" + messageId;
    Boolean success = redis.opsForValue().setIfAbsent(key, "1", Duration.ofHours(8));
    return Boolean.TRUE.equals(success);  // true=首次, false=重复
}
```

### 9.2 限流

防止恶意用户通过 IM Bot 大量调用 RAG 消耗 LLM Token。

```java
// RateLimiter — Redis Lua 固定窗口，基于 kbUserId
// Key: kb:rate:rag:{kbUserId}
// 窗口: 60s, 默认限额: 20次/分钟
// 超限 → 回复"提问太频繁了，请稍后再试 ⏳"
```

---

## 十、回复消息

### 10.1 企微主动回复 API

```json
POST https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token={token}

{
    "touser": "ChenXiaoBing",
    "msgtype": "markdown",
    "agentid": 1000002,
    "markdown": {
        "content": "**KnowBrain 回答**\n\nVPN 配置步骤如下：\n\n1. ...\n\n---\n📖 **参考来源**：\n1. IT运维手册"
    }
}
```

### 10.2 消息格式约定

- 固定前缀：`**KnowBrain 回答**`
- 流式进行中：末尾追加 `⏳ 回答生成中…`
- 完成时：追加 `📖 参考来源` + 文档标题列表 + 相关度百分比
- 错误时：`抱歉，系统处理出错了，请稍后重试。`

### 10.3 流式回复（企微当前实现）

企微不支持消息编辑 API，所以流式回复通过连续发送多条消息模拟：
1. `streamFirst` → 发送 "正在检索知识库… 🔍"
2. `streamSegment` → 每 80 token 发送一次带序号的片段
3. `streamFinal` → 发送完整答案 + 溯源链接

---

## 十一、AccessToken 管理

### 11.1 获取

```http
GET https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid={corpId}&corpsecret={secret}
→ { "errcode": 0, "access_token": "xxx", "expires_in": 7200 }
```

### 11.2 缓存

```java
// WecomBotAdapter — 内存缓存 + double-checked locking
// 有效期 7200s，提前 5 分钟（300s）刷新
private volatile String accessToken;
private volatile long accessTokenExpiry;

private String getAccessToken() {
    if (accessToken != null && System.currentTimeMillis() < accessTokenExpiry - 300_000) {
        return accessToken;  // 缓存命中
    }
    synchronized (this) {
        // double-check → 调 API 刷新
    }
}
```

> **生产多实例**：建议将 AccessToken 改为 Redis 共享缓存，避免每个实例独立刷新（企微每日 token 获取次数有上限 2000 次/天）。

---

## 十二、单元测试

### 12.1 测试覆盖

| 测试类 | 内容 | 用例数 |
|--------|------|--------|
| `WecomBotAdapterTest` | SHA1 签名、AES 加解密往返、XML 解析、签名验证 | 11 |
| `ImDeptResolverTest` | 映射命中、未命中、空部门、未知平台、API 异常、边界值 | 10 |

### 12.2 运行

```bash
mvn test -Dtest="WecomBotAdapterTest,ImDeptResolverTest"
# Tests run: 21, Failures: 0, Errors: 0, Skipped: 0 ✅
```

---

## 十三、排障指南

### 13.1 常见问题速查

| 现象 | 可能原因 | 排查步骤 |
|------|---------|---------|
| 企微发消息无响应 | cloudflared 隧道过期 | `curl` 隧道 URL → 若返回 Cloudflare 1033 错误 → 重启 cloudflared |
| 企微发消息无响应 | 服务器未启动 | `netstat -ano \| findstr "8080"` → 无 LISTEN → 启动服务器 |
| URL 验证保存失败 | Token / EncodingAESKey 不匹配 | 对比企微后台配置与 `WECOM_TOKEN` / `WECOM_ENCODING_AES_KEY` 环境变量 |
| URL 验证「openapi 回调地址请求不通过」 | IP 不在企微白名单 | 将 cloudflared 出口 IP（查 `ifconfig.me`）加入「企业可信 IP」 |
| 解码失败 `BadPaddingException` | 企微非标准 PKCS7 padding | 代码已修复为 `NoPadding`，参见第五章 |
| 消息回复两遍 | 旧版代码 doOnNext + doOnComplete | 已修复，升级到 collectList 判断版本 |
| 用户只能搜到 PUBLIC 空间 | 部门映射未配置 | 检查 `kb_im_dept_mapping` 表 + 确认企微 API 能通 |
| `WECOM_TOKEN` 加载为空 | 环境变量未设置 | 检查 `application.yml` 中 `${WECOM_TOKEN:}` 是否正确读取 |

### 13.2 诊断脚本

```java
// 独立 Java 程序，验证密钥和加解密
// 用 NoPadding 解密 POST 消息的 Encrypt 字段，查看原始结构
String postEncrypt = "TXa0sKNK5Nf..."; // 从日志中复制
byte[] aesKey = Base64.getDecoder().decode(encodingAesKey + "=");
byte[] ciphertext = Base64.getDecoder().decode(postEncrypt);

Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"),
        new IvParameterSpec(aesKey, 0, 16));
byte[] decrypted = cipher.doFinal(ciphertext);

int msgLen = bytesToInt(decrypted, 16);
String msg = new String(decrypted, 20, msgLen, StandardCharsets.UTF_8);

// msg 为有效 XML → 解密正确，问题在 padding（已修复）
// msg 为乱码 → 密钥错误或 ciphertext 损坏
```

### 13.3 网络连通性验证

```bash
# ① 本地服务是否正常
curl "http://localhost:8080/api/v1/im/wecom/callback?msg_signature=test&timestamp=1&nonce=2&echostr=test"
# 预期: HTTP 403 {"code":403,"message":"URL 验证签名不匹配"} = 正常

# ② 隧道是否正常
curl "https://{tunnel}.trycloudflare.com/api/v1/im/wecom/callback?msg_signature=test&timestamp=1&nonce=2&echostr=test"
# 预期: HTTP 403（同①）
# 异常: HTTP 000 (SSL error) / Error 1033 Cloudflare Tunnel error / timeout
```

---

## 十四、钉钉 Bot 集成

> 官方文档：[机器人接收消息](https://open.dingtalk.com/document/orgapp/receive-message) | [Stream 模式介绍](https://open.dingtalk.com/document/resourcedownload/introduction-to-stream-mode)
> 实现状态：✅ 已完成（Stream 模式）

### 14.1 钉钉 vs 企微 核心差异

| 维度 | 企业微信 | 钉钉 |
|------|---------|------|
| 消息加密 | AES-256-CBC 强制加密 XML | **明文 JSON**，仅签名校验 |
| URL 验证 | GET → 解密 echostr → 返回明文 | **无**（HTTP 回调模式无需验证） |
| 签名位置 | URL Query String (`msg_signature`) | **HTTP Header** (`timestamp` + `sign`) |
| 签名算法 | SHA1(sort(token, ts, nonce, encrypt)) | **HmacSHA256**(timestamp+"\n"+appSecret, appSecret) → Base64 |
| 时间窗口 | 无（依赖签名防重放） | **±1 小时** |
| 回复方式 | 主动 API（access_token，7200s 有效） | **sessionWebhook**（消息自带，约 90 分钟有效） |
| 消息格式 | XML（CDATA 包裹） | **JSON** |
| 用户标识 | FromUserName（明文 userid） | **senderStaffId**（需发布后才有）+ senderId（加密） |
| 显示名 | 需额外 API 获取 | **senderNick** 直接包含在回调 body 中 |

### 14.2 消息格式（官方文档验证）

```json
{
    "msgtype": "text",
    "text": { "content": "用户发送的消息" },
    "msgId": "msg0xxxxx",
    "createAt": 1613630252678,
    "conversationType": "2",
    "conversationId": "xxx",
    "conversationTitle": "群名称（群聊时有）",
    "senderId": "$:LWCP_v1:$Ff09GIxxxxx",
    "senderStaffId": "user123",
    "senderNick": "张三",
    "senderCorpId": "dinge8a565xxxx",
    "isAdmin": true,
    "isInAtList": true,
    "atUsers": [ { "dingtalkId": "xxx", "staffId": "xxx" } ],
    "chatbotUserId": "$:LWCP_v1:$Cxxxxx",
    "chatbotCorpId": "dinge8a565xxxx",
    "sessionWebhook": "https://oapi.dingtalk.com/robot/sendBySession?session=xxxxx",
    "sessionWebhookExpiredTime": 1613635652738
}
```

**关键字段说明**：

| 字段 | 说明 |
|------|------|
| `senderStaffId` | **用户 staffId**（企业内 userid），仅在机器人发布线上版本后返回 |
| `senderId` | 加密的发送者 ID，建议优先使用 senderStaffId |
| `senderNick` | **发送者昵称/显示名**，直接可用，无需额外 API 调用 |
| `sessionWebhook` | 临时回复地址，约 **90 分钟**有效 |
| `sessionWebhookExpiredTime` | Webhook 过期时间（毫秒时间戳） |
| `conversationType` | `"1"` = 单聊，`"2"` = 群聊 |
| `createAt` | 消息时间戳（毫秒） |
| `msgId` | 消息唯一 ID，用于去重 |

### 14.3 签名验证算法

```java
// 1. 从 HTTP Header 提取 timestamp 和 sign
String timestamp = request.getHeader("timestamp");
String sign = request.getHeader("sign");

// 2. 时间戳窗口校验（±1 小时）
if (Math.abs(System.currentTimeMillis() - Long.parseLong(timestamp)) > 3_600_000) {
    return false;
}

// 3. HmacSHA256 签名校验
// sign = Base64(HmacSHA256(timestamp + "\n" + appSecret, appSecret))
Mac mac = Mac.getInstance("HmacSHA256");
mac.init(new SecretKeySpec(appSecret.getBytes(UTF_8), "HmacSHA256"));
byte[] signData = mac.doFinal((timestamp + "\n" + appSecret).getBytes(UTF_8));
String expectedSign = Base64.getEncoder().encodeToString(signData);
return expectedSign.equals(sign);
```

### 14.4 回复消息

钉钉不支持 HTTP Response 体直接回复，必须主动 POST `sessionWebhook`：

```json
POST {sessionWebhook}
Content-Type: application/json

{
    "msgtype": "markdown",
    "markdown": {
        "title": "KnowBrain 回答",
        "text": "## VPN 配置步骤\n\n1. ...\n\n---\n**📖 参考来源**"
    }
}
```

**sessionWebhook 过期处理**：`sessionWebhookExpiredTime` 约 90 分钟。过期后需回退到钉钉 API 主动发送（`POST /v1.0/robot/groupMessages/send`，需 access_token），当前 MVP 阶段过期直接丢弃。

### 14.5 现有实现状态

`DingtalkBotAdapter` 已退化为 REST API 工具类（用户/部门查询），消息收发全部由 `DingtalkStreamBotHandler` + `DingtalkStreamConfig` 通过 WebSocket 长连接处理。关键实现细节：

| 项目 | 实现方式 | 状态 |
|------|---------|:----:|
| 用户 ID | `senderStaffId` 优先，回退 `senderId` | ✅ |
| 显示名 | `senderNick` 直接可用 | ✅ |
| 签名算法 | SDK 内置，无需手动验签 | ✅ |
| URL 验证 | Stream 模式无需验证 | ✅ |
| 消息去重 | `msgId` → Redis SETNX | ✅ |
| 回复方式 | `BotReplier.fromWebhook(sessionWebhook)` | ✅ |
| 流式回复 | 逐段推送 Markdown（无消息编辑 API） | ✅ |
| 部门 API | `listsub` 递归 + `get` 取根部门 | ✅ |

### 14.6 钉钉后台配置步骤

1. 登录 [钉钉开发者后台](https://open-dev.dingtalk.com)
2. 创建企业内部应用 → 获取 `AppKey` + `AppSecret`
3. 应用功能 → 机器人 → 开启 → 配置：
   - 机器人名称、描述、图标
   - **消息接收模式**：HTTP 回调
   - **回调地址**：`https://{host}/api/v1/im/dingtalk/callback`
   - 安全设置：签名校验（输入自定义 secret，即 `DINGTALK_APP_SECRET`）
4. 发布上线（**发布后 `senderStaffId` 才会返回**）
5. 可见范围：添加需要使用 Bot 的员工/部门

### 14.7 启动环境变量

```bash
export DINGTALK_ENABLED=true
export DINGTALK_APP_KEY=dingxxxxxxxxxxxx
export DINGTALK_APP_SECRET=your-secret
export DINGTALK_ROBOT_CODE=your-robot-code
```

### 14.8 Stream 模式（已实现）

> 官方文档：[Stream 模式介绍](https://open.dingtalk.com/document/resourcedownload/introduction-to-stream-mode) | [SDK GitHub](https://github.com/open-dingtalk/dingtalk-stream-sdk-java)

#### 原理

基于 WebSocket 长连接的推送模式。SDK 自动管理连接、鉴权、重连，消息通过回调接口推送到业务代码。

```
         DingTalk Server
              │
              │ wss://... (WebSocket)
              │
    ┌─────────┴──────────┐
    │  dingtalk-stream   │  ← SDK 管理连接 + 鉴权 + 回调分发
    │  SDK               │
    └─────────┬──────────┘
              │ onMessage(ChatbotMessage)
              ▼
    DingtalkStreamBotHandler  ← 内置 RAG 流水线（去重→映射→限流→RAG）
              │
              ▼
    BotReplier.fromWebhook(sessionWebhook).replyMarkdown(...)
```

#### 优势对比（均已实现）

| | HTTP 回调（企微） | Stream 模式（钉钉+飞书） |
|---|---|---|
| 公网地址 | 需要 cloudflared 隧道 | **不需要** |
| 加解密 | 需手动实现验签 | SDK 内置 |
| URL 验证 | GET echostr 解密 | 无/自动完成 |
| 恢复 | 服务器宕机消息丢失 | **SDK 重连 + 追回** |
| 部署 | 需考虑防火墙/白名单 | 出境 WebSocket 即可 |

#### Maven 依赖

```xml
<dependency>
    <groupId>com.dingtalk.open</groupId>
    <artifactId>dingtalk-stream</artifactId>
    <version>1.3.2</version>
</dependency>
```

#### 实现要点（已实现，摘要）

`DingtalkStreamConfig` 通过 `@ConditionalOnProperty(name = "im.dingtalk.enabled")` 控制启用，`@Bean(initMethod = "start", destroyMethod = "stop")` 自动管理生命周期。

`DingtalkStreamBotHandler` 实现 `OpenDingTalkCallbackListener<ChatbotMessage, GenericOpenDingTalkEvent>`，内置完整的 RAG 流水线（去重→用户映射→限流→异步 RAG+流式回复），不经过 `ImBotDispatcher`。

流式回复通过 `BotReplier.fromWebhook(sessionWebhook)` 逐段推送 Markdown 消息，非流式（FAQ 命中）直接发一条完整消息。

#### 钉钉后台配置（Stream 模式）

与应用管理后台的「HTTP 回调」无关——SDK 通过 `AppKey` + `AppSecret` 自动建立 WebSocket 连接。只需确保：
1. 应用已创建 → 有 AppKey + AppSecret
2. 应用功能 → **机器人** → 已开启
3. 应用已发布上线（否则 `senderStaffId` 不返回）
4. 可见范围已配置

---

## 十五、飞书 Bot 集成

> 官方文档：[飞书事件订阅](https://open.feishu.cn/document/server-docs/event-subscription-guide/overview) | [飞书开发指南](https://open.feishu.cn/document/home/index)
> 实现状态：✅ 已完成（Stream 模式）

### 15.1 飞书 vs 企微/钉钉 核心差异

| 维度 | 企业微信 | 钉钉 | 飞书 |
|------|---------|------|------|
| 接入模式 | HTTP 回调 | WebSocket Stream | **WebSocket Stream** |
| SDK | 无（裸 HTTP） | dingtalk-stream | **lark-oapi** |
| 消息格式 | XML（加密） | JSON（明文） | **JSON v2.0**（content 是 JSON 字符串） |
| 用户 ID | 明文 userid | senderStaffId | **open_id** 优先 → user_id |
| 显示名 | API 获取 | senderNick 直接可用 | **API 获取**（回调不含显示名） |
| @提及格式 | 文本前缀 | @{nick} | **`<at user_id="xxx">名字</at>`** |
| 回复方式 | POST API | POST sessionWebhook | **POST `/open-apis/im/v1/messages/{id}/reply`** |
| Token | access_token (7200s) | access_token (7200s) | **tenant_access_token** (SDK 自动管理) |
| 部门 API | `/cgi-bin/department/list` | `listsub` 递归 | **`list` + `children` 递归** |
| 配置启用 | `@ConditionalOnProperty` | `@ConditionalOnProperty` | **运行时 `isConfigured()`**（避免占位符时序问题） |

### 15.2 Stream 模式架构

```
         Feishu Server
              │
              │ wss://... (WebSocket)
              │
    ┌─────────┴──────────┐
    │  lark-oapi SDK     │  ← SDK 管理连接 + tenant_access_token + 事件分发
    │  (Client + ws)     │
    └─────────┬──────────┘
              │ P2MessageReceiveV1 Event
              ▼
    FeishuStreamBotHandler  ← 内置 RAG 流水线（去重→映射→限流→RAG）
              │
              ▼
    feishuClient.im().v1().message().reply(...)
```

### 15.3 配置类设计

飞书使用 `FeishuStreamConfig` + `FeishuStreamBotHandler`，但与钉钉的关键区别在于**运行时判空**而非 `@ConditionalOnProperty`：

```java
@Configuration
public class FeishuStreamConfig {

    @Value("${im.feishu.app-id:}")
    private String appId;

    @Value("${im.feishu.app-secret:}")
    private String appSecret;

    private boolean isConfigured() {
        return appId != null && !appId.isBlank()
                && appSecret != null && !appSecret.isBlank();
    }

    @Bean
    public Client feishuApiClient() {
        if (!isConfigured()) {
            handler.setFeishuClient(null);
            return null;  // 安全降级，管理后台功能不可用但不阻塞启动
        }
        Client client = Client.newBuilder(appId, appSecret).build();
        handler.setFeishuClient(client);
        return client;
    }

    @Bean(initMethod = "start", destroyMethod = "close")
    public com.lark.oapi.ws.Client feishuStreamClient() {
        if (!isConfigured()) return null;
        // 注册 EventDispatcher → P2MessageReceiveV1 → handler.handleMessageEvent()
        return new com.lark.oapi.ws.Client.Builder(appId, appSecret)
                .eventHandler(eventDispatcher)
                .autoReconnect(true)
                .build();
    }
}
```

> **为什么不直接用 `@ConditionalOnProperty`？** `application.yml` 中的 `${FEISHU_ENABLED:false}` 占位符在 Spring Condition 评估阶段尚未被 Docker 环境变量解析，导致 `@ConditionalOnProperty(name = "im.feishu.enabled")` 始终读到默认值 `false`。改为运行时 `isConfigured()` 判空后，只要 Docker 环境变量正确传递即可激活。

### 15.4 消息处理

```java
// FeishuStreamBotHandler.handleMessageEvent()
public void handleMessageEvent(P2MessageReceiveV1 event) {
    // Step 1: 提取文本（飞书 content 是 JSON 字符串 {"text":"hello"}）
    JSONObject contentJson = JSONUtil.parseObj(message.getContent());
    String text = contentJson.getStr("text");

    // Step 2: 去除 @机器人 提及（飞书格式: <at user_id="xxx">名字</at>）
    text = text.replaceAll("<at\\s+user_id=\"[^\"]*\">[^<]*</at>", "").trim();

    // Step 3-8: 去重 → 用户 ID 提取(open_id) → 用户映射 → 限流 → 异步 RAG + 回复
    // （流程与钉钉 Stream 一致）
}
```

### 15.5 消息回复

飞书使用 post 消息格式（支持富文本，含 md 标签）：

```java
private void sendFeishuReply(String markdownContent, ImIncomingMessage msg) {
    String postJson = buildPostContent(markdownContent);
    // post 格式: {"zh_cn": {"title": "KnowBrain 回答", "content": [[{"tag":"md","text":"..."}]]}}

    ReplyMessageReq req = ReplyMessageReq.newBuilder()
            .messageId(replyToMsgId)
            .replyMessageReqBody(ReplyMessageReqBody.newBuilder()
                    .msgType("post")
                    .content(postJson)
                    .build())
            .build();

    feishuClient.im().v1().message().reply(req);
}
```

### 15.6 部门 API：双策略降级

飞书部门拉取是实战中踩坑最多的部分。最终采用**双策略智能降级**：

```
fetchDepartmentList()
  │
  ├─ 策略 A: list API + fetchChild=true
  │     ListDepartmentReq.fetchChild(true).departmentIdType("open_department_id")
  │     期望：一次性返回所有部门
  │     ↓
  │     实际：某些租户只返回空壳根部门 {"open_department_id":"0","name":""}
  │     ↓
  │     isShellRoot() → true → 降级
  │
  └─ 策略 B: get 根部门 + children 递归
        GetDepartmentReq("0") → 获取根部门名称（fallback "公司"）
        ChildrenDepartmentReq(parentId).departmentIdType("open_department_id")
        → 递归获取所有子部门
```

#### 踩坑记录

| 坑 | 现象 | 根因 | 修复 |
|----|------|------|------|
| page_size | 40011 错误 | Feishu API 最大 50，代码写 100 | `DEPT_PAGE_SIZE = 50` |
| department_id 格式 | list API 只返回空壳 | app 身份用 `department_id` 兼容差 | 改用 `open_department_id` |
| children 递归 ID 错误 | 99992357 invalid open_department_id | 递归调用了 `getDepartmentId()`（数字） | 优先 `getOpenDepartmentId()`（od-xxx） |
| 根部门缺失 | 只返回 4 条子部门 | `children("0")` 不含根部门自身 | `get` API 单独获取根部门 |

### 15.7 飞书后台配置

1. 登录 [飞书开发者后台](https://open.feishu.cn/app)
2. 创建企业自建应用 → 获取 `App ID` + `App Secret`
3. 添加应用能力 → **机器人** → 开启
4. **事件订阅**：无需配置 HTTP 回调地址（Stream 模式自动建立 WebSocket）
5. 权限管理 → 申请权限：
   - `im:message:send_as_bot` — 发送消息
   - `im:message:read` — 读取消息
   - `contact:department.base:readonly` — 部门基础读取
   - `contact:department.organize:readonly` — 部门组织架构读取
   - `directory:department:list` — 部门列表
6. 发布上线 → 设置可见范围（全部成员）

### 15.8 启动环境变量

```bash
export FEISHU_ENABLED=true
export FEISHU_APP_ID=cli_xxxxxxxxxxxx
export FEISHU_APP_SECRET=your-secret
```

> Stream 模式在 IDEA 中本地启动即可直接接收消息，无需 cloudflared 隧道。

---

## 十六、生产环境检查清单

### 通用
- [ ] 敏感配置（Secret、EncodingAESKey、App Secret）使用 K8s Secret / Vault
- [ ] 回调端点加 Prometheus 指标：消息量、RAG 耗时、失败率
- [ ] `kb_im_dept_mapping` 通过管理后台 CRUD（已实现：`/admin/im`）
- [ ] `kb_user_identity` 跨平台身份关联正常工作
- [ ] 告警：5 分钟内无消息 / 消息失败率 > 1%

### 企微（HTTP 回调）
- [ ] cloudflared 替换为固定域名方案（nginx 反向代理 / frp）
- [ ] 企微白名单 IP 更新为生产服务器出口 IP
- [ ] AccessToken 改为 Redis 缓存（支持多实例）
- [ ] 告警：解密失败率 > 0.1% / AccessToken 刷新失败

### 钉钉 / 飞书（Stream 模式）
- [x] 无需公网地址 / cloudflared（Stream 模式天然优势）
- [x] 无需手动管理 Token（SDK 内置）
- [ ] 确认应用已发布上线（否则用户 ID 不返回）
- [ ] 可见范围覆盖目标部门

---

## 十七、多平台身份统一（跨 IM 账号合并）

### 16.1 问题

当前 `ImUserMapping` 按 `im_{platform}_{userId}` 生成 KB 用户名。同一个员工通过不同 IM 平台（企微、钉钉）发消息时，会创建**多个独立的 KB 账号**：

```
员工「张三」
  企微 ChenXiaoBing → kb_sys_user: im_wecom_ChenXiaoBing   (dept=技术部, 10条问答历史)
  钉钉 013527466xxx  → kb_sys_user: im_dingtalk_013527466xxx (dept=技术部, 5条问答历史)
                                                          ↑
                                                  同一个人，两个独立账号！
```

**影响**：权限配置割裂、问答历史无法聚合、反馈数据分散、管理员需要在两个账号上分别操作。

### 16.2 业界方案对比

| 方案 | 原理 | 优点 | 缺点 | 适用场景 |
|------|------|------|------|---------|
| **① 手机号/邮箱匹配** | 各 IM API 获取手机号/邮箱，以此为唯一键自动合并 | 自动化高，用户无感 | 手机号可能隐藏（企微敏感部门）、一人多号 | 中小企业，多数用户手机号可见 |
| **② 企业目录同步（LDAP/AD）** | 所有 IM + KB 都对接同一 LDAP/AD，员工 ID 预先统一 | 权威数据源，无一致问题 | 需要企业有 LDAP/AD 基础设施 | 中大型企业 |
| **③ 预置 + 工号匹配** | 管理员先批量导入全员（含工号），IM 用户通过工号匹配已有 KB 账号 | 可控、确定性最强 | 需要管理员操作、人员变动需维护 | 所有规模 |
| **④ 用户自助绑定** | 自动创建独立账号，用户可在个人中心绑定多个 IM 身份 | 灵活、尊重用户意愿 | 需要用户主动操作，覆盖率低 | 辅助手段 |
| **⑤ 管理员手动合并** | 后台支持合并账号 + 迁移历史数据 | 兜底，覆盖所有特殊情况 | 手动操作 | 兜底必备 |

### 16.3 推荐策略：管理员手动关联为主，自动匹配为辅

#### 实际约束

当前系统 KB 用户手机号由管理员事后补充，大部分为空。手机号自动匹配命中率预计 &lt; 30%。因此策略优先级调整为：

**管理员手动绑定 > 手机号自动匹配（辅助） > 自动创建独立账号（兜底）**

```
IM 用户首次发消息
  │
  ├─ ① 查 kb_user_identity 关联表
  │     命中 → 直接返回 kbUserId（管理员可能已手动绑定）
  │     未命中 ↓
  │
  ├─ ② 调平台 API 获取手机号 → 匹配 kb_user_identity.mobile
  │     ├─ 命中 → 自动归并到已有 KB 用户 ← 辅助作用，命中率取决于手机号覆盖率
  │     └─ 未命中 / 无手机号 → 继续
  │
  ├─ ③ Fallback: 查旧命名 im_{platform}_{userId}
  │     命中 → 回填关联表
  │     未命中 ↓
  │
  └─ ④ 创建独立 KB 用户 + identity 记录
        → 管理员可在后台将其绑定到已有用户 ← 事后清理入口
```

#### 各层职责

| 优先级 | 方式 | 触发者 | 命中率 | 适用场景 |
|--------|------|--------|--------|---------|
| **主** | 管理员手动绑定 | 管理员在后台操作 | 100%（管理员主动操作） | **核心手段**，覆盖所有场景 |
| 辅助 | 手机号自动匹配 | 用户发消息时自动触发 | 取决于 KB 用户手机号覆盖率 | 锦上添花，有则自动归并 |
| 兜底 | 自动创建独立账号 | 用户发消息时自动触发 | 100%（无匹配时必然创建） | 保证用户不丢消息 |

> **现实**：大多数中小企业 KB 用户手机号为空的现状下，管理员手动关联是唯一可靠的手段。手机号匹配的价值随着管理员逐步补全用户信息而增长。

#### 管理后台（已实现）

`/admin/im` 页面提供两个 Tab：

- **部门映射**：企微/钉钉/飞书部门 ID ↔ KB 部门，CRUD
- **IM 身份**：查看所有 IM → KB 用户绑定关系，手动绑定/解绑

管理员典型操作：
1. 新员工入职 → 在「用户管理」创建 KB 用户 → 在「IM 集成」绑定其企微 ID
2. 发现未绑定用户 → 「IM 身份」列表筛选 → 手动绑定到已有 KB 用户
3. 员工离职 → 「IM 身份」解绑旧身份 → 新员工接手时绑定新 KB 用户
| 合并账号 | 将两个 KB 用户的问答历史、反馈、权限合并，旧账号禁用 |

**账号合并逻辑**：

```sql
-- 合并 kbUserId=2 到 kbUserId=1（以 1 为主身份）
UPDATE kb_feedback     SET user_id = 1 WHERE user_id = 2;
UPDATE kb_chat_history SET user_id = 1 WHERE user_id = 2;
UPDATE kb_user_identity SET kb_user_id = 1 WHERE kb_user_id = 2;
UPDATE kb_sys_user    SET status = 'DISABLED' WHERE id = 2;
```

#### 阶段 C：用户自助绑定（长期优化）

在 Web 前端「个人中心」页面提供：
- 「关联 IM 账号」按钮 → 生成一次性验证码 → 用户在企微/钉钉中发送验证码 → 确认绑定
- 已关联的 IM 身份列表（平台图标 + 显示名 + 关联时间）
- 「解绑」按钮（至少保留一个身份）

### 16.4 过渡期兼容策略

`kb_user_identity` 表上线时，为现有 `im_xxx` 命名的用户自动创建关联记录：

```sql
-- 迁移脚本：将现有 IM 用户写入关联表
INSERT INTO kb_user_identity (kb_user_id, platform, platform_uid, platform_name)
SELECT
    su.id,
    SUBSTRING_INDEX(SUBSTRING_INDEX(su.username, '_', 2), '_', -1) AS platform,
    SUBSTRING(su.username, LENGTH('im_') + LENGTH(SUBSTRING_INDEX(SUBSTRING_INDEX(su.username, '_', 2), '_', -1)) + 2) AS platform_uid,
    su.name
FROM kb_sys_user su
WHERE su.username LIKE 'im\_%'
  AND NOT EXISTS (
      SELECT 1 FROM kb_user_identity i
      WHERE i.kb_user_id = su.id
  );
```

此后新逻辑以 `kb_user_identity` 为权威来源，旧 `im_` 前缀的 `kb_sys_user.username` 仅作 fallback。

### 16.4 设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 身份匹配主手段 | 管理员手动绑定 | KB 用户手机号大部分为空，自动匹配不可靠；管理员操作确定性最强 |
| 自动匹配辅助键 | 手机号 | 跨平台通用，能覆盖多少算多少，随管理员补全手机号而提升 |
| 匹配失败降级 | 创建独立账号 + 后台绑定入口 | 不因匹配失败让用户无法使用，事后管理员可合并 |
| 多身份主次 | 不设"主身份" | 所有平台平权，降低概念复杂度 |
| 已合并数据迁移 | 合并时迁移全部历史 | 用户无感知，数据不丢失 |

