# KnowBrain 脚手架 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 创建 Spring Boot 3.3 项目脚手架，包含 pom.xml、启动类、公共类、配置类和完整的包结构。

**Architecture:** 单模块 Maven 项目，包名 `com.knowbrain`，Spring Boot 3.3.5 + Spring AI 1.0.0-M4，构造器注入模式。

**Tech Stack:** Java 17, Spring Boot 3.3.5, Spring AI 1.0.0-M4, MyBatis-Plus 3.5.7, Hutool 5.8.28

## Global Constraints

- 基础包名: `com.knowbrain`
- Java 版本: 17
- Spring Boot: 3.3.5
- Spring AI: 1.0.0-M4（Milestone 仓库）
- MyBatis-Plus: 3.5.7
- 构造器注入（禁止 `@Autowired` 字段注入）
- API 前缀: `/api/v1/`
- 数据库表前缀: `kb_`
- 配置模式: `${ENV_VAR:default}`

---

### Task 1: 创建项目目录结构和 pom.xml

**Files:**
- Create: `knowbrain-server/pom.xml`

**Interfaces:**
- Consumes: 无（第一个任务）
- Produces: Maven 项目定义，供后续所有任务依赖

- [ ] **Step 1: 创建 knowbrain-server/ 目录**

```bash
mkdir -p d:/github/KnowBrain/knowbrain-server/src/main/java/com/knowbrain
mkdir -p d:/github/KnowBrain/knowbrain-server/src/main/resources/prompts
mkdir -p d:/github/KnowBrain/knowbrain-server/src/test/java/com/knowbrain
```

- [ ] **Step 2: 编写 pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>

    <groupId>com.knowbrain</groupId>
    <artifactId>knowbrain-server</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <name>KnowBrain Server</name>
    <description>企业私有知识大脑 - 智能知识库服务</description>

    <properties>
        <java.version>17</java.version>
        <spring-ai.version>1.0.0-M4</spring-ai.version>
        <mybatis-plus.version>3.5.7</mybatis-plus.version>
        <hutool.version>5.8.28</hutool.version>
    </properties>

    <repositories>
        <repository>
            <id>spring-milestones</id>
            <name>Spring Milestones</name>
            <url>https://repo.spring.io/milestone</url>
            <snapshots><enabled>false</enabled></snapshots>
        </repository>
    </repositories>

    <dependencies>
        <!-- SpringBoot Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- SpringBoot Data Redis -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>

        <!-- SpringAI — LLM 调用 + RAG + 向量存储 -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-milvus-store-spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-tika-document-reader</artifactId>
        </dependency>

        <!-- MyBatis-Plus -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
            <version>${mybatis-plus.version}</version>
        </dependency>

        <!-- MySQL -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- MinIO 对象存储 -->
        <dependency>
            <groupId>io.minio</groupId>
            <artifactId>minio</artifactId>
            <version>8.5.9</version>
        </dependency>

        <!-- API 文档 -->
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>2.6.0</version>
        </dependency>

        <!-- 重试与熔断 -->
        <dependency>
            <groupId>org.springframework.retry</groupId>
            <artifactId>spring-retry</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-aspects</artifactId>
        </dependency>

        <!-- 工具库 -->
        <dependency>
            <groupId>cn.hutool</groupId>
            <artifactId>hutool-all</artifactId>
            <version>${hutool.version}</version>
        </dependency>
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-lang3</artifactId>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter-test</artifactId>
            <version>3.5.7</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>${spring-ai.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 3: 提交**

```bash
git add knowbrain-server/pom.xml
git commit -m "feat: 创建 KnowBrain Spring Boot 3.3 项目脚手架 pom.xml

依赖: Spring Boot 3.3.5, Spring AI 1.0.0-M4, MyBatis-Plus 3.5.7, Hutool 5.8.28

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: 创建启动类和 application.yml

**Files:**
- Create: `knowbrain-server/src/main/java/com/knowbrain/KnowBrainApplication.java`
- Create: `knowbrain-server/src/main/resources/application.yml`

**Interfaces:**
- Consumes: `pom.xml`（Task 1）
- Produces: Spring Boot 入口 + 完整配置，后续所有 Task 依赖

- [ ] **Step 1: 编写启动类**

```java
package com.knowbrain;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class KnowBrainApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowBrainApplication.class, args);
    }
}
```

- [ ] **Step 2: 编写 application.yml**

```yaml
server:
  port: 8080
  servlet:
    encoding:
      charset: UTF-8
      enabled: true
      force: true

spring:
  # 数据源 — MySQL
  datasource:
    url: jdbc:mysql://${MYSQL_HOST:localhost}:${MYSQL_PORT:3306}/${MYSQL_DATABASE:knowbrain}?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai&useSSL=false
    username: ${MYSQL_USERNAME:root}
    password: ${MYSQL_PASSWORD:root}
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      idle-timeout: 300000
      connection-timeout: 20000

  # Redis
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:knowbrain_redis_2024}
      lettuce:
        pool:
          max-active: 16
          max-idle: 8
          min-idle: 4

  # Spring AI — LLM（OpenAI 兼容协议，默认接阿里云 DashScope）
  ai:
    openai:
      base-url: ${SPRING_AI_OPENAI_BASE_URL:https://dashscope.aliyuncs.com/compatible-mode}
      api-key: ${SPRING_AI_OPENAI_API_KEY:}
      chat:
        options:
          model: ${SPRING_AI_OPENAI_CHAT_MODEL:qwen-max}
          temperature: 0.7
      embedding:
        options:
          model: ${SPRING_AI_OPENAI_EMBEDDING_MODEL:text-embedding-v4}
          dimensions: 1024

    # Spring AI — Milvus 向量存储
    vectorstore:
      milvus:
        client:
          host: ${MILVUS_HOST:localhost}
          port: ${MILVUS_GRPC_PORT:19530}
          username: ${MILVUS_USERNAME:}
          password: ${MILVUS_PASSWORD:}
        collection-name: ${MILVUS_COLLECTION:knowbrain_knowledge_base}
        index-type: IVF_FLAT
        metric-type: COSINE
        embedding-dimension: 1024
        initialize-schema: true

# MinIO 对象存储
minio:
  endpoint: ${MINIO_ENDPOINT:http://localhost:9000}
  access-key: ${MINIO_ACCESS_KEY:minioadmin}
  secret-key: ${MINIO_SECRET_KEY:minioadmin}
  bucket: ${MINIO_BUCKET:knowbrain-documents}

# JWT 配置
jwt:
  secret: ${JWT_SECRET:knowbrain_jwt_secret_2024}
  expire-hours: ${JWT_EXPIRE_HOURS:24}

# 敏感词过滤
security:
  sensitive-word:
    enabled: ${SENSITIVE_WORD_ENABLED:true}
    custom-words: ${SENSITIVE_WORD_CUSTOM:}

# MyBatis-Plus
mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
  global-config:
    db-config:
      table-prefix: kb_
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl

# 日志
logging:
  level:
    com.knowbrain: DEBUG
    org.springframework.ai: DEBUG
```

- [ ] **Step 3: 提交**

```bash
git add knowbrain-server/src/main/java/com/knowbrain/KnowBrainApplication.java
git add knowbrain-server/src/main/resources/application.yml
git commit -m "feat: 添加启动类和 application.yml 配置

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: 创建公共类（Result + GlobalExceptionHandler）

**Files:**
- Create: `knowbrain-server/src/main/java/com/knowbrain/common/Result.java`
- Create: `knowbrain-server/src/main/java/com/knowbrain/common/GlobalExceptionHandler.java`

**Interfaces:**
- Consumes: 无
- Produces:
  - `Result<T>` — `Result.ok(data)`, `Result.ok(msg, data)`, `Result.fail(code, msg)`, `Result.fail(msg)`, `Result.notFound(msg)`, `Result.badRequest(msg)`
  - `GlobalExceptionHandler` — `@RestControllerAdvice`，处理 `BizException`、`MethodArgumentNotValidException`、`Exception`
  - `BizException` — 内部类，继承 `RuntimeException`，字段 `int code` + `String message`

- [ ] **Step 1: 编写 Result.java**

```java
package com.knowbrain.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    private int code;
    private String message;
    private T data;

    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "success", data);
    }

    public static <T> Result<T> ok(String message, T data) {
        return new Result<>(200, message, data);
    }

    public static <T> Result<T> fail(String message) {
        return new Result<>(500, message, null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> notFound(String message) {
        return new Result<>(404, message, null);
    }

    public static <T> Result<T> badRequest(String message) {
        return new Result<>(400, message, null);
    }
}
```

- [ ] **Step 2: 编写 GlobalExceptionHandler.java**

```java
package com.knowbrain.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常
     */
    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleBizException(BizException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验失败
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        log.warn("参数校验失败: {}", message);
        return Result.badRequest(message);
    }

    /**
     * 兜底异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.fail("服务器内部错误");
    }
}
```

- [ ] **Step 3: 在同文件中补充 BizException 内部类**

在 `GlobalExceptionHandler.java` 文件末尾追加（class closing brace 之前）:

```java

    /**
     * 业务异常类
     */
    public static class BizException extends RuntimeException {

        private final int code;

        public BizException(int code, String message) {
            super(message);
            this.code = code;
        }

        public BizException(String message) {
            this(500, message);
        }

        public int getCode() {
            return code;
        }
    }
```

- [ ] **Step 4: 提交**

```bash
git add knowbrain-server/src/main/java/com/knowbrain/common/Result.java
git add knowbrain-server/src/main/java/com/knowbrain/common/GlobalExceptionHandler.java
git commit -m "feat: 添加统一返回体 Result<T> 和全局异常处理器

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: 创建配置类

**Files:**
- Create: `knowbrain-server/src/main/java/com/knowbrain/config/MinioConfig.java`
- Create: `knowbrain-server/src/main/java/com/knowbrain/config/MybatisPlusConfig.java`
- Create: `knowbrain-server/src/main/java/com/knowbrain/config/CorsConfig.java`
- Create: `knowbrain-server/src/main/java/com/knowbrain/config/OpenApiConfig.java`
- Create: `knowbrain-server/src/main/java/com/knowbrain/config/WebMvcConfig.java`
- Create: `knowbrain-server/src/main/java/com/knowbrain/config/RetryConfig.java`

**Interfaces:**
- Consumes: `application.yml`（Task 2）
- Produces:
  - `MinioConfig` → `MinioClient` Bean
  - `MybatisPlusConfig` → 分页拦截器 + 自动填充
  - `CorsConfig` → CORS 全局过滤器
  - `OpenApiConfig` → SpringDoc OpenAPI Bean
  - `WebMvcConfig` → 拦截器注册（预留）
  - `RetryConfig` → `@EnableRetry` 生效

- [ ] **Step 1: MinioConfig.java**

```java
package com.knowbrain.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    @Value("${minio.endpoint}")
    private String endpoint;

    @Value("${minio.access-key}")
    private String accessKey;

    @Value("${minio.secret-key}")
    private String secretKey;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
```

- [ ] **Step 2: MybatisPlusConfig.java**

```java
package com.knowbrain.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

@Slf4j
@Configuration
public class MybatisPlusConfig {

    /**
     * 分页插件
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /**
     * 自动填充 create_time / update_time
     */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
                this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
            }
        };
    }
}
```

- [ ] **Step 3: CorsConfig.java**

```java
package com.knowbrain.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOriginPattern("*");
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
```

- [ ] **Step 4: OpenApiConfig.java**

```java
package com.knowbrain.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI knowBrainOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("KnowBrain API")
                        .description("企业私有知识大脑 - 智能知识库 API 文档")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("KnowBrain Team")));
    }
}
```

- [ ] **Step 5: WebMvcConfig.java**

```java
package com.knowbrain.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 * 阶段 2 将在此注册 AuthInterceptor
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    // 预留：阶段 2 注册 AuthInterceptor 到 /api/v1/**
    // 预留：阶段 2 添加白名单路径排除
}
```

- [ ] **Step 6: RetryConfig.java**

```java
package com.knowbrain.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

@Configuration
@EnableRetry
public class RetryConfig {
}
```

- [ ] **Step 7: 提交**

```bash
git add knowbrain-server/src/main/java/com/knowbrain/config/
git commit -m "feat: 添加配置类（MinIO, MyBatis-Plus, CORS, OpenAPI, WebMVC, Retry）

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: 创建业务空包结构

**Files:**
- 仅创建目录，无 Java 文件（后续任务填充），各包下放置 `.gitkeep` 占位。

**Interfaces:**
- Consumes: 无
- Produces: 完整的包结构，后续业务代码直接放入

- [ ] **Step 1: 创建所有业务包目录**

```bash
# 在 knowbrain-server/src/main/java/com/knowbrain/ 下创建
mkdir -p d:/github/KnowBrain/knowbrain-server/src/main/java/com/knowbrain/auth
mkdir -p d:/github/KnowBrain/knowbrain-server/src/main/java/com/knowbrain/document/entity
mkdir -p d:/github/KnowBrain/knowbrain-server/src/main/java/com/knowbrain/document/mapper
mkdir -p d:/github/KnowBrain/knowbrain-server/src/main/java/com/knowbrain/document/service
mkdir -p d:/github/KnowBrain/knowbrain-server/src/main/java/com/knowbrain/document/controller
mkdir -p d:/github/KnowBrain/knowbrain-server/src/main/java/com/knowbrain/retrieval/engine
mkdir -p d:/github/KnowBrain/knowbrain-server/src/main/java/com/knowbrain/retrieval/vector
mkdir -p d:/github/KnowBrain/knowbrain-server/src/main/java/com/knowbrain/retrieval/keyword
mkdir -p d:/github/KnowBrain/knowbrain-server/src/main/java/com/knowbrain/retrieval/rerank
mkdir -p d:/github/KnowBrain/knowbrain-server/src/main/java/com/knowbrain/generation
mkdir -p d:/github/KnowBrain/knowbrain-server/src/main/java/com/knowbrain/permission
mkdir -p d:/github/KnowBrain/knowbrain-server/src/main/java/com/knowbrain/space
mkdir -p d:/github/KnowBrain/knowbrain-server/src/main/java/com/knowbrain/feedback
mkdir -p d:/github/KnowBrain/knowbrain-server/src/main/java/com/knowbrain/statistics
mkdir -p d:/github/KnowBrain/knowbrain-server/src/main/java/com/knowbrain/scenario
mkdir -p d:/github/KnowBrain/knowbrain-server/src/main/java/com/knowbrain/websocket
```

- [ ] **Step 2: 在各空包下放 .gitkeep 占位**

```bash
for dir in auth document/entity document/mapper document/service document/controller \
    retrieval/engine retrieval/vector retrieval/keyword retrieval/rerank \
    generation permission space feedback statistics scenario websocket; do
    touch "d:/github/KnowBrain/knowbrain-server/src/main/java/com/knowbrain/$dir/.gitkeep"
done
```

- [ ] **Step 3: 提交**

```bash
git add knowbrain-server/src/main/java/com/knowbrain/auth/
git add knowbrain-server/src/main/java/com/knowbrain/document/
git add knowbrain-server/src/main/java/com/knowbrain/retrieval/
git add knowbrain-server/src/main/java/com/knowbrain/generation/
git add knowbrain-server/src/main/java/com/knowbrain/permission/
git add knowbrain-server/src/main/java/com/knowbrain/space/
git add knowbrain-server/src/main/java/com/knowbrain/feedback/
git add knowbrain-server/src/main/java/com/knowbrain/statistics/
git add knowbrain-server/src/main/java/com/knowbrain/scenario/
git add knowbrain-server/src/main/java/com/knowbrain/websocket/
git commit -m "feat: 创建业务包结构（空包占位）

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 6: 验证编译

**Files:**
- 无新文件

**Interfaces:**
- Consumes: Task 1-5 的全部产物
- Produces: 编译成功的 Maven 项目

- [ ] **Step 1: 检查 Maven 是否可用**

```bash
mvn --version
```

- [ ] **Step 2: 跳过测试编译项目**

```bash
cd d:/github/KnowBrain/knowbrain-server && mvn compile -q
```

Expected: BUILD SUCCESS（下载依赖可能需要几分钟）

- [ ] **Step 3: 运行测试确认项目基础正常**

```bash
cd d:/github/KnowBrain/knowbrain-server && mvn test -q
```

Expected: BUILD SUCCESS（尚无测试用例，跳过所有）

- [ ] **Step 4: 验证项目可以打包**

```bash
cd d:/github/KnowBrain/knowbrain-server && mvn package -DskipTests -q
```

Expected: `target/knowbrain-server-1.0.0-SNAPSHOT.jar` 生成成功
