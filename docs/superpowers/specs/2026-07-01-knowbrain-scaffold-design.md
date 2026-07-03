# KnowBrain 脚手架设计文档

> 日期：2026-07-01 | 范围：Phase 1 第一步 — 项目骨架搭建

---

## 一、目标

创建 Spring Boot 3.3 项目脚手架，包含完整的构建配置、基础公共类和配置层。后续业务模块在此骨架之上逐步填充。

## 二、项目命名与路径

| 项目 | 值 |
|------|-----|
| Maven artifactId | `knowbrain-server` |
| 基础包名 | `com.knowbrain` |
| 主模块目录 | `knowbrain-server/` |

## 三、pom.xml

### 3.1 Parent

`spring-boot-starter-parent:3.3.5`（与 EICS 一致）

### 3.2 关键属性

- `java.version`: **17**
- `spring-ai.version`: **1.0.0-M4**（与 EICS 一致，确保 API 兼容）
- `mybatis-plus.version`: **3.5.7**
- `hutool.version`: **5.8.28**

### 3.3 依赖清单

| 类别 | artifact | 说明 |
|------|----------|------|
| Web | `spring-boot-starter-web` | REST API |
| 校验 | `spring-boot-starter-validation` | 请求参数校验 |
| Redis | `spring-boot-starter-data-redis` | 高频问答缓存、会话 |
| Spring AI | `spring-ai-openai-spring-boot-starter` | LLM 调用（OpenAI 兼容协议） |
| Spring AI | `spring-ai-milvus-store-spring-boot-starter` | 向量存储 |
| Spring AI | `spring-ai-tika-document-reader` | 文档解析 |
| ORM | `mybatis-plus-spring-boot3-starter:3.5.7` | 数据访问 |
| DB | `mysql-connector-j` (runtime) | MySQL 驱动 |
| OSS | `minio:8.5.9` | 文档存储 |
| API 文档 | `springdoc-openapi-starter-webmvc-ui:2.6.0` | Swagger UI |
| 重试 | `spring-retry` + `spring-aspects` | 检索重试 |
| 工具 | `hutool-all:5.8.28` | 通用工具 |
| 工具 | `commons-lang3` | 字符串/反射工具 |
| 简化 | `lombok` (optional) | 减少样板代码 |
| 测试 | `spring-boot-starter-test` (test) | 单元测试 |
| 测试 | `h2` (test) | 内存数据库 |
| 测试 | `mybatis-plus-spring-boot3-starter-test:3.5.7` | MP 测试 |

### 3.4 仓库

`spring-milestones`（Snapshots 关闭），因为 Spring AI 1.0.0-M4 在 milestone 仓库。

### 3.5 BOM

`spring-ai-bom:1.0.0-M4` 统一管理 Spring AI 子模块版本。

## 四、包结构

```
com.knowbrain/
├── KnowBrainApplication.java       # @SpringBootApplication
├── common/
│   ├── Result.java                 # 统一返回体 Result<T>
│   └── GlobalExceptionHandler.java # @RestControllerAdvice + BizException
├── config/
│   ├── MinioConfig.java            # MinioClient @Bean
│   ├── MybatisPlusConfig.java      # 分页插件 + 自动填充
│   ├── CorsConfig.java             # CORS 过滤器
│   ├── OpenApiConfig.java          # SpringDoc 元数据
│   ├── WebMvcConfig.java           # 拦截器注册（预留）
│   └── RetryConfig.java            # @EnableRetry
├── auth/                           # 阶段 2
├── document/                       # 后续业务
│   ├── entity/
│   ├── mapper/
│   ├── service/
│   └── controller/
├── retrieval/                      # 后续业务
│   ├── engine/
│   ├── vector/
│   ├── keyword/
│   └── rerank/
├── generation/                     # 后续业务
├── permission/                     # 阶段 2
├── space/                          # 阶段 2
├── feedback/                       # 后续业务
├── statistics/                     # 后续业务
├── scenario/                       # 后续业务
└── websocket/                      # 后续业务
```

## 五、application.yml 大纲

### 5.1 服务

- `server.port: 8080`
- `server.servlet.encoding.charset: UTF-8`

### 5.2 数据源

- MySQL HikariCP 连接池
- 所有连接参数使用 `${ENV_VAR:default}` 模式
- 默认数据库名 `knowbrain`

### 5.3 Redis

- Lettuce 连接池，max-active=16
- 密码有默认值

### 5.4 Spring AI — OpenAI

- base-url: `https://dashscope.aliyuncs.com/compatible-mode`
- 默认模型: `qwen-max`
- embedding 模型: `text-embedding-v4`（1024 维）

### 5.5 Spring AI — Milvus

- collection: `knowbrain_knowledge_base`
- index: `IVF_FLAT`
- metric: `COSINE`
- dimensions: `1024`
- `initialize-schema: true`

### 5.6 MinIO

- bucket: `knowbrain-documents`

### 5.7 JWT（预留）

- `jwt.secret` + `jwt.expire-hours: 24`

### 5.8 敏感词（预留）

- `security.sensitive-word.enabled: true`
- `security.sensitive-word.custom-words: ""`

### 5.9 MyBatis-Plus

- mapper XML: `classpath*:/mapper/**/*.xml`
- 全局逻辑删除: `deleted` 字段（1=删除，0=正常）
- 全局自动填充: `create_time` / `update_time`

### 5.10 日志

- `com.knowbrain: DEBUG`
- `org.springframework.ai: DEBUG`

## 六、公共类设计

### 6.1 Result<T>

```java
public class Result<T> {
    int code;
    String message;
    T data;

    // 静态工厂
    static <T> Result<T> ok(T data)
    static <T> Result<T> ok(String msg, T data)
    static <T> Result<T> fail(int code, String msg)
    static <T> Result<T> fail(String msg)
    static <T> Result<T> notFound(String msg)
    static <T> Result<T> badRequest(String msg)
}
```

### 6.2 GlobalExceptionHandler

- `@RestControllerAdvice` 全局抓异常
- 处理 `BizException`（内部业务异常，继承 RuntimeException，含 code + message）
- 处理 `MethodArgumentNotValidException`（参数校验失败 → 400）
- 处理 `Exception`（兜底 → 500）
- BizException 定义为一并写在同一个文件中（减少文件数）

## 七、设计决策

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 模块结构 | 单模块 | Phase 1 快速跑通，包级边界已足够 |
| 依赖注入 | 构造器注入 | 可测试、不可变、依赖透明 |
| 返回体 | `Result<T>` + 静态工厂 | 与 EICS 一致，简单够用 |
| 异常 | `BizException` + 全局处理器 | 业务异常统一 code，不依赖 Spring 异常 |
| 配置模式 | `${ENV_VAR:default}` | 12-factor，Docker Compose 友好 |
| 表前缀 | `kb_` | CLAUDE.md 规定 |
| API 前缀 | `/api/v1/` | CLAUDE.md 规定 |
| Spring AI 版本 | 1.0.0-M4 | 与 EICS 一致，避免 API 差异 |

## 八、不包含

- 任何业务代码（文档、检索、生成等后续填充）
- JWT 工具类（阶段 2）
- WebSocket 配置（后续）
- 前端代码（后续）
