package com.campus.diet.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProdSecurityBaselineValidatorTest {

    @Test
    void validate_shouldRejectDefaultDatasourceCredentialInProd() {
        ProdSecurityBaselineValidator validator = new ProdSecurityBaselineValidator(
                buildProdEnvironment(),
                buildLlmProperties("llm-key"));

        assertThrows(IllegalStateException.class, validator::validate);
    }

    @Test
    void validate_shouldRejectClasspathDefaultDatasourcePasswordInProd() {
        MockEnvironment environment = buildProdEnvironment();
        environment.setProperty("spring.datasource.username", "prod_user");
        environment.setProperty("spring.datasource.password", "change-this-db-password");

        ProdSecurityBaselineValidator validator = new ProdSecurityBaselineValidator(
                environment,
                buildLlmProperties("llm-key"));

        assertThrows(IllegalStateException.class, validator::validate);
    }

    @Test
    void validate_shouldRejectDefaultSeedUserPasswordInProd() {
        MockEnvironment environment = buildProdEnvironment();
        environment.setProperty("spring.datasource.username", "prod_user");
        environment.setProperty("spring.datasource.password", "prod_pwd");

        ProdSecurityBaselineValidator validator = new ProdSecurityBaselineValidator(
                environment,
                buildLlmProperties("llm-key"));

        assertThrows(IllegalStateException.class, validator::validate);
    }

    @Test
    void validate_shouldPassWhenProdSecurityConfigIsSafe() {
        MockEnvironment environment = buildProdEnvironment();
        environment.setProperty("spring.datasource.username", "prod_user");
        environment.setProperty("spring.datasource.password", "prod_pwd");
        environment.setProperty("campus.seed-users.admin-password", "ProdAdmin#2026");
        environment.setProperty("campus.seed-users.canteen-password", "ProdCanteen#2026");
        environment.setProperty("campus.seed-users.demo-password", "ProdDemo#2026");
        environment.setProperty("campus.seed-users.student-password", "ProdStudent#2026");

        ProdSecurityBaselineValidator validator = new ProdSecurityBaselineValidator(
                environment,
                buildLlmProperties("llm-key"));

        assertDoesNotThrow(validator::validate);
    }

    private static MockEnvironment buildProdEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty("campus.jwt.secret", "prod-secret-key-should-be-long-enough-123456");
        environment.setProperty("spring.sql.init.mode", "never");
        environment.setProperty("campus.diet.seed-mock-recipes", "false");
        environment.setProperty("campus.diet.seed-demo-interactions", "false");
        environment.setProperty("campus.diet.seed-weekly-calendar", "false");
        environment.setProperty("spring.datasource.username", "root");
        environment.setProperty("spring.datasource.password", "root");
        environment.setProperty("campus.seed-users.admin-password", "SeedAdmin#2026!");
        environment.setProperty("campus.seed-users.canteen-password", "SeedCanteen#2026!");
        environment.setProperty("campus.seed-users.demo-password", "SeedDemo#2026!");
        environment.setProperty("campus.seed-users.student-password", "SeedStudent#2026!");
        return environment;
    }

    private static LlmProperties buildLlmProperties(String apiKey) {
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setApiKey(apiKey);
        return llmProperties;
    }
}
