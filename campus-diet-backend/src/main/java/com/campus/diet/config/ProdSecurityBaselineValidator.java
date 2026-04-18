package com.campus.diet.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Component
public class ProdSecurityBaselineValidator {

    /**
     * 与 {@code application.yml} 中开发占位默认值及常见弱口令对齐，避免 prod 误用未改库的口令上线。
     */
    private static final Set<String> FORBIDDEN_DATASOURCE_PASSWORDS = new HashSet<>(Arrays.asList(
            "root",
            "change-this-db-password",
            "changeme",
            "password",
            "123456",
            "12345678"
    ));

    private final Environment environment;
    private final LlmProperties llmProperties;

    public ProdSecurityBaselineValidator(Environment environment, LlmProperties llmProperties) {
        this.environment = environment;
        this.llmProperties = llmProperties;
    }

    @PostConstruct
    public void validate() {
        if (!isProdProfileActive()) {
            return;
        }
        assertNoDefaultDatasourceCredential();
        String jwtSecret = trimToEmpty(environment.getProperty("campus.jwt.secret"));
        if (jwtSecret.length() < 32 || jwtSecret.contains("dev-only") || jwtSecret.contains("change-before-prod")) {
            throw new IllegalStateException("生产环境必须配置高强度 CAMPUS_JWT_SECRET（至少 32 位，且不可使用开发默认值）");
        }
        String llmKey = trimToEmpty(llmProperties.getApiKey());
        if (llmKey.isEmpty()) {
            throw new IllegalStateException("生产环境必须配置 LLM_API_KEY");
        }
        String sqlInitMode = trimToEmpty(environment.getProperty("spring.sql.init.mode")).toLowerCase(Locale.ROOT);
        if ("always".equals(sqlInitMode)) {
            throw new IllegalStateException("生产环境禁止 spring.sql.init.mode=always");
        }
        assertFlagFalse("campus.diet.seed-mock-recipes");
        assertFlagFalse("campus.diet.seed-demo-interactions");
        assertFlagFalse("campus.diet.seed-weekly-calendar");
        assertNotDefaultSeedPassword("campus.seed-users.admin-password", "SeedAdmin#2026!");
        assertNotDefaultSeedPassword("campus.seed-users.canteen-password", "SeedCanteen#2026!");
        assertNotDefaultSeedPassword("campus.seed-users.demo-password", "SeedDemo#2026!");
        assertNotDefaultSeedPassword("campus.seed-users.student-password", "SeedStudent#2026!");
    }

    private void assertFlagFalse(String key) {
        if (Boolean.parseBoolean(trimToEmpty(environment.getProperty(key)))) {
            throw new IllegalStateException("生产环境禁止开启演示数据开关: " + key);
        }
    }

    private void assertNoDefaultDatasourceCredential() {
        String username = trimToEmpty(environment.getProperty("spring.datasource.username"));
        String password = trimToEmpty(environment.getProperty("spring.datasource.password"));
        if (username.isEmpty() || "root".equalsIgnoreCase(username)) {
            throw new IllegalStateException("生产环境禁止使用 spring.datasource.username 默认值（如 root）");
        }
        if (isForbiddenDatasourcePassword(password)) {
            throw new IllegalStateException(
                    "生产环境禁止使用弱/占位 spring.datasource.password（请通过 MYSQL_PASSWORD 配置强随机口令，勿使用仓库默认值）");
        }
    }

    private static boolean isForbiddenDatasourcePassword(String password) {
        if (password.isEmpty()) {
            return true;
        }
        String normalized = password.toLowerCase(Locale.ROOT);
        return FORBIDDEN_DATASOURCE_PASSWORDS.contains(normalized);
    }

    private void assertNotDefaultSeedPassword(String key, String defaultValue) {
        String password = trimToEmpty(environment.getProperty(key));
        if (password.isEmpty() || defaultValue.equals(password)) {
            throw new IllegalStateException("生产环境禁止使用默认种子用户口令: " + key);
        }
    }

    private boolean isProdProfileActive() {
        return Arrays.stream(environment.getActiveProfiles()).anyMatch("prod"::equalsIgnoreCase);
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
