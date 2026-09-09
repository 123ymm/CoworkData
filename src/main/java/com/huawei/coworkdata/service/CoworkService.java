package com.huawei.coworkdata.service;

import com.huawei.coworkdata.dto.CoworkDto;
import com.huawei.coworkdata.dto.CoworkPermissionDto;

import java.util.List;

public interface CoworkService {

    List<CoworkDto> listAll();

    CoworkDto get(String coworkId);

    void save(CoworkDto dto);

    boolean delete(String coworkId);

    CoworkPermissionDto getPermission(String coworkId);

    void savePermission(CoworkPermissionDto dto);

    boolean deletePermission(String coworkId);
}
