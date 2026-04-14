package com.campus.diet.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 供启动脚本与运维探活；不经过业务鉴权。
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public String health() {
        return "ok";
    }

    /** JSON 探活，与联调脚本 {@code "status":"ok"} 断言一致 */
    @GetMapping("/api/health")
    public Map<String, String> apiHealth() {
        return Map.of("status", "ok");
    }
}
