package com.campus.diet.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("campus_scene")
public class CampusScene {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String icon;
    private String description;
    /** JSON 数组字符串，如 ["提神","护眼"] */
    private String tagsJson;
    /** 场景扩展 JSON：痛点、茶饮、禁忌、食材解读等 */
    private String extraJson;
    private Integer sortOrder;
    @TableLogic
    private Integer deleted;
}
