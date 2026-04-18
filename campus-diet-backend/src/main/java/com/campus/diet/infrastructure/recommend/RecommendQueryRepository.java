package com.campus.diet.infrastructure.recommend;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.diet.entity.BrowseHistory;
import com.campus.diet.entity.Recipe;
import com.campus.diet.entity.UserFavorite;
import com.campus.diet.mapper.BrowseHistoryMapper;
import com.campus.diet.mapper.RecipeMapper;
import com.campus.diet.mapper.UserFavoriteMapper;
import com.campus.diet.security.LoginUser;
import com.campus.diet.security.LoginUserHolder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class RecommendQueryRepository {

    private final RecipeMapper recipeMapper;
    private final UserFavoriteMapper userFavoriteMapper;
    private final BrowseHistoryMapper browseHistoryMapper;

    public RecommendQueryRepository(
            RecipeMapper recipeMapper,
            UserFavoriteMapper userFavoriteMapper,
            BrowseHistoryMapper browseHistoryMapper) {
        this.recipeMapper = recipeMapper;
        this.userFavoriteMapper = userFavoriteMapper;
        this.browseHistoryMapper = browseHistoryMapper;
    }

    public int countSceneRecipes(long sceneId) {
        return recipeMapper.countSceneRecipes(sceneId);
    }

    public List<Recipe> pageSceneByCollect(long sceneId, int offset, int pageSize) {
        return recipeMapper.pageByScene(sceneId, offset, pageSize);
    }

    public List<Recipe> listByScene(long sceneId) {
        return recipeMapper.listByScene(sceneId);
    }

    public List<Recipe> listRecommendCandidates(String keyword, String sceneTag, int candidateLimit) {
        return recipeMapper.listRecommendCandidates(keyword, sceneTag, candidateLimit);
    }

    public BehaviorProfile loadBehaviorProfileForCurrentUser() {
        LoginUser loginUser = LoginUserHolder.get();
        if (loginUser == null) {
            return BehaviorProfile.EMPTY;
        }
        long userId = loginUser.getUserId();
        List<UserFavorite> favorites = userFavoriteMapper.selectList(
                Wrappers.<UserFavorite>lambdaQuery()
                        .eq(UserFavorite::getUserId, userId)
                        .orderByDesc(UserFavorite::getCreatedAt)
                        .last("LIMIT 80"));
        List<BrowseHistory> histories = browseHistoryMapper.selectList(
                Wrappers.<BrowseHistory>lambdaQuery()
                        .eq(BrowseHistory::getUserId, userId)
                        .orderByDesc(BrowseHistory::getViewedAt)
                        .last("LIMIT 120"));
        Set<Long> favoriteRecipeIds = favorites.stream()
                .map(UserFavorite::getRecipeId)
                .filter(id -> id != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        LinkedHashSet<Long> recipeIdsToLoad = new LinkedHashSet<>(favoriteRecipeIds);
        for (BrowseHistory h : histories) {
            Long rid = h.getRecipeId();
            if (rid != null && !favoriteRecipeIds.contains(rid)) {
                recipeIdsToLoad.add(rid);
            }
        }
        if (recipeIdsToLoad.isEmpty()) {
            return BehaviorProfile.EMPTY;
        }
        List<Recipe> loaded = recipeMapper.selectBatchIds(new ArrayList<>(recipeIdsToLoad));
        Map<Long, Recipe> byId = loaded.stream()
                .filter(r -> r.getId() != null)
                .collect(Collectors.toMap(Recipe::getId, r -> r, (a, b) -> a));
        Map<String, Double> tagWeights = new HashMap<>();
        for (UserFavorite f : favorites) {
            Recipe r = f.getRecipeId() == null ? null : byId.get(f.getRecipeId());
            if (r != null) {
                addSplitRecipeTags(r, 1.0, tagWeights);
            }
        }
        for (BrowseHistory h : histories) {
            Long rid = h.getRecipeId();
            if (rid == null || favoriteRecipeIds.contains(rid)) {
                continue;
            }
            Recipe r = byId.get(rid);
            if (r != null) {
                addSplitRecipeTags(r, 0.45, tagWeights);
            }
        }
        if (tagWeights.isEmpty()) {
            return BehaviorProfile.EMPTY;
        }
        return new BehaviorProfile(tagWeights);
    }

    private static void addSplitRecipeTags(Recipe recipe, double recipeWeight, Map<String, Double> acc) {
        LinkedHashSet<String> keys = uniqueTagKeys(recipe);
        if (keys.isEmpty()) {
            return;
        }
        double per = recipeWeight / keys.size();
        for (String k : keys) {
            acc.merge(k, per, Double::sum);
        }
    }

    private static LinkedHashSet<String> uniqueTagKeys(Recipe recipe) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        addCsvKeys(out, recipe.getEfficacyTags());
        addCsvKeys(out, recipe.getConstitutionTags());
        addCsvKeys(out, recipe.getSeasonTags());
        addCsvKeys(out, recipe.getSymptomTags());
        return out;
    }

    private static void addCsvKeys(LinkedHashSet<String> sink, String csv) {
        if (csv == null || csv.isBlank()) {
            return;
        }
        for (String part : csv.split(",")) {
            String t = part.trim();
            if (t.isEmpty()) {
                continue;
            }
            sink.add(t.toLowerCase(Locale.ROOT));
        }
    }

    public static final class BehaviorProfile {
        public static final BehaviorProfile EMPTY = new BehaviorProfile(Map.of());

        private final Map<String, Double> interestByTag;
        private final double totalInterest;

        public BehaviorProfile(Map<String, Double> interestByTag) {
            this.interestByTag = interestByTag.isEmpty() ? Map.of() : Map.copyOf(interestByTag);
            this.totalInterest = this.interestByTag.values().stream().mapToDouble(Double::doubleValue).sum();
        }

        /**
         * 0~1：候选菜谱标签与用户收藏/浏览沉淀的标签兴趣重叠度（相对总兴趣强度归一化）。
         */
        public double boostForRecipe(Recipe recipe) {
            if (recipe == null || interestByTag.isEmpty()) {
                return 0.0;
            }
            LinkedHashSet<String> keys = uniqueTagKeys(recipe);
            if (keys.isEmpty()) {
                return 0.0;
            }
            double overlap = 0.0;
            for (String k : keys) {
                overlap += interestByTag.getOrDefault(k, 0.0);
            }
            if (totalInterest <= 1e-9) {
                return 0.0;
            }
            return Math.min(1.0, overlap / totalInterest);
        }
    }
}
