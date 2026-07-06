package com.knowbrain.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 请求上下文工具 — 从 Spring RequestContextHolder 读取当前请求属性
 */
@Component
public class RequestContext {

    /**
     * 获取当前登录用户 ID（由 AuthInterceptor 注入）
     */
    public Long getCurrentUserId() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return null;
        HttpServletRequest request = attrs.getRequest();
        Object uid = request.getAttribute("userId");
        if (uid instanceof Number n) return n.longValue();
        return null;
    }
}
