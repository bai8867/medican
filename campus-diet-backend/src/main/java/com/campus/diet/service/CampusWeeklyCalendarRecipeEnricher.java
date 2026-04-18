package com.campus.diet.service;

import com.campus.diet.entity.Recipe;
import com.campus.diet.mapper.RecipeMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CampusWeeklyCalendarRecipeEnricher {

    private static final String[] MEAL_KEYS = {"breakfast", "lunch", "dinner", "midnightSnack"};

    private static final Map<String, String> CONSTITUTION_CODE_TO_LABEL = Map.ofEntries(
            Map.entry("pinghe", "平和质"),
            Map.entry("qixu", "气虚质"),
            Map.entry("yangxu", "阳虚质"),
            Map.entry("yinxu", "阴虚质"),
            Map.entry("tanshi", "痰湿质"),
            Map.entry("shire", "湿热质"),
            Map.entry("xueyu", "血瘀质"),
            Map.entry("qiyu", "气郁质"),
            Map.entry("tebing", "特禀质"));

    /** 历史周历 JSON 中的 demo 药膳 id → 与 data.sql 一致的 recipe.id */
    private static final Map<String, Long> LEGACY_CALENDAR_DEMO_RECIPE_ID = Map.ofEntries(
            Map.entry("demo-001", 2L),
            Map.entry("demo-002", 9L),
            Map.entry("demo-003", 10L),
            Map.entry("demo-004", 6L),
            Map.entry("demo-005", 1L),
            Map.entry("demo-006", 5L),
            Map.entry("demo-007", 8L),
            Map.entry("demo-008", 4L),
            Map.entry("demo-009", 7L));

    private final RecipeMapper recipeMapper;
    private final ObjectMapper objectMapper;

    public CampusWeeklyCalendarRecipeEnricher(RecipeMapper recipeMapper, ObjectMapper objectMapper) {
        this.recipeMapper = recipeMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 公开周历：按 {@code recipeId} 关联 {@code recipe} 表，统一展示名、禁忌与适宜体质（与详情页一致）。
     * 档口价、供应时段等仍来自 days_json。
     */
    public void enrichDaysDishesFromRecipeTable(ArrayNode days) {
        if (days == null) {
            return;
        }
        Set<Long> idSet = new HashSet<>();
        for (JsonNode day : days) {
            if (!day.isObject()) {
                continue;
            }
            JsonNode meals = day.get("meals");
            if (meals == null || !meals.isObject()) {
                continue;
            }
            for (String mk : MEAL_KEYS) {
                JsonNode slot = meals.get(mk);
                if (slot == null || !slot.isArray()) {
                    continue;
                }
                for (JsonNode d : slot) {
                    Long rid = resolveCalendarRecipeTableId(d.get("recipeId"));
                    if (rid != null) {
                        idSet.add(rid);
                    }
                }
            }
        }
        if (idSet.isEmpty()) {
            return;
        }
        List<Recipe> rows = recipeMapper.selectBatchIds(idSet);
        Map<Long, Recipe> byId =
                rows.stream().collect(Collectors.toMap(Recipe::getId, r -> r, (a, b) -> a));
        for (JsonNode day : days) {
            if (!day.isObject()) {
                continue;
            }
            ObjectNode dayObj = (ObjectNode) day;
            JsonNode meals = dayObj.get("meals");
            if (meals == null || !meals.isObject()) {
                continue;
            }
            ObjectNode mealsObj = (ObjectNode) meals;
            for (String mk : MEAL_KEYS) {
                JsonNode slot = mealsObj.get(mk);
                if (slot == null || !slot.isArray()) {
                    continue;
                }
                ArrayNode arr = (ArrayNode) slot;
                for (int i = 0; i < arr.size(); i++) {
                    JsonNode d = arr.get(i);
                    if (!d.isObject()) {
                        continue;
                    }
                    Long rid = resolveCalendarRecipeTableId(d.get("recipeId"));
                    Recipe r = rid == null ? null : byId.get(rid);
                    if (r == null || r.getStatus() == null || r.getStatus() != 1) {
                        continue;
                    }
                    ObjectNode dish = (ObjectNode) d;
                    dish.put("recipeId", String.valueOf(r.getId()));
                    dish.put("name", r.getName() != null ? r.getName() : "");
                    if (r.getContraindication() != null && !r.getContraindication().isBlank()) {
                        dish.put("contraindicationNote", r.getContraindication());
                    }
                    ArrayNode suits = objectMapper.createArrayNode();
                    for (String code : splitCommaCodes(r.getConstitutionTags())) {
                        String lab = CONSTITUTION_CODE_TO_LABEL.get(code);
                        if (lab != null) {
                            suits.add(lab);
                        }
                    }
                    dish.set("suitConstitutionLabels", suits);
                }
            }
        }
    }

    /**
     * 周历菜品 recipeId：支持数字主键，或旧版 {@code demo-xxx}（映射到 seed 药膳）。
     */
    private static Long resolveCalendarRecipeTableId(JsonNode recipeIdNode) {
        if (recipeIdNode == null || recipeIdNode.isNull()) {
            return null;
        }
        String s;
        if (recipeIdNode.isIntegralNumber()) {
            s = String.valueOf(recipeIdNode.longValue());
        } else if (recipeIdNode.isTextual()) {
            s = recipeIdNode.asText().trim();
        } else {
            return null;
        }
        if (s.isEmpty()) {
            return null;
        }
        Long legacy = LEGACY_CALENDAR_DEMO_RECIPE_ID.get(s);
        if (legacy != null) {
            return legacy;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<String> splitCommaCodes(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toList());
    }
}
