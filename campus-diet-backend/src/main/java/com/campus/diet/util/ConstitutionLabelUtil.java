package com.campus.diet.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 九种体质 code（库表、推荐算法）与中文标签（前台展示）互转。
 */
public final class ConstitutionLabelUtil {

    private static final String[] CODES = {
            "pinghe", "qixu", "yangxu", "yinxu", "tanshi", "shire", "xueyu", "qiyu", "tebing"
    };
    private static final String[] LABELS = {
            "平和质", "气虚质", "阳虚质", "阴虚质", "痰湿质", "湿热质", "血瘀质", "气郁质", "特禀质"
    };

    private static final Map<String, String> CODE_TO_LABEL = new LinkedHashMap<>();
    private static final Map<String, String> LABEL_TO_CODE = new LinkedHashMap<>();

    static {
        for (int i = 0; i < CODES.length; i++) {
            CODE_TO_LABEL.put(CODES[i], LABELS[i]);
            LABEL_TO_CODE.put(LABELS[i], CODES[i]);
        }
    }

    private ConstitutionLabelUtil() {
    }

    public static String codeForLabel(String label) {
        if (label == null) {
            return null;
        }
        return LABEL_TO_CODE.get(label.trim());
    }

    public static String labelForCode(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        return CODE_TO_LABEL.getOrDefault(code.trim().toLowerCase(), code.trim());
    }

    /**
     * 逗号分隔体质 code → 中文标签列表（顺序保持）。
     */
    public static List<String> labelsFromCodesCsv(String csv) {
        List<String> out = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return out;
        }
        for (String p : csv.split(",")) {
            String c = p.trim();
            if (c.isEmpty()) {
                continue;
            }
            out.add(labelForCode(c));
        }
        return out;
    }

    /**
     * 中文体质名列表 → 逗号分隔 code（跳过无法识别的项）。
     */
    public static String codesCsvFromLabels(Iterable<String> labels) {
        if (labels == null) {
            return "";
        }
        List<String> codes = new ArrayList<>();
        for (String label : labels) {
            if (label == null) {
                continue;
            }
            String code = codeForLabel(label.trim());
            if (code != null) {
                codes.add(code);
            }
        }
        return String.join(",", codes);
    }
}
