package com.campus.diet.service.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TherapyPlanLlmSkillAssemblerTest {

    @Test
    void assemble_default_matchesBuildSystemPrompt() {
        String catalog = "[{\"id\":\"1\",\"name\":\"x\"}]";
        TherapyPlanSkillAssembly a = TherapyPlanLlmSkillAssembler.assemble(catalog, TherapyPlanLlmRoute.DEFAULT);
        assertEquals(AiTherapyPlanLlmPromptBuilder.buildSystemPrompt(catalog), a.getSystemPrompt());
        assertEquals("therapy_plan.default", a.getSkillSetId());
        assertEquals(64, a.getSkillContentSha256Hex().length());
        assertTrue(a.getAppliedSkillRefs().stream().noneMatch(r -> r.contains("vague")));
        assertTrue(a.getAppliedSkillRefs().stream().anyMatch(r -> r.contains("core-compliance")));
        assertTrue(a.getAppliedSkillRefs().stream().anyMatch(r -> r.contains("core.identity")));
        assertTrue(a.getAppliedSkillRefs().stream().anyMatch(r -> r.contains("locale.zh-cn")));
        assertTrue(a.getAppliedSkillRefs().stream().anyMatch(r -> r.contains("context.recipe-catalog")));
        assertTrue(a.getAppliedSkillRefs().stream().noneMatch(r -> r.contains("system-prompt-part-b")));
    }

    @Test
    void assemble_recipeCatalogIntro_fallsBackToPartBWhenContextFileAbsent() {
        TherapyPlanRuntimeSkillPaths p =
                TherapyPlanRuntimeSkillPaths.ofPrefix("llm-skills/runtime-llm/therapy-catalog-fallback-only");
        TherapyPlanSkillAssembly a = TherapyPlanLlmSkillAssembler.assemble("[]", TherapyPlanLlmRoute.DEFAULT, p);
        assertTrue(a.getAppliedSkillRefs().stream().anyMatch(r -> r.contains("system-prompt-part-b")));
        assertTrue(a.getAppliedSkillRefs().stream().noneMatch(r -> r.contains("context.recipe-catalog")));
        assertTrue(a.getSystemPrompt().contains("菜谱目录（只能引用其中 id 作为 recipeId）"));
    }

    @Test
    void assemble_brief_addsVagueSkillAndDiffersFromDefault() {
        String catalog = "[]";
        TherapyPlanSkillAssembly def = TherapyPlanLlmSkillAssembler.assemble(catalog, TherapyPlanLlmRoute.DEFAULT);
        TherapyPlanSkillAssembly brief = TherapyPlanLlmSkillAssembler.assemble(catalog, TherapyPlanLlmRoute.BRIEF_INPUT);
        assertNotEquals(def.getSystemPrompt(), brief.getSystemPrompt());
        assertTrue(brief.getSystemPrompt().contains("较短或较笼统"));
        assertEquals("therapy_plan.brief", brief.getSkillSetId());
        assertTrue(brief.getAppliedSkillRefs().stream().anyMatch(r -> r.contains("vague")));
        assertTrue(brief.getAppliedSkillRefs().stream().anyMatch(r -> r.contains("core-compliance")));
    }

    @Test
    void fromOverride_honorsOverride() {
        assertEquals(TherapyPlanLlmRoute.DEFAULT, TherapyPlanLlmRoute.fromOverride("default", true));
        assertEquals(TherapyPlanLlmRoute.BRIEF_INPUT, TherapyPlanLlmRoute.fromOverride("brief", false));
    }
}
