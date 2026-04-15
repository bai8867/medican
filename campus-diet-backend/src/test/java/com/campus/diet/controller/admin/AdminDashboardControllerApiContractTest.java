package com.campus.diet.controller.admin;

import com.campus.diet.common.GlobalExceptionHandler;
import com.campus.diet.entity.Recipe;
import com.campus.diet.mapper.RecipeMapper;
import com.campus.diet.mapper.SysUserMapper;
import com.campus.diet.mapper.UserProfileMapper;
import com.campus.diet.security.LoginUser;
import com.campus.diet.security.LoginUserHolder;
import com.campus.diet.security.Roles;
import com.campus.diet.service.RecipeRecommendService;
import com.campus.diet.service.RuntimeMetricService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.oneOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理端看板契约（对齐 docs/api-contract.md；需内容管理员角色）。
 */
@ExtendWith(MockitoExtension.class)
class AdminDashboardControllerApiContractTest {

    @Mock
    private RecipeMapper recipeMapper;
    @Mock
    private RecipeRecommendService recipeRecommendService;
    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private UserProfileMapper userProfileMapper;
    @Mock
    private RuntimeMetricService runtimeMetricService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AdminDashboardController controller =
                new AdminDashboardController(
                        recipeMapper,
                        recipeRecommendService,
                        sysUserMapper,
                        userProfileMapper,
                        runtimeMetricService);
        mockMvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(new GlobalExceptionHandler(runtimeMetricService))
                        .addFilters(new TestLoginUserFilter())
                        .build();
    }

    @Test
    void observabilitySummary_shouldReturn401WhenNotLoggedIn() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard/observability-summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void observabilitySummary_shouldReturn403WhenRoleNotContentManager() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard/observability-summary").header("X-Test-Role", Roles.USER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void observabilitySummary_shouldReturnDerivedFieldsForAdmin() throws Exception {
        Map<String, Object> snap = new LinkedHashMap<>();
        Map<String, Long> counters = new LinkedHashMap<>();
        counters.put("http.request.total", 10L);
        counters.put("http.request.error.none", 8L);
        counters.put("http.request.error.server", 1L);
        counters.put("http.request.error.client", 1L);
        counters.put("recommend.feed.page_nonempty", 3L);
        counters.put("recommend.feed.page_empty", 1L);
        counters.put("ai.generate.upstream.success", 2L);
        counters.put("ai.generate.upstream.failed", 0L);
        counters.put("ai.diet.upstream.success", 1L);
        counters.put("ai.diet.upstream.failed", 0L);
        snap.put("counters", counters);
        Map<String, Long> avg = new LinkedHashMap<>();
        avg.put("http.request.cost", 15L);
        avg.put("recommend.request", 20L);
        avg.put("ai.generate", 1200L);
        snap.put("avgCostMs", avg);
        Map<String, Long> max = new LinkedHashMap<>();
        max.put("http.request.cost", 80L);
        max.put("recommend.request", 45L);
        max.put("ai.generate", 3500L);
        snap.put("maxCostMs", max);
        when(runtimeMetricService.snapshot()).thenReturn(snap);

        mockMvc.perform(get("/api/admin/dashboard/observability-summary").header("X-Test-Role", Roles.ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data['http.request.total']").value(10))
                .andExpect(jsonPath("$.data['http.error_classified']").value(2))
                .andExpect(jsonPath("$.data['recommend.feed.nonempty_ratio']").value(0.75))
                .andExpect(jsonPath("$.data['ai.therapy.upstream_ok_ratio']").value(1.0))
                .andExpect(jsonPath("$.data['ai.diet.upstream_ok_ratio']").value(1.0))
                .andExpect(jsonPath("$.data['http.avg_cost_ms']").value(15))
                .andExpect(jsonPath("$.data['http.max_cost_ms']").value(80));
    }

    @Test
    void observabilitySummary_shouldSucceedForCanteenManager() throws Exception {
        when(runtimeMetricService.snapshot()).thenReturn(Map.of("counters", Map.of(), "avgCostMs", Map.of(), "maxCostMs", Map.of()));

        mockMvc.perform(
                        get("/api/admin/dashboard/observability-summary")
                                .header("X-Test-Role", Roles.CANTEEN_MANAGER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data['http.request.total']").value(0));
    }

    @Test
    void dashboard_shouldReturn401WhenNotLoggedIn() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void dashboard_shouldReturn403WhenRoleNotContentManager() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard").header("X-Test-Role", Roles.USER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void overview_shouldReturn401WhenNotLoggedIn() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard/overview"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void overview_shouldReturn403WhenRoleNotContentManager() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard/overview").header("X-Test-Role", Roles.USER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void overview_shouldReturnAggregateShapeForAdmin() throws Exception {
        Map<String, Object> sumRow = new HashMap<>();
        sumRow.put("totalCollect", 100L);
        when(recipeMapper.selectMaps(any())).thenReturn(List.of(sumRow));

        Recipe top = new Recipe();
        top.setId(9L);
        top.setName("契约样例药膳");
        top.setCollectCount(5);
        when(recipeMapper.selectList(any())).thenReturn(List.of(top));

        when(sysUserMapper.selectCount(any())).thenReturn(12L);

        mockMvc.perform(get("/api/admin/dashboard/overview").header("X-Test-Role", Roles.ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalCollectCount").value(100))
                .andExpect(jsonPath("$.data.hotTop3[0].id").value(9))
                .andExpect(jsonPath("$.data.hotTop3[0].name").value("契约样例药膳"))
                .andExpect(jsonPath("$.data.hotTop3[0].collectCount").value(5))
                .andExpect(jsonPath("$.data.userTotal").value(12));
    }

    @Test
    void dashboard_shouldReturnSeasonalBundleShapeForAdmin() throws Exception {
        Recipe collected = new Recipe();
        collected.setId(11L);
        collected.setName("收藏榜样例");
        when(recipeMapper.selectList(any())).thenReturn(List.of(collected));

        Recipe seasonal = new Recipe();
        seasonal.setId(22L);
        seasonal.setName("应季样例");
        when(recipeRecommendService.recommendFeed(eq(1), eq(10), isNull(), eq(""), eq(false), anyString(), isNull()))
                .thenReturn(List.of(seasonal));

        mockMvc.perform(get("/api/admin/dashboard").header("X-Test-Role", Roles.ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.seasonCode").value(oneOf("spring", "summer", "autumn", "winter")))
                .andExpect(jsonPath("$.data.topCollected[0].id").value(11))
                .andExpect(jsonPath("$.data.topCollected[0].name").value("收藏榜样例"))
                .andExpect(jsonPath("$.data.seasonalPicks[0].id").value(22))
                .andExpect(jsonPath("$.data.seasonalPicks[0].name").value("应季样例"));
    }

    @Test
    void runtimeMetrics_shouldReturn401WhenNotLoggedIn() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard/runtime-metrics"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void runtimeMetrics_shouldReturn403WhenRoleNotContentManager() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard/runtime-metrics").header("X-Test-Role", Roles.USER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void constitutionDistribution_shouldReturn401WhenNotLoggedIn() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard/constitution-distribution"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void constitutionDistribution_shouldReturn403WhenRoleNotContentManager() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard/constitution-distribution").header("X-Test-Role", Roles.USER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void constitutionDistribution_shouldReturnEmptyItemsWhenNoRows() throws Exception {
        when(userProfileMapper.selectMaps(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/dashboard/constitution-distribution").header("X-Test-Role", Roles.ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void runtimeMetrics_shouldReturnSnapshotShapeForAdmin() throws Exception {
        Map<String, Object> snap = new LinkedHashMap<>();
        Map<String, Long> counters = new LinkedHashMap<>();
        counters.put("http.request.total", 2L);
        snap.put("counters", counters);
        snap.put("avgCostMs", Map.of("http.request.cost", 5L));
        snap.put("maxCostMs", Map.of("http.request.cost", 9L));
        when(runtimeMetricService.snapshot()).thenReturn(snap);

        mockMvc.perform(get("/api/admin/dashboard/runtime-metrics").header("X-Test-Role", Roles.ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data['counters']['http.request.total']").value(2))
                .andExpect(jsonPath("$.data['avgCostMs']['http.request.cost']").value(5))
                .andExpect(jsonPath("$.data['maxCostMs']['http.request.cost']").value(9));
    }

    @Test
    void constitutionDistribution_shouldReturnPieItemsForAdmin() throws Exception {
        Map<String, Object> row = new HashMap<>();
        row.put("constitutionCode", "qixu");
        row.put("cnt", 4);
        when(userProfileMapper.selectMaps(any())).thenReturn(List.of(row));

        mockMvc.perform(get("/api/admin/dashboard/constitution-distribution").header("X-Test-Role", Roles.ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items[0].name").value("气虚质"))
                .andExpect(jsonPath("$.data.items[0].value").value(4));
    }

    private static final class TestLoginUserFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain filterChain) throws ServletException, IOException {
            String role = request.getHeader("X-Test-Role");
            try {
                if (role != null && !role.isBlank()) {
                    LoginUserHolder.set(new LoginUser(1L, "test", role));
                }
                filterChain.doFilter(request, response);
            } finally {
                LoginUserHolder.clear();
            }
        }
    }
}
