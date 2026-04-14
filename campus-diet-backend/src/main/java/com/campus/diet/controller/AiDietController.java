package com.campus.diet.controller;

import com.campus.diet.common.ApiResponse;
import com.campus.diet.service.AiDietService;
import com.campus.diet.service.AiTherapyPlanService;
import lombok.Data;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/ai")
public class AiDietController {

    private final AiDietService aiDietService;
    private final AiTherapyPlanService aiTherapyPlanService;

    public AiDietController(AiDietService aiDietService, AiTherapyPlanService aiTherapyPlanService) {
        this.aiDietService = aiDietService;
        this.aiTherapyPlanService = aiTherapyPlanService;
    }

    @PostMapping("/diet-plan")
    public ApiResponse<Map<String, Object>> dietPlan(@Valid @RequestBody SymptomBody body) throws Exception {
        Map<String, Object> data = aiDietService.generate(body.getSymptoms());
        return ApiResponse.ok(data);
    }

    /**
     * PRD 5.4 — AI 食疗方案（前端 {@code /ai/generate}），走 OpenAI 兼容上游。
     */
    @PostMapping("/generate")
    public ApiResponse<Map<String, Object>> generateTherapy(@Valid @RequestBody TherapyGenerateBody body)
            throws Exception {
        Map<String, Object> data = aiTherapyPlanService.generate(body.getSymptom(), body.getConstitution());
        return ApiResponse.ok(data);
    }

    @PostMapping("/feedback")
    public ApiResponse<Map<String, Object>> feedback(@RequestBody(required = false) Map<String, Object> ignored) {
        return ApiResponse.ok(Map.of("received", true));
    }

    @Data
    public static class SymptomBody {
        @NotBlank
        private String symptoms;
    }

    @Data
    public static class TherapyGenerateBody {
        @NotBlank
        private String symptom;
        /** 体质 code，如 yinxu；可选 */
        private String constitution;
    }
}
