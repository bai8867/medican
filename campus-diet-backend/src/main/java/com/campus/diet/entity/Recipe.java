package com.campus.diet.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("recipe")
public class Recipe {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String coverUrl;
    private String efficacySummary;
    private Integer collectCount;
    private String seasonTags;
    private String constitutionTags;
    private String efficacyTags;
    /** 适用症状/痛点关键词，逗号分隔，与场景痛点动态匹配 */
    private String symptomTags;
    private String instructionSummary;
    private String stepsJson;
    private String contraindication;
    private Integer status;
    private LocalDateTime createdAt;
    @TableLogic
    private Integer deleted;
}
