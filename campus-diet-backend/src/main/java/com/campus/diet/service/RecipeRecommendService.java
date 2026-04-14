package com.campus.diet.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.diet.common.PageResult;
import com.campus.diet.entity.Recipe;
import com.campus.diet.mapper.RecipeMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecipeRecommendService {

    private final RecipeMapper recipeMapper;
    private final SystemKvService systemKvService;

    private final double matchWeight;
    private final double popularityWeight;

    public RecipeRecommendService(
            RecipeMapper recipeMapper,
            SystemKvService systemKvService,
            @Value("${campus.recommend.match-weight:0.6}") double matchWeight,
            @Value("${campus.recommend.popularity-weight:0.4}") double popularityWeight) {
        this.recipeMapper = recipeMapper;
        this.systemKvService = systemKvService;
        this.matchWeight = matchWeight;
        this.popularityWeight = popularityWeight;
    }

    public PageResult<Recipe> pageSceneRecipes(long sceneId, int page, int pageSize, String sortBy) {
        List<Recipe> all = recipeMapper.listByScene(sceneId);
        if ("collect".equalsIgnoreCase(sortBy)) {
            all.sort(Comparator.comparingInt(Recipe::getCollectCount).reversed());
        }
        int total = all.size();
        int from = Math.max(0, (page - 1) * pageSize);
        List<Recipe> slice = from >= total ? List.of() : all.subList(from, Math.min(total, from + pageSize));
        return new PageResult<>(slice, total, page, pageSize, from + pageSize < total);
    }

    public List<Recipe> recommendFeed(
            int page,
            int pageSize,
            String sceneTag,
            String constitutionCode,
            boolean personalized,
            String seasonCode,
            String keyword) {
        return recommendFeedPage(page, pageSize, sceneTag, constitutionCode, personalized, seasonCode, keyword)
                .getRecords();
    }

    /**
     * 与 {@link #recommendFeed} 相同排序与过滤逻辑，额外返回 {@code hasMore} 供分页加载。
     */
    public PageResult<Recipe> recommendFeedPage(
            int page,
            int pageSize,
            String sceneTag,
            String constitutionCode,
            boolean personalized,
            String seasonCode,
            String keyword) {
        if (!systemKvService.flagOn("recommend.global.enabled", true)) {
            return new PageResult<>(List.of(), 0, page, pageSize, false);
        }
        List<Recipe> all = recipeMapper.selectList(Wrappers.<Recipe>lambdaQuery()
                .eq(Recipe::getStatus, 1)
                .orderByDesc(Recipe::getCollectCount));
        if (sceneTag != null && !sceneTag.isBlank()) {
            String tag = sceneTag.trim();
            all = all.stream()
                    .filter(r -> containsTag(r.getEfficacyTags(), tag) || containsTag(r.getConstitutionTags(), tag))
                    .collect(Collectors.toList());
        }
        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.trim();
            all = all.stream().filter(r -> recipeMatchesKeyword(r, k)).collect(Collectors.toList());
        }
        if (all.isEmpty()) {
            return new PageResult<>(List.of(), 0, page, pageSize, false);
        }
        double maxLog = all.stream().mapToDouble(r -> Math.log1p(r.getCollectCount())).max().orElse(1);
        String season = seasonCode != null && !seasonCode.isBlank() ? seasonCode : SeasonUtil.currentSeasonCode(java.time.LocalDate.now());
        final String userConstitution = constitutionCode == null ? "" : constitutionCode.trim();

        List<ScoredRecipe> scored = new ArrayList<>();
        for (Recipe r : all) {
            double pop = Math.log1p(r.getCollectCount()) / maxLog;
            double seasonFit = seasonFit(r.getSeasonTags(), season);
            double constFit = constitutionFit(r.getConstitutionTags(), userConstitution);
            double match;
            if (personalized && !userConstitution.isEmpty()) {
                match = 0.7 * constFit + 0.3 * seasonFit;
            } else {
                match = seasonFit;
            }
            double totalScore = matchWeight * match + popularityWeight * pop;
            scored.add(new ScoredRecipe(r, totalScore));
        }
        Comparator<ScoredRecipe> byScore = Comparator.comparingDouble((ScoredRecipe sr) -> sr.score).reversed();
        Comparator<ScoredRecipe> byNewerId = Comparator.comparingLong(
                (ScoredRecipe sr) -> sr.recipe.getId() == null ? 0L : sr.recipe.getId()).reversed();
        scored.sort(byScore.thenComparing(byNewerId));
        int from = Math.max(0, (page - 1) * pageSize);
        List<Recipe> slice = scored.stream()
                .skip(from)
                .limit(pageSize)
                .map(sr -> sr.recipe)
                .collect(Collectors.toList());
        boolean hasMore = from + slice.size() < scored.size();
        return new PageResult<>(slice, scored.size(), page, pageSize, hasMore);
    }

    private boolean containsTag(String csv, String needle) {
        if (csv == null || csv.isBlank()) {
            return false;
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .anyMatch(t -> t.equalsIgnoreCase(needle));
    }

    /** 名称、功效摘要、标签、症状词等与关键词子串匹配（中英大小写不敏感） */
    private boolean recipeMatchesKeyword(Recipe r, String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return true;
        }
        if (textContains(r.getName(), keyword)) {
            return true;
        }
        if (textContains(r.getEfficacySummary(), keyword)) {
            return true;
        }
        if (textContains(r.getInstructionSummary(), keyword)) {
            return true;
        }
        if (csvContainsSubstring(r.getEfficacyTags(), keyword)) {
            return true;
        }
        if (csvContainsSubstring(r.getConstitutionTags(), keyword)) {
            return true;
        }
        return csvContainsSubstring(r.getSymptomTags(), keyword);
    }

    private static boolean textContains(String haystack, String needle) {
        if (haystack == null || haystack.isBlank()) {
            return false;
        }
        if (haystack.contains(needle)) {
            return true;
        }
        return haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private static boolean csvContainsSubstring(String csv, String needle) {
        if (csv == null || csv.isBlank()) {
            return false;
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .anyMatch(t -> textContains(t, needle));
    }

    private double seasonFit(String seasonTags, String season) {
        if (seasonTags == null || seasonTags.isBlank()) {
            return 0.35;
        }
        boolean hit = Arrays.stream(seasonTags.split(","))
                .map(String::trim)
                .anyMatch(s -> s.equalsIgnoreCase(season));
        return hit ? 1.0 : 0.25;
    }

    private double constitutionFit(String constitutionTags, String userCode) {
        if (userCode.isEmpty()) {
            return 0.5;
        }
        if (constitutionTags == null || constitutionTags.isBlank()) {
            return 0.2;
        }
        boolean hit = Arrays.stream(constitutionTags.split(","))
                .map(String::trim)
                .anyMatch(s -> s.equalsIgnoreCase(userCode));
        return hit ? 1.0 : 0.15;
    }

    private static final class ScoredRecipe {
        final Recipe recipe;
        final double score;

        ScoredRecipe(Recipe recipe, double score) {
            this.recipe = recipe;
            this.score = score;
        }
    }
}
