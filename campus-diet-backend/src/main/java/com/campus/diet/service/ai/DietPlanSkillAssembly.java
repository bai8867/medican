package com.campus.diet.service.ai;

import java.util.Collections;
import java.util.List;

/**
 * AI 膳食方案：一次 LLM 调用的 system 与可观测元数据（与食疗侧 {@link TherapyPlanSkillAssembly} 对称）。
 */
public final class DietPlanSkillAssembly {

    private final String systemPrompt;
    private final String skillSetId;
    private final String skillContentSha256Hex;
    private final List<String> appliedSkillRefs;

    public DietPlanSkillAssembly(
            String systemPrompt,
            String skillSetId,
            String skillContentSha256Hex,
            List<String> appliedSkillRefs) {
        this.systemPrompt = systemPrompt;
        this.skillSetId = skillSetId;
        this.skillContentSha256Hex = skillContentSha256Hex;
        this.appliedSkillRefs = appliedSkillRefs == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(appliedSkillRefs);
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public String getSkillSetId() {
        return skillSetId;
    }

    public String getSkillContentSha256Hex() {
        return skillContentSha256Hex;
    }

    public List<String> getAppliedSkillRefs() {
        return appliedSkillRefs;
    }
}
