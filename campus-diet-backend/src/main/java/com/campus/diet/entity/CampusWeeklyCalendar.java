package com.campus.diet.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("campus_weekly_calendar")
public class CampusWeeklyCalendar {

    @TableId(type = IdType.AUTO)
    private Long id;
    private LocalDate weekMonday;
    private String canteenId;
    private Integer published;
    private String weekTitle;
    private String estimatedPublishNote;
    @TableField("days_json")
    private String daysJson;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
