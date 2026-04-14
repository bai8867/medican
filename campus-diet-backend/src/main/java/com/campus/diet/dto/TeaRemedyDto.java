package com.campus.diet.dto;



import lombok.AllArgsConstructor;

import lombok.Data;

import lombok.NoArgsConstructor;



@Data

@NoArgsConstructor

@AllArgsConstructor

public class TeaRemedyDto {



    private String title;

    private String body;

    /** tea | tip */

    private String type;

}

