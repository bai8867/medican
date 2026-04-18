package com.campus.diet.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 Chat Completions 调用（与《大模型调用调试说明》上游一致）。
 */
@Component
public class LlmChatClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LlmChatClient(@Qualifier("llmRestTemplate") RestTemplate llmRestTemplate) {
        this.restTemplate = llmRestTemplate;
    }

    /**
     * @return choices[0].message.content 原文
     */
    public String chatCompletionsContent(
            String upstreamUrl,
            String apiKey,
            String model,
            List<Map<String, String>> messages) throws Exception {
        return chatCompletionsContent(upstreamUrl, apiKey, model, messages, null);
    }

    /**
     * 方案 C 预留：将 {@code extraTopLevelFields} 合并进请求 JSON 顶层（如 {@code tools}、{@code tool_choice}、
     * {@code response_format}）。键与上游 OpenAI 兼容接口对齐；未启用时传 {@code null}。
     */
    public String chatCompletionsContent(
            String upstreamUrl,
            String apiKey,
            String model,
            List<Map<String, String>> messages,
            Map<String, Object> extraTopLevelFields) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            headers.setBearerAuth(apiKey.trim());
        }
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        if (extraTopLevelFields != null && !extraTopLevelFields.isEmpty()) {
            for (Map.Entry<String, Object> e : extraTopLevelFields.entrySet()) {
                body.put(e.getKey(), e.getValue());
            }
        }
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> resp = restTemplate.postForEntity(upstreamUrl, entity, String.class);
        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
            throw new IllegalStateException("LLM HTTP " + resp.getStatusCode());
        }
        JsonNode root = objectMapper.readTree(resp.getBody());
        return root.path("choices").path(0).path("message").path("content").asText("");
    }
}
