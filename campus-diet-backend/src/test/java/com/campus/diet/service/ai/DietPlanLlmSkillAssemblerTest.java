package com.campus.diet.service.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DietPlanLlmSkillAssemblerTest {

    private static final String LEGACY_SYSTEM =
            "禁止：疾病命名诊断、处方药物、保证治愈、夸大功效、恐吓性表述。本内容仅供校园食养与膳食科普参考，不能替代诊疗与用药；症状持续或加重请及时就医。\n\n"
                    + "你是校园膳食指导助手。根据用户症状输出**仅 JSON**（不要 Markdown），字段："
                    + "summary(string), meals(array of {name, ingredients[], note}), cautions(string[]), "
                    + "isAiGenerated(true), notMedicalAdvice(true), regulatoryNotice(\"CN-TCM-DIET-AI-V1\")。";

    @Test
    void assemble_matchesLegacyPromptAndMetadata() {
        DietPlanSkillAssembly a = DietPlanLlmSkillAssembler.assemble();
        assertEquals(LEGACY_SYSTEM, a.getSystemPrompt());
        assertEquals("diet_plan.default", a.getSkillSetId());
        assertEquals(64, a.getSkillContentSha256Hex().length());
        assertEquals(
                List.of(
                        "runtime-llm.shared.core-compliance@1",
                        "runtime-llm.diet-plan.core.identity@1",
                        "runtime-llm.diet-plan.output.json-only@1"),
                a.getAppliedSkillRefs(),
                "先共享合规，再膳食 identity + JSON-only 片段");
    }

    @Test
    void buildSystemPrompt_delegatesToAssembler() {
        assertEquals(DietPlanLlmSkillAssembler.assemble().getSystemPrompt(), AiDietLlmPromptBuilder.buildSystemPrompt());
    }

    @Test
    void assemble_withoutIdentityFragment_fallsBackToAnchorFileBody() {
        String anchor = "llm-skills/runtime-llm/diet-custom-mono/custom-system.txt";
        DietPlanSkillAssembly a = DietPlanLlmSkillAssembler.assemble(anchor);
        assertEquals("diet_plan.custom", a.getSkillSetId());
        assertTrue(a.getSystemPrompt().endsWith("LEGACY_MONO_BODY_ONLY"));
        assertEquals(
                List.of("runtime-llm.shared.core-compliance@1", "runtime-llm.diet-custom-mono.custom-system"),
                a.getAppliedSkillRefs());
    }
}
