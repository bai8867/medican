package com.campus.diet.dto.admin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/** 管理端食材写入：与前端字段名对齐（功效简介 → 库表 {@code note}）。 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AdminIngredientUpsertRequest {

    private String name;
    /** 对应表字段 {@code note} */
    private String efficacySummary;
    private Boolean enabled;
    private String category;
}
