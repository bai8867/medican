package com.campus.diet.service;

import com.campus.diet.service.ai.AiTherapyPlanGenerationOrchestrator;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * PRD 5.4.3 AI 食疗方案：对外入口；生成编排见 {@link AiTherapyPlanGenerationOrchestrator}。
 */
@Service
public class AiTherapyPlanService {

    private final AiTherapyPlanGenerationOrchestrator generationOrchestrator;

    public AiTherapyPlanService(AiTherapyPlanGenerationOrchestrator generationOrchestrator) {
        this.generationOrchestrator = generationOrchestrator;
    }

    public Map<String, Object> generate(String symptom, String constitutionCode) throws Exception {
        return generationOrchestrator.generate(symptom, constitutionCode);
    }

    public Map<String, Object> evaluateOnlyForReplay(String symptom, String constitutionCode, Map<String, Object> output) {
        return generationOrchestrator.evaluateOnlyForReplay(symptom, constitutionCode, output);
    }
}
