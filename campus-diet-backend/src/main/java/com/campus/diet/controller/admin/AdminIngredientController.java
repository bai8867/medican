package com.campus.diet.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.diet.common.ApiResponse;
import com.campus.diet.common.PageResult;
import com.campus.diet.entity.Ingredient;
import com.campus.diet.mapper.IngredientMapper;
import com.campus.diet.security.SecurityUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/ingredients")
public class AdminIngredientController {

    private final IngredientMapper ingredientMapper;

    public AdminIngredientController(IngredientMapper ingredientMapper) {
        this.ingredientMapper = ingredientMapper;
    }

    @GetMapping
    public ApiResponse<PageResult<Ingredient>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(value = "page_size", defaultValue = "10") int pageSize) {
        SecurityUtils.requireContentManager();
        Page<Ingredient> p = ingredientMapper.selectPage(new Page<>(page, pageSize), new LambdaQueryWrapper<>());
        return ApiResponse.ok(new PageResult<>(p.getRecords(), p.getTotal(), page, pageSize, p.hasNext()));
    }

    @PostMapping
    public ApiResponse<Ingredient> create(@RequestBody Ingredient body) {
        SecurityUtils.requireContentManager();
        ingredientMapper.insert(body);
        return ApiResponse.ok(ingredientMapper.selectById(body.getId()));
    }

    @PutMapping("/{id}")
    public ApiResponse<Ingredient> update(@PathVariable long id, @RequestBody Ingredient body) {
        SecurityUtils.requireContentManager();
        body.setId(id);
        ingredientMapper.updateById(body);
        return ApiResponse.ok(ingredientMapper.selectById(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        SecurityUtils.requireContentManager();
        ingredientMapper.deleteById(id);
        return ApiResponse.ok();
    }
}
