package com.campus.diet.config;

import com.campus.diet.service.ai.TherapyPlanRuntimeSkillPaths;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * M2：runtime-llm Skill 的 classpath 位置可通过 YAML / 环境变量切换；运行时若 {@code system_kv} 配置了同语义键则优先生效（见
 * {@link com.campus.diet.service.RuntimeLlmSkillPathResolver}）。
 */
@Component
@ConfigurationProperties(prefix = "campus.ai.runtime-llm")
public class RuntimeLlmSkillPathProperties {

    /**
     * 食疗 plan 资源目录前缀（不含末尾斜杠）。基线包：{@code core.identity@1.txt}、{@code output.schema-therapy-json@1.txt}、{@code
     * output.markdown-therapy-body@1.txt}（可选 {@code locale.zh-cn@1.txt}）、{@code context.recipe-catalog@1.txt}（无则回退
     * {@code system-prompt-part-b.txt}）、{@code skill-input-vague-symptom@1.txt}；若未提供 identity 片段则回退单文件 {@code
     * system-prompt-part-a.txt}。
     */
    private String therapyResourcePrefix = "llm-skills/runtime-llm/therapy-plan";

    /**
     * 膳食 plan 资源锚点 classpath 路径（取其父目录解析 {@code core.identity@1.txt} / {@code output.json-only@1.txt}）；若同目录无 identity
     * 片段则仍读取该路径对应全文（旧单文件包）。
     */
    private String dietSystemPromptResource = "llm-skills/runtime-llm/diet-plan/system-prompt@1.txt";

    public TherapyPlanRuntimeSkillPaths therapyPaths() {
        return TherapyPlanRuntimeSkillPaths.ofPrefix(therapyResourcePrefix);
    }

    public String getTherapyResourcePrefix() {
        return therapyResourcePrefix;
    }

    public void setTherapyResourcePrefix(String therapyResourcePrefix) {
        this.therapyResourcePrefix = therapyResourcePrefix;
    }

    public String getDietSystemPromptResource() {
        return dietSystemPromptResource;
    }

    public void setDietSystemPromptResource(String dietSystemPromptResource) {
        this.dietSystemPromptResource = dietSystemPromptResource;
    }
}
