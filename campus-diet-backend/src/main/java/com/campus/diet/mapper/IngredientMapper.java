package com.campus.diet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.diet.entity.Ingredient;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IngredientMapper extends BaseMapper<Ingredient> {
}
