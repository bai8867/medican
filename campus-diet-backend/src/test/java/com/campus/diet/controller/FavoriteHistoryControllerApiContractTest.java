package com.campus.diet.controller;

import com.campus.diet.common.GlobalExceptionHandler;
import com.campus.diet.common.PageResult;
import com.campus.diet.entity.Recipe;
import com.campus.diet.security.LoginUser;
import com.campus.diet.security.LoginUserHolder;
import com.campus.diet.security.Roles;
import com.campus.diet.service.BrowseHistoryService;
import com.campus.diet.service.FavoriteService;
import com.campus.diet.service.RuntimeMetricService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 收藏 / 浏览历史 / 推荐反馈等 {@code /api/user/*} 契约样例（对齐 docs/api-contract.md 与前端收藏页）。
 */
@ExtendWith(MockitoExtension.class)
class FavoriteHistoryControllerApiContractTest {

    @Mock
    private FavoriteService favoriteService;

    @Mock
    private BrowseHistoryService browseHistoryService;

    @Mock
    private RuntimeMetricService runtimeMetricService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new FavoriteHistoryController(
                                        favoriteService, browseHistoryService, runtimeMetricService))
                        .setControllerAdvice(new GlobalExceptionHandler(runtimeMetricService))
                        .build();
    }

    @AfterEach
    void tearDown() {
        LoginUserHolder.clear();
    }

    @Test
    void listFavorites_whenNotLoggedIn_shouldReturn401ApiResponse() throws Exception {
        mockMvc.perform(get("/api/user/favorites"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void listFavorites_whenLoggedIn_shouldReturnPageResultWithRecipeCards() throws Exception {
        LoginUserHolder.set(new LoginUser(42L, "demo", Roles.USER));

        Recipe r = new Recipe();
        r.setId(501L);
        r.setName("契约收藏药膳");
        r.setCoverUrl("https://example.invalid/c.jpg");
        r.setCollectCount(3);
        r.setEfficacySummary("润肺");
        r.setSeasonTags("spring");
        r.setConstitutionTags("qixu");
        r.setEfficacyTags("runfei");
        when(favoriteService.page(eq(42L), eq(1), eq(10)))
                .thenReturn(new PageResult<>(List.of(r), 1, 1, 10, false));

        mockMvc.perform(get("/api/user/favorites").param("page", "1").param("page_size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.hasMore").value(false))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.records[0].id").value("501"))
                .andExpect(jsonPath("$.data.records[0].name").value("契约收藏药膳"))
                .andExpect(jsonPath("$.data.records[0].collectCount").value(3))
                .andExpect(jsonPath("$.data.records[0].seasonFit[0]").value("spring"));
    }

    @Test
    void listHistory_whenNotLoggedIn_shouldReturn401ApiResponse() throws Exception {
        mockMvc.perform(get("/api/user/history"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void listHistory_whenLoggedIn_shouldReturnRecordsEnvelope() throws Exception {
        LoginUserHolder.set(new LoginUser(43L, "hist", Roles.USER));

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("historyId", "9001");
        row.put("viewedAt", 1_710_000_000_000L);
        row.put("recipeId", "201");
        row.put("name", "契约浏览药膳");
        row.put("coverUrl", "");
        row.put("efficacySummary", "示例摘要");
        List<Map<String, Object>> records = new ArrayList<>();
        records.add(row);
        when(browseHistoryService.listHistoryRecords(eq(43L), eq(10))).thenReturn(records);

        mockMvc.perform(get("/api/user/history").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.records[0].historyId").value("9001"))
                .andExpect(jsonPath("$.data.records[0].recipeId").value("201"))
                .andExpect(jsonPath("$.data.records[0].name").value("契约浏览药膳"))
                .andExpect(jsonPath("$.data.records[0].viewedAt").value(1_710_000_000_000L))
                .andExpect(jsonPath("$.data.records[0].efficacySummary").value("示例摘要"));
    }

    @Test
    void addFavorite_whenNotLoggedIn_shouldReturn401ApiResponse() throws Exception {
        mockMvc.perform(
                        post("/api/user/favorites")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"recipeId\":501}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void addFavorite_whenLoggedIn_shouldReturnOkEnvelope() throws Exception {
        LoginUserHolder.set(new LoginUser(42L, "demo", Roles.USER));

        mockMvc.perform(
                        post("/api/user/favorites")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"recipeId\":501}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.ok").value(true));

        verify(favoriteService).add(eq(42L), eq(501L));
    }

    @Test
    void recommendFeedback_whenLoggedIn_shouldReturnOkAndIncrementMetric() throws Exception {
        LoginUserHolder.set(new LoginUser(42L, "demo", Roles.USER));

        mockMvc.perform(
                        post("/api/user/recommend-feedback")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"event\":\"dismiss\",\"recipeId\":\"1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.ok").value(true));

        verify(runtimeMetricService).increment("recommend.feedback.dismiss");
    }

    @Test
    void removeFavorite_whenLoggedIn_shouldReturnOkAndCallService() throws Exception {
        LoginUserHolder.set(new LoginUser(42L, "demo", Roles.USER));

        mockMvc.perform(delete("/api/user/favorites/501"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.ok").value(true));

        verify(favoriteService).remove(eq(42L), eq(501L));
    }

    @Test
    void addHistory_whenLoggedIn_shouldReturnOkAndCallService() throws Exception {
        LoginUserHolder.set(new LoginUser(42L, "demo", Roles.USER));

        mockMvc.perform(
                        post("/api/user/history")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"recipeId\":88}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.ok").value(true));

        verify(browseHistoryService).record(eq(42L), eq(88L));
    }

    @Test
    void deleteHistory_whenInvalidId_shouldReturn400ApiResponse() throws Exception {
        LoginUserHolder.set(new LoginUser(42L, "demo", Roles.USER));

        mockMvc.perform(delete("/api/user/history/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void deleteHistoryRow_whenLoggedIn_shouldReturnOkAndCallService() throws Exception {
        LoginUserHolder.set(new LoginUser(42L, "demo", Roles.USER));

        mockMvc.perform(delete("/api/user/history/12005"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.ok").value(true));

        verify(browseHistoryService).deleteHistoryRow(eq(42L), eq(12005L));
    }

    @Test
    void clearHistory_whenLoggedIn_shouldReturnOkAndCallService() throws Exception {
        LoginUserHolder.set(new LoginUser(42L, "demo", Roles.USER));

        mockMvc.perform(delete("/api/user/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.ok").value(true));

        verify(browseHistoryService).clearHistory(eq(42L));
    }
}
