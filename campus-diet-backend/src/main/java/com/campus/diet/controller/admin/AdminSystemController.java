package com.campus.diet.controller.admin;

import com.campus.diet.common.ApiResponse;
import com.campus.diet.security.SecurityUtils;
import com.campus.diet.service.SystemKvService;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/system")
public class AdminSystemController {

    private final SystemKvService systemKvService;

    public AdminSystemController(SystemKvService systemKvService) {
        this.systemKvService = systemKvService;
    }

    @PutMapping("/recommend")
    public ApiResponse<Map<String, Object>> setRecommend(@RequestParam boolean enabled) {
        SecurityUtils.requireAdmin();
        systemKvService.upsert("recommend.global.enabled", enabled ? "1" : "0");
        Map<String, Object> m = new HashMap<>();
        m.put("ok", true);
        return ApiResponse.ok(m);
    }

    @PutMapping("/ai")
    public ApiResponse<Map<String, Object>> setAi(@RequestParam boolean enabled) {
        SecurityUtils.requireAdmin();
        systemKvService.upsert("ai.generation.enabled", enabled ? "1" : "0");
        Map<String, Object> m = new HashMap<>();
        m.put("ok", true);
        return ApiResponse.ok(m);
    }

    @GetMapping("/flags")
    public ApiResponse<Map<String, Object>> flags() {
        SecurityUtils.requireAdmin();
        Map<String, Object> m = new HashMap<>();
        m.put("recommendGlobalEnabled", systemKvService.flagOn("recommend.global.enabled", true));
        m.put("aiGenerationEnabled", systemKvService.flagOn("ai.generation.enabled", true));
        return ApiResponse.ok(m);
    }

    @PutMapping("/kv")
    public ApiResponse<Map<String, Object>> kv(@RequestBody KvBody body) {
        SecurityUtils.requireAdmin();
        systemKvService.upsert(body.getK(), body.getV());
        Map<String, Object> m = new HashMap<>();
        m.put("ok", true);
        return ApiResponse.ok(m);
    }

    @Data
    public static class KvBody {
        private String k;
        private String v;
    }
}
