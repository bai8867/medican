package com.campus.diet.bootstrap;

import com.campus.diet.mapper.CompatibilityPatchMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 本机已有库导入后，{@code deleted}/{@code status} 为 NULL 时管理端与推荐列表会表现为「无数据」。
 */
@Component
@Order(10)
public class LegacyDbSanitizeRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacyDbSanitizeRunner.class);

    private final CompatibilityPatchMapper compatibilityPatchMapper;

    public LegacyDbSanitizeRunner(CompatibilityPatchMapper compatibilityPatchMapper) {
        this.compatibilityPatchMapper = compatibilityPatchMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int r1 = compatibilityPatchMapper.fixRecipeDeletedNulls();
            int r2 = compatibilityPatchMapper.fixRecipeStatusNulls();
            int i = compatibilityPatchMapper.fixIngredientDeletedNulls();
            int s = compatibilityPatchMapper.fixCampusSceneDeletedNulls();
            int u = compatibilityPatchMapper.fixSysUserDeletedNulls();
            if (r1 + r2 + i + s + u > 0) {
                log.info(
                        "已修正导入数据中的 NULL 字段：recipe.deleted={}, recipe.status={}, ingredient.deleted={}, campus_scene.deleted={}, sys_user.deleted={}",
                        r1,
                        r2,
                        i,
                        s,
                        u);
            }
        } catch (Exception e) {
            log.warn("兼容性修正（NULL deleted/status）跳过：{}", e.getMessage());
        }
    }
}
