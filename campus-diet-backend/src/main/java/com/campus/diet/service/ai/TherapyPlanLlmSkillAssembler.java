package com.campus.diet.service.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 方案 B：按路由从 classpath Skill 文本拼装 system，并生成可观测指纹（不含 user 原文）。
 */
public final class TherapyPlanLlmSkillAssembler {

    private TherapyPlanLlmSkillAssembler() {
    }

    public static TherapyPlanSkillAssembly assemble(String catalogJson, TherapyPlanLlmRoute route) {
        return assemble(catalogJson, route, TherapyPlanRuntimeSkillPaths.ofPrefix(null));
    }

    public static TherapyPlanSkillAssembly assemble(
            String catalogJson, TherapyPlanLlmRoute route, TherapyPlanRuntimeSkillPaths paths) {
        if (catalogJson == null) {
            catalogJson = "";
        }
        TherapyPlanRuntimeSkillPaths p = paths == null ? TherapyPlanRuntimeSkillPaths.ofPrefix(null) : paths;
        List<String> refs = new ArrayList<>();
        String partA = loadTherapyPlanPartA(p, refs);
        String catalogIntro = loadRecipeCatalogIntro(p, refs);
        StringBuilder system = new StringBuilder(partA.length() + catalogIntro.length() + catalogJson.length() + 64);
        system.append(partA);
        if (route == TherapyPlanLlmRoute.BRIEF_INPUT) {
            String vague = RuntimeLlmSkillLoader.loadRequiredUtf8(p.vagueAddon());
            system.append(vague);
            if (!vague.endsWith("\n")) {
                system.append('\n');
            }
            refs.add("runtime-llm.input.vague-symptom@1");
        }
        String shared = RuntimeLlmSkillLoader.loadRequiredUtf8(RuntimeLlmSharedSkillResources.CORE_COMPLIANCE).trim();
        system.append(shared).append('\n').append('\n');
        refs.add("runtime-llm.shared.core-compliance@1");
        system.append(catalogIntro);
        system.append(catalogJson);
        String prompt = system.toString();
        return new TherapyPlanSkillAssembly(prompt, route.skillSetId(), sha256Hex(prompt), refs);
    }

    /**
     * 默认 classpath：按 §8 多段 Skill 拼装 part-A；若自定义前缀下未提供 {@code core.identity@1.txt}，则回退单文件
     * {@code system-prompt-part-a.txt}（兼容旧 M2 资源包）。
     */
    private static String loadTherapyPlanPartA(TherapyPlanRuntimeSkillPaths p, List<String> refs) {
        String identityRaw = RuntimeLlmSkillLoader.loadOptionalUtf8(p.partAIdentity());
        if (identityRaw == null || identityRaw.trim().isEmpty()) {
            refs.add("runtime-llm.therapy-plan.system-prompt-part-a@1");
            return RuntimeLlmSkillLoader.loadRequiredUtf8(p.partA());
        }
        String identity = identityRaw.trim();
        String schema = RuntimeLlmSkillLoader.loadRequiredUtf8(p.partASchemaJson()).trim();
        String markdown = RuntimeLlmSkillLoader.loadRequiredUtf8(p.partAMarkdownBody()).trim();
        refs.add("runtime-llm.therapy-plan.core.identity@1");
        refs.add("runtime-llm.therapy-plan.output.schema-therapy-json@1");
        refs.add("runtime-llm.therapy-plan.output.markdown-therapy-body@1");
        StringBuilder sb = new StringBuilder(identity.length() + schema.length() + markdown.length() + 32);
        sb.append(identity).append("\n\n").append(schema).append('\n').append(markdown);
        String localeRaw = RuntimeLlmSkillLoader.loadOptionalUtf8(p.localeZhCn());
        if (localeRaw != null && !localeRaw.trim().isEmpty()) {
            sb.append("\n\n").append(localeRaw.trim());
            refs.add("runtime-llm.therapy-plan.locale.zh-cn@1");
        }
        return sb.toString();
    }

    /**
     * §8 {@code context.recipe-catalog}：有独立片段则用之；否则读 {@code system-prompt-part-b.txt}（旧 M2 包仅含 part-b 时仍可用）。
     */
    private static String loadRecipeCatalogIntro(TherapyPlanRuntimeSkillPaths p, List<String> refs) {
        String ctx = RuntimeLlmSkillLoader.loadOptionalUtf8(p.contextRecipeCatalog());
        if (ctx != null && !ctx.trim().isEmpty()) {
            refs.add("runtime-llm.therapy-plan.context.recipe-catalog@1");
            return ctx.trim();
        }
        refs.add("runtime-llm.therapy-plan.system-prompt-part-b@1");
        return RuntimeLlmSkillLoader.loadRequiredUtf8(p.partB()).trim();
    }

    /**
     * 解析 {@code ai.therapy.route.override}：空则按 {@code vague} 自动路由；{@code default}/{@code brief} 强制路由。
     */
    public static TherapyPlanLlmRoute resolveRoute(String overrideRaw, boolean vague) {
        if (overrideRaw == null) {
            return TherapyPlanLlmRoute.fromVague(vague);
        }
        String o = overrideRaw.trim().toLowerCase(Locale.ROOT);
        if ("default".equals(o)) {
            return TherapyPlanLlmRoute.DEFAULT;
        }
        if ("brief".equals(o)) {
            return TherapyPlanLlmRoute.BRIEF_INPUT;
        }
        return TherapyPlanLlmRoute.fromVague(vague);
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
