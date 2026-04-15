package com.campus.diet.controller;

import com.campus.diet.common.GlobalExceptionHandler;
import com.campus.diet.entity.Recipe;
import com.campus.diet.mapper.RecipeMapper;
import com.campus.diet.security.LoginUser;
import com.campus.diet.security.LoginUserHolder;
import com.campus.diet.security.Roles;
import com.campus.diet.service.FavoriteService;
import com.campus.diet.service.RuntimeMetricService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 公开药膳详情 API 契约样例（对齐 docs/api-contract.md 与前端菜谱详情页）。
 */
@ExtendWith(MockitoExtension.class)
class RecipePublicControllerApiContractTest {

    @Mock
    private RecipeMapper recipeMapper;

    @Mock
    private FavoriteService favoriteService;

    @Mock
    private RuntimeMetricService runtimeMetricService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new RecipePublicController(recipeMapper, objectMapper, favoriteService))
                        .setControllerAdvice(new GlobalExceptionHandler(runtimeMetricService))
                        .build();
    }

    @AfterEach
    void tearDown() {
        LoginUserHolder.clear();
    }

    @Test
    void detail_shouldReturnApiResponseWithCoreFields() throws Exception {
        Recipe r = new Recipe();
        r.setId(101L);
        r.setName("契约示例药膳");
        r.setStatus(1);
        r.setCollectCount(12);
        r.setCoverUrl("https://example.invalid/cover.jpg");
        r.setEfficacySummary("示例功效摘要");
        r.setInstructionSummary("简介");
        r.setStepsJson(null);
        r.setContraindication("孕妇慎用");
        r.setSeasonTags("spring");
        r.setConstitutionTags("qixu");
        when(recipeMapper.selectById(101L)).thenReturn(r);

        mockMvc.perform(get("/api/recipes/101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("101"))
                .andExpect(jsonPath("$.data.name").value("契约示例药膳"))
                .andExpect(jsonPath("$.data.collectCount").value(12))
                .andExpect(jsonPath("$.data.efficacySummary").value("示例功效摘要"));
    }

    @Test
    void detail_whenLoggedIn_shouldIncludeFavoritedFlag() throws Exception {
        Recipe r = new Recipe();
        r.setId(102L);
        r.setName("已登录收藏字段");
        r.setStatus(1);
        r.setCollectCount(0);
        when(recipeMapper.selectById(102L)).thenReturn(r);
        when(favoriteService.isFavorite(eq(7L), eq(102L))).thenReturn(true);

        LoginUserHolder.set(new LoginUser(7L, "u1", Roles.USER));

        mockMvc.perform(get("/api/recipes/102"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.favorited").value(true));
    }

    @Test
    void detail_invalidId_shouldReturn400ApiResponse() throws Exception {
        mockMvc.perform(get("/api/recipes/not-numeric"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }
}
