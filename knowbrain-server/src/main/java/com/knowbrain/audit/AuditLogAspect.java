package com.knowbrain.audit;

import com.knowbrain.audit.entity.AuditLog;
import com.knowbrain.common.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * 审计日志 AOP 切面 — 拦截 @Auditable 注解的方法，自动记录操作审计
 *
 * 核心逻辑：
 * 1. 方法执行前提取操作人/IP 等上下文信息
 * 2. 执行目标方法（proceed）
 * 3. finally 块中构建审计记录并异步写入（保证异常场景也记录）
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogService auditLogService;
    private final RequestContext requestContext;

    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer paramDiscoverer = new DefaultParameterNameDiscoverer();

    @Around("@annotation(auditable)")
    public Object around(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        // 1. 方法执行前 — 捕获上下文（必须在主线程完成，异步线程没有 RequestContext）
        LocalDateTime operationTime = LocalDateTime.now();
        String ip = getClientIp();
        Long operatorId = requestContext.getCurrentUserId();
        String operatorName = getOperatorName();

        Object result = null;
        boolean success = true;
        String errorMessage = null;

        // 2. 执行目标方法
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable t) {
            success = false;
            errorMessage = t.getMessage();
            if (errorMessage != null && errorMessage.length() > 500) {
                errorMessage = errorMessage.substring(0, 497) + "...";
            }
            throw t; // 重新抛出，让 GlobalExceptionHandler 正常处理
        } finally {
            // 3. 构建 SpEL 上下文
            EvaluationContext spelCtx = buildSpelContext(joinPoint, result);

            // 4. 解析注解中的 SpEL 表达式
            Long resourceId = evalSpelAsLong(auditable.resourceId(), spelCtx);
            String resourceName = evalSpelAsString(auditable.resourceName(), spelCtx);

            // 5. 构建审计日志
            AuditLog auditLog = new AuditLog();
            auditLog.setOperatorId(operatorId);
            auditLog.setOperatorName(operatorName);
            auditLog.setOperationType(auditable.operation());
            auditLog.setResourceType(auditable.resourceType());
            auditLog.setResourceId(resourceId);
            auditLog.setResourceName(resourceName);
            auditLog.setDescription(auditable.description());
            auditLog.setIpAddress(ip);
            auditLog.setSuccess(success ? 1 : 0);
            auditLog.setErrorMessage(errorMessage);
            auditLog.setCreateTime(operationTime);

            // 6. 异步持久化
            auditLogService.saveAsync(auditLog);
        }
    }

    /**
     * 构建 SpEL 表达式求值上下文
     * 将方法参数名 → 参数值映射，并暴露返回值 result
     */
    private EvaluationContext buildSpelContext(ProceedingJoinPoint joinPoint, Object result) {
        StandardEvaluationContext ctx = new StandardEvaluationContext();

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String[] paramNames = paramDiscoverer.getParameterNames(method);
        Object[] args = joinPoint.getArgs();

        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                ctx.setVariable(paramNames[i], args[i]);
            }
        }

        // 暴露返回值 — 用于 CREATE 操作提取生成后的 ID
        if (result != null) {
            ctx.setVariable("result", result);
        }

        return ctx;
    }

    private Long evalSpelAsLong(String expression, EvaluationContext ctx) {
        if (expression == null || expression.isBlank()) return null;
        try {
            Object val = parser.parseExpression(expression).getValue(ctx);
            if (val instanceof Number n) return n.longValue();
            if (val instanceof String s) {
                try { return Long.parseLong(s); } catch (NumberFormatException ignored) {}
            }
            return null;
        } catch (Exception e) {
            log.debug("SpEL 求值失败 [Long]: '{}' — {}", expression, e.getMessage());
            return null;
        }
    }

    private String evalSpelAsString(String expression, EvaluationContext ctx) {
        if (expression == null || expression.isBlank()) return null;
        try {
            Object val = parser.parseExpression(expression).getValue(ctx);
            return val != null ? val.toString() : null;
        } catch (Exception e) {
            log.debug("SpEL 求值失败 [String]: '{}' — {}", expression, e.getMessage());
            return null;
        }
    }

    private String getClientIp() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return "unknown";
        HttpServletRequest req = attrs.getRequest();
        String ip = req.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = req.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = req.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private String getOperatorName() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return null;
        Object username = attrs.getRequest().getAttribute("username");
        return username != null ? username.toString() : null;
    }
}
