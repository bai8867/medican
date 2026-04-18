package com.campus.diet.service;

import com.campus.diet.common.PageResult;
import com.campus.diet.entity.Recipe;
import com.campus.diet.entity.UserFavorite;
import com.campus.diet.mapper.BrowseHistoryMapper;
import com.campus.diet.mapper.RecipeMapper;
import com.campus.diet.mapper.UserFavoriteMapper;
import com.campus.diet.security.LoginUser;
import com.campus.diet.security.LoginUserHolder;
import com.campus.diet.security.Roles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipeRecommendServiceTest {

    @Mock
    private RecipeMapper recipeMapper;
    @Mock
    private SystemKvService systemKvService;
    @Mock
    private UserFavoriteMapper userFavoriteMapper;
    @Mock
    private BrowseHistoryMapper browseHistoryMapper;

    @AfterEach
    void tearDown() {
        LoginUserHolder.clear();
    }

    @Test
    void recommendFeedPage_shouldSortByMatchingThenPopularity() {
        RuntimeMetricService runtimeMetricService = new RuntimeMetricService();
        RecipeRecommendService service = new RecipeRecommendService(
                recipeMapper,
                systemKvService,
                runtimeMetricService,
                userFavoriteMapper,
                browseHistoryMapper,
                0.7,
                0.3,
                0.1,
                240,
                "rules");
        when(systemKvService.flagOn("recommend.global.enabled", true)).thenReturn(true);

        Recipe highMatchLowPop = recipe(1L, 10, "spring", "qixu");
        Recipe lowMatchHighPop = recipe(2L, 999, "winter", "yangxu");
        when(recipeMapper.listRecommendCandidates(nullable(String.class), nullable(String.class), anyInt()))
                .thenReturn(List.of(lowMatchHighPop, highMatchLowPop));

        PageResult<Recipe> result = service.recommendFeedPage(1, 10, null, "qixu", true, "spring", null);

        assertEquals(2, result.getTotal());
        assertEquals(2, result.getRecords().size());
        assertEquals(1L, result.getRecords().get(0).getId());
        assertTrue(counter(runtimeMetricService, "recommend.request.total") >= 1L);
    }

    @Test
    void recommendFeedPage_shouldReturnEmptyWhenGlobalSwitchIsOff() {
        RuntimeMetricService runtimeMetricService = new RuntimeMetricService();
        RecipeRecommendService service = new RecipeRecommendService(
                recipeMapper,
                systemKvService,
                runtimeMetricService,
                userFavoriteMapper,
                browseHistoryMapper,
                0.7,
                0.3,
                0.1,
                240,
                "rules");
        when(systemKvService.flagOn("recommend.global.enabled", true)).thenReturn(false);

        PageResult<Recipe> result = service.recommendFeedPage(1, 10, null, "", false, "spring", null);

        assertEquals(0, result.getTotal());
        assertEquals(0, result.getRecords().size());
        assertEquals(1L, counter(runtimeMetricService, "recommend.request.disabled"));
    }

    /**
     * hybrid：行为分来自「标签兴趣画像」，与收藏源菜谱标签重叠的候选应排在更前。
     */
    @Test
    void recommendFeedPage_hybrid_shouldBoostByTagOverlapNotSameRecipeId() {
        RuntimeMetricService runtimeMetricService = new RuntimeMetricService();
        RecipeRecommendService service = new RecipeRecommendService(
                recipeMapper,
                systemKvService,
                runtimeMetricService,
                userFavoriteMapper,
                browseHistoryMapper,
                0.5,
                0.3,
                0.5,
                240,
                "hybrid");
        when(systemKvService.flagOn("recommend.global.enabled", true)).thenReturn(true);
        LoginUserHolder.set(new LoginUser(1L, "u", Roles.USER));

        UserFavorite fav = new UserFavorite();
        fav.setUserId(1L);
        fav.setRecipeId(100L);
        when(userFavoriteMapper.selectList(any())).thenReturn(List.of(fav));
        when(browseHistoryMapper.selectList(any())).thenReturn(List.of());

        Recipe source = recipeTagged(100L, 50, "spring", "qixu", "补气,健脾");
        when(recipeMapper.selectBatchIds(any())).thenReturn(List.of(source));

        Recipe overlapTags = recipeTagged(1L, 50, "spring", "qixu", "补气,安神");
        Recipe noOverlap = recipeTagged(2L, 50, "spring", "qixu", "清热,利湿");
        when(recipeMapper.listRecommendCandidates(nullable(String.class), nullable(String.class), anyInt()))
                .thenReturn(List.of(noOverlap, overlapTags));

        PageResult<Recipe> result = service.recommendFeedPage(1, 10, null, "qixu", true, "spring", null);

        assertEquals(2, result.getTotal());
        assertEquals(1L, result.getRecords().get(0).getId(), "应优先推荐与收藏菜谱共享功效标签的候选");
    }

    private static Recipe recipe(Long id, int collectCount, String seasonTags, String constitutionTags) {
        Recipe recipe = new Recipe();
        recipe.setId(id);
        recipe.setName("recipe-" + id);
        recipe.setCollectCount(collectCount);
        recipe.setSeasonTags(seasonTags);
        recipe.setConstitutionTags(constitutionTags);
        recipe.setStatus(1);
        return recipe;
    }

    private static Recipe recipeTagged(
            Long id, int collectCount, String seasonTags, String constitutionTags, String efficacyTags) {
        Recipe recipe = recipe(id, collectCount, seasonTags, constitutionTags);
        recipe.setEfficacyTags(efficacyTags);
        return recipe;
    }

    private static long counter(RuntimeMetricService runtimeMetricService, String key) {
        Object counters = runtimeMetricService.snapshot().get("counters");
        if (!(counters instanceof Map<?, ?>)) {
            return 0L;
        }
        Object value = ((Map<?, ?>) counters).get(key);
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }
}
