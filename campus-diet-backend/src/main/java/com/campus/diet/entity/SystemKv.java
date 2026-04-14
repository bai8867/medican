package com.campus.diet.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("system_kv")
public class SystemKv {

    @TableId
    private String k;
    private String v;
}
