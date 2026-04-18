package com.campus.diet.service.ai;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.diet.entity.Recipe;
import com.campus.diet.mapper.RecipeMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI 食疗方案：从库中加载用于 LLM 目录与本地兜底的菜谱池（单一查询入口，避免编排层重复）。
 */
@Component
public class AiTherapyPlanRecipePoolLoader {

    static final int DEFAULT_POOL_LIMIT = 60;

    private final RecipeMapper recipeMapper;

    public AiTherapyPlanRecipePoolLoader(RecipeMapper recipeMapper) {
        this.recipeMapper = recipeMapper;
    }

    public List<Recipe> loadActivePoolOrderedByCollectDesc() {
        return recipeMapper.selectList(
                Wrappers.<Recipe>lambdaQuery()
                        .eq(Recipe::getStatus, 1)
                        .orderByDesc(Recipe::getCollectCount)
                        .last("LIMIT " + DEFAULT_POOL_LIMIT));
    }
}
