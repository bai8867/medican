package com.campus.diet.dto;



import lombok.AllArgsConstructor;

import lombok.Data;

import lombok.NoArgsConstructor;



import java.util.List;



/**

 * 校园场景食疗列表项（故事化 + 痛点标签）。

 */

@Data

@NoArgsConstructor

@AllArgsConstructor

public class SceneTherapyListItemDto {



    private long id;

    private String name;

    private String icon;

    /** 一句话生活化描述 */

    private String description;

    private String tagline;

    private List<String> painTags;

    private int recipeCount;

    /** 与药膳功效标签对齐的检索词，供推荐页 scene_tag 使用 */

    private List<String> tags;

}

