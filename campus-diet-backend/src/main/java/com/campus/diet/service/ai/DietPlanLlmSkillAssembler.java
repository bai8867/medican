package com.campus.diet.service.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * M3：膳食 LLM system 由 classpath Skill 承载，与食疗链路共用 {@link RuntimeLlmSkillLoader} 模式。
 * 默认包将 JSON-only 等约束拆到 {@code output.json-only@1.txt}；若自定义目录未提供 {@code core.identity@1.txt}，则仍读锚点文件全文。
 */
public final class DietPlanLlmSkillAssembler {

    /** 锚点 classpath 路径（用于 M2 覆盖与父目录解析；基线包可不存在该文件，只要同目录下有 identity/json 片段即可）。 */
    private static final String DEFAULT_DIET_SYSTEM_RESOURCE = "llm-skills/runtime-llm/diet-plan/system-prompt@1.txt";

    private DietPlanLlmSkillAssembler() {
    }

    public static DietPlanSkillAssembly assemble() {
        return assemble(DEFAULT_DIET_SYSTEM_RESOURCE);
    }

    /**
     * @param dietSystemResource classpath 下 UTF-8 文本，默认见 {@link #assemble()}
     */
    public static DietPlanSkillAssembly assemble(String dietSystemResource) {
        String path =
                dietSystemResource == null || dietSystemResource.isEmpty()
                        ? DEFAULT_DIET_SYSTEM_RESOURCE
                        : dietSystemResource.trim();
        String shared = RuntimeLlmSkillLoader.loadRequiredUtf8(RuntimeLlmSharedSkillResources.CORE_COMPLIANCE).trim();
        List<String> refs = new ArrayList<>();
        refs.add("runtime-llm.shared.core-compliance@1");
        String body = loadDietPlanBody(path, refs);
        String prompt = shared + "\n\n" + body;
        boolean baseline = DEFAULT_DIET_SYSTEM_RESOURCE.equals(path);
        String skillSetId = baseline ? "diet_plan.default" : "diet_plan.custom";
        return new DietPlanSkillAssembly(prompt, skillSetId, sha256Hex(prompt), refs);
    }

    /**
     * 与 {@link TherapyPlanLlmSkillAssembler} 类似：同目录存在 {@code core.identity@1.txt} 时拼接 identity + json-only（中间无换行，保持与旧单文件
     * system 一致）；否则读取 {@code anchorClasspathResource} 全文（旧包兼容）。
     */
    private static String loadDietPlanBody(String anchorClasspathResource, List<String> refs) {
        String dir = parentDirOfClasspathResource(anchorClasspathResource);
        String identityRaw = RuntimeLlmSkillLoader.loadOptionalUtf8(dir + "/core.identity@1.txt");
        if (identityRaw == null || identityRaw.trim().isEmpty()) {
            refs.add(bodyRefFromClasspath(anchorClasspathResource));
            return RuntimeLlmSkillLoader.loadRequiredUtf8(anchorClasspathResource).trim();
        }
        String identity = identityRaw.trim();
        String jsonOnly = RuntimeLlmSkillLoader.loadRequiredUtf8(dir + "/output.json-only@1.txt").trim();
        refs.add("runtime-llm.diet-plan.core.identity@1");
        refs.add("runtime-llm.diet-plan.output.json-only@1");
        return identity + jsonOnly;
    }

    private static String parentDirOfClasspathResource(String resourcePath) {
        String p = resourcePath.replace('\\', '/').trim();
        int i = p.lastIndexOf('/');
        return i > 0 ? p.substring(0, i) : "";
    }

    private static String bodyRefFromClasspath(String path) {
        String bodyRef = path.replace("llm-skills/", "").replace('/', '.');
        if (bodyRef.endsWith(".txt")) {
            bodyRef = bodyRef.substring(0, bodyRef.length() - 4);
        }
        return bodyRef;
    }

    private static String sha256Hex(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format(Locale.ROOT, "%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "unavailable";
        }
    }
}
