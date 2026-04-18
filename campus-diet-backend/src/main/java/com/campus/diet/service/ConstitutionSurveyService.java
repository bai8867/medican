package com.campus.diet.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 体质问卷评分服务：
 * 1) 保留 legacy-v1 九题规则用于对照；
 * 2) 默认使用 v2-research-hybrid（45题、转化分、主次体质、置信度与规则追踪）。
 */
@Service
public class ConstitutionSurveyService {

    public static final String LEGACY_VERSION = "legacy-v1";
    public static final String RESEARCH_VERSION = "v2-research-hybrid";

    private static final String[] CODES = {
            "pinghe", "qixu", "yangxu", "yinxu", "tanshi", "shire", "xueyu", "qiyu", "tebing"
    };
    private static final String[] LABELS = {
            "平和质", "气虚质", "阳虚质", "阴虚质", "痰湿质", "湿热质", "血瘀质", "气郁质", "特禀质"
    };
    private static final String[] LEGACY_TIE_BREAK = {
            "qixu", "yangxu", "yinxu", "tanshi", "shire", "xueyu", "qiyu", "tebing", "pinghe"
    };
    private static final String[] LEGACY_QUESTION_TARGETS = {
            "qixu", "yangxu", "yinxu", "tanshi", "shire", "xueyu", "qiyu", "tebing", "pinghe"
    };

    private static final double PINGHE_MIN = 60.0;
    private static final double BIASED_MAX_FOR_PINGHE = 40.0;
    private static final double SECONDARY_MIN = 45.0;
    private static final double SECONDARY_DELTA_MAX = 30.0;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final List<QuestionDef> RESEARCH_QUESTIONS = buildResearchQuestions();

    public SurveyResult evaluate(List<Integer> answers) {
        return evaluate(answers, RESEARCH_VERSION);
    }

    public SurveyResult evaluate(List<Integer> answers, String questionVersion) {
        String version = questionVersion == null || questionVersion.isBlank()
                ? RESEARCH_VERSION
                : questionVersion.trim().toLowerCase(Locale.ROOT);
        if (LEGACY_VERSION.equals(version) || (answers != null && answers.size() == 9)) {
            return evaluateLegacyNine(answers);
        }
        return evaluateResearch(answers);
    }

    public SurveyResult evaluateLegacyNine(List<Integer> answers) {
        if (answers == null || answers.size() != 9) {
            throw new IllegalArgumentException("legacy-v1 问卷需提交 9 个整型得分（1-5）");
        }
        Map<String, Double> rawScores = new LinkedHashMap<>();
        for (String code : LEGACY_TIE_BREAK) {
            rawScores.put(code, 0.0);
        }
        for (int i = 0; i < 9; i++) {
            int v = answers.get(i);
            if (v < 1 || v > 5) {
                throw new IllegalArgumentException("每题得分须在 1-5 之间");
            }
            String code = LEGACY_QUESTION_TARGETS[i];
            rawScores.put(code, rawScores.get(code) + v);
        }
        String primary = resolveLegacyPrimary(rawScores);
        String label = labelFor(primary);
        Map<String, Double> transformed = normalizeLegacy(rawScores);
        List<String> trace = Collections.singletonList("legacy-nine");
        try {
            Map<String, Object> jsonPayload = new LinkedHashMap<>();
            jsonPayload.put("questionVersion", LEGACY_VERSION);
            jsonPayload.put("rawScores", rawScores);
            jsonPayload.put("transformedScores", transformed);
            jsonPayload.put("ruleTrace", trace);
            return new SurveyResult(
                    primary,
                    label,
                    Collections.emptyList(),
                    transformed,
                    rawScores,
                    0.55,
                    trace,
                    LEGACY_VERSION,
                    objectMapper.writeValueAsString(jsonPayload)
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private SurveyResult evaluateResearch(List<Integer> answers) {
        if (answers == null || answers.size() != RESEARCH_QUESTIONS.size()) {
            throw new IllegalArgumentException("v2-research-hybrid 问卷需提交 " + RESEARCH_QUESTIONS.size() + " 个整型得分（1-5）");
        }

        Map<String, Double> rawScores = emptyDoubleMap();
        Map<String, Double> weightSums = emptyDoubleMap();
        for (QuestionDef q : RESEARCH_QUESTIONS) {
            int v = answers.get(q.id - 1);
            if (v < 1 || v > 5) {
                throw new IllegalArgumentException("第 " + q.id + " 题得分须在 1-5 之间");
            }
            double scored = "reverse".equals(q.direction) ? (6 - v) : v;
            double weighted = scored * q.weight;
            rawScores.put(q.targetCode, rawScores.get(q.targetCode) + weighted);
            weightSums.put(q.targetCode, weightSums.get(q.targetCode) + q.weight);
        }

        Map<String, Double> transformedScores = new LinkedHashMap<>();
        for (String code : CODES) {
            double min = weightSums.get(code);
            double max = weightSums.get(code) * 5.0;
            transformedScores.put(code, transform(rawScores.get(code), min, max));
        }

        List<String> ruleTrace = new ArrayList<>();
        double pingheScore = transformedScores.get("pinghe");
        double maxBiased = transformedScores.entrySet().stream()
                .filter(e -> !"pinghe".equals(e.getKey()))
                .map(Map.Entry::getValue)
                .max(Double::compareTo)
                .orElse(0.0);

        String primaryCode;
        if (pingheScore >= PINGHE_MIN && maxBiased < BIASED_MAX_FOR_PINGHE) {
            primaryCode = "pinghe";
            ruleTrace.add("pinghe-gate:hit");
        } else {
            primaryCode = rankByScore(transformedScores).get(0).getKey();
            ruleTrace.add("pinghe-gate:miss");
        }

        List<Map.Entry<String, Double>> ranked = rankByScore(transformedScores);
        double top = ranked.get(0).getValue();
        double second = ranked.size() > 1 ? ranked.get(1).getValue() : top;
        double delta = top - second;
        double confidence = clamp(0.5 + delta / 50.0 - (top < 45 ? 0.08 : 0.0), 0.45, 0.98);

        List<String> secondaryCodes = new ArrayList<>();
        for (Map.Entry<String, Double> e : ranked) {
            if (e.getKey().equals(primaryCode)) {
                continue;
            }
            if (e.getValue() >= SECONDARY_MIN && top - e.getValue() <= SECONDARY_DELTA_MAX) {
                secondaryCodes.add(e.getKey());
            }
            if (secondaryCodes.size() >= 2) {
                break;
            }
        }
        if (!secondaryCodes.isEmpty()) {
            ruleTrace.add("secondary:" + String.join(",", secondaryCodes));
        }

        SurveyResult legacy = evaluateLegacyNine(answers.subList(0, 9));
        ruleTrace.add("legacy-primary:" + legacy.primaryCode);

        String primaryLabel = labelFor(primaryCode);
        try {
            Map<String, Object> jsonPayload = new LinkedHashMap<>();
            jsonPayload.put("questionVersion", RESEARCH_VERSION);
            jsonPayload.put("rawScores", rawScores);
            jsonPayload.put("transformedScores", transformedScores);
            jsonPayload.put("confidence", confidence);
            jsonPayload.put("secondaryCodes", secondaryCodes);
            jsonPayload.put("ruleTrace", ruleTrace);
            jsonPayload.put("legacyPrimaryCode", legacy.primaryCode);
            return new SurveyResult(
                    primaryCode,
                    primaryLabel,
                    secondaryCodes,
                    transformedScores,
                    rawScores,
                    confidence,
                    ruleTrace,
                    RESEARCH_VERSION,
                    objectMapper.writeValueAsString(jsonPayload)
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Double> normalizeLegacy(Map<String, Double> rawScores) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (String code : CODES) {
            out.put(code, clamp(rawScores.getOrDefault(code, 0.0) / 5.0 * 100.0, 0, 100));
        }
        return out;
    }

    private String resolveLegacyPrimary(Map<String, Double> scores) {
        double max = scores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        for (String code : LEGACY_TIE_BREAK) {
            if (Double.compare(scores.getOrDefault(code, 0.0), max) == 0) {
                return code;
            }
        }
        return "pinghe";
    }

    private Map<String, Double> emptyDoubleMap() {
        Map<String, Double> out = new LinkedHashMap<>();
        for (String code : CODES) {
            out.put(code, 0.0);
        }
        return out;
    }

    private List<Map.Entry<String, Double>> rankByScore(Map<String, Double> scores) {
        List<Map.Entry<String, Double>> ranked = new ArrayList<>(scores.entrySet());
        ranked.sort(Comparator.comparing(Map.Entry<String, Double>::getValue).reversed());
        return ranked;
    }

    private static double transform(double raw, double min, double max) {
        if (max <= min) return 0;
        double value = ((raw - min) / (max - min)) * 100.0;
        return clamp(value, 0, 100);
    }

    private static double clamp(double value, double min, double max) {
        if (value < min) return min;
        if (value > max) return max;
        return Math.round(value * 100.0) / 100.0;
    }

    private String labelFor(String code) {
        for (int i = 0; i < CODES.length; i++) {
            if (CODES[i].equals(code)) {
                return LABELS[i];
            }
        }
        return "";
    }

    private static List<QuestionDef> buildResearchQuestions() {
        List<QuestionDef> list = new ArrayList<>();
        int id = 1;
        id = addDimensionQuestions(list, id, "pinghe",
                new String[]{"您精力充沛、体力恢复较快吗？", "您睡眠质量稳定、醒后精神较好吗？", "您对季节变换与环境变化适应较好吗？"},
                new String[]{"考试周或赶作业期间，您整体状态仍较平稳吗？", "连续一周在食堂/外卖间切换时，您消化反应依然稳定吗？"});
        id = addDimensionQuestions(list, id, "qixu",
                new String[]{"您容易疲乏、气短、说话无力吗？", "您稍微活动后就容易出汗或乏力吗？", "您容易反复感冒、恢复偏慢吗？"},
                new String[]{"上楼到教室后，您常感觉明显气短吗？", "期末周连续复习几天后，您常出现明显倦怠吗？"});
        id = addDimensionQuestions(list, id, "yangxu",
                new String[]{"您手足不温、怕冷明显吗？", "您偏好热饮热食，受凉后不适明显吗？", "您清晨易腹泻或小便清长吗？"},
                new String[]{"冬天教室空调不足时，您常明显畏寒吗？", "淋雨或夜间受凉后，您常出现不适吗？"});
        id = addDimensionQuestions(list, id, "yinxu",
                new String[]{"您常感口干咽燥、想喝水吗？", "您手足心发热或午后潮热吗？", "您容易心烦、睡眠偏浅吗？"},
                new String[]{"熬夜学习后，您次日口干咽燥明显吗？", "连续食用辛辣烧烤后，您常出现“上火”感吗？"});
        id = addDimensionQuestions(list, id, "tanshi",
                new String[]{"您感到身体沉重困倦、头身不清爽吗？", "您腹部肥满松软、痰多或口黏吗？", "您运动后出汗较黏、恢复偏慢吗？"},
                new String[]{"连续外卖高油饮食后，您更易困倦乏力吗？", "久坐上课一天后，您常感身重懒动吗？"});
        id = addDimensionQuestions(list, id, "shire",
                new String[]{"您面部油腻、口苦口黏或易生痤疮吗？", "您大便黏滞不爽或小便短黄吗？", "您常有心烦、口气偏重吗？"},
                new String[]{"夏季闷热时，您更易长痘和口苦吗？", "连续辛辣油炸后，您不适加重明显吗？"});
        id = addDimensionQuestions(list, id, "xueyu",
                new String[]{"您肤色晦暗或易出现瘀斑吗？", "您常有固定部位刺痛或痛经吗？", "您记忆力下降、健忘较明显吗？"},
                new String[]{"久坐后肩颈或腰背固定痛点更明显吗？", "熬夜后面色暗沉恢复较慢吗？"});
        id = addDimensionQuestions(list, id, "qiyu",
                new String[]{"您常情绪低落、胸闷或喜叹息吗？", "您紧张时易胃口差、腹胀或咽部异物感吗？", "您对压力事件恢复较慢吗？"},
                new String[]{"课程/社团冲突增多时，您情绪波动明显吗？", "在宿舍人际压力下，您易感胸闷烦躁吗？"});
        addDimensionQuestions(list, id, "tebing",
                new String[]{"您常有过敏反应（鼻炎、皮疹、食物不耐受）吗？", "季节交替时，您过敏症状易发作吗？", "接触尘螨/花粉后，您反应明显吗？"},
                new String[]{"换宿舍或打扫后，您鼻塞喷嚏加重吗？", "尝试新饮料/零食后，您更易出现不适吗？"});
        return Collections.unmodifiableList(list);
    }

    private static int addDimensionQuestions(List<QuestionDef> list, int startId, String code, String[] core, String[] scene) {
        int id = startId;
        for (String text : core) {
            list.add(new QuestionDef(id, code, "core", "general", "direct", 1.0, text));
            id++;
        }
        for (String text : scene) {
            list.add(new QuestionDef(id, code, "scene", "campus", "direct", 0.9, text));
            id++;
        }
        return id;
    }

    private static final class QuestionDef {
        final int id;
        final String targetCode;
        final String source;
        final String symptomDomain;
        final String direction;
        final double weight;
        final String text;

        private QuestionDef(int id, String targetCode, String source, String symptomDomain, String direction, double weight, String text) {
            this.id = id;
            this.targetCode = targetCode;
            this.source = source;
            this.symptomDomain = symptomDomain;
            this.direction = direction;
            this.weight = weight;
            this.text = text;
        }
    }

    public static class SurveyResult {
        public final String primaryCode;
        public final String primaryLabel;
        public final List<String> secondaryCodes;
        public final Map<String, Double> transformedScores;
        public final Map<String, Double> rawScores;
        public final double confidence;
        public final List<String> ruleTrace;
        public final String questionVersion;
        public final String scoresJson;

        public SurveyResult(
                String primaryCode,
                String primaryLabel,
                List<String> secondaryCodes,
                Map<String, Double> transformedScores,
                Map<String, Double> rawScores,
                double confidence,
                List<String> ruleTrace,
                String questionVersion,
                String scoresJson) {
            this.primaryCode = primaryCode;
            this.primaryLabel = primaryLabel;
            this.secondaryCodes = secondaryCodes;
            this.transformedScores = transformedScores;
            this.rawScores = rawScores;
            this.confidence = confidence;
            this.ruleTrace = ruleTrace;
            this.questionVersion = questionVersion;
            this.scoresJson = scoresJson;
        }
    }
}
