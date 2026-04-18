package com.campus.diet.service;

import com.campus.diet.entity.Recipe;
import com.campus.diet.mapper.RecipeMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CampusWeeklyCalendarRecipeEnricherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void enrichDaysDishesFromRecipeTable_shouldEnrichPublishedRecipeAndMapLegacyId() throws Exception {
        RecipeMapper recipeMapper = mock(RecipeMapper.class);
        CampusWeeklyCalendarRecipeEnricher enricher =
                new CampusWeeklyCalendarRecipeEnricher(recipeMapper, objectMapper);

        Recipe active = new Recipe();
        active.setId(2L);
        active.setName("黄芪山药粥");
        active.setStatus(1);
        active.setContraindication("阴虚火旺慎食");
        active.setConstitutionTags("qixu,qiyu");

        Recipe inactive = new Recipe();
        inactive.setId(3L);
        inactive.setName("状态关闭菜品");
        inactive.setStatus(0);

        when(recipeMapper.selectBatchIds(anyCollection())).thenReturn(List.of(active, inactive));

        ArrayNode days = (ArrayNode) objectMapper.readTree(
                "[{\"date\":\"2026-04-13\",\"meals\":{\"lunch\":[{\"id\":\"lc-1\",\"recipeId\":\"demo-001\"},{\"id\":\"lc-2\",\"recipeId\":\"3\"}],\"breakfast\":[],\"dinner\":[],\"midnightSnack\":[]}}]");

        enricher.enrichDaysDishesFromRecipeTable(days);

        assertEquals("2", days.at("/0/meals/lunch/0/recipeId").asText());
        assertEquals("黄芪山药粥", days.at("/0/meals/lunch/0/name").asText());
        assertEquals("阴虚火旺慎食", days.at("/0/meals/lunch/0/contraindicationNote").asText());
        assertEquals("气虚质", days.at("/0/meals/lunch/0/suitConstitutionLabels/0").asText());
        assertEquals("气郁质", days.at("/0/meals/lunch/0/suitConstitutionLabels/1").asText());

        assertEquals("3", days.at("/0/meals/lunch/1/recipeId").asText());
        assertNull(days.at("/0/meals/lunch/1/name").textValue());
    }

    @Test
    void enrichDaysDishesFromRecipeTable_shouldSkipMapperWhenNoValidRecipeId() throws Exception {
        RecipeMapper recipeMapper = mock(RecipeMapper.class);
        CampusWeeklyCalendarRecipeEnricher enricher =
                new CampusWeeklyCalendarRecipeEnricher(recipeMapper, objectMapper);

        ArrayNode days = (ArrayNode) objectMapper.readTree(
                "[{\"date\":\"2026-04-14\",\"meals\":{\"lunch\":[{\"id\":\"lc-1\",\"recipeId\":\"bad-id\"}],\"breakfast\":[],\"dinner\":[],\"midnightSnack\":[]}}]");

        enricher.enrichDaysDishesFromRecipeTable(days);

        verify(recipeMapper, never()).selectBatchIds(anyCollection());
        assertFalse(days.at("/0/meals/lunch/0").has("name"));
        assertTrue(days.at("/0/meals/lunch/0").has("recipeId"));
    }
}
