package com.campus.diet.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.diet.entity.CampusScene;
import com.campus.diet.mapper.CampusSceneMapper;
import com.campus.diet.mapper.RecipeMapper;
import org.springframework.stereotype.Service;

import java.util.List;
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
        return listOrdered().stream().map(s -> new SceneView(s, recipeCount(s.getId()))).collect(Collectors.toList());
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
