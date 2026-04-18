package com.campus.diet.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.diet.common.ApiResponse;
import com.campus.diet.common.BizException;
import com.campus.diet.common.PageResult;
import com.campus.diet.dto.admin.AdminIngredientUpsertRequest;
import com.campus.diet.entity.Ingredient;
import com.campus.diet.mapper.IngredientMapper;
import com.campus.diet.security.SecurityUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/ingredients")
public class AdminIngredientController {

    private static final int PAGE_SIZE_MAX = 100;

    private final IngredientMapper ingredientMapper;

    public AdminIngredientController(IngredientMapper ingredientMapper) {
        this.ingredientMapper = ingredientMapper;
    }

    @GetMapping
    public ApiResponse<PageResult<Ingredient>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(value = "page_size", defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String enabled) {
        SecurityUtils.requireContentManager();
        int p = Math.max(1, page);
        int ps = Math.min(PAGE_SIZE_MAX, Math.max(1, pageSize));
        LambdaQueryWrapper<Ingredient> w = new LambdaQueryWrapper<>();
        String k = keyword == null ? "" : keyword.trim();
        if (!k.isEmpty()) {
            w.and(q -> q.like(Ingredient::getName, k).or().like(Ingredient::getNote, k));
        }
        applyEnabledFilter(w, enabled);
        Page<Ingredient> result = ingredientMapper.selectPage(new Page<>(p, ps), w);
        return ApiResponse.ok(
                new PageResult<>(
                        result.getRecords(),
                        result.getTotal(),
                        p,
                        ps,
                        result.hasNext()));
    }

    private static void applyEnabledFilter(LambdaQueryWrapper<Ingredient> w, String enabledParam) {
        if (enabledParam == null || enabledParam.isBlank()) {
            return;
        }
        String e = enabledParam.trim().toLowerCase();
        if ("true".equals(e) || "1".equals(e)) {
            w.eq(Ingredient::getEnabled, true);
        } else if ("false".equals(e) || "0".equals(e)) {
            w.eq(Ingredient::getEnabled, false);
        }
    }

    @PostMapping
    public ApiResponse<Ingredient> create(@RequestBody AdminIngredientUpsertRequest body) {
        SecurityUtils.requireContentManager();
        if (!StringUtils.hasText(body.getName())) {
            throw new IllegalArgumentException("食材名称不能为空");
        }
        String name = body.getName().trim();
        assertNameUnique(name, null);
        Ingredient ing = new Ingredient();
        ing.setName(name);
        if (body.getEfficacySummary() != null) {
            ing.setNote(body.getEfficacySummary().trim());
        }
        ing.setCategory(body.getCategory());
        ing.setEnabled(body.getEnabled() != null ? body.getEnabled() : Boolean.TRUE);
        ingredientMapper.insert(ing);
        return ApiResponse.ok(ingredientMapper.selectById(ing.getId()));
    }

    @PutMapping("/{id}")
    public ApiResponse<Ingredient> update(@PathVariable long id, @RequestBody AdminIngredientUpsertRequest body) {
        SecurityUtils.requireContentManager();
        Ingredient existing = ingredientMapper.selectById(id);
        if (existing == null) {
            throw new BizException(404, "未找到该食材");
        }
        if (StringUtils.hasText(body.getName())) {
            String name = body.getName().trim();
            assertNameUnique(name, id);
            existing.setName(name);
        }
        if (body.getEfficacySummary() != null) {
            existing.setNote(body.getEfficacySummary().trim());
        }
        if (body.getCategory() != null) {
            existing.setCategory(body.getCategory());
        }
        if (body.getEnabled() != null) {
            existing.setEnabled(body.getEnabled());
        }
        ingredientMapper.updateById(existing);
        return ApiResponse.ok(ingredientMapper.selectById(id));
    }

    private void assertNameUnique(String name, Long excludeId) {
        LambdaQueryWrapper<Ingredient> w = new LambdaQueryWrapper<Ingredient>().eq(Ingredient::getName, name);
        if (excludeId != null) {
            w.ne(Ingredient::getId, excludeId);
        }
        Long cnt = ingredientMapper.selectCount(w);
        if (cnt != null && cnt > 0) {
            throw new BizException(409, "食材名称已存在，请更换名称");
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        SecurityUtils.requireContentManager();
        ingredientMapper.deleteById(id);
        return ApiResponse.ok();
    }
}
