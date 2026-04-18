package com.campus.diet.service.ai;

/**
 * AI 膳食方案：无 Spring 的提示装配入口（与 {@link AiTherapyPlanLlmPromptBuilder} 对称）。
 */
public final class AiDietLlmPromptBuilder {

    /** 与指标 {@code ai.diet.skill_set.default} 及装配器 {@code diet_plan.default} 对应。 */
    public static final String DIET_PLAN_SKILL_SET_ID = "runtime-llm.diet-plan@v1";

    private AiDietLlmPromptBuilder() {
    }

    public static String buildSystemPrompt() {
        return DietPlanLlmSkillAssembler.assemble().getSystemPrompt();
    }
}
