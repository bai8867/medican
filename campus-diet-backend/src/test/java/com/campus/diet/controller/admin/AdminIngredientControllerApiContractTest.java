package com.campus.diet.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.diet.common.GlobalExceptionHandler;
import com.campus.diet.entity.Ingredient;
import com.campus.diet.mapper.IngredientMapper;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理端食材 CRUD API 契约（需 {@link Roles#canManageContent}：管理员或食堂负责人）。
 */
@ExtendWith(MockitoExtension.class)
class AdminIngredientControllerApiContractTest {

    @Mock
    private IngredientMapper ingredientMapper;

    @Mock
    private RuntimeMetricService runtimeMetricService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new AdminIngredientController(ingredientMapper))
                        .setControllerAdvice(new GlobalExceptionHandler(runtimeMetricService))
                        .build();
    }

    @AfterEach
    void tearDown() {
        LoginUserHolder.clear();
    }

    @Test
    void page_whenNotLoggedIn_shouldReturn401ApiResponse() throws Exception {
        mockMvc.perform(get("/api/admin/ingredients"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void page_whenPlainUser_shouldReturn403ApiResponse() throws Exception {
        LoginUserHolder.set(new LoginUser(9L, "u", Roles.USER));
        mockMvc.perform(get("/api/admin/ingredients"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void page_whenCanteenManager_shouldReturnPageResultOnData() throws Exception {
        LoginUserHolder.set(new LoginUser(3L, "cm", Roles.CANTEEN_MANAGER));
        Page<Ingredient> p = new Page<>(1, 10);
        p.setRecords(List.of());
        p.setTotal(0);
        when(ingredientMapper.selectPage(any(Page.class), any())).thenReturn(p);

        mockMvc.perform(get("/api/admin/ingredients").param("page", "1").param("page_size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.hasMore").value(false));
    }

    @Test
    void create_whenAdmin_shouldReturnIngredientOnData() throws Exception {
        LoginUserHolder.set(new LoginUser(1L, "admin", Roles.ADMIN));
        doAnswer(
                        invocation -> {
                            Ingredient arg = invocation.getArgument(0);
                            arg.setId(501L);
                            return 1;
                        })
                .when(ingredientMapper)
                .insert(any(Ingredient.class));
        Ingredient saved = new Ingredient();
        saved.setId(501L);
        saved.setName("契约食材");
        saved.setCategory("herb");
        when(ingredientMapper.selectById(501L)).thenReturn(saved);

        mockMvc.perform(
                        post("/api/admin/ingredients")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"契约食材\",\"category\":\"herb\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(501))
                .andExpect(jsonPath("$.data.name").value("契约食材"))
                .andExpect(jsonPath("$.data.category").value("herb"));

        verify(ingredientMapper).insert(any(Ingredient.class));
    }

    @Test
    void delete_whenAdmin_shouldReturnOkAndCallMapper() throws Exception {
        LoginUserHolder.set(new LoginUser(1L, "admin", Roles.ADMIN));

        mockMvc.perform(delete("/api/admin/ingredients/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(ingredientMapper).deleteById(eq(42L));
    }
}
