package com.campus.diet.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

/**
 * 导入数据常见：逻辑删除列 {@code deleted} 为 NULL，MyBatis-Plus 会生成 {@code deleted = 0}，导致行永远查不出。
 */
@Mapper
public interface CompatibilityPatchMapper {

    @Update("UPDATE recipe SET deleted = 0 WHERE deleted IS NULL")
    int fixRecipeDeletedNulls();

    @Update("UPDATE recipe SET status = 1 WHERE status IS NULL")
    int fixRecipeStatusNulls();

    @Update("UPDATE ingredient SET deleted = 0 WHERE deleted IS NULL")
    int fixIngredientDeletedNulls();

    @Update("UPDATE campus_scene SET deleted = 0 WHERE deleted IS NULL")
    int fixCampusSceneDeletedNulls();

    @Update("UPDATE sys_user SET deleted = 0 WHERE deleted IS NULL")
    int fixSysUserDeletedNulls();
}
