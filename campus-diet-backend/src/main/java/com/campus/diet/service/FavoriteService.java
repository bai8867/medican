package com.campus.diet.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.diet.common.BizException;
import com.campus.diet.common.PageResult;
import com.campus.diet.entity.Recipe;
import com.campus.diet.entity.UserFavorite;
import com.campus.diet.mapper.RecipeMapper;
import com.campus.diet.mapper.UserFavoriteMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FavoriteService {

    private final UserFavoriteMapper userFavoriteMapper;
    private final RecipeMapper recipeMapper;

    public FavoriteService(UserFavoriteMapper userFavoriteMapper, RecipeMapper recipeMapper) {
        this.userFavoriteMapper = userFavoriteMapper;
        this.recipeMapper = recipeMapper;
    }

    @Transactional
    public void add(long userId, long recipeId) {
        Recipe r = recipeMapper.selectById(recipeId);
        if (r == null) {
            throw new BizException(404, "药膳不存在");
        }
        Long exists = userFavoriteMapper.selectCount(
                Wrappers.<UserFavorite>lambdaQuery().eq(UserFavorite::getUserId, userId).eq(UserFavorite::getRecipeId, recipeId));
        if (exists != null && exists > 0) {
            return;
        }
        UserFavorite f = new UserFavorite();
        f.setUserId(userId);
        f.setRecipeId(recipeId);
        userFavoriteMapper.insert(f);
        recipeMapper.update(
                null,
                Wrappers.<Recipe>lambdaUpdate()
                        .eq(Recipe::getId, recipeId)
                        .setSql("collect_count = collect_count + 1"));
    }

    @Transactional
    public void remove(long userId, long recipeId) {
        int rows = userFavoriteMapper.delete(
                Wrappers.<UserFavorite>lambdaQuery().eq(UserFavorite::getUserId, userId).eq(UserFavorite::getRecipeId, recipeId));
        if (rows > 0) {
            recipeMapper.update(
                    null,
                    Wrappers.<Recipe>lambdaUpdate()
                            .eq(Recipe::getId, recipeId)
                            .setSql("collect_count = GREATEST(collect_count - 1, 0)"));
        }
    }

    public PageResult<Recipe> page(long userId, int page, int pageSize) {
        List<UserFavorite> favs = userFavoriteMapper.selectList(
                Wrappers.<UserFavorite>lambdaQuery()
                        .eq(UserFavorite::getUserId, userId)
                        .orderByDesc(UserFavorite::getCreatedAt));
        int total = favs.size();
        int from = Math.max(0, (page - 1) * pageSize);
        List<UserFavorite> slice = from >= total ? List.of() : favs.subList(from, Math.min(total, from + pageSize));
        List<Long> recipeIds = slice.stream()
                .map(UserFavorite::getRecipeId)
                .collect(Collectors.toList());
        Map<Long, Recipe> byId = recipeIds.isEmpty()
                ? Map.of()
                : recipeMapper.selectBatchIds(recipeIds).stream()
                .collect(Collectors.toMap(Recipe::getId, Function.identity(), (a, b) -> a));
        List<Recipe> records = recipeIds.stream()
                .map(byId::get)
                .filter(r -> r != null)
                .collect(Collectors.toList());
        return new PageResult<>(records, total, page, pageSize, from + pageSize < total);
    }

    public boolean isFavorite(long userId, long recipeId) {
        Long c = userFavoriteMapper.selectCount(
                Wrappers.<UserFavorite>lambdaQuery().eq(UserFavorite::getUserId, userId).eq(UserFavorite::getRecipeId, recipeId));
        return c != null && c > 0;
    }
}
