package com.campus.diet.service.ai;

import com.campus.diet.entity.Recipe;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * AI 食疗方案：将模型/兜底 JSON 与菜谱池合并为统一输出结构（无 Spring 依赖以外的状态）。
 */
@Component
public class AiTherapyPlanOutputMerger {

    public Map<String, Object> mergeAndValidate(
            Map<String, Object> parsed,
            String symptom,
            String constLabel,
            List<Recipe> pool,
            boolean vague,
            boolean fromFallback) {
        Map<Long, Recipe> byId = pool.stream().collect(Collectors.toMap(Recipe::getId, r -> r, (a, b) -> a));
        Map<String, Recipe> byName = new HashMap<>();
        for (Recipe r : pool) {
            byName.put(r.getName().trim().toLowerCase(Locale.ROOT), r);
        }

        String planId = "plan-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
        if (parsed.get("planId") instanceof String && !((String) parsed.get("planId")).isBlank()) {
            planId = ((String) parsed.get("planId")).trim();
        }

        String symptomSummary = str(parsed.get("symptomSummary"), symptom.isEmpty() ? "日常调养" : symptom);
        String constitutionApplied = str(parsed.get("constitutionApplied"), constLabel != null ? constLabel : "");

        List<Map<String, Object>> recipesOut = new ArrayList<>();
        Object recObj = parsed.get("recipes");
        if (recObj instanceof List<?>) {
            for (Object o : (List<?>) recObj) {
                if (!(o instanceof Map<?, ?>)) {
                    continue;
                }
                Map<?, ?> m = (Map<?, ?>) o;
                String rid = String.valueOf(m.get("recipeId")).trim();
                String rname = str(m.get("recipeName"), "");
                String reason = str(m.get("matchReason"), "与当前调养方向相符的参考搭配。");
                Recipe hit = null;
                try {
                    Long id = Long.parseLong(rid);
                    hit = byId.get(id);
                } catch (NumberFormatException ignored) {
                    // ignore
                }
                if (hit == null && !rname.isEmpty()) {
                    hit = byName.get(rname.toLowerCase(Locale.ROOT));
                }
                if (hit != null) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("recipeId", String.valueOf(hit.getId()));
                    row.put("recipeName", hit.getName());
                    row.put("matchReason", reason);
                    recipesOut.add(row);
                }
            }
        }
        Set<String> seen = new HashSet<>();
        recipesOut = recipesOut.stream()
                .filter(r -> seen.add(String.valueOf(r.get("recipeId"))))
                .limit(5)
                .collect(Collectors.toCollection(ArrayList::new));

        if (recipesOut.size() < 3) {
            List<Recipe> pick = pickRecipesLocal(symptom, pool, 3 - recipesOut.size());
            for (Recipe r : pick) {
                String idStr = String.valueOf(r.getId());
                if (seen.contains(idStr)) {
                    continue;
                }
                seen.add(idStr);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("recipeId", idStr);
                row.put("recipeName", r.getName());
                row.put("matchReason", r.getEfficacySummary() != null && !r.getEfficacySummary().isBlank()
                        ? r.getEfficacySummary()
                        : "与当前症状方向相符的参考搭配。");
                recipesOut.add(row);
                if (recipesOut.size() >= 3) {
                    break;
                }
            }
        }

        List<Map<String, String>> core = readCoreIngredients(parsed.get("coreIngredients"));
        if (core.size() < 3) {
            core = defaultCoreIngredients();
        }

        List<String> life = readStringList(parsed.get("lifestyleAdvice"));
        if (life.size() < 3) {
            life = defaultLifestyle();
        }

        List<String> caution = readStringList(parsed.get("cautionNotes"));
        if (caution.size() < 3) {
            caution = defaultCautions();
        }

        String rationale = str(parsed.get("rationale"), "");
        if (rationale.isEmpty()) {
            rationale = defaultRationale(symptom, constLabel);
        }

        String disclaimer = str(
                parsed.get("disclaimer"),
                "内容由大模型生成，仅供健康教育参考，不构成医疗诊断或治疗方案。");

        if (!rationale.contains("方案编号：")) {
            rationale = rationale + "（方案编号：" + planId + "）";
        }

        String therapyMd = str(parsed.get("therapyRecommendMarkdown"), "");
        if (therapyMd.isEmpty()) {
            therapyMd = renderTherapyMarkdownFallback(
                    symptomSummary,
                    constitutionApplied.isEmpty() ? null : constitutionApplied,
                    recipesOut,
                    core,
                    life,
                    caution,
                    rationale);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("planId", planId);
        out.put("symptomSummary", symptomSummary);
        out.put("constitutionApplied", constitutionApplied.isEmpty() ? null : constitutionApplied);
        out.put("recipes", recipesOut);
        out.put("coreIngredients", core);
        out.put("lifestyleAdvice", life);
        out.put("cautionNotes", caution);
        out.put("rationale", rationale);
        out.put("disclaimer", disclaimer);
        out.put("therapyRecommendMarkdown", therapyMd);
        out.put("isGenericPlan", vague);
        out.put("localFallback", fromFallback);
        return out;
    }

    public Map<String, Object> buildLocalPlan(
            String symptom,
            String constLabel,
            List<Recipe> pool,
            boolean vague,
            boolean fromFallback) {
        Map<String, Object> shell = new LinkedHashMap<>();
        shell.put("symptomSummary", symptom.isEmpty() ? "日常调养" : symptom);
        shell.put("constitutionApplied", constLabel);
        shell.put("recipes", List.of());
        shell.put("coreIngredients", List.of());
        shell.put("lifestyleAdvice", List.of());
        shell.put("cautionNotes", List.of());
        shell.put("rationale", "");
        shell.put("disclaimer", "");
        return mergeAndValidate(shell, symptom, constLabel, pool, vague, fromFallback);
    }

    private String renderTherapyMarkdownFallback(
            String symptomSummary,
            String constitutionApplied,
            List<Map<String, Object>> recipesOut,
            List<Map<String, String>> core,
            List<String> life,
            List<String> caution,
            String rationale) {
        StringBuilder md = new StringBuilder();
        md.append("## 调养焦点\n\n");
        md.append("结合您描述的「**").append(mdEscape(symptomSummary)).append("**」");
        if (constitutionApplied != null && !constitutionApplied.isBlank()) {
            md.append("，并参考体质倾向「**").append(mdEscape(constitutionApplied)).append("**」");
        }
        md.append("，以下从**药膳与饮食搭配**角度给出可执行的温和建议（仅供养生参考）。\n\n");

        md.append("## 食养思路与原则\n\n");
        md.append("- 以**清淡、温热适口、易消化**为主，避免过于滋腻或刺激。\n");
        md.append("- 食材与菜谱可轮换，**适量、长期**优于一次大量。\n\n");

        md.append("## 推荐食材与意象\n\n");
        for (Map<String, String> row : core) {
            String n = row.get("name");
            String b = row.get("benefit");
            if (n != null && !n.isBlank() && b != null && !b.isBlank()) {
                md.append("- **").append(mdEscape(n.trim())).append("**：").append(mdEscape(b.trim())).append("\n");
            }
        }
        md.append("\n");

        md.append("## 一周参考搭配\n\n");
        md.append("下列与下方「推荐食疗方」列表一致，建议**每周选做 3～4 次**，单次适量；点击菜谱名可查看详情。\n\n");
        for (Map<String, Object> r : recipesOut) {
            String name = String.valueOf(r.getOrDefault("recipeName", "")).trim();
            String reason = String.valueOf(r.getOrDefault("matchReason", "")).trim();
            if (!name.isEmpty()) {
                md.append("- **").append(mdEscape(name)).append("**");
                if (!reason.isEmpty()) {
                    md.append("：").append(mdEscape(reason));
                }
                md.append("\n");
            }
        }
        md.append("\n");

        md.append("## 生活习惯配合\n\n");
        for (String line : life) {
            if (line != null && !line.isBlank()) {
                md.append("- ").append(mdEscape(line.trim())).append("\n");
            }
        }
        md.append("\n");

        md.append("## 禁忌与就医提示\n\n");
        for (String line : caution) {
            if (line != null && !line.isBlank()) {
                md.append("- ").append(mdEscape(line.trim())).append("\n");
            }
        }
        md.append("\n");

        md.append("## 简要说明\n\n");
        md.append(mdEscape(rationale != null ? rationale.trim() : "")).append("\n\n");
        md.append("---\n\n*本页内容为膳食与生活调养参考，**不替代**诊疗与处方；急症或症状持续加重请及时就医。*\n");
        return md.toString();
    }

    private static String mdEscape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\r\n", "\n").replace('\r', '\n').replace("\n\n", "\n").trim().replace('\n', ' ');
    }

    public static String str(Object v, String dft) {
        if (v == null) {
            return dft;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? dft : s;
    }

    public static List<Map<String, String>> readCoreIngredients(Object v) {
        List<Map<String, String>> out = new ArrayList<>();
        if (!(v instanceof List<?>)) {
            return out;
        }
        for (Object o : (List<?>) v) {
            if (!(o instanceof Map<?, ?>)) {
                continue;
            }
            Map<?, ?> m = (Map<?, ?>) o;
            String name = str(m.get("name"), "");
            String benefit = str(m.get("benefit"), "");
            if (!name.isEmpty() && !benefit.isEmpty()) {
                out.add(Map.of("name", name, "benefit", benefit));
            }
        }
        return out;
    }

    public static List<String> readStringList(Object v) {
        if (!(v instanceof List<?>)) {
            return new ArrayList<>();
        }
        List<String> out = new ArrayList<>();
        for (Object o : (List<?>) v) {
            if (o == null) {
                continue;
            }
            String s = String.valueOf(o).trim();
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    private static List<Map<String, String>> defaultCoreIngredients() {
        return List.of(
                Map.of("name", "山药", "benefit", "健脾益肺、补肾固精，适合作为温和食补基底。"),
                Map.of("name", "薏苡仁", "benefit", "渗湿健脾；体寒者可选用炒制薏米以减凉性。"),
                Map.of("name", "百合", "benefit", "养阴润肺、清心安神，对口干舌燥、睡眠不佳可作膳食参考。")
        );
    }

    private static List<String> defaultLifestyle() {
        return List.of(
                "尽量固定作息，减少连续熬夜；用脑间隙做轻度拉伸。",
                "少量多次饮水，温水为宜；减少过甜饮料。",
                "每周数次轻松有氧运动（快走、八段锦），以微汗为度。"
        );
    }

    private static List<String> defaultCautions() {
        return List.of(
                "本方案为膳食参考，不能替代诊疗；症状持续或加重请及时就医。",
                "孕妇、哺乳期、慢性肾病或正在服药者，食用药膳前请咨询医师或药师。",
                "对列出的食材过敏者请勿食用相应菜品。"
        );
    }

    private static String defaultRationale(String symptom, String constLabel) {
        String s = symptom.isEmpty() ? "日常调养" : symptom;
        if (constLabel != null && !constLabel.isEmpty()) {
            return "结合您描述的主要不适「" + s + "」，并参考体质倾向「" + constLabel
                    + "」，从清补兼顾、少刺激、易执行的角度给出食疗与生活建议。";
        }
        return "结合您描述的主要不适「" + s + "」，从清补兼顾、少刺激、易执行的角度给出食疗与生活建议。";
    }

    private List<Recipe> pickRecipesLocal(String symptom, List<Recipe> pool, int need) {
        if (pool.isEmpty() || need <= 0) {
            return List.of();
        }
        String sym = symptom.toLowerCase(Locale.ROOT);
        List<Recipe> scored = new ArrayList<>(pool);
        scored.sort(Comparator.comparingInt(Recipe::getCollectCount).reversed());
        List<Recipe> filtered = scored.stream()
                .filter(r -> matchesSymptomHeuristic(sym, r))
                .limit(Math.max(need, 3))
                .collect(Collectors.toList());
        if (filtered.size() < need) {
            for (Recipe r : scored) {
                if (filtered.contains(r)) {
                    continue;
                }
                filtered.add(r);
                if (filtered.size() >= need) {
                    break;
                }
            }
        }
        return filtered.stream().limit(need).collect(Collectors.toList());
    }

    private static boolean matchesSymptomHeuristic(String symLower, Recipe r) {
        if (symLower.isEmpty()) {
            return true;
        }
        String blob = (nz(r.getName()) + " " + nz(r.getEfficacySummary()) + " " + nz(r.getSymptomTags())
                + " " + nz(r.getEfficacyTags())).toLowerCase(Locale.ROOT);
        if (blob.isEmpty()) {
            return false;
        }
        if ((symLower.contains("痘") || symLower.contains("口干") || symLower.contains("熬夜"))
                && (blob.contains("百合") || blob.contains("银耳") || blob.contains("粥"))) {
            return true;
        }
        if ((symLower.contains("疲劳") || symLower.contains("气短") || symLower.contains("虚"))
                && (blob.contains("黄芪") || blob.contains("鸡") || blob.contains("山药"))) {
            return true;
        }
        if ((symLower.contains("凉") || symLower.contains("怕冷") || symLower.contains("胃"))
                && (blob.contains("姜") || blob.contains("羊肉") || blob.contains("粥"))) {
            return true;
        }
        String[] tokens = symLower.split("[\\s,，、;；]+");
        for (String t : tokens) {
            if (t.length() >= 2 && blob.contains(t)) {
                return true;
            }
        }
        return false;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** 质量评分用：解析 recipes 列表 */
    public static List<Map<String, Object>> readRecipes(Object v) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!(v instanceof List<?>)) {
            return out;
        }
        for (Object o : (List<?>) v) {
            if (o instanceof Map<?, ?>) {
                @SuppressWarnings("unchecked")
                Map<String, Object> row = (Map<String, Object>) o;
                out.add(row);
            }
        }
        return out;
    }
}
