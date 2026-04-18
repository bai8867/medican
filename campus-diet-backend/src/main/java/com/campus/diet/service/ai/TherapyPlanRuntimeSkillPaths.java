package com.campus.diet.service.ai;

/**
 * 食疗 runtime-llm 片段在 classpath 下的定位（M2：前缀可配置）。
 */
public final class TherapyPlanRuntimeSkillPaths {

    public static final String STANDARD_PREFIX = "llm-skills/runtime-llm/therapy-plan";

    private final String prefix;

    private TherapyPlanRuntimeSkillPaths(String prefix) {
        this.prefix = prefix;
    }

    /**
     * @param rawPrefix 为空则使用标准前缀
     */
    public static TherapyPlanRuntimeSkillPaths ofPrefix(String rawPrefix) {
        if (rawPrefix == null || rawPrefix.trim().isEmpty()) {
            return new TherapyPlanRuntimeSkillPaths(STANDARD_PREFIX);
        }
        return new TherapyPlanRuntimeSkillPaths(normalize(rawPrefix));
    }

    public boolean isBaselinePack() {
        return STANDARD_PREFIX.equals(prefix);
    }

    public String partA() {
        return prefix + "/system-prompt-part-a.txt";
    }

    /** §8：助手身份与任务边界（与 {@link #partA()} 二选一，优先本组片段）。 */
    public String partAIdentity() {
        return prefix + "/core.identity@1.txt";
    }

    public String partASchemaJson() {
        return prefix + "/output.schema-therapy-json@1.txt";
    }

    public String partAMarkdownBody() {
        return prefix + "/output.markdown-therapy-body@1.txt";
    }

    /** 可选；存在则追加在 part-A 文本末尾。 */
    public String localeZhCn() {
        return prefix + "/locale.zh-cn@1.txt";
    }

    public String partB() {
        return prefix + "/system-prompt-part-b.txt";
    }

    /** §8：菜谱目录引用规则前缀（与 {@link #partB()} 二选一；存在则优先，否则回退 {@code system-prompt-part-b.txt}）。 */
    public String contextRecipeCatalog() {
        return prefix + "/context.recipe-catalog@1.txt";
    }

    public String vagueAddon() {
        return prefix + "/skill-input-vague-symptom@1.txt";
    }

    public String prefix() {
        return prefix;
    }

    private static String normalize(String p) {
        String t = p.replace('\\', '/').trim();
        while (t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }
}
