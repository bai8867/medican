package com.campus.diet.service.ai;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI 食疗方案在「全局生成功能关闭」时的固定响应体，从编排器中拆出以降低单类职责体积。
 */
@Component
public class AiTherapyPlanDisabledResponseFactory {

    public Map<String, Object> build(String symptomTrimmed, String constitutionLabel) {
        String sym = symptomTrimmed == null ? "" : symptomTrimmed;
        String constLabel = constitutionLabel == null ? "" : constitutionLabel;
        Map<String, Object> off = new LinkedHashMap<>();
        off.put("enabled", false);
        off.put("message", "AI 生成功能已由管理员关闭");
        off.put("planId", "off-" + UUID.randomUUID());
        off.put("symptomSummary", sym.isEmpty() ? "日常调养" : sym);
        off.put("constitutionApplied", constLabel);
        off.put("recipes", List.of());
        off.put("coreIngredients", List.of());
        off.put("lifestyleAdvice", List.of());
        off.put("cautionNotes", List.of("功能已关闭。"));
        off.put("rationale", "");
        off.put("disclaimer", "本内容由系统提示，不构成医疗建议。");
        off.put("isGenericPlan", true);
        off.put(
                "therapyRecommendMarkdown",
                "## 说明\n\n当前 **AI 食疗方案** 功能已由管理员关闭，无法生成个性化推荐文案。您可前往 **药膳推荐** 浏览公开菜谱，或稍后再试。\n");
        return off;
    }
}
