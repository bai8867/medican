package com.campus.diet.service.ai;

/**
 * 多场景复用的 runtime-llm 资源路径（M3）。
 */
public final class RuntimeLlmSharedSkillResources {

    /** 合规红线：食疗 part-b 与膳食 system 共用 */
    public static final String CORE_COMPLIANCE = "llm-skills/runtime-llm/shared/core-compliance@1.txt";

    private RuntimeLlmSharedSkillResources() {}
}
