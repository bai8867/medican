package com.campus.diet.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.diet.entity.AiIssueSample;
import com.campus.diet.mapper.AiIssueSampleMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiIssueSampleService {

    private final AiIssueSampleMapper aiIssueSampleMapper;
    private final AiTherapyPlanService aiTherapyPlanService;
    private final ObjectMapper objectMapper;

    public AiIssueSampleService(
            AiIssueSampleMapper aiIssueSampleMapper,
            AiTherapyPlanService aiTherapyPlanService,
            ObjectMapper objectMapper) {
        this.aiIssueSampleMapper = aiIssueSampleMapper;
        this.aiTherapyPlanService = aiTherapyPlanService;
        this.objectMapper = objectMapper;
    }

    public List<AiIssueSample> listLatest(int page, int pageSize, boolean unresolvedOnly) {
        return aiIssueSampleMapper.selectList(
                Wrappers.<AiIssueSample>lambdaQuery()
                        .eq(unresolvedOnly, AiIssueSample::getReplayed, 0)
                        .orderByDesc(AiIssueSample::getId)
                        .last("LIMIT " + Math.max(1, pageSize) + " OFFSET " + Math.max(0, (page - 1) * pageSize)));
    }

    public long count(boolean unresolvedOnly) {
        return aiIssueSampleMapper.selectCount(
                Wrappers.<AiIssueSample>lambdaQuery().eq(unresolvedOnly, AiIssueSample::getReplayed, 0));
    }

    public Map<String, Object> detail(Long id) {
        AiIssueSample row = aiIssueSampleMapper.selectById(id);
        if (row == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.getId());
        out.put("symptom", row.getSymptom());
        out.put("constitutionCode", row.getConstitutionCode());
        out.put("qualityScore", row.getQualityScore());
        out.put("scoreThreshold", row.getScoreThreshold());
        out.put("safetyPassed", row.getSafetyPassed());
        out.put("violatedRules", parseJsonArray(row.getViolatedRulesJson()));
        out.put("requestPayload", parseJsonObject(row.getRequestPayloadJson()));
        out.put("responsePayload", parseJsonObject(row.getResponsePayloadJson()));
        out.put("replayed", row.getReplayed());
        out.put("createdAt", row.getCreatedAt());
        return out;
    }

    public Map<String, Object> replay(Long id) {
        AiIssueSample row = aiIssueSampleMapper.selectById(id);
        if (row == null) {
            return Map.of("ok", false, "message", "样本不存在");
        }
        Map<String, Object> payload = parseJsonObject(row.getResponsePayloadJson());
        if (payload.isEmpty()) {
            return Map.of("ok", false, "message", "样本内容为空");
        }
        Map<String, Object> evaluated = aiTherapyPlanService.evaluateOnlyForReplay(
                row.getSymptom(),
                row.getConstitutionCode(),
                payload);
        row.setReplayed(1);
        aiIssueSampleMapper.updateById(row);
        return Map.of("ok", true, "sampleId", id, "result", evaluated);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonObject(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            Object obj = objectMapper.readValue(raw, Map.class);
            if (obj instanceof Map<?, ?>) {
                return (Map<String, Object>) obj;
            }
        } catch (Exception ignore) {
            // ignore
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private List<Object> parseJsonArray(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            Object obj = objectMapper.readValue(raw, List.class);
            if (obj instanceof List<?>) {
                return (List<Object>) obj;
            }
        } catch (Exception ignore) {
            // ignore
        }
        return List.of();
    }
}
