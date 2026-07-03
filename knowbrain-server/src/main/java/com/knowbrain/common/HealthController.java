package com.knowbrain.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查端点 — Docker 容器健康探测 + 启动脚本就绪检测
 */
@RestController
public class HealthController {

    @GetMapping("/api/v1/health")
    public Result<String> health() {
        return Result.ok("OK", "KnowBrain is running");
    }
}
