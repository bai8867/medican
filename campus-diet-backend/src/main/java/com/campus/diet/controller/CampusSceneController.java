package com.campus.diet.controller;

import com.campus.diet.common.ApiResponse;
import com.campus.diet.common.PageResult;
import com.campus.diet.dto.RecipeCardDto;
import com.campus.diet.dto.SceneItemDto;
import com.campus.diet.entity.Recipe;
import com.campus.diet.service.CampusSceneService;
import com.campus.diet.service.RecipeRecommendService;
import com.campus.diet.util.JsonTags;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/campus")
public class CampusSceneController {

    private final CampusSceneService campusSceneService;
    private final RecipeRecommendService recipeRecommendService;

    public CampusSceneController(CampusSceneService campusSceneService, RecipeRecommendService recipeRecommendService) {
        this.campusSceneService = campusSceneService;
        this.recipeRecommendService = recipeRecommendService;
    }

    @GetMapping("/scenes")
    public ApiResponse<Map<String, Object>> scenes() {
        List<SceneItemDto> list = campusSceneService.listWithCounts().stream()
                .map(v -> new SceneItemDto(
                        v.scene.getId(),
                        v.scene.getName(),
                        v.scene.getIcon(),
                        v.scene.getDescription(),
                        v.recipeCount,
                        JsonTags.parseStringList(v.scene.getTagsJson())
                ))
                .collect(Collectors.toList());
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        return ApiResponse.ok(data);
    }

    @GetMapping("/scenes/recipes")
    public ApiResponse<PageResult<RecipeCardDto>> sceneRecipes(
            @RequestParam("scene_id") long sceneId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(value = "page_size", defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "collect") String sortBy) {
        PageResult<Recipe> pr = recipeRecommendService.pageSceneRecipes(sceneId, page, pageSize, sortBy);
        List<RecipeCardDto> records = pr.getRecords().stream().map(RecipeCardDto::from).collect(Collectors.toList());
        return ApiResponse.ok(new PageResult<>(records, pr.getTotal(), pr.getPage(), pr.getPageSize(), pr.isHasMore()));
    }

    @GetMapping("/recipes/recommend-feed")
    public ApiResponse<Map<String, Object>> recommendFeed(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(value = "page_size", defaultValue = "8") int pageSize,
            @RequestParam(required = false) String sceneTag,
            @RequestParam(required = false) String constitutionCode,
            @RequestParam(required = false) Boolean personalized,
            @RequestParam(value = "recommend_enabled", required = false) Boolean recommendEnabled,
            @RequestParam(required = false) String seasonCode,
            @RequestParam(required = false) String keyword) {
        boolean recOn = recommendEnabled == null || Boolean.TRUE.equals(recommendEnabled);
        boolean person = Boolean.TRUE.equals(personalized) && recOn;
        PageResult<RecipeCardDto> pr = recipeRecommendService.recommendFeedCardPage(
                page,
                pageSize,
                sceneTag,
                constitutionCode == null ? "" : constitutionCode,
                person,
                seasonCode,
                keyword);
        Map<String, Object> data = new HashMap<>();
        data.put("records", pr.getRecords());
        data.put("list", pr.getRecords());
        data.put("hasMore", pr.isHasMore());
        data.put("total", pr.getTotal());
        return ApiResponse.ok(data);
    }
}
