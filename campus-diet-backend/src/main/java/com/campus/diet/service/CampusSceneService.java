package com.campus.diet.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.diet.entity.CampusScene;
import com.campus.diet.mapper.CampusSceneMapper;
import com.campus.diet.mapper.RecipeMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CampusSceneService {

    private final CampusSceneMapper campusSceneMapper;
    private final RecipeMapper recipeMapper;

    public CampusSceneService(CampusSceneMapper campusSceneMapper, RecipeMapper recipeMapper) {
        this.campusSceneMapper = campusSceneMapper;
        this.recipeMapper = recipeMapper;
    }

    public List<CampusScene> listOrdered() {
        return campusSceneMapper.selectList(
                Wrappers.<CampusScene>lambdaQuery().orderByAsc(CampusScene::getSortOrder, CampusScene::getId));
    }

    public int recipeCount(long sceneId) {
        return recipeMapper.countByScene(sceneId);
    }

    public List<SceneView> listWithCounts() {
        List<CampusScene> scenes = listOrdered();
        if (scenes.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> rows = recipeMapper.listSceneRecipeCounts();
        Map<Long, Integer> bySceneId = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object sceneIdObj = row.get("sceneId");
            Object recipeCountObj = row.get("recipeCount");
            if (!(sceneIdObj instanceof Number) || !(recipeCountObj instanceof Number)) {
                continue;
            }
            bySceneId.put(((Number) sceneIdObj).longValue(), ((Number) recipeCountObj).intValue());
        }
        return scenes.stream()
                .map(s -> new SceneView(s, bySceneId.getOrDefault(s.getId(), 0)))
                .collect(Collectors.toList());
    }

    public static class SceneView {
        public final CampusScene scene;
        public final int recipeCount;

        public SceneView(CampusScene scene, int recipeCount) {
            this.scene = scene;
            this.recipeCount = recipeCount;
        }
    }
}
