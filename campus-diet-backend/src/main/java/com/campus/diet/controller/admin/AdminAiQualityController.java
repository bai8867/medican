package com.campus.diet.controller.admin;

import com.campus.diet.common.ApiResponse;
import com.campus.diet.security.SecurityUtils;
import com.campus.diet.service.AiIssueSampleService;
import com.campus.diet.service.SystemKvService;
import lombok.Data;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/ai-quality")
public class AdminAiQualityController {

    private final SystemKvService systemKvService;
    private final AiIssueSampleService aiIssueSampleService;

    public AdminAiQualityController(SystemKvService systemKvService, AiIssueSampleService aiIssueSampleService) {
        this.systemKvService = systemKvService;
        this.aiIssueSampleService = aiIssueSampleService;
    }

    @GetMapping("/rules")
    public ApiResponse<Map<String, Object>> rules() {
        SecurityUtils.requireAdmin();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("guardEnabled", systemKvService.flagOn("ai.quality.guard.enabled", true));
        data.put("strictSafety", systemKvService.flagOn("ai.quality.safety.strict", true));
        data.put("scoreThreshold", parseThreshold(systemKvService.get("ai.quality.score.threshold", "75")));
        return ApiResponse.ok(data);
    }

    @PutMapping("/rules")
    public ApiResponse<Map<String, Object>> updateRules(@RequestBody UpdateRulesBody body) {
        SecurityUtils.requireAdmin();
        if (body.getGuardEnabled() != null) {
            systemKvService.upsert("ai.quality.guard.enabled", body.getGuardEnabled() ? "1" : "0");
        }
        if (body.getStrictSafety() != null) {
            systemKvService.upsert("ai.quality.safety.strict", body.getStrictSafety() ? "1" : "0");
        }
        if (body.getScoreThreshold() != null) {
            systemKvService.upsert("ai.quality.score.threshold", String.valueOf(clamp(body.getScoreThreshold())));
        }
        return rules();
    }

    @GetMapping("/samples")
    public ApiResponse<Map<String, Object>> samples(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "false") boolean unresolvedOnly) {
        SecurityUtils.requireAdmin();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("page", Math.max(1, page));
        data.put("pageSize", Math.max(1, pageSize));
        data.put("total", aiIssueSampleService.count(unresolvedOnly));
        data.put("records", aiIssueSampleService.listLatest(page, pageSize, unresolvedOnly));
        return ApiResponse.ok(data);
    }

    @GetMapping("/samples/{id}")
    public ApiResponse<Map<String, Object>> sampleDetail(@PathVariable Long id) {
        SecurityUtils.requireAdmin();
        return ApiResponse.ok(aiIssueSampleService.detail(id));
    }

    @PostMapping("/samples/{id}/replay")
    public ApiResponse<Map<String, Object>> replay(@PathVariable Long id) {
        SecurityUtils.requireAdmin();
        return ApiResponse.ok(aiIssueSampleService.replay(id));
    }

    private static int parseThreshold(String raw) {
        try {
            return clamp(Integer.parseInt(raw == null ? "" : raw.trim()));
        } catch (Exception ignore) {
            return 75;
        }
    }

    private static int clamp(int n) {
        if (n < 0) {
            return 0;
        }
        if (n > 100) {
            return 100;
        }
        return n;
    }

    @Data
    public static class UpdateRulesBody {
        private Boolean guardEnabled;
        private Boolean strictSafety;
        private Integer scoreThreshold;
    }
}
