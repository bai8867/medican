package com.campus.diet.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.diet.common.GlobalExceptionHandler;
import com.campus.diet.entity.Recipe;
import com.campus.diet.mapper.RecipeMapper;
import com.campus.diet.mapper.SceneRecipeMapper;
import com.campus.diet.security.LoginUser;
import com.campus.diet.security.LoginUserHolder;
import com.campus.diet.security.Roles;
import com.campus.diet.service.RuntimeMetricService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理端药膳资源 API 契约（需 {@link Roles#canManageContent}；对齐 docs/api-contract.md {@code ApiResponse} 与实体字段）。
 */
@ExtendWith(MockitoExtension.class)
class AdminRecipeControllerApiContractTest {

    @Mock
    private RecipeMapper recipeMapper;

    @Mock
    private SceneRecipeMapper sceneRecipeMapper;

    @Mock
    private RuntimeMetricService runtimeMetricService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new AdminRecipeController(recipeMapper, sceneRecipeMapper))
                        .setControllerAdvice(new GlobalExceptionHandler(runtimeMetricService))
                        .build();
    }

    @AfterEach
    void tearDown() {
        LoginUserHolder.clear();
    }

    @Test
    void getOne_whenNotLoggedIn_shouldReturn401ApiResponse() throws Exception {
        mockMvc.perform(get("/api/admin/recipes/9"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void getOne_whenMissing_shouldReturn404ApiResponse() throws Exception {
        LoginUserHolder.set(new LoginUser(1L, "admin", Roles.ADMIN));
        when(recipeMapper.selectById(404L)).thenReturn(null);

        mockMvc.perform(get("/api/admin/recipes/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.msg").value("药膳不存在"));
    }

    @Test
    void getOne_whenFound_shouldReturnRecipeOnData() throws Exception {
        LoginUserHolder.set(new LoginUser(1L, "cm", Roles.CANTEEN_MANAGER));
        Recipe r = new Recipe();
        r.setId(9L);
        r.setName("契约药膳");
        r.setEfficacySummary("润肺");
        r.setCollectCount(12);
        r.setStatus(1);
        when(recipeMapper.selectById(9L)).thenReturn(r);

        mockMvc.perform(get("/api/admin/recipes/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(9))
                .andExpect(jsonPath("$.data.name").value("契约药膳"))
                .andExpect(jsonPath("$.data.efficacySummary").value("润肺"))
                .andExpect(jsonPath("$.data.collectCount").value(12))
                .andExpect(jsonPath("$.data.status").value(1));
    }

    @Test
    void page_whenAdmin_shouldReturnPageResultShape() throws Exception {
        LoginUserHolder.set(new LoginUser(1L, "admin", Roles.ADMIN));
        Recipe row = new Recipe();
        row.setId(100L);
        row.setName("分页契约药膳");
        row.setStatus(1);
        Page<Recipe> p = new Page<>(1, 10);
        p.setRecords(List.of(row));
        p.setTotal(1);
        when(recipeMapper.selectPage(any(Page.class), any())).thenReturn(p);

        mockMvc.perform(get("/api/admin/recipes").param("page", "1").param("page_size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.hasMore").value(false))
                .andExpect(jsonPath("$.data.records[0].id").value(100))
                .andExpect(jsonPath("$.data.records[0].name").value("分页契约药膳"));
    }

    @Test
    void delete_whenAdmin_shouldReturnOkAndUnlink() throws Exception {
        LoginUserHolder.set(new LoginUser(1L, "admin", Roles.ADMIN));

        mockMvc.perform(delete("/api/admin/recipes/55"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(sceneRecipeMapper).unlinkByRecipe(eq(55L));
        verify(recipeMapper).deleteById(eq(55L));
    }
}
