package com.huawei.coworkdata.service;

import com.huawei.coworkdata.dto.UserProfileDto;

import java.util.List;

public interface UserProfileService {

    List<UserProfileDto> listAll();

    UserProfileDto get(String userId);

    void save(UserProfileDto dto);

    boolean delete(String userId);
}
