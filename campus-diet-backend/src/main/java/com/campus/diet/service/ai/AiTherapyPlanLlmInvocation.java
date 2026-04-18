package com.campus.diet.service.ai;

import com.campus.diet.service.LlmChatClient;
import com.campus.diet.service.RuntimeMetricService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * AI 食疗方案：构建消息、调用上游 OpenAI 兼容接口、将 JSON 文本解析为 Map（不含业务合并与质量门禁）。
 */
@Component
public class AiTherapyPlanLlmInvocation {

    private static final Logger log = LoggerFactory.getLogger(AiTherapyPlanLlmInvocation.class);

    private final LlmChatClient llmChatClient;
    private final ObjectMapper objectMapper;
    private final RuntimeMetricService runtimeMetricService;

    public AiTherapyPlanLlmInvocation(
            LlmChatClient llmChatClient, ObjectMapper objectMapper, RuntimeMetricService runtimeMetricService) {
        this.llmChatClient = llmChatClient;
        this.objectMapper = objectMapper;
        this.runtimeMetricService = runtimeMetricService;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> chatCompletionsAndParseJson(
            String systemPrompt, String userMessage, String upstreamUrl, String apiKey, String model) throws Exception {
        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMessage));
        int promptChars = LlmPromptBudgetHooks.sumMessageUtf16Units(messages);
        runtimeMetricService.increment("ai.generate.therapy.prompt_budget.observed");
        runtimeMetricService.incrementBy("ai.generate.therapy.prompt_budget.chars_total", promptChars);
        log.debug(
                "therapy_plan LLM prompt_budget utf16_content_units={} approx_tokens={} (observability only; no truncation)",
                promptChars,
                LlmPromptBudgetHooks.approxTokensHeuristic(promptChars));
        String rawContent = llmChatClient.chatCompletionsContent(upstreamUrl, apiKey, model, messages);
        String json = AiTherapyPlanLlmResponseSanitizer.stripMarkdownFence(rawContent);
        JsonNode root = objectMapper.readTree(json);
        return objectMapper.convertValue(root, Map.class);
    }
}
