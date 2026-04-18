package com.campus.diet.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.campus.diet.config.LlmProperties;
import com.campus.diet.config.RuntimeLlmSkillPathProperties;
import com.campus.diet.service.RuntimeLlmSkillPathResolver;
import com.campus.diet.service.RuntimeMetricService;
import com.campus.diet.entity.AiIssueSample;
import com.campus.diet.entity.Recipe;
import com.campus.diet.mapper.AiIssueSampleMapper;
import com.campus.diet.mapper.RecipeMapper;
import com.campus.diet.service.ai.AiTherapyPlanDisabledResponseFactory;
import com.campus.diet.service.ai.AiTherapyPlanGenerationOrchestrator;
import com.campus.diet.service.ai.AiTherapyPlanLlmInvocation;
import com.campus.diet.service.ai.AiTherapyPlanOutputMerger;
import com.campus.diet.service.ai.AiTherapyPlanQualityGovernance;
import com.campus.diet.service.ai.AiTherapyPlanRecipePoolLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiTherapyPlanServiceTest {

    @Mock
    private SystemKvService systemKvService;
    @Mock
    private RecipeMapper recipeMapper;
    @Mock
    private LlmChatClient llmChatClient;
    @Mock
    private AiIssueSampleMapper aiIssueSampleMapper;

    private AiTherapyPlanService buildService(LlmProperties llmProperties) {
        return buildService(llmProperties, new RuntimeMetricService());
    }

    private AiTherapyPlanService buildService(LlmProperties llmProperties, RuntimeMetricService runtimeMetricService) {
        AiTherapyPlanOutputMerger merger = new AiTherapyPlanOutputMerger();
        AiTherapyPlanRecipePoolLoader poolLoader = new AiTherapyPlanRecipePoolLoader(recipeMapper);
        ObjectMapper objectMapper = new ObjectMapper();
        AiTherapyPlanQualityGovernance qualityGovernance = new AiTherapyPlanQualityGovernance(
                systemKvService,
                runtimeMetricService,
                poolLoader,
                aiIssueSampleMapper,
                merger,
                objectMapper);
        AiTherapyPlanLlmInvocation llmInvocation =
                new AiTherapyPlanLlmInvocation(llmChatClient, objectMapper, runtimeMetricService);
        RuntimeLlmSkillPathProperties runtimeLlmSkillPathProperties = new RuntimeLlmSkillPathProperties();
        lenient().when(systemKvService.get("ai.runtime-llm.therapy-resource-prefix", "")).thenReturn("");
        lenient().when(systemKvService.get("ai.runtime-llm.diet-system-prompt-resource", "")).thenReturn("");
        RuntimeLlmSkillPathResolver runtimeLlmSkillPathResolver =
                new RuntimeLlmSkillPathResolver(runtimeLlmSkillPathProperties, systemKvService);
        AiTherapyPlanGenerationOrchestrator orchestrator = new AiTherapyPlanGenerationOrchestrator(
                new AiTherapyPlanDisabledResponseFactory(),
                systemKvService,
                runtimeMetricService,
                poolLoader,
                runtimeLlmSkillPathResolver,
                llmProperties,
                merger,
                qualityGovernance,
                llmInvocation,
                "",
                false);
        return new AiTherapyPlanService(orchestrator);
    }

    @Test
    void generate_shouldFallbackToLocalWhenUpstreamResponseCannotParse() throws Exception {
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setUrl("http://mock-llm/v1/chat/completions");
        llmProperties.setModel("gpt-test");
        llmProperties.setApiKey("k");

        RuntimeMetricService runtimeMetricService = new RuntimeMetricService();
        AiTherapyPlanService service = buildService(llmProperties, runtimeMetricService);

        when(systemKvService.flagOn("ai.generation.enabled", true)).thenReturn(true);
        when(systemKvService.get("ai.therapy.route.override", "")).thenReturn("");
        when(recipeMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(recipe(1L, "百合银耳汤"), recipe(2L, "山药鸡汤"), recipe(3L, "薏米粥")));
        when(llmChatClient.chatCompletionsContent(anyString(), anyString(), anyString(), anyList()))
                .thenReturn("this-is-not-json");

        Map<String, Object> result = service.generate("熬夜后口干", "yinxu");

        assertEquals(true, result.get("localFallback"));
        assertEquals(false, result.get("isGenericPlan"));
        assertNotNull(result.get("therapyRecommendMarkdown"));
        assertTrue(((String) result.get("therapyRecommendMarkdown")).contains("## 调养焦点"));
        assertTrue(((List<?>) result.get("recipes")).size() >= 3);
        assertTrue(result.containsKey("qualityGovernance"));
        assertEquals(1L, counter(runtimeMetricService, "ai.generate.upstream.failed"));
    }

    @Test
    void generate_shouldReturnDisabledPayloadWhenAiSwitchOff() throws Exception {
        RuntimeMetricService runtimeMetricService = new RuntimeMetricService();
        AiTherapyPlanService service = buildService(new LlmProperties(), runtimeMetricService);

        when(systemKvService.flagOn("ai.generation.enabled", true)).thenReturn(false);

        Map<String, Object> result = service.generate("失眠", "qixu");

        assertEquals(false, result.get("enabled"));
        assertEquals(true, result.get("isGenericPlan"));
        assertFalse(((String) result.get("therapyRecommendMarkdown")).isBlank());
        assertEquals(1L, counter(runtimeMetricService, "ai.generate.disabled"));
    }

    @Test
    void generate_shouldCaptureIssueSampleWhenSafetyRulesViolated() throws Exception {
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setUrl("http://mock-llm/v1/chat/completions");
        llmProperties.setModel("gpt-test");
        llmProperties.setApiKey("k");

        AiTherapyPlanService service = buildService(llmProperties);

        when(systemKvService.flagOn("ai.generation.enabled", true)).thenReturn(true);
        when(systemKvService.get("ai.therapy.route.override", "")).thenReturn("");
        when(systemKvService.flagOn("ai.quality.guard.enabled", true)).thenReturn(true);
        when(systemKvService.flagOn("ai.quality.safety.strict", true)).thenReturn(true);
        when(systemKvService.get("ai.quality.score.threshold", "75")).thenReturn("75");
        when(recipeMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(recipe(1L, "百合银耳汤"), recipe(2L, "山药鸡汤"), recipe(3L, "薏米粥")));
        when(llmChatClient.chatCompletionsContent(anyString(), anyString(), anyString(), anyList()))
                .thenReturn("{\"symptomSummary\":\"咽喉不适\",\"constitutionApplied\":\"阴虚质\",\"recipes\":[],"
                        + "\"coreIngredients\":[],\"lifestyleAdvice\":[],\"cautionNotes\":[],"
                        + "\"rationale\":\"保证治愈，三天见效\",\"disclaimer\":\"\",\"therapyRecommendMarkdown\":\"## 调养焦点\\n保证治愈\\n\"}");

        Map<String, Object> result = service.generate("咽喉痛", "yinxu");

        assertEquals(true, result.get("localFallback"));
        assertTrue(result.containsKey("qualityGovernance"));
        verify(aiIssueSampleMapper).insert(any(AiIssueSample.class));
    }

    @Test
    void generate_briefRoute_insertsVagueSkillIntoSystemPrompt() throws Exception {
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setUrl("http://mock-llm/v1/chat/completions");
        llmProperties.setModel("gpt-test");
        llmProperties.setApiKey("k");

        AiTherapyPlanService service = buildService(llmProperties);

        when(systemKvService.flagOn("ai.generation.enabled", true)).thenReturn(true);
        when(systemKvService.get("ai.therapy.route.override", "")).thenReturn("");
        when(recipeMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(recipe(1L, "百合银耳汤"), recipe(2L, "山药鸡汤"), recipe(3L, "薏米粥")));
        ArgumentCaptor<List<Map<String, String>>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        when(llmChatClient.chatCompletionsContent(
                        anyString(), anyString(), anyString(), messagesCaptor.capture()))
                .thenReturn(
                        "{\"symptomSummary\":\"口干\",\"constitutionApplied\":\"阴虚质\","
                                + "\"recipes\":[{\"id\":1,\"name\":\"百合银耳汤\"}],"
                                + "\"coreIngredients\":[],\"lifestyleAdvice\":[],\"cautionNotes\":[],"
                                + "\"rationale\":\"\",\"disclaimer\":\"提示\",\"therapyRecommendMarkdown\":"
                                + "\"## 调养焦点\\n正文\\n\"}");

        service.generate("口干", "yinxu");

        List<Map<String, String>> messages = messagesCaptor.getValue();
        assertEquals("system", messages.get(0).get("role"));
        assertTrue(messages.get(0).get("content").contains("当前用户输入可能较短或较笼统"));
    }

    @Test
    void generate_routeOverrideDefault_skipsBriefSkillDespiteShortSymptom() throws Exception {
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setUrl("http://mock-llm/v1/chat/completions");
        llmProperties.setModel("gpt-test");
        llmProperties.setApiKey("k");

        RuntimeMetricService runtimeMetricService = new RuntimeMetricService();
        AiTherapyPlanService service = buildService(llmProperties, runtimeMetricService);

        when(systemKvService.flagOn("ai.generation.enabled", true)).thenReturn(true);
        when(systemKvService.get("ai.therapy.route.override", "")).thenReturn("default");
        when(recipeMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(recipe(1L, "百合银耳汤"), recipe(2L, "山药鸡汤"), recipe(3L, "薏米粥")));
        when(llmChatClient.chatCompletionsContent(anyString(), anyString(), anyString(), anyList()))
                .thenReturn(
                        "{\"symptomSummary\":\"口干\",\"constitutionApplied\":\"阴虚质\","
                                + "\"recipes\":[{\"id\":1,\"name\":\"百合银耳汤\"}],"
                                + "\"coreIngredients\":[],\"lifestyleAdvice\":[],\"cautionNotes\":[],"
                                + "\"rationale\":\"\",\"disclaimer\":\"提示\",\"therapyRecommendMarkdown\":"
                                + "\"## 调养焦点\\n正文\\n\"}");

        service.generate("口干", "yinxu");

        assertEquals(1L, counter(runtimeMetricService, "ai.generate.therapy.skill_set.default"));
        assertEquals(0L, counter(runtimeMetricService, "ai.generate.therapy.skill_set.brief"));
    }

    private static Recipe recipe(Long id, String name) {
        Recipe recipe = new Recipe();
        recipe.setId(id);
        recipe.setName(name);
        recipe.setStatus(1);
        recipe.setCollectCount(10);
        recipe.setEfficacySummary("调养参考");
        return recipe;
    }

    private static long counter(RuntimeMetricService runtimeMetricService, String key) {
        Object counters = runtimeMetricService.snapshot().get("counters");
        if (!(counters instanceof Map<?, ?>)) {
            return 0L;
        }
        Object value = ((Map<?, ?>) counters).get(key);
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }
}
