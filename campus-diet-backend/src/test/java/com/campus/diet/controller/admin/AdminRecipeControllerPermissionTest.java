package com.campus.diet.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.diet.common.GlobalExceptionHandler;
import com.campus.diet.entity.Recipe;
import com.campus.diet.mapper.RecipeMapper;
import com.campus.diet.mapper.SceneRecipeMapper;
import com.campus.diet.security.LoginUser;
import com.campus.diet.security.LoginUserHolder;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminRecipeControllerPermissionTest {

    @Mock
    private RecipeMapper recipeMapper;
    @Mock
    private SceneRecipeMapper sceneRecipeMapper;

    @Mock
    private RuntimeMetricService runtimeMetricService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AdminRecipeController controller = new AdminRecipeController(recipeMapper, sceneRecipeMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(runtimeMetricService))
                .addFilters(new TestLoginUserFilter())
                .build();
    }

    @Test
    void page_shouldReturn401WhenNotLoggedIn() throws Exception {
        mockMvc.perform(get("/api/admin/recipes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void page_shouldReturn403WhenUserRoleNotAllowed() throws Exception {
        mockMvc.perform(get("/api/admin/recipes").header("X-Test-Role", "USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void page_shouldSucceedForAdminRole() throws Exception {
        Page<Recipe> page = new Page<>(1, 10);
        page.setRecords(List.of(recipe(1L, "当归鸡汤")));
        page.setTotal(1L);
        when(recipeMapper.selectPage(any(Page.class), any())).thenReturn(page);

        mockMvc.perform(get("/api/admin/recipes").header("X-Test-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(1));
    }

    private static Recipe recipe(long id, String name) {
        Recipe recipe = new Recipe();
        recipe.setId(id);
        recipe.setName(name);
        return recipe;
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
