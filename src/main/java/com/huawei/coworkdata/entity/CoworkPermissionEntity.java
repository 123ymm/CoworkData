package com.huawei.coworkdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("cowork_permission")
public class CoworkPermissionEntity {

    @TableId(type = IdType.INPUT)
    private String coworkId;
    /** JSON 数组文本，如 ["accountA","accountB"] */
    private String llm;
}
