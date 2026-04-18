package com.campus.diet.service.ai;

import com.campus.diet.entity.Recipe;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiTherapyPlanOutputMergerTest {

    private final AiTherapyPlanOutputMerger merger = new AiTherapyPlanOutputMerger();

    @Test
    void buildLocalPlan_shouldFillAtLeastThreeRecipesFromPool() {
        List<Recipe> pool = List.of(recipe(1L, "百合粥"), recipe(2L, "山药鸡汤"), recipe(3L, "薏米粥"));
        Map<String, Object> out = merger.buildLocalPlan("口干", "阴虚质", pool, false, true);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> recipes = (List<Map<String, Object>>) out.get("recipes");
        assertEquals(3, recipes.size());
        assertTrue(out.get("rationale").toString().contains("方案编号："));
    }

    @Test
    void mergeAndValidate_shouldDedupeRecipeIds() {
        List<Recipe> pool = List.of(recipe(10L, "银耳羹"), recipe(11L, "莲子汤"), recipe(12L, "枸杞茶"));
        Map<String, Object> parsed = new LinkedHashMap<>();
        parsed.put("recipes", List.of(
                Map.of("recipeId", "10", "recipeName", "银耳羹", "matchReason", "a"),
                Map.of("recipeId", "10", "recipeName", "银耳羹", "matchReason", "b")));
        parsed.put("symptomSummary", "熬夜");
        Map<String, Object> out = merger.mergeAndValidate(parsed, "熬夜", "阴虚质", pool, false, false);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> recipes = (List<Map<String, Object>>) out.get("recipes");
        long id10 = recipes.stream().filter(m -> "10".equals(String.valueOf(m.get("recipeId")))).count();
        assertEquals(1, id10);
        assertTrue(recipes.size() >= 1);
    }

    private static Recipe recipe(Long id, String name) {
        Recipe r = new Recipe();
        r.setId(id);
        r.setName(name);
        r.setStatus(1);
        r.setCollectCount(5);
        r.setEfficacySummary("参考");
        return r;
    }
}
