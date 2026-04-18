package com.campus.diet.service.ai;

import java.util.Map;

/**
 * 九种体质代码与展示文案（AI 食疗方案与质量门禁共用）。
 */
public final class AiTherapyPlanConstitutionLabels {

    private static final Map<String, String> BY_CODE = Map.ofEntries(
            Map.entry("pinghe", "平和质"),
            Map.entry("qixu", "气虚质"),
            Map.entry("yangxu", "阳虚质"),
            Map.entry("yinxu", "阴虚质"),
            Map.entry("tanshi", "痰湿质"),
            Map.entry("shire", "湿热质"),
            Map.entry("xueyu", "血瘀质"),
            Map.entry("qiyu", "气郁质"),
            Map.entry("tebing", "特禀质")
    );

    private AiTherapyPlanConstitutionLabels() {
    }

    /** 空或空白返回 null；未知代码返回 trimmed 原码。 */
    public static String labelOrRaw(String constitutionCode) {
        if (constitutionCode == null) {
            return null;
        }
        String code = constitutionCode.trim();
        if (code.isEmpty()) {
            return null;
        }
        return BY_CODE.getOrDefault(code, code);
    }
}
