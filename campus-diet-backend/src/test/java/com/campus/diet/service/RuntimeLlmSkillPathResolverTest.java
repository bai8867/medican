package com.campus.diet.service;

import com.campus.diet.config.RuntimeLlmSkillPathProperties;
import com.campus.diet.service.ai.TherapyPlanRuntimeSkillPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeLlmSkillPathResolverTest {

    @Mock
    private SystemKvService systemKvService;

    @Test
    void resolveTherapyPaths_systemKvOverridesProperties() {
        RuntimeLlmSkillPathProperties props = new RuntimeLlmSkillPathProperties();
        props.setTherapyResourcePrefix("llm-skills/runtime-llm/SHOULD-NOT-USE");
        when(systemKvService.get("ai.runtime-llm.therapy-resource-prefix", ""))
                .thenReturn("llm-skills/runtime-llm/therapy-plan");
        RuntimeLlmSkillPathResolver resolver = new RuntimeLlmSkillPathResolver(props, systemKvService);
        assertEquals(TherapyPlanRuntimeSkillPaths.STANDARD_PREFIX, resolver.resolveTherapyPaths().prefix());
    }

    @Test
    void resolveDietSystemPromptResource_systemKvOverridesProperties() {
        RuntimeLlmSkillPathProperties props = new RuntimeLlmSkillPathProperties();
        props.setDietSystemPromptResource("llm-skills/runtime-llm/diet-plan/WRONG.txt");
        when(systemKvService.get("ai.runtime-llm.diet-system-prompt-resource", ""))
                .thenReturn("llm-skills/runtime-llm/diet-plan/system-prompt@1.txt");
        RuntimeLlmSkillPathResolver resolver = new RuntimeLlmSkillPathResolver(props, systemKvService);
        assertEquals("llm-skills/runtime-llm/diet-plan/system-prompt@1.txt", resolver.resolveDietSystemPromptResource());
    }
}
