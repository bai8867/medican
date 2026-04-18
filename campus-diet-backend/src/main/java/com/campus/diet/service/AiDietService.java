package com.campus.diet.service;

import com.campus.diet.config.LlmProperties;
import com.campus.diet.service.ai.DietPlanLlmSkillAssembler;
import com.campus.diet.service.ai.DietPlanSkillAssembly;
import com.campus.diet.service.ai.LlmPromptBudgetHooks;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 食疗方案生成：默认 Mock；关闭 mock 且配置上游时调用 OpenAI 兼容 Chat Completions。
 */
@Service
public class AiDietService {

    private static final Logger log = LoggerFactory.getLogger(AiDietService.class);

    private final SystemKvService systemKvService;
    private final RuntimeLlmSkillPathResolver runtimeLlmSkillPathResolver;
    private final RuntimeMetricService runtimeMetricService;
    private final LlmChatClient llmChatClient;
    private final LlmProperties llmProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final boolean mock;

    public AiDietService(
            SystemKvService systemKvService,
            RuntimeLlmSkillPathResolver runtimeLlmSkillPathResolver,
            RuntimeMetricService runtimeMetricService,
            LlmChatClient llmChatClient,
            LlmProperties llmProperties,
            @Value("${campus.ai.mock:true}") boolean mock) {
        this.systemKvService = systemKvService;
        this.runtimeLlmSkillPathResolver = runtimeLlmSkillPathResolver;
        this.runtimeMetricService = runtimeMetricService;
        this.llmChatClient = llmChatClient;
        this.llmProperties = llmProperties;
        this.mock = mock;
    }

    public Map<String, Object> generate(String symptoms) throws Exception {
        long started = System.currentTimeMillis();
        runtimeMetricService.increment("ai.diet.total");
        if (!systemKvService.flagOn("ai.generation.enabled", true)) {
            runtimeMetricService.increment("ai.diet.disabled");
            Map<String, Object> off = baseEnvelope(symptoms);
            off.put("enabled", false);
            off.put("message", "AI 生成功能已由管理员关闭");
            runtimeMetricService.recordCostMs("ai.diet", System.currentTimeMillis() - started);
            return off;
        }
        String apiKey = llmProperties.getApiKey() == null ? "" : llmProperties.getApiKey().trim();
        String upstreamUrl = llmProperties.getUrl() == null ? "" : llmProperties.getUrl().trim();
        String model = llmProperties.getModel() == null ? "" : llmProperties.getModel().trim();
        if (mock || upstreamUrl.isEmpty() || model.isEmpty() || "mock".equalsIgnoreCase(model)) {
            runtimeMetricService.increment("ai.diet.fallback");
            Map<String, Object> out = mockPlan(symptoms);
            runtimeMetricService.recordCostMs("ai.diet", System.currentTimeMillis() - started);
            return out;
        }
        Map<String, Object> out = callUpstream(symptoms, upstreamUrl, apiKey, model);
        runtimeMetricService.recordCostMs("ai.diet", System.currentTimeMillis() - started);
        return out;
    }

    private Map<String, Object> callUpstream(String symptoms, String upstreamUrl, String apiKey, String model)
            throws Exception {
        DietPlanSkillAssembly assembly =
                DietPlanLlmSkillAssembler.assemble(runtimeLlmSkillPathResolver.resolveDietSystemPromptResource());
        if ("diet_plan.default".equals(assembly.getSkillSetId())) {
            runtimeMetricService.increment("ai.diet.skill_set.default");
        } else {
            runtimeMetricService.increment("ai.diet.skill_set.custom");
        }
        log.debug(
                "diet_plan LLM skill_set_id={} system_sha256={}",
                assembly.getSkillSetId(),
                assembly.getSkillContentSha256Hex());
        String system = assembly.getSystemPrompt();
        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", "症状描述：" + symptoms)
        );
        int promptChars = LlmPromptBudgetHooks.sumMessageUtf16Units(messages);
        runtimeMetricService.increment("ai.diet.prompt_budget.observed");
        runtimeMetricService.incrementBy("ai.diet.prompt_budget.chars_total", promptChars);
        log.debug(
                "diet_plan LLM prompt_budget utf16_content_units={} approx_tokens={} (observability only; no truncation)",
                promptChars,
                LlmPromptBudgetHooks.approxTokensHeuristic(promptChars));
        String content = llmChatClient.chatCompletionsContent(upstreamUrl, apiKey, model, messages);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(content, Map.class);
            parsed.putAll(complianceFields(true));
            parsed.put("symptomsInput", symptoms);
            runtimeMetricService.increment("ai.diet.upstream.success");
            return parsed;
        } catch (Exception parseEx) {
            runtimeMetricService.increment("ai.diet.upstream.failed");
            return mockPlan(symptoms);
        }
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
