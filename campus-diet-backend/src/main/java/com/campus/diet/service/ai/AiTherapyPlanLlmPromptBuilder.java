package com.campus.diet.service.ai;

import com.campus.diet.entity.Recipe;

import java.util.List;

/**
 * AI 食疗方案：构造上游 LLM 所需的菜谱目录 JSON 与 system/user 提示词（无 Spring 状态）。
 */
public final class AiTherapyPlanLlmPromptBuilder {

    /** 与 {@link TherapyPlanLlmRoute#DEFAULT} 的 {@link TherapyPlanLlmRoute#skillSetId()} 对齐，供指标与日志打点。 */
    public static final String THERAPY_PLAN_SKILL_SET_ID = TherapyPlanLlmRoute.DEFAULT.skillSetId();

    private AiTherapyPlanLlmPromptBuilder() {
    }

    public static String buildCatalogJson(List<Recipe> pool) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        int n = Math.min(pool.size(), 48);
        for (int i = 0; i < n; i++) {
            Recipe r = pool.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"id\":\"")
                    .append(r.getId())
                    .append("\",\"name\":\"")
                    .append(escapeJson(r.getName()))
                    .append("\"}");
        }
        sb.append(']');
        return sb.toString();
    }

    public static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", "");
    }

    public static String buildSystemPrompt(String catalogJson, TherapyPlanLlmRoute route) {
        return TherapyPlanLlmSkillAssembler.assemble(catalogJson, route).getSystemPrompt();
    }

    public static String buildSystemPrompt(
            String catalogJson, TherapyPlanLlmRoute route, TherapyPlanRuntimeSkillPaths paths) {
        return TherapyPlanLlmSkillAssembler.assemble(catalogJson, route, paths).getSystemPrompt();
    }

    /** 等价于 {@link #buildSystemPrompt(String, TherapyPlanLlmRoute)} 且路由固定为 {@link TherapyPlanLlmRoute#DEFAULT}。 */
    public static String buildSystemPrompt(String catalogJson) {
        return buildSystemPrompt(catalogJson, TherapyPlanLlmRoute.DEFAULT);
    }

    public static String buildUserMessage(String symptom, String constLabel, boolean vague) {
        StringBuilder u = new StringBuilder();
        u.append("【用户输入】近期身体不适或调养目标（请整体理解语气与诉求，勿逐字当成医学主诉）：\n");
        u.append(symptom.isEmpty() ? "（未填写，按日常轻养生处理）" : symptom).append('\n');
        if (constLabel != null) {
            u.append("【可选参考】用户档案中的体质倾向：").append(constLabel).append("（请作为膳食偏性参考，不得据此下诊断）\n");
        }
        if (vague) {
            u.append("【说明】表述较短或较笼统，请输出偏温和的通用食养方案；rationale 与 therapyRecommendMarkdown 中均需简短说明「信息有限、建议补充描述或线下咨询」。\n");
        }
        u.append("\n请严格按 system 要求的字段生成 JSON；therapyRecommendMarkdown 须为完整 Markdown 正文。");
        return u.toString();
    }
}
