package com.huawei.coworkdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.huawei.coworkdata.util.Strings;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("user_profile")
public class UserProfileEntity {

    /** PK = W3 工号 */
    @TableId(type = IdType.INPUT)
    private String userId;
    /** 展示姓名（张三/李四）；可空串 */
    private String username;

    public void setUsername(String username) {
        this.username = Strings.nz(username);
    }
}
