package com.knowbrain.audit;

import java.lang.annotation.*;

/**
 * 审计日志注解 — 标记需要记录操作审计的 Controller 方法
 *
 * 使用 AOP 切面自动捕获：操作人、IP、时间戳、操作结果
 * 通过 SpEL 表达式从方法参数中提取目标资源 ID 和名称
 *
 * <pre>
 * // 简单用法：资源 ID 来自路径变量
 * &#64;Auditable(operation = "DELETE", resourceType = "USER", resourceId = "#id", description = "删除用户")
 *
 * // CREATE 用法：资源 ID 来自返回值
 * &#64;Auditable(operation = "CREATE", resourceType = "USER", resourceId = "#result.data.id", resourceName = "#request['username']")
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Auditable {

    /** 操作类型: CREATE / UPDATE / DELETE / OTHER */
    String operation();

    /** 目标资源类型: USER / DEPARTMENT / SPACE / DOCUMENT / SCENARIO_CATEGORY / ... */
    String resourceType();

    /**
     * SpEL 表达式，提取目标资源 ID
     * 示例: "#id" (路径变量), "#result.data.id" (从返回值提取)
     */
    String resourceId() default "";

    /**
     * SpEL 表达式，提取目标资源名称
     * 示例: "#request['username']", "#dept.name"
     */
    String resourceName() default "";

    /** 静态操作描述（不需要 SpEL 时直接填写） */
    String description() default "";
}
