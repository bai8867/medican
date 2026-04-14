package com.campus.diet.service;

import com.campus.diet.config.LlmProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 食疗方案生成：默认 Mock；关闭 mock 且配置上游时调用 OpenAI 兼容 Chat Completions。
 */
@Service
public class AiDietService {

    private final SystemKvService systemKvService;
    private final RestTemplate restTemplate;
    private final LlmProperties llmProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final boolean mock;

    public AiDietService(
            SystemKvService systemKvService,
            @Qualifier("llmRestTemplate") RestTemplate llmRestTemplate,
            LlmProperties llmProperties,
            @Value("${campus.ai.mock:true}") boolean mock) {
        this.systemKvService = systemKvService;
        this.restTemplate = llmRestTemplate;
        this.llmProperties = llmProperties;
        this.mock = mock;
    }

    public Map<String, Object> generate(String symptoms) throws Exception {
        if (!systemKvService.flagOn("ai.generation.enabled", true)) {
            Map<String, Object> off = baseEnvelope(symptoms);
            off.put("enabled", false);
            off.put("message", "AI 生成功能已由管理员关闭");
            return off;
        }
        String apiKey = llmProperties.getApiKey() == null ? "" : llmProperties.getApiKey().trim();
        String upstreamUrl = llmProperties.getUrl() == null ? "" : llmProperties.getUrl().trim();
        String model = llmProperties.getModel() == null ? "" : llmProperties.getModel().trim();
        if (mock || upstreamUrl.isEmpty() || model.isEmpty() || "mock".equalsIgnoreCase(model)) {
            return mockPlan(symptoms);
        }
        return callUpstream(symptoms, upstreamUrl, apiKey, model);
    }

    private Map<String, Object> callUpstream(String symptoms, String upstreamUrl, String apiKey, String model)
            throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isBlank()) {
            headers.setBearerAuth(apiKey.trim());
        }
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
                Map.of("role", "system", "content", buildSystemPrompt()),
                Map.of("role", "user", "content", "症状描述：" + symptoms)
        ));
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> resp = restTemplate.postForEntity(upstreamUrl, entity, String.class);
        JsonNode root = objectMapper.readTree(resp.getBody());
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(content, Map.class);
            parsed.putAll(complianceFields(true));
            parsed.put("symptomsInput", symptoms);
            return parsed;
        } catch (Exception parseEx) {
            return mockPlan(symptoms);
        }
    }

    private String buildSystemPrompt() {
        return "你是校园膳食指导助手。根据用户症状输出**仅 JSON**（不要 Markdown），字段："
                + "summary(string), meals(array of {name, ingredients[], note}), cautions(string[]), "
                + "isAiGenerated(true), notMedicalAdvice(true), regulatoryNotice(\"CN-TCM-DIET-AI-V1\")。"
                + "不得诊断疾病，仅作膳食参考。";
    }

    private Map<String, Object> mockPlan(String symptoms) {
        Map<String, Object> m = new HashMap<>();
        m.put("symptomsInput", symptoms);
        m.put("summary", "根据描述，建议以清淡易消化、规律进食为主，并结合校园食堂常见菜品做温和调理（演示数据）。");
        m.put("meals", List.of(
                Map.of(
                        "name", "山药小米粥",
                        "ingredients", List.of("小米 50g", "山药 30g"),
                        "note", "早晚温服，适量即可。"
                ),
                Map.of(
                        "name", "陈皮茯苓饮",
                        "ingredients", List.of("陈皮 3g", "茯苓 6g"),
                        "note", "沸水冲泡代茶，胃寒明显者减量。"
                )
        ));
        m.put("cautions", List.of("本方案为演示生成，不能替代诊疗。", "如症状持续或加重请及时就医。"));
        m.putAll(complianceFields(true));
        m.put("mock", true);
        return m;
    }

    private Map<String, Object> baseEnvelope(String symptoms) {
        Map<String, Object> m = new HashMap<>();
        m.put("symptomsInput", symptoms);
        m.putAll(complianceFields(false));
        return m;
    }

    private Map<String, Object> complianceFields(boolean ai) {
        Map<String, Object> c = new HashMap<>();
        c.put("isAiGenerated", ai);
        c.put("notMedicalAdvice", true);
        c.put("regulatoryNotice", "CN-TCM-DIET-AI-V1");
        c.put("disclaimer", "本内容由 AI 生成，仅供校园膳食科普与参考，不构成医疗建议。");
        return c;
    }
}
