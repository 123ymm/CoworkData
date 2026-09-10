package com.huawei.coworkdata.dto;

import lombok.Data;

@Data
public class UserProfileDto {

    /** W3 工号 */
    private String userId;
    /** 展示姓名（张三/李四）；可空串 */
    private String username;
}
