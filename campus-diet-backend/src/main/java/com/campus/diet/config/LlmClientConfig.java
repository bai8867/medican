package com.campus.diet.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmClientConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmClientConfig.class);

    /**
     * 专用于大模型 HTTP 调用，超时与 {@link LlmProperties#getTimeout()} 对齐（读超时）。
     */
    @Bean
    public RestTemplate llmRestTemplate(LlmProperties llmProperties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int readMs = Math.max(1_000, llmProperties.getTimeout());
        factory.setReadTimeout(readMs);
        int connectMs = Math.min(15_000, readMs);
        factory.setConnectTimeout(Math.max(1_000, connectMs));
        return new RestTemplate(factory);
    }

    /** 启动时打印 LLM 配置摘要，便于确认是否将走真实上游（Ollama 可不配 api-key）。 */
    @Bean
    public ApplicationRunner llmConfigSummary(LlmProperties llmProperties) {
        return args -> {
            String url = llmProperties.getUrl() == null ? "" : llmProperties.getUrl().trim();
            String model = llmProperties.getModel() == null ? "" : llmProperties.getModel().trim();
            boolean hasKey =
                    llmProperties.getApiKey() != null && !llmProperties.getApiKey().trim().isEmpty();
            if (url.isEmpty() || model.isEmpty()) {
                log.warn("LLM 未就绪：请配置 llm.url 与 llm.model（内网网关或本机 Ollama 等 OpenAI 兼容地址）");
            } else {
                log.info("LLM 已配置：url={} model={} apiKeyConfigured={}", url, model, hasKey);
            }
        };
    }
}
