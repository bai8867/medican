package com.campus.diet.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_profile")
public class UserProfile {

    @TableId
    private Long userId;
    private String constitutionCode;
    private String constitutionSource;
    private String seasonCode;
    private String surveyScoresJson;
    private Integer recommendEnabled;
    private Integer dataCollectionEnabled;
    private LocalDateTime updatedAt;
}
