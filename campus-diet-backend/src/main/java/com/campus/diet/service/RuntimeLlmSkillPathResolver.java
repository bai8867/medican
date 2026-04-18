package com.campus.diet.service;

import com.campus.diet.config.RuntimeLlmSkillPathProperties;
import com.campus.diet.service.ai.TherapyPlanRuntimeSkillPaths;
import org.springframework.stereotype.Component;

/**
 * M2：runtime-llm 的 classpath 位置由 {@code system_kv} 覆盖，其次为 {@link RuntimeLlmSkillPathProperties}（YAML / 环境变量绑定）。
 */
@Component
public class RuntimeLlmSkillPathResolver {

    private final RuntimeLlmSkillPathProperties properties;
    private final SystemKvService systemKvService;

    public RuntimeLlmSkillPathResolver(RuntimeLlmSkillPathProperties properties, SystemKvService systemKvService) {
        this.properties = properties;
        this.systemKvService = systemKvService;
    }

    public TherapyPlanRuntimeSkillPaths resolveTherapyPaths() {
        String kv = nz(systemKvService.get("ai.runtime-llm.therapy-resource-prefix", ""));
        String prefix = !kv.isEmpty() ? kv : nz(properties.getTherapyResourcePrefix());
        return TherapyPlanRuntimeSkillPaths.ofPrefix(prefix);
    }

    public String resolveDietSystemPromptResource() {
        String kv = nz(systemKvService.get("ai.runtime-llm.diet-system-prompt-resource", ""));
        return !kv.isEmpty() ? kv : nz(properties.getDietSystemPromptResource());
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }
}
