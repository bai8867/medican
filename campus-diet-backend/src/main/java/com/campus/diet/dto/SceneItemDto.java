package com.campus.diet.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SceneItemDto {

    private long id;
    private String name;
    private String icon;
    private String description;
    private int recipeCount;
    private List<String> tags;
}
