package com.campus.diet.service.ai;

import java.util.Collections;
import java.util.List;

/**
 * 一次 LLM 调用装配好的 system 提示与可观测元数据（不含用户原文）。
 */
public final class TherapyPlanSkillAssembly {

    private final String systemPrompt;
    private final String skillSetId;
    private final String skillContentSha256Hex;
    private final List<String> appliedSkillRefs;

    public TherapyPlanSkillAssembly(
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

    /** 对完整 system 文本的 SHA-256（十六进制），用于审计与排障，不含 user 消息。 */
    public String getSkillContentSha256Hex() {
        return skillContentSha256Hex;
    }

    /** 形如 {@code runtime-llm.therapy-plan.core.identity@1} 或旧包 {@code system-prompt-part-a@1} 的引用列表，顺序即拼装顺序。 */
    public List<String> getAppliedSkillRefs() {
        return appliedSkillRefs;
    }
}
