package com.campus.diet.controller;

import com.campus.diet.common.BizException;
import com.campus.diet.common.GlobalExceptionHandler;
import com.campus.diet.dto.RecipeCardDto;
import com.campus.diet.dto.SceneSolutionDto;
import com.campus.diet.dto.SceneTherapyListItemDto;
import com.campus.diet.service.RuntimeMetricService;
import com.campus.diet.service.SceneTherapyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/scenes} 独立食疗流契约样例（与 {@code /api/campus/scenes} 并存），对齐 docs/api-contract.md。
 */
@ExtendWith(MockitoExtension.class)
class SceneTherapyControllerApiContractTest {

    @Mock
    private SceneTherapyService sceneTherapyService;

    @Mock
    private RuntimeMetricService runtimeMetricService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new SceneTherapyController(sceneTherapyService))
                        .setControllerAdvice(new GlobalExceptionHandler(runtimeMetricService))
                        .build();
    }

    @Test
    void list_shouldExposeListOnData() throws Exception {
        SceneTherapyListItemDto row =
                new SceneTherapyListItemDto(
                        7L,
                        "图书馆",
                        "lib",
                        "安静场景",
                        "护眼一句话",
                        List.of("眼干"),
                        12,
                        List.of("护眼"));
        when(sceneTherapyService.listScenes()).thenReturn(List.of(row));

        mockMvc.perform(get("/api/scenes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.list[0].id").value(7))
                .andExpect(jsonPath("$.data.list[0].name").value("图书馆"))
                .andExpect(jsonPath("$.data.list[0].recipeCount").value(12))
                .andExpect(jsonPath("$.data.list[0].painTags[0]").value("眼干"))
                .andExpect(jsonPath("$.data.list[0].tags[0]").value("护眼"));
    }

    @Test
    void sceneRecipes_shouldExposeSolutionShapeOnData() throws Exception {
        SceneTherapyListItemDto header =
                new SceneTherapyListItemDto(
                        3L, "操场", "run", "运动场景", "动后调养", List.of("乏力"), 4, List.of("提神"));
        RecipeCardDto card = new RecipeCardDto();
        card.setId("101");
        card.setName("契约场景药膳");
        SceneSolutionDto dto =
                new SceneSolutionDto(
                        header,
                        List.of(card),
                        List.of(),
                        "食材提示",
                        List.of("生冷"));
        when(sceneTherapyService.getSceneSolution(eq(3L))).thenReturn(dto);

        mockMvc.perform(get("/api/scenes/3/recipes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.scene.id").value(3))
                .andExpect(jsonPath("$.data.scene.name").value("操场"))
                .andExpect(jsonPath("$.data.recipes[0].id").value("101"))
                .andExpect(jsonPath("$.data.recipes[0].name").value("契约场景药膳"))
                .andExpect(jsonPath("$.data.ingredientInsight").value("食材提示"))
                .andExpect(jsonPath("$.data.forbidden[0]").value("生冷"));
    }

    @Test
    void sceneRecipes_whenMissing_shouldReturn404ApiResponse() throws Exception {
        when(sceneTherapyService.getSceneSolution(eq(999L)))
                .thenThrow(new BizException(404, "场景不存在"));

        mockMvc.perform(get("/api/scenes/999/recipes"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.msg").value("场景不存在"));
    }
}
