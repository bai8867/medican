package com.campus.diet.controller;



import com.campus.diet.common.ApiResponse;

import com.campus.diet.common.BizException;

import com.campus.diet.entity.Recipe;

import com.campus.diet.mapper.RecipeMapper;

import com.campus.diet.security.LoginUser;
import com.campus.diet.security.LoginUserHolder;
import com.campus.diet.service.FavoriteService;
import com.campus.diet.util.ConstitutionLabelUtil;

import com.fasterxml.jackson.databind.JsonNode;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.web.bind.annotation.PathVariable;

import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;



import java.util.ArrayList;

import java.util.HashMap;

import java.util.List;

import java.util.Map;



@RestController

@RequestMapping("/api/recipes")

public class RecipePublicController {



    private final RecipeMapper recipeMapper;

    private final ObjectMapper objectMapper;

    private final FavoriteService favoriteService;



    public RecipePublicController(
            RecipeMapper recipeMapper,
            ObjectMapper objectMapper,
            FavoriteService favoriteService) {

        this.recipeMapper = recipeMapper;

        this.objectMapper = objectMapper;

        this.favoriteService = favoriteService;

    }



    @GetMapping("/{id}")

    public ApiResponse<Map<String, Object>> detail(@PathVariable String id) {

        long rid;

        try {

            rid = Long.parseLong(id);

        } catch (NumberFormatException e) {

            throw new BizException(400, "非法 ID");

        }

        Recipe r = recipeMapper.selectById(rid);

        if (r == null) {

            throw new BizException(404, "药膳不存在");

        }

        if (r.getStatus() == null || r.getStatus() != 1) {

            throw new BizException(404, "药膳不存在或已下架");

        }

        Map<String, Object> m = new HashMap<>();

        m.put("id", String.valueOf(r.getId()));

        m.put("name", r.getName());

        m.put("coverUrl", r.getCoverUrl());

        m.put("collectCount", r.getCollectCount());

        m.put("efficacySummary", r.getEfficacySummary());

        m.put("instructionSummary", r.getInstructionSummary());

        m.put("stepsJson", r.getStepsJson());

        m.put("contraindication", r.getContraindication());

        m.put("seasonTags", r.getSeasonTags());

        m.put("constitutionTags", r.getConstitutionTags());

        mergeFrontendFields(r, m);

        LoginUser u = LoginUserHolder.get();
        if (u != null) {
            m.put("favorited", favoriteService.isFavorite(u.getUserId(), rid));
        }

        return ApiResponse.ok(m);

    }



    /**

     * 将 {@code steps_json} 中的扩展包与表字段展开为前台 normalizeRecipeDetail 可用的结构。

     */

    private void mergeFrontendFields(Recipe r, Map<String, Object> m) {

        boolean mergedEnvelope = false;

        String raw = r.getStepsJson();

        if (raw != null && !raw.isBlank()) {

            String t = raw.trim();

            if (t.startsWith("{")) {

                try {

                    JsonNode env = objectMapper.readTree(raw);

                    if (env.has("steps") && env.get("steps").isArray()) {

                        List<Map<String, Object>> steps = new ArrayList<>();

                        for (JsonNode s : env.get("steps")) {

                            Map<String, Object> one = new HashMap<>();

                            String text = textOr(s, "text", "description");

                            one.put("text", text);

                            if (s.has("tip") && !s.get("tip").isNull()) {

                                one.put("tip", s.get("tip").isTextual() ? s.get("tip").asText() : s.get("tip").toString());

                            }

                            steps.add(one);

                        }

                        m.put("steps", steps);

                    }

                    if (env.has("ingredients") && env.get("ingredients").isArray()) {

                        List<Map<String, Object>> ings = new ArrayList<>();

                        for (JsonNode it : env.get("ingredients")) {

                            Map<String, Object> o = new HashMap<>();

                            o.put("name", text(it, "name"));

                            o.put("amount", text(it, "amount"));

                            if (it.has("note") && !it.get("note").isNull()) {

                                o.put("note", it.get("note").isTextual() ? it.get("note").asText() : it.get("note").toString());

                            }

                            ings.add(o);

                        }

                        m.put("ingredients", ings);

                    }

                    putStringList(m, env, "effectTags");

                    putStringList(m, env, "seasonFit");

                    putStringList(m, env, "suitConstitutions");

                    copyTextField(m, env, "summary");

                    copyTextField(m, env, "effect");

                    copyTextField(m, env, "cookTime");

                    copyTextField(m, env, "difficulty");

                    copyTextField(m, env, "recommendReason");

                    mergedEnvelope = m.containsKey("steps") || m.containsKey("ingredients");

                } catch (Exception ignored) {

                    // 保持原始 stepsJson

                }

            }

        }

        if (!m.containsKey("effectTags") && r.getEfficacyTags() != null && !r.getEfficacyTags().isBlank()) {

            List<String> tags = new ArrayList<>();

            for (String p : r.getEfficacyTags().split(",")) {

                String x = p.trim();

                if (!x.isEmpty()) {

                    tags.add(x);

                }

            }

            m.put("effectTags", tags);

        }

        if (!m.containsKey("suitConstitutions") && r.getConstitutionTags() != null && !r.getConstitutionTags().isBlank()) {

            m.put("suitConstitutions", ConstitutionLabelUtil.labelsFromCodesCsv(r.getConstitutionTags()));

        }

        if (!m.containsKey("seasonFit") && r.getSeasonTags() != null && !r.getSeasonTags().isBlank()) {

            List<String> seasons = new ArrayList<>();

            for (String p : r.getSeasonTags().split(",")) {

                String x = p.trim().toLowerCase();

                if (!x.isEmpty()) {

                    seasons.add(x);

                }

            }

            m.put("seasonFit", seasons);

        }

        String sum = (String) m.get("summary");

        if ((sum == null || sum.isBlank()) && r.getEfficacySummary() != null) {

            m.put("summary", r.getEfficacySummary());

        }

        if (mergedEnvelope) {

            m.remove("stepsJson");

        }

    }



    private static void putStringList(Map<String, Object> m, JsonNode env, String key) {

        if (!env.has(key) || !env.get(key).isArray()) {

            return;

        }

        List<String> list = new ArrayList<>();

        for (JsonNode n : env.get(key)) {

            if (n.isTextual()) {

                String v = n.asText().trim();

                if (!v.isEmpty()) {

                    list.add(v);

                }

            }

        }

        if (!list.isEmpty()) {

            m.put(key, list);

        }

    }



    private static void copyTextField(Map<String, Object> m, JsonNode env, String key) {

        if (!env.has(key) || env.get(key).isNull()) {

            return;

        }

        JsonNode v = env.get(key);

        String s = v.isTextual() ? v.asText().trim() : v.toString();

        if (!s.isEmpty()) {

            m.put(key, s);

        }

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

}

