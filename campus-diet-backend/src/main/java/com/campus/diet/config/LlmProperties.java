package com.campus.diet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 大模型网关（OpenAI 兼容 Chat Completions），由 {@code application.yml} 的 {@code llm} 段注入。
 */
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    /** 完整上游 URL，如 .../v1/chat/completions */
    private String url = "";

    /** Bearer Token，对应网关 App Key */
    private String apiKey = "";

    private String model = "";

    /** 读取超时（毫秒），连接超时在 {@link LlmClientConfig} 中取较小固定值 */
    private int timeout = 60_000;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
}
