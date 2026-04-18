package com.campus.diet.service.ai;

import java.util.Locale;

/**
 * 食疗 LLM system 拼装路由（方案 B 子集）：默认与「短输入」增强块。
 */
public enum TherapyPlanLlmRoute {
    DEFAULT("therapy_plan.default"),
    BRIEF_INPUT("therapy_plan.brief");

    private final String skillSetId;

    TherapyPlanLlmRoute(String skillSetId) {
        this.skillSetId = skillSetId;
    }

    public String skillSetId() {
        return skillSetId;
    }

    public static TherapyPlanLlmRoute fromVague(boolean vague) {
        return vague ? BRIEF_INPUT : DEFAULT;
    }

    /**
     * 解析 {@code ai.therapy.route.override}：空则按 {@code vague} 自动路由；{@code default}/{@code brief} 强制路由。
     */
    public static TherapyPlanLlmRoute fromOverride(String overrideRaw, boolean vague) {
        if (overrideRaw == null) {
            return fromVague(vague);
        }
        String o = overrideRaw.trim().toLowerCase(Locale.ROOT);
        if ("default".equals(o)) {
            return DEFAULT;
        }
        if ("brief".equals(o)) {
            return BRIEF_INPUT;
        }
        return fromVague(vague);
    }
}
