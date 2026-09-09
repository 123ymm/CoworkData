package com.huawei.coworkdata.dto;

import lombok.Data;

@Data
public class CoworkPermissionDto {

    private String coworkId;
    /** JSON 数组文本，或由客户端传 list 再由服务端序列化；此处统一用字符串存库形态 */
    private String llm;
}
