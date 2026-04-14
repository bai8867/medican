package com.campus.diet.controller;

import com.campus.diet.common.ApiResponse;
import com.campus.diet.dto.SceneSolutionDto;
import com.campus.diet.dto.SceneTherapyListItemDto;
import com.campus.diet.service.SceneTherapyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 校园十大场景食疗（独立 API，与历史 {@code /api/campus/scenes} 并存）。
 */
@RestController
@RequestMapping("/api/scenes")
public class SceneTherapyController {

    private final SceneTherapyService sceneTherapyService;

    public SceneTherapyController(SceneTherapyService sceneTherapyService) {
        this.sceneTherapyService = sceneTherapyService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list() {
        List<SceneTherapyListItemDto> list = sceneTherapyService.listScenes();
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        return ApiResponse.ok(data);
    }

    @GetMapping("/{id}/recipes")
    public ApiResponse<SceneSolutionDto> sceneRecipes(@PathVariable("id") long id) {
        return ApiResponse.ok(sceneTherapyService.getSceneSolution(id));
    }
}
