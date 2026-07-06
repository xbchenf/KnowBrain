package com.knowbrain.common;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 健康检查端点 — 深度探测所有外部依赖
 *
 * GET /api/v1/health
 * - 所有组件 UP   → HTTP 200 + 完整状态
 * - 任一组件 DOWN → HTTP 503 + 完整状态
 */
@RestController
@RequiredArgsConstructor
public class HealthController {

    private final HealthService healthService;

    @GetMapping("/api/v1/health")
    public Map<String, Object> health(HttpServletResponse response) {
        HealthService.HealthResult result = healthService.check();

        if ("DOWN".equals(result.status())) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }

        return Map.of(
                "status", result.status(),
                "components", result.components(),
                "uptime_seconds", result.uptimeSeconds()
        );
    }
}
