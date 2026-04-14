package com.campus.diet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.diet.entity.Recipe;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RecipeMapper extends BaseMapper<Recipe> {

    @Select("SELECT COUNT(1) FROM scene_recipe WHERE scene_id = #{sceneId}")
    int countByScene(@Param("sceneId") long sceneId);

    @Select("SELECT r.* FROM recipe r INNER JOIN scene_recipe sr ON r.id = sr.recipe_id "
            + "WHERE sr.scene_id = #{sceneId} AND r.deleted = 0 AND r.status = 1 "
            + "ORDER BY r.collect_count DESC, r.id DESC")
    List<Recipe> listByScene(@Param("sceneId") long sceneId);
}
