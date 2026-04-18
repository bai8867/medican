package com.campus.diet.service.ai;

import com.campus.diet.entity.Recipe;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AiTherapyPlanLlmPromptBuilderTest {

    @Test
    void buildCatalogJson_containsRecipeFields() {
        Recipe a = new Recipe();
        a.setId(1L);
        a.setName("百合汤");
        String json = AiTherapyPlanLlmPromptBuilder.buildCatalogJson(List.of(a));
        assertTrue(json.startsWith("["));
        assertTrue(json.contains("\"id\":\"1\""));
        assertTrue(json.contains("百合汤"));
    }

    @Test
    void buildSystemPrompt_includesCatalog() {
        String catalog = "[{\"id\":\"1\",\"name\":\"x\"}]";
        String prompt = AiTherapyPlanLlmPromptBuilder.buildSystemPrompt(catalog);
        assertTrue(prompt.contains("therapyRecommendMarkdown"));
        assertTrue(prompt.contains("疾病命名诊断"));
        assertTrue(prompt.endsWith(catalog));
    }
}
