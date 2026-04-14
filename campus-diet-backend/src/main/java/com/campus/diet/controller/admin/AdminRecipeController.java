package com.campus.diet.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.diet.common.ApiResponse;
import com.campus.diet.common.BizException;
import com.campus.diet.common.PageResult;
import com.campus.diet.entity.Recipe;
import com.campus.diet.mapper.RecipeMapper;
import com.campus.diet.mapper.SceneRecipeMapper;
import com.campus.diet.security.SecurityUtils;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/recipes")
public class AdminRecipeController {

    private final RecipeMapper recipeMapper;
    private final SceneRecipeMapper sceneRecipeMapper;

    public AdminRecipeController(RecipeMapper recipeMapper, SceneRecipeMapper sceneRecipeMapper) {
        this.recipeMapper = recipeMapper;
        this.sceneRecipeMapper = sceneRecipeMapper;
    }

    @GetMapping
    public ApiResponse<PageResult<Recipe>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(value = "page_size", defaultValue = "10") int pageSize) {
        SecurityUtils.requireContentManager();
        Page<Recipe> p = recipeMapper.selectPage(new Page<>(page, pageSize), new LambdaQueryWrapper<>());
        PageResult<Recipe> pr = new PageResult<>(p.getRecords(), p.getTotal(), page, pageSize, p.hasNext());
        return ApiResponse.ok(pr);
    }

    @GetMapping("/{id}")
    public ApiResponse<Recipe> getOne(@PathVariable long id) {
        SecurityUtils.requireContentManager();
        Recipe r = recipeMapper.selectById(id);
        if (r == null) {
            throw new BizException(404, "药膳不存在");
        }
        return ApiResponse.ok(r);
    }

    @PostMapping
    public ApiResponse<Recipe> create(@RequestBody RecipeSave body) {
        SecurityUtils.requireContentManager();
        Recipe r = body.toEntity();
        recipeMapper.insert(r);
        linkScenes(r.getId(), body.getSceneIds());
        return ApiResponse.ok(recipeMapper.selectById(r.getId()));
    }

    @PutMapping("/{id}")
    public ApiResponse<Recipe> update(@PathVariable long id, @RequestBody RecipeSave body) {
        SecurityUtils.requireContentManager();
        Recipe r = body.toEntity();
        r.setId(id);
        recipeMapper.updateById(r);
        sceneRecipeMapper.unlinkByRecipe(id);
        linkScenes(id, body.getSceneIds());
        return ApiResponse.ok(recipeMapper.selectById(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        SecurityUtils.requireContentManager();
        sceneRecipeMapper.unlinkByRecipe(id);
        recipeMapper.deleteById(id);
        return ApiResponse.ok();
    }

    private void linkScenes(long recipeId, List<Long> sceneIds) {
        if (sceneIds == null) {
            return;
        }
        for (Long sid : sceneIds) {
            if (sid != null) {
                sceneRecipeMapper.link(sid, recipeId);
            }
        }
    }

    @Data
    public static class RecipeSave {
        private String name;
        private String coverUrl;
        private String efficacySummary;
        private Integer collectCount;
        private String seasonTags;
        private String constitutionTags;
        private String efficacyTags;
        private String instructionSummary;
        private String stepsJson;
        private String contraindication;
        private Integer status;
        private List<Long> sceneIds;

        Recipe toEntity() {
            Recipe r = new Recipe();
            r.setName(name);
            r.setCoverUrl(coverUrl);
            r.setEfficacySummary(efficacySummary);
            r.setCollectCount(collectCount == null ? 0 : collectCount);
            r.setSeasonTags(seasonTags);
            r.setConstitutionTags(constitutionTags);
            r.setEfficacyTags(efficacyTags);
            r.setInstructionSummary(instructionSummary);
            r.setStepsJson(stepsJson);
            r.setContraindication(contraindication);
            r.setStatus(status == null ? 1 : status);
            return r;
        }
    }
}
