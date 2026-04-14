package com.campus.diet.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SceneRecipeMapper {

    @Insert("INSERT IGNORE INTO scene_recipe(scene_id, recipe_id) VALUES(#{sceneId}, #{recipeId})")
    int link(@Param("sceneId") long sceneId, @Param("recipeId") long recipeId);

    @Delete("DELETE FROM scene_recipe WHERE recipe_id = #{recipeId}")
    int unlinkByRecipe(@Param("recipeId") long recipeId);
}
