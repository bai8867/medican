package com.campus.diet.dto;



import lombok.AllArgsConstructor;

import lombok.Data;

import lombok.NoArgsConstructor;



import java.util.List;



@Data

@NoArgsConstructor

@AllArgsConstructor

public class SceneSolutionDto {



    private SceneTherapyListItemDto scene;

    private List<RecipeCardDto> recipes;

    private List<TeaRemedyDto> teas;

    private String ingredientInsight;

    private List<String> forbidden;

}

