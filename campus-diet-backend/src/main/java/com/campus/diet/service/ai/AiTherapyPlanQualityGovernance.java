package com.campus.diet.service.ai;

import com.campus.diet.entity.AiIssueSample;
import com.campus.diet.entity.Recipe;
import com.campus.diet.mapper.AiIssueSampleMapper;
import com.campus.diet.security.LoginUser;
import com.campus.diet.security.LoginUserHolder;
import com.campus.diet.service.RuntimeMetricService;
import com.campus.diet.service.SystemKvService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * AI 食疗方案质量门禁：打分、安全规则、未达标样本沉淀、严格模式下上游结果回退本地拼装。
 * <p>由 {@link AiTherapyPlanGenerationOrchestrator} 调用，不包含大模型 I/O。
 */
@Component
public class AiTherapyPlanQualityGovernance {

    private static final Logger log = LoggerFactory.getLogger(AiTherapyPlanQualityGovernance.class);

    private static final List<Map.Entry<String, Pattern>> SAFETY_RULES = List.of(
            Map.entry("NO_CURE_CLAIM", Pattern.compile("保证治愈|根治|包治|药到病除|三天见效|特效药")),
            Map.entry("NO_DRUG_PRESCRIPTION", Pattern.compile("阿莫西林|头孢|处方药|抗生素|剂量\\s*\\d+\\s*(mg|g|ml)", Pattern.CASE_INSENSITIVE)),
            Map.entry("NO_DIAGNOSIS", Pattern.compile("确诊|诊断为|你患有|属于.*疾病"))
    );

    private final SystemKvService systemKvService;
    private final RuntimeMetricService runtimeMetricService;
    private final AiTherapyPlanRecipePoolLoader recipePoolLoader;
    private final AiIssueSampleMapper aiIssueSampleMapper;
    private final AiTherapyPlanOutputMerger outputMerger;
    private final ObjectMapper objectMapper;

    public AiTherapyPlanQualityGovernance(
            SystemKvService systemKvService,
            RuntimeMetricService runtimeMetricService,
            AiTherapyPlanRecipePoolLoader recipePoolLoader,
            AiIssueSampleMapper aiIssueSampleMapper,
            AiTherapyPlanOutputMerger outputMerger,
            ObjectMapper objectMapper) {
        this.systemKvService = systemKvService;
        this.runtimeMetricService = runtimeMetricService;
        this.recipePoolLoader = recipePoolLoader;
        this.aiIssueSampleMapper = aiIssueSampleMapper;
        this.outputMerger = outputMerger;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> apply(
            String symptom,
            String constitutionCode,
            Map<String, Object> out,
            boolean fromFallback,
            String source) {
        boolean guardEnabled = systemKvService.flagOn("ai.quality.guard.enabled", true);
        boolean strictSafety = systemKvService.flagOn("ai.quality.safety.strict", true);
        int threshold = parseScoreThreshold(systemKvService.get("ai.quality.score.threshold", "75"));

        int score = scoreOutput(out);
        List<String> violatedRules = detectSafetyViolations(out);
        boolean safetyPassed = violatedRules.isEmpty();
        boolean pass = (!guardEnabled) || (score >= threshold && safetyPassed);

        Long sampleId = null;
        if (guardEnabled && !pass) {
            sampleId = captureIssueSample(symptom, constitutionCode, out, score, threshold, safetyPassed, violatedRules, source);
        }

        Map<String, Object> governance = new LinkedHashMap<>();
        governance.put("guardEnabled", guardEnabled);
        governance.put("strictSafety", strictSafety);
        governance.put("threshold", threshold);
        governance.put("score", score);
        governance.put("safetyPassed", safetyPassed);
        governance.put("violatedRules", violatedRules);
        governance.put("pass", pass);
        governance.put("sampleCaptured", sampleId != null);
        governance.put("sampleId", sampleId);

        out.put("qualityGovernance", governance);

        if (!pass && strictSafety && !fromFallback) {
            runtimeMetricService.increment("ai.quality.blocked");
            List<Recipe> pool = recipePoolLoader.loadActivePoolOrderedByCollectDesc();
            Map<String, Object> safeFallback = outputMerger.buildLocalPlan(
                    symptom,
                    AiTherapyPlanConstitutionLabels.labelOrRaw(constitutionCode),
                    pool,
                    symptom.length() > 0 && symptom.length() < 4,
                    true);
            safeFallback.put("qualityGovernance", governance);
            safeFallback.put("qualityBlocked", true);
            return safeFallback;
        }
        return out;
    }

    private int scoreOutput(Map<String, Object> out) {
        int score = 100;
        if (AiTherapyPlanOutputMerger.str(out.get("therapyRecommendMarkdown"), "").isBlank()) {
            score -= 30;
        }
        if (AiTherapyPlanOutputMerger.readRecipes(out.get("recipes")).size() < 3) {
            score -= 20;
        }
        if (AiTherapyPlanOutputMerger.readCoreIngredients(out.get("coreIngredients")).size() < 3) {
            score -= 15;
        }
        if (AiTherapyPlanOutputMerger.readStringList(out.get("lifestyleAdvice")).size() < 3) {
            score -= 10;
        }
        if (AiTherapyPlanOutputMerger.readStringList(out.get("cautionNotes")).size() < 3) {
            score -= 10;
        }
        if (AiTherapyPlanOutputMerger.str(out.get("disclaimer"), "").isBlank()) {
            score -= 5;
        }
        if (AiTherapyPlanOutputMerger.str(out.get("rationale"), "").length() < 20) {
            score -= 10;
        }
        return Math.max(0, score);
    }

    private List<String> detectSafetyViolations(Map<String, Object> out) {
        String text = AiTherapyPlanOutputMerger.str(out.get("therapyRecommendMarkdown"), "")
                + "\n"
                + AiTherapyPlanOutputMerger.str(out.get("rationale"), "");
        List<String> violations = new ArrayList<>();
        for (Map.Entry<String, Pattern> rule : SAFETY_RULES) {
            if (rule.getValue().matcher(text).find()) {
                violations.add(rule.getKey());
            }
        }
        return violations;
    }

    private Long captureIssueSample(
            String symptom,
            String constitutionCode,
            Map<String, Object> output,
            int score,
            int threshold,
            boolean safetyPassed,
            List<String> violatedRules,
            String source) {
        try {
            AiIssueSample sample = new AiIssueSample();
            sample.setSymptom(symptom);
            sample.setConstitutionCode(constitutionCode);
            sample.setQualityScore(score);
            sample.setScoreThreshold(threshold);
            sample.setSafetyPassed(safetyPassed ? 1 : 0);
            sample.setViolatedRulesJson(objectMapper.writeValueAsString(violatedRules));
            Map<String, Object> requestPayload = new LinkedHashMap<>();
            requestPayload.put("symptom", symptom);
            requestPayload.put("constitutionCode", constitutionCode);
            requestPayload.put("userId", currentUserIdSafe());
            sample.setRequestPayloadJson(objectMapper.writeValueAsString(requestPayload));
            sample.setResponsePayloadJson(objectMapper.writeValueAsString(output));
            sample.setGuardEnabled(systemKvService.flagOn("ai.quality.guard.enabled", true) ? 1 : 0);
            sample.setStrictSafety(systemKvService.flagOn("ai.quality.safety.strict", true) ? 1 : 0);
            sample.setSource(source);
            sample.setReplayed(0);
            aiIssueSampleMapper.insert(sample);
            runtimeMetricService.increment("ai.quality.sample.captured");
            return sample.getId();
        } catch (Exception e) {
            log.warn("AI质量问题样本沉淀失败: {}", e.getMessage());
            return null;
        }
    }

    private static Long currentUserIdSafe() {
        LoginUser user = LoginUserHolder.get();
        return user == null ? null : user.getUserId();
    }

    private static int parseScoreThreshold(String raw) {
        try {
            int n = Integer.parseInt(raw == null ? "" : raw.trim());
            if (n < 0) {
                return 0;
            }
            if (n > 100) {
                return 100;
            }
            return n;
        } catch (Exception ignore) {
            return 75;
        }
    }
}
