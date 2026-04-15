package com.campus.diet.controller;

import com.campus.diet.common.GlobalExceptionHandler;
import com.campus.diet.common.PageResult;
import com.campus.diet.dto.RecipeCardDto;
import com.campus.diet.entity.CampusScene;
import com.campus.diet.service.CampusSceneService;
import com.campus.diet.service.RecipeRecommendService;
import com.campus.diet.service.RuntimeMetricService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 核心校园场景 API 的可执行契约样例（对齐 docs/api-contract.md 中「推荐卡片 / 场景」流）。
 * 字段名变更时本测试应同步更新，避免前后端静默漂移。
 */
@ExtendWith(MockitoExtension.class)
class CampusSceneControllerApiContractTest {

    @Mock
    private CampusSceneService campusSceneService;

    @Mock
    private RecipeRecommendService recipeRecommendService;

    @Mock
    private RuntimeMetricService runtimeMetricService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new CampusSceneController(campusSceneService, recipeRecommendService))
                        .setControllerAdvice(new GlobalExceptionHandler(runtimeMetricService))
                        .build();
    }

    @Test
    void recommendFeed_shouldExposePagingShapeOnData() throws Exception {
        when(recipeRecommendService.recommendFeedCardPage(
                        eq(1),
                        eq(8),
                        isNull(),
                        eq(""),
                        eq(false),
                        isNull(),
                        isNull()))
                .thenReturn(new PageResult<>(List.of(), 0L, 1, 8, false));

        mockMvc.perform(get("/api/campus/recipes/recommend-feed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.hasMore").value(false));
    }

    @Test
    void recommendFeed_withPagingParams_shouldPassPageToService() throws Exception {
        RecipeCardDto card = new RecipeCardDto();
        card.setId("101");
        card.setName("测试药膳");
        when(recipeRecommendService.recommendFeedCardPage(
                        eq(2),
                        eq(4),
                        eq("\u63d0\u795e"),
                        eq("qixu"),
                        eq(true),
                        eq("spring"),
                        eq("\u7ca5")))
                .thenReturn(new PageResult<>(List.of(card), 99L, 2, 4, true));

        mockMvc.perform(
                        get("/api/campus/recipes/recommend-feed")
                                .param("page", "2")
                                .param("page_size", "4")
                                .param("sceneTag", "\u63d0\u795e")
                                .param("constitutionCode", "qixu")
                                .param("personalized", "true")
                                .param("recommend_enabled", "true")
                                .param("seasonCode", "spring")
                                .param("keyword", "\u7ca5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].id").value("101"))
                .andExpect(jsonPath("$.data.records[0].name").value("测试药膳"))
                .andExpect(jsonPath("$.data.total").value(99))
                .andExpect(jsonPath("$.data.hasMore").value(true));
    }

    @Test
    void scenes_shouldExposeListOnData() throws Exception {
        CampusScene scene = new CampusScene();
        scene.setId(7L);
        scene.setName("图书馆");
        scene.setIcon("lib");
        scene.setDescription("安静场景");
        scene.setTagsJson("[\"护眼\"]");
        when(campusSceneService.listWithCounts())
                .thenReturn(List.of(new CampusSceneService.SceneView(scene, 12)));

        mockMvc.perform(get("/api/campus/scenes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.list[0].id").value(7))
                .andExpect(jsonPath("$.data.list[0].name").value("图书馆"))
                .andExpect(jsonPath("$.data.list[0].recipeCount").value(12))
                .andExpect(jsonPath("$.data.list[0].tags[0]").value("护眼"));
    }

    @Test
    void sceneRecipes_shouldExposePageResultEnvelope() throws Exception {
        when(recipeRecommendService.pageSceneRecipes(eq(3L), eq(1), eq(10), eq("collect")))
                .thenReturn(new PageResult<>(List.of(), 0L, 1, 10, false));

        mockMvc.perform(get("/api/campus/scenes/recipes").param("scene_id", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.hasMore").value(false));
    }
}
