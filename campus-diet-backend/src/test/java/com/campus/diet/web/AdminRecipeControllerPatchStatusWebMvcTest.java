package com.campus.diet.web;

import com.campus.diet.common.GlobalExceptionHandler;
import com.campus.diet.controller.admin.AdminRecipeController;
import com.campus.diet.entity.Recipe;
import com.campus.diet.mapper.RecipeMapper;
import com.campus.diet.mapper.SceneRecipeMapper;
import com.campus.diet.security.LoginUser;
import com.campus.diet.security.LoginUserHolder;
import com.campus.diet.security.Roles;
import com.campus.diet.service.RuntimeMetricService;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code PATCH /api/admin/recipes/{id}/status} 的轻量切片契约：独立 {@link SpringBootApplication}（仅扫描 {@link WebMvcSliceMarker}），
 * 不拉起 {@code CampusDietApplication}。注意 {@code @MapperScan(basePackageClasses = RecipeMapper.class)} 会扫描整个
 * {@code com.campus.diet.mapper} 包；若再用 {@code @MockBean} 替换 {@link RecipeMapper}，则真实 Mapper 不会注册，
 * MyBatis-Plus 无法建立 Recipe 的 lambda 列缓存（{@code Wrappers#lambdaUpdate} 在控制器内会失败）。此处改为仅对
 * {@link RecipeMapper} 使用显式 {@link MapperFactoryBean} + 内存 H2 真表；{@link SceneRecipeMapper} 仍 {@link MockBean}（本接口不依赖）。
 */
@SpringBootTest(
        classes = AdminRecipeControllerPatchStatusWebMvcTest.SliceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("admin-recipe-patch-contract")
@Transactional
class AdminRecipeControllerPatchStatusWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecipeMapper recipeMapper;

    @MockBean
    private SceneRecipeMapper sceneRecipeMapper;

    @MockBean
    private RuntimeMetricService runtimeMetricService;

    private Long seededRecipeId;

    @BeforeEach
    void seedRecipe() {
        Recipe seed = new Recipe();
        seed.setName("契约行");
        seed.setStatus(1);
        seed.setCollectCount(0);
        recipeMapper.insert(seed);
        seededRecipeId = seed.getId();
    }

    @AfterEach
    void tearDown() {
        LoginUserHolder.clear();
    }

    @Test
    void patchStatus_whenContentManager_shouldReturnUpdatedRecipeOnData() throws Exception {
        LoginUserHolder.set(new LoginUser(1L, "cm", Roles.CANTEEN_MANAGER));

        mockMvc.perform(
                        patch("/api/admin/recipes/" + seededRecipeId + "/status")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(seededRecipeId))
                .andExpect(jsonPath("$.data.name").value("契约行"))
                .andExpect(jsonPath("$.data.status").value(0));
    }

    @Test
    void patchStatus_whenNotLoggedIn_shouldReturn401ApiResponse() throws Exception {
        mockMvc.perform(
                        patch("/api/admin/recipes/999/status")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":1}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @SpringBootApplication(scanBasePackageClasses = WebMvcSliceMarker.class)
    @Import({AdminRecipeController.class, GlobalExceptionHandler.class, SliceMapperFactoriesConfig.class})
    static class SliceApplication {}

    @Configuration
    static class SliceMapperFactoriesConfig {
        @Bean
        public MapperFactoryBean<RecipeMapper> recipeMapper(SqlSessionFactory sqlSessionFactory) {
            MapperFactoryBean<RecipeMapper> factoryBean = new MapperFactoryBean<>(RecipeMapper.class);
            factoryBean.setSqlSessionFactory(sqlSessionFactory);
            return factoryBean;
        }
    }
}
