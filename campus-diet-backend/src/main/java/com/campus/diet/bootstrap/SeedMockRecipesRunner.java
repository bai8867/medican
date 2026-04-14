package com.campus.diet.bootstrap;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.diet.entity.Recipe;
import com.campus.diet.mapper.RecipeMapper;
import com.campus.diet.util.ConstitutionLabelUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 启动时将前端 {@code recommendMock.js} 导出的 {@code bootstrap/mock-recipes.json}
 * 写入数据库：按药膳名称去重，已存在则跳过。
 * <p>
 * 重新生成 JSON：在 {@code tcm-diet-frontend} 目录执行 {@code npm run sync:backend-mock-json}
 */
@Component
@Order(25)
public class SeedMockRecipesRunner implements ApplicationRunner {

    private static final String DEFAULT_TABOO =
            "实热证、急性炎症期慎用；对所含食材或药材过敏者请勿食用。孕妇、哺乳期妇女及服药人群请先咨询医师。";

    private final RecipeMapper recipeMapper;
    private final ObjectMapper objectMapper;

    @Value("${campus.diet.seed-mock-recipes:true}")
    private boolean seedMockRecipes;

    public SeedMockRecipesRunner(RecipeMapper recipeMapper, ObjectMapper objectMapper) {
        this.recipeMapper = recipeMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!seedMockRecipes) {
            return;
        }
        InputStream in = getClass().getResourceAsStream("/bootstrap/mock-recipes.json");
        if (in == null) {
            return;
        }
        JsonNode root = objectMapper.readTree(in);
        if (!root.isArray()) {
            return;
        }
        for (JsonNode item : root) {
            seedOne(item);
        }
    }

    private void seedOne(JsonNode item) {
        try {
            String name = text(item, "name");
            if (name.isEmpty()) {
                return;
            }
            Long exists = recipeMapper.selectCount(
                    Wrappers.<Recipe>lambdaQuery().eq(Recipe::getName, name));
            if (exists != null && exists > 0) {
                return;
            }

            Recipe r = new Recipe();
            r.setName(name);
            r.setCoverUrl(trimToNull(text(item, "coverUrl")));
            r.setEfficacySummary(truncate(textOr(item, "summary", "effect"), 255));
            r.setCollectCount(intOr(item.get("collectCount"), 0));
            r.setSeasonTags(seasonTagsFromFit(item.get("seasonFit")));
            r.setConstitutionTags(constitutionTagsFromMock(item));
            r.setEfficacyTags(efficacyTagsCsv(item));
            r.setSymptomTags(symptomTagsCsv(item));
            r.setInstructionSummary(buildInstructionSummary(item));
            r.setStepsJson(buildStepsEnvelopeJson(item));
            r.setContraindication(trimToNull(text(item, "taboo")) != null
                    ? text(item, "taboo").trim()
                    : DEFAULT_TABOO);
            r.setStatus(1);
            recipeMapper.insert(r);
        } catch (Exception ignored) {
            // 单条失败不影响其余种子数据
        }
    }

    private static String buildInstructionSummary(JsonNode item) {
        StringBuilder sb = new StringBuilder();
        String summary = text(item, "summary");
        if (!summary.isEmpty()) {
            sb.append(summary);
        }
        String effect = text(item, "effect");
        if (!effect.isEmpty()) {
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append("功效要点：").append(effect);
        }
        String cook = text(item, "cookTime");
        String diff = text(item, "difficulty");
        if (!cook.isEmpty() || !diff.isEmpty()) {
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            if (!diff.isEmpty()) {
                sb.append("难度：").append(diff);
            }
            if (!cook.isEmpty()) {
                if (!diff.isEmpty()) {
                    sb.append("；");
                }
                sb.append("制作时长：").append(cook);
            }
        }
        return sb.length() > 0 ? sb.toString() : "详见步骤说明。";
    }

    private String buildStepsEnvelopeJson(JsonNode item) throws Exception {
        ObjectNode env = objectMapper.createObjectNode();
        ArrayNode steps = objectMapper.createArrayNode();
        JsonNode stepsIn = item.get("steps");
        if (stepsIn != null && stepsIn.isArray()) {
            for (JsonNode s : stepsIn) {
                ObjectNode one = objectMapper.createObjectNode();
                String t = textOr(s, "text", "description");
                one.put("text", t);
                if (s.has("tip") && !s.get("tip").isNull()) {
                    one.set("tip", s.get("tip"));
                }
                steps.add(one);
            }
        }
        env.set("steps", steps);

        ArrayNode ingredients = objectMapper.createArrayNode();
        JsonNode ing = item.get("ingredients");
        if (ing != null && ing.isArray()) {
            for (JsonNode it : ing) {
                ObjectNode o = objectMapper.createObjectNode();
                o.put("name", text(it, "name"));
                o.put("amount", text(it, "amount"));
                if (it.has("note") && !it.get("note").isNull()) {
                    o.set("note", it.get("note"));
                }
                ingredients.add(o);
            }
        }
        env.set("ingredients", ingredients);

        List<String> effectTags = new ArrayList<>();
        JsonNode et = item.get("effectTags");
        if (et != null && et.isArray()) {
            for (JsonNode t : et) {
                if (t.isTextual()) {
                    effectTags.add(t.asText());
                }
            }
        }
        if (effectTags.isEmpty() && text(item, "effect").length() > 0) {
            for (String p : text(item, "effect").split("[、，,]")) {
                String x = p.trim();
                if (!x.isEmpty()) {
                    effectTags.add(x);
                }
            }
        }
        env.set("effectTags", objectMapper.valueToTree(effectTags));

        ArrayNode seasonFit = objectMapper.createArrayNode();
        JsonNode sf = item.get("seasonFit");
        if (sf != null && sf.isArray()) {
            for (JsonNode n : sf) {
                if (n.isTextual()) {
                    seasonFit.add(n.asText());
                }
            }
        }
        env.set("seasonFit", seasonFit);

        List<String> suits = suitLabels(item);
        env.set("suitConstitutions", objectMapper.valueToTree(suits));

        env.put("summary", text(item, "summary"));
        env.put("effect", text(item, "effect"));
        env.put("cookTime", text(item, "cookTime"));
        env.put("difficulty", text(item, "difficulty"));
        String rr = text(item, "recommendReason");
        if (!rr.isEmpty()) {
            env.put("recommendReason", rr);
        }
        return objectMapper.writeValueAsString(env);
    }

    private static List<String> suitLabels(JsonNode item) {
        List<String> out = new ArrayList<>();
        JsonNode arr = item.get("suitConstitutions");
        if (arr != null && arr.isArray()) {
            for (JsonNode n : arr) {
                if (n.isTextual()) {
                    String v = n.asText().trim();
                    if (!v.isEmpty()) {
                        out.add(v);
                    }
                }
            }
        }
        if (!out.isEmpty()) {
            return out;
        }
        String single = text(item, "suitConstitution");
        if (!single.isEmpty()) {
            out.add(single);
        }
        return out;
    }

    private static String constitutionTagsFromMock(JsonNode item) {
        return ConstitutionLabelUtil.codesCsvFromLabels(suitLabels(item));
    }

    private static String symptomTagsCsv(JsonNode item) {
        List<String> tags = new ArrayList<>();
        JsonNode st = item.get("symptomTags");
        if (st != null && st.isArray()) {
            for (JsonNode t : st) {
                if (t.isTextual()) {
                    String v = t.asText().trim();
                    if (!v.isEmpty()) {
                        tags.add(v);
                    }
                }
            }
        }
        String joined = String.join(",", tags);
        return joined.length() > 500 ? joined.substring(0, 500) : joined;
    }

    private static String efficacyTagsCsv(JsonNode item) {
        List<String> tags = new ArrayList<>();
        JsonNode et = item.get("effectTags");
        if (et != null && et.isArray()) {
            for (JsonNode t : et) {
                if (t.isTextual()) {
                    String v = t.asText().trim();
                    if (!v.isEmpty()) {
                        tags.add(v);
                    }
                }
            }
        }
        if (tags.isEmpty() && text(item, "effect").length() > 0) {
            for (String p : text(item, "effect").split("[、，,]")) {
                String x = p.trim();
                if (!x.isEmpty() && tags.size() < 12) {
                    tags.add(x);
                }
            }
        }
        String joined = String.join(",", tags);
        return joined.length() > 240 ? joined.substring(0, 240) : joined;
    }

    private static String seasonTagsFromFit(JsonNode seasonFit) {
        if (seasonFit == null || !seasonFit.isArray() || seasonFit.isEmpty()) {
            return "spring,summer,autumn,winter";
        }
        List<String> parts = new ArrayList<>();
        for (JsonNode n : seasonFit) {
            if (n.isTextual()) {
                String v = n.asText().trim().toLowerCase();
                if ("all".equals(v)) {
                    return "spring,summer,autumn,winter";
                }
                if (!v.isEmpty()) {
                    parts.add(v);
                }
            }
        }
        return parts.isEmpty() ? "spring,summer,autumn,winter" : String.join(",", parts);
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return "";
        }
        JsonNode v = node.get(field);
        return v.isTextual() ? v.asText().trim() : v.toString();
    }

    private static String textOr(JsonNode node, String a, String b) {
        String x = text(node, a);
        return !x.isEmpty() ? x : text(node, b);
    }

    private static int intOr(JsonNode n, int def) {
        if (n == null || n.isNull() || !n.isNumber()) {
            return def;
        }
        return Math.max(0, n.asInt());
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }

    private static String trimToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }
}
