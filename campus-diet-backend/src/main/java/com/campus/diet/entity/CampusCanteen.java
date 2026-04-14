package com.campus.diet.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("campus_canteen")
public class CampusCanteen {

    @TableId(type = IdType.INPUT)
    private String id;
    private String campusName;
    private String displayName;
    private Integer sortOrder;
}
