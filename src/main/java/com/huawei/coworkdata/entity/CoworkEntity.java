package com.huawei.coworkdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("cowork")
public class CoworkEntity {

    @TableId(type = IdType.INPUT)
    private String coworkId;
    private String name;
}
