package com.campus.diet.service.ai;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.campus.diet.entity.Recipe;
import com.campus.diet.mapper.RecipeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiTherapyPlanRecipePoolLoaderTest {

    @Mock
    private RecipeMapper recipeMapper;

    @Test
    void loadActivePoolOrderedByCollectDesc_delegatesToMapper() {
        Recipe r = new Recipe();
        r.setId(1L);
        when(recipeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(r));

        List<Recipe> out = new AiTherapyPlanRecipePoolLoader(recipeMapper).loadActivePoolOrderedByCollectDesc();

        assertEquals(1, out.size());
        verify(recipeMapper).selectList(any(Wrapper.class));
    }
}
