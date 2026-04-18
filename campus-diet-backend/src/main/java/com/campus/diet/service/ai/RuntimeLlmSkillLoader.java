package com.campus.diet.service.ai;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 从 classpath 加载线上 LLM 使用的 Skill 文本（UTF-8）。与 Cursor / openskills 无耦合。
 */
public final class RuntimeLlmSkillLoader {

    private RuntimeLlmSkillLoader() {
    }

    /**
     * @param resourcePath 不以 {@code /} 开头，例如 {@code llm-skills/runtime-llm/therapy-plan/core.identity@1.txt}
     */
    public static String loadRequiredUtf8(String resourcePath) {
        ClassLoader cl = RuntimeLlmSkillLoader.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource: " + resourcePath);
            }
            try (BufferedInputStream bin = new BufferedInputStream(in)) {
                byte[] bytes = bin.readAllBytes();
                return new String(bytes, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read resource: " + resourcePath, e);
        }
    }

    /**
     * 与 {@link #loadRequiredUtf8} 相同读取规则；资源不存在时返回 {@code null}（用于探测可选 Skill 或 M2 旧包回退）。
     */
    public static String loadOptionalUtf8(String resourcePath) {
        ClassLoader cl = RuntimeLlmSkillLoader.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return null;
            }
            try (BufferedInputStream bin = new BufferedInputStream(in)) {
                byte[] bytes = bin.readAllBytes();
                return new String(bytes, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read resource: " + resourcePath, e);
        }
    }
}
