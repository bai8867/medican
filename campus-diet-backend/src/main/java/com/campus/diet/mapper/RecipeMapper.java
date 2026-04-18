package com.campus.diet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.diet.entity.Recipe;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface RecipeMapper extends BaseMapper<Recipe> {

    @Select("SELECT COUNT(1) FROM scene_recipe WHERE scene_id = #{sceneId}")
    int countByScene(@Param("sceneId") long sceneId);

    @Select("SELECT r.* FROM recipe r INNER JOIN scene_recipe sr ON r.id = sr.recipe_id "
            + "WHERE sr.scene_id = #{sceneId} AND r.deleted = 0 AND r.status = 1 "
            + "ORDER BY r.collect_count DESC, r.id DESC")
    List<Recipe> listByScene(@Param("sceneId") long sceneId);

    @Select("SELECT COUNT(1) FROM recipe r INNER JOIN scene_recipe sr ON r.id = sr.recipe_id "
            + "WHERE sr.scene_id = #{sceneId} AND r.deleted = 0 AND r.status = 1")
    int countSceneRecipes(@Param("sceneId") long sceneId);

    @Select("SELECT r.* FROM recipe r INNER JOIN scene_recipe sr ON r.id = sr.recipe_id "
            + "WHERE sr.scene_id = #{sceneId} AND r.deleted = 0 AND r.status = 1 "
            + "ORDER BY r.collect_count DESC, r.id DESC "
            + "LIMIT #{limit} OFFSET #{offset}")
    List<Recipe> pageByScene(
            @Param("sceneId") long sceneId,
            @Param("offset") int offset,
            @Param("limit") int limit);

    @Select("<script>"
            + "SELECT r.* FROM recipe r "
            + "WHERE r.deleted = 0 AND r.status = 1 "
            + "<if test='keyword != null and keyword.trim() != \"\"'>"
            + "AND (r.name LIKE CONCAT('%', #{keyword}, '%') "
            + "OR r.efficacy_summary LIKE CONCAT('%', #{keyword}, '%') "
            + "OR r.instruction_summary LIKE CONCAT('%', #{keyword}, '%') "
            + "OR r.efficacy_tags LIKE CONCAT('%', #{keyword}, '%') "
            + "OR r.constitution_tags LIKE CONCAT('%', #{keyword}, '%') "
            + "OR r.symptom_tags LIKE CONCAT('%', #{keyword}, '%')) "
            + "</if>"
            + "<if test='sceneTag != null and sceneTag.trim() != \"\"'>"
            + "AND (r.efficacy_tags LIKE CONCAT('%', #{sceneTag}, '%') "
            + "OR r.constitution_tags LIKE CONCAT('%', #{sceneTag}, '%')) "
            + "</if>"
            + "ORDER BY r.collect_count DESC, r.id DESC "
            + "LIMIT #{limit}"
            + "</script>")
    List<Recipe> listRecommendCandidates(
            @Param("keyword") String keyword,
            @Param("sceneTag") String sceneTag,
            @Param("limit") int limit);

    @Select("SELECT sr.scene_id AS sceneId, COUNT(1) AS recipeCount "
            + "FROM scene_recipe sr INNER JOIN recipe r ON r.id = sr.recipe_id "
            + "WHERE r.deleted = 0 AND r.status = 1 "
            + "GROUP BY sr.scene_id")
    List<Map<String, Object>> listSceneRecipeCounts();
}
