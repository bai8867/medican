package com.campus.diet.service.ai;

/**
 * 上游 LLM 返回中可能出现的 Markdown JSON 围栏剥离（无状态）。
 */
public final class AiTherapyPlanLlmResponseSanitizer {

    private AiTherapyPlanLlmResponseSanitizer() {
    }

    public static String stripMarkdownFence(String content) {
        if (content == null) {
            return "";
        }
        String s = content.trim();
        if (s.startsWith("```")) {
            int firstNl = s.indexOf('\n');
            if (firstNl > 0) {
                s = s.substring(firstNl + 1).trim();
            }
            int fence = s.lastIndexOf("```");
            if (fence >= 0) {
                s = s.substring(0, fence).trim();
            }
        }
        return s;
    }
}
