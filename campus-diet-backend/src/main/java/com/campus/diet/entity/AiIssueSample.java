package com.campus.diet.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_issue_sample")
public class AiIssueSample {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String symptom;
    private String constitutionCode;
    private Integer qualityScore;
    private Integer scoreThreshold;
    private Integer safetyPassed;
    private String violatedRulesJson;
    private String requestPayloadJson;
    private String responsePayloadJson;
    private Integer guardEnabled;
    private Integer strictSafety;
    private String source;
    private Integer replayed;
    private LocalDateTime createdAt;
}
