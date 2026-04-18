package com.campus.diet.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ingredient")
public class Ingredient {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String category;
    private String note;
    /** 是否在药膳表单与食材下拉中展示（禁用则隐藏）。 */
    private Boolean enabled;
    private String imageUrl;
    private LocalDateTime createdAt;
    @TableLogic
    private Integer deleted;
}
