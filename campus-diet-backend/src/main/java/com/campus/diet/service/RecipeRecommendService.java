package com.campus.diet.service;

import com.campus.diet.application.recommend.RecommendApplicationService;
import com.campus.diet.common.PageResult;
import com.campus.diet.dto.RecipeCardDto;
import com.campus.diet.entity.Recipe;
import com.campus.diet.domain.recommend.RecommendFilterDomainService;
import com.campus.diet.domain.recommend.RecommendScoringDomainService;
import com.campus.diet.domain.recommend.RecommendScoringDomainService.ScoredRecipe;
import com.campus.diet.infrastructure.recommend.RecommendQueryRepository;
import com.campus.diet.mapper.BrowseHistoryMapper;
import com.campus.diet.mapper.RecipeMapper;
import com.campus.diet.mapper.UserFavoriteMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class RecipeRecommendService {

    private final SystemKvService systemKvService;
    private final RecommendApplicationService recommendApplicationService;

    public RecipeRecommendService(
            RecipeMapper recipeMapper,
            SystemKvService systemKvService,
            RuntimeMetricService runtimeMetricService,
            UserFavoriteMapper userFavoriteMapper,
            BrowseHistoryMapper browseHistoryMapper,
            @Value("${campus.recommend.match-weight:0.6}") double matchWeight,
            @Value("${campus.recommend.popularity-weight:0.4}") double popularityWeight,
            @Value("${campus.recommend.behavior-weight:0.1}") double behaviorWeight,
            @Value("${campus.recommend.candidate-window-size:240}") int candidateWindowSize,
            @Value("${campus.recommend.algorithm-path:rules}") String algorithmPath) {
        this.systemKvService = systemKvService;
        RecommendQueryRepository recommendQueryRepository =
                new RecommendQueryRepository(recipeMapper, userFavoriteMapper, browseHistoryMapper);
        RecommendFilterDomainService filterDomainService = new RecommendFilterDomainService();
        RecommendScoringDomainService scoringDomainService =
                new RecommendScoringDomainService(matchWeight, popularityWeight, behaviorWeight);
        this.recommendApplicationService = new RecommendApplicationService(
                recommendQueryRepository,
                filterDomainService,
                scoringDomainService,
                runtimeMetricService,
                Math.max(60, candidateWindowSize),
                algorithmPath == null ? "rules" : algorithmPath.trim().toLowerCase(Locale.ROOT));
    }

    public PageResult<Recipe> pageSceneRecipes(long sceneId, int page, int pageSize, String sortBy) {
        return recommendApplicationService.pageSceneRecipes(sceneId, page, pageSize, sortBy);
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

    public PageResult<RecipeCardDto> recommendFeedCardPage(
            int page,
            int pageSize,
            String sceneTag,
            String constitutionCode,
            boolean personalized,
            String seasonCode,
            String keyword) {
        PageResult<ScoredRecipe> scored = scoredRecommendFeedPage(
                page,
                pageSize,
                sceneTag,
                constitutionCode,
                personalized,
                seasonCode,
                keyword);
        List<RecipeCardDto> cards = scored.getRecords().stream().map(sr -> {
            RecipeCardDto dto = RecipeCardDto.from(sr.getRecipe());
            dto.setRecommendReason(sr.getRecommendReason());
            return dto;
        }).collect(Collectors.toList());
        return new PageResult<>(cards, scored.getTotal(), scored.getPage(), scored.getPageSize(), scored.isHasMore());
    }

    public PageResult<Recipe> recommendFeedPage(
            int page,
            int pageSize,
            String sceneTag,
            String constitutionCode,
            boolean personalized,
            String seasonCode,
            String keyword) {
        PageResult<ScoredRecipe> scored = scoredRecommendFeedPage(
                page,
                pageSize,
                sceneTag,
                constitutionCode,
                personalized,
                seasonCode,
                keyword);
        List<Recipe> records = scored.getRecords().stream().map(ScoredRecipe::getRecipe).collect(Collectors.toList());
        return new PageResult<>(records, scored.getTotal(), scored.getPage(), scored.getPageSize(), scored.isHasMore());
    }

    private PageResult<ScoredRecipe> scoredRecommendFeedPage(
            int page,
            int pageSize,
            String sceneTag,
            String constitutionCode,
            boolean personalized,
            String seasonCode,
            String keyword) {
        return recommendApplicationService.scoredRecommendFeedPage(
                page,
                pageSize,
                sceneTag,
                constitutionCode,
                personalized,
                seasonCode,
                keyword,
                systemKvService.flagOn("recommend.global.enabled", true));
    }
}
