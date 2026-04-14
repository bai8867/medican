package com.campus.diet.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.diet.common.BizException;
import com.campus.diet.dto.RecipeCardDto;
import com.campus.diet.dto.SceneSolutionDto;
import com.campus.diet.dto.SceneTherapyListItemDto;
import com.campus.diet.dto.TeaRemedyDto;
import com.campus.diet.entity.CampusScene;
import com.campus.diet.entity.Recipe;
import com.campus.diet.mapper.CampusSceneMapper;
import com.campus.diet.mapper.RecipeMapper;
import com.campus.diet.util.JsonTags;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SceneTherapyService {

    private final CampusSceneMapper campusSceneMapper;
    private final RecipeMapper recipeMapper;
    private final CampusSceneService campusSceneService;
    private final ObjectMapper objectMapper;

    public SceneTherapyService(
            CampusSceneMapper campusSceneMapper,
            RecipeMapper recipeMapper,
            CampusSceneService campusSceneService,
            ObjectMapper objectMapper) {
        this.campusSceneMapper = campusSceneMapper;
        this.recipeMapper = recipeMapper;
        this.campusSceneService = campusSceneService;
        this.objectMapper = objectMapper;
    }

    public List<SceneTherapyListItemDto> listScenes() {
        return campusSceneService.listWithCounts().stream()
                .map(v -> toListItem(v.scene, v.recipeCount))
                .collect(Collectors.toList());
    }

    public SceneSolutionDto getSceneSolution(long sceneId) {
        CampusScene scene = campusSceneMapper.selectById(sceneId);
        if (scene == null) {
            throw new BizException(404, "场景不存在");
        }
        int count = campusSceneService.recipeCount(sceneId);
        SceneTherapyListItemDto header = toListItem(scene, count);
        ParsedExtra extra = parseExtra(scene.getExtraJson());

        List<String> painTags = extra.painTags.isEmpty()
                ? Collections.singletonList("日常调养")
                : extra.painTags;
        List<String> matcherTags = JsonTags.parseStringList(scene.getTagsJson());

        List<ScoredRecipe> ranked = rankRecipes(sceneId, painTags, matcherTags);
        List<RecipeCardDto> cards = ranked.stream()
                .limit(3)
                .map(sr -> {
                    RecipeCardDto d = RecipeCardDto.from(sr.recipe);
                    d.setMatchedPainTags(sr.matchedPains.isEmpty() ? null : sr.matchedPains);
                    d.setWhyFit(buildWhyFit(sr, matcherTags));
                    return d;
                })
                .collect(Collectors.toList());

        return new SceneSolutionDto(
                header,
                cards,
                extra.teas,
                extra.ingredientInsight,
                extra.forbidden);
    }

    private SceneTherapyListItemDto toListItem(CampusScene scene, int recipeCount) {
        ParsedExtra ex = parseExtra(scene.getExtraJson());
        return new SceneTherapyListItemDto(
                scene.getId(),
                scene.getName(),
                scene.getIcon(),
                scene.getDescription(),
                ex.tagline,
                ex.painTags,
                recipeCount,
                JsonTags.parseStringList(scene.getTagsJson()));
    }

    private String buildWhyFit(ScoredRecipe sr, List<String> matcherTags) {
        String pains = sr.matchedPains.isEmpty()
                ? "当前场景常见不适"
                : String.join("、", sr.matchedPains);
        String eff = summarizeEfficacy(sr.recipe);
        String matchLine = matcherTags.isEmpty()
                ? ""
                : "场景调养侧重「"
                        + String.join("、", matcherTags.stream().limit(4).collect(Collectors.toList()))
                        + "」。";
        return "你的困扰标签包含「"
                + pains
                + "」。本药膳功效侧重「"
                + eff
                + "」，与上述调养方向相契合。"
                + (matchLine.isEmpty() ? "" : matchLine)
                + "个体有差异，请结合自身情况参考。";
    }

    private static String summarizeEfficacy(Recipe r) {
        if (r.getEfficacyTags() != null && !r.getEfficacyTags().isBlank()) {
            return r.getEfficacyTags().replace(',', '、');
        }
        String s = r.getEfficacySummary();
        return s == null || s.isBlank() ? "综合调养" : s.trim();
    }

    private List<ScoredRecipe> rankRecipes(long sceneId, List<String> painTags, List<String> matcherTags) {
        List<Recipe> linked = recipeMapper.listByScene(sceneId);
        Set<Long> linkedIds = linked.stream().map(Recipe::getId).collect(Collectors.toCollection(HashSet::new));

        List<Recipe> all = recipeMapper.selectList(Wrappers.<Recipe>lambdaQuery()
                .eq(Recipe::getStatus, 1)
                .orderByDesc(Recipe::getCollectCount));

        List<ScoredRecipe> scored = new ArrayList<>();
        for (Recipe r : all) {
            List<String> matched = matchPainTags(r, painTags);
            int score = matched.size() * 4;
            score += efficacyMatcherScore(r, matcherTags);
            if (linkedIds.contains(r.getId())) {
                score += 40;
            }
            if (score > 0 || linkedIds.contains(r.getId())) {
                scored.add(new ScoredRecipe(r, score, matched));
            }
        }

        scored.sort(Comparator
                .comparingInt((ScoredRecipe sr) -> sr.score).reversed()
                .thenComparingInt(sr -> sr.recipe.getCollectCount() == null ? 0 : sr.recipe.getCollectCount())
                .reversed());

        if (scored.isEmpty() && !linked.isEmpty()) {
            for (Recipe r : linked) {
                scored.add(new ScoredRecipe(r, 1, matchPainTags(r, painTags)));
            }
        }
        // 库里有 recipe 但未写入 scene_recipe、且功效/症状字段与场景痛点未匹配时，上面会得到 0 条；按热度兜底避免「有数据却空白」
        if (scored.isEmpty() && !all.isEmpty()) {
            int n = Math.min(3, all.size());
            for (int i = 0; i < n; i++) {
                Recipe r = all.get(i);
                scored.add(new ScoredRecipe(r, 1, matchPainTags(r, painTags)));
            }
        }
        return scored;
    }

    private static int efficacyMatcherScore(Recipe r, List<String> matcherTags) {
        if (matcherTags == null || matcherTags.isEmpty()) {
            return 0;
        }
        String eff = norm(r.getEfficacyTags());
        int s = 0;
        for (String t : matcherTags) {
            if (t == null || t.isBlank()) {
                continue;
            }
            String n = norm(t);
            if (!n.isEmpty() && eff.contains(n)) {
                s += 3;
            }
        }
        return s;
    }

    private static List<String> matchPainTags(Recipe r, List<String> painTags) {
        String hay = norm(r.getEfficacyTags())
                + " "
                + norm(r.getSymptomTags())
                + " "
                + norm(r.getEfficacySummary());
        String hayFlat = hay.replace(",", "").replace("，", "").replace("、", "");
        List<String> out = new ArrayList<>();
        for (String p : painTags) {
            if (p == null || p.isBlank()) {
                continue;
            }
            String raw = p.trim();
            String n = norm(raw).replace(",", "").replace("，", "").replace("、", "");
            if (n.length() >= 2 && (hay.contains(n) || hayFlat.contains(n))) {
                out.add(raw);
                continue;
            }
            if (n.length() >= 4) {
                String head = n.substring(0, Math.min(4, n.length()));
                if (hayFlat.contains(head)) {
                    out.add(raw);
                }
            }
        }
        return out.stream().distinct().collect(Collectors.toList());
    }

    private static String norm(String s) {
        if (s == null) {
            return "";
        }
        return s.toLowerCase(Locale.ROOT).replace('，', ',').replace(" ", "");
    }

    private ParsedExtra parseExtra(String raw) {
        if (raw == null || raw.isBlank()) {
            return ParsedExtra.empty();
        }
        String t = raw.trim();
        if (!t.startsWith("{")) {
            return ParsedExtra.empty();
        }
        try {
            JsonNode n = objectMapper.readTree(raw);
            String tagline = text(n, "tagline");
            String insight = text(n, "ingredientInsight");
            List<String> pains = readStringArray(n.get("painTags"));
            List<String> forbidden = readStringArray(n.get("forbidden"));
            List<TeaRemedyDto> teas = readTeas(n.get("teas"));
            return new ParsedExtra(tagline, pains, forbidden, insight, teas);
        } catch (Exception e) {
            return ParsedExtra.empty();
        }
    }

    private static List<String> readStringArray(JsonNode arr) {
        if (arr == null || !arr.isArray()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (JsonNode x : arr) {
            if (x.isTextual()) {
                String v = x.asText().trim();
                if (!v.isEmpty()) {
                    out.add(v);
                }
            }
        }
        return out;
    }

    private static List<TeaRemedyDto> readTeas(JsonNode arr) {
        if (arr == null || !arr.isArray()) {
            return Collections.emptyList();
        }
        List<TeaRemedyDto> out = new ArrayList<>();
        for (JsonNode x : arr) {
            if (!x.isObject()) {
                continue;
            }
            String title = text(x, "title");
            String body = text(x, "body");
            String type = text(x, "type");
            if (type.isEmpty()) {
                type = "tea";
            }
            if (!title.isEmpty() || !body.isEmpty()) {
                out.add(new TeaRemedyDto(title, body, type));
            }
        }
        return out;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return "";
        }
        JsonNode v = node.get(field);
        return v.isTextual() ? v.asText().trim() : v.toString();
    }

    private static final class ParsedExtra {
        final String tagline;
        final List<String> painTags;
        final List<String> forbidden;
        final String ingredientInsight;
        final List<TeaRemedyDto> teas;

        ParsedExtra(
                String tagline,
                List<String> painTags,
                List<String> forbidden,
                String ingredientInsight,
                List<TeaRemedyDto> teas) {
            this.tagline = tagline;
            this.painTags = painTags;
            this.forbidden = forbidden;
            this.ingredientInsight = ingredientInsight;
            this.teas = teas;
        }

        static ParsedExtra empty() {
            return new ParsedExtra("", Collections.emptyList(), Collections.emptyList(), "", Collections.emptyList());
        }
    }

    private static final class ScoredRecipe {
        final Recipe recipe;
        final int score;
        final List<String> matchedPains;

        ScoredRecipe(Recipe recipe, int score, List<String> matchedPains) {
            this.recipe = recipe;
            this.score = score;
            this.matchedPains = matchedPains;
        }
    }
}
