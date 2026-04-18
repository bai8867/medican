package com.campus.diet.service.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeLlmSkillLoaderTest {

    @Test
    void loadRequiredUtf8_loadsTherapyPlanSkillParts() {
        String identity =
                RuntimeLlmSkillLoader.loadRequiredUtf8("llm-skills/runtime-llm/therapy-plan/core.identity@1.txt");
        String schema =
                RuntimeLlmSkillLoader.loadRequiredUtf8(
                        "llm-skills/runtime-llm/therapy-plan/output.schema-therapy-json@1.txt");
        String catalogCtx =
                RuntimeLlmSkillLoader.loadRequiredUtf8(
                        "llm-skills/runtime-llm/therapy-plan/context.recipe-catalog@1.txt");
        assertTrue(identity.contains("校园中医药膳"));
        assertTrue(schema.contains("therapyRecommendMarkdown"));
        assertTrue(catalogCtx.contains("菜谱目录（只能引用其中 id 作为 recipeId）"));
    }

    @Test
    void loadOptionalUtf8_returnsNullWhenMissing() {
        assertNull(RuntimeLlmSkillLoader.loadOptionalUtf8("llm-skills/runtime-llm/missing/not-there.txt"));
    }

    @Test
    void loadRequiredUtf8_loadsSharedCoreCompliance() {
        String shared =
                RuntimeLlmSkillLoader.loadRequiredUtf8("llm-skills/runtime-llm/shared/core-compliance@1.txt");
        assertTrue(shared.contains("疾病命名诊断"));
    }

    @Test
    void loadRequiredUtf8_throwsWhenMissing() {
        assertThrows(
                IllegalStateException.class,
                () -> RuntimeLlmSkillLoader.loadRequiredUtf8("llm-skills/runtime-llm/missing/no-such.txt"));
    }
}
