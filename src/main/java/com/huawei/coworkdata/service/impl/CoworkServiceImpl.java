package com.huawei.coworkdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huawei.coworkdata.dto.CoworkDto;
import com.huawei.coworkdata.dto.CoworkPermissionDto;
import com.huawei.coworkdata.entity.CoworkEntity;
import com.huawei.coworkdata.entity.CoworkPermissionEntity;
import com.huawei.coworkdata.mapper.CoworkMapper;
import com.huawei.coworkdata.mapper.CoworkPermissionMapper;
import com.huawei.coworkdata.service.CoworkService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CoworkServiceImpl implements CoworkService {

    private final CoworkMapper coworkMapper;
    private final CoworkPermissionMapper permissionMapper;

    @Override
    public List<CoworkDto> listAll() {
        return coworkMapper.selectList(new LambdaQueryWrapper<CoworkEntity>()
                        .orderByAsc(CoworkEntity::getCoworkId))
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public CoworkDto get(String coworkId) {
        CoworkEntity row = coworkMapper.selectById(coworkId);
        return row == null ? null : toDto(row);
    }

    @Override
    @Transactional
    public void save(CoworkDto dto) {
        CoworkEntity existing = coworkMapper.selectById(dto.getCoworkId());
        if (existing != null) {
            // 空 name 不覆盖已有展示名（地端查不到 suite 时会传 ""）
            if (dto.getName() != null && !dto.getName().trim().isEmpty()) {
                existing.setName(dto.getName());
                coworkMapper.updateById(existing);
            }
            return;
        }
        CoworkEntity entity = new CoworkEntity();
        entity.setCoworkId(dto.getCoworkId());
        entity.setName(dto.getName() != null ? dto.getName() : "");
        coworkMapper.insert(entity);
    }

    @Override
    @Transactional
    public boolean delete(String coworkId) {
        permissionMapper.deleteById(coworkId);
        return coworkMapper.deleteById(coworkId) > 0;
    }

    @Override
    public CoworkPermissionDto getPermission(String coworkId) {
        CoworkPermissionEntity row = permissionMapper.selectById(coworkId);
        return row == null ? null : toPermDto(row);
    }

    @Override
    @Transactional
    public void savePermission(CoworkPermissionDto dto) {
        // 确保 cowork 行存在（权限外键）
        if (coworkMapper.selectById(dto.getCoworkId()) == null) {
            CoworkEntity stub = new CoworkEntity();
            stub.setCoworkId(dto.getCoworkId());
            stub.setName("");
            coworkMapper.insert(stub);
        }
        String llm = dto.getLlm() != null ? dto.getLlm() : "[]";
        CoworkPermissionEntity existing = permissionMapper.selectById(dto.getCoworkId());
        if (existing != null) {
            existing.setLlm(llm);
            permissionMapper.updateById(existing);
            return;
        }
        CoworkPermissionEntity entity = new CoworkPermissionEntity();
        entity.setCoworkId(dto.getCoworkId());
        entity.setLlm(llm);
        permissionMapper.insert(entity);
    }

    @Override
    @Transactional
    public boolean deletePermission(String coworkId) {
        return permissionMapper.deleteById(coworkId) > 0;
    }

    private CoworkDto toDto(CoworkEntity row) {
        CoworkDto dto = new CoworkDto();
        dto.setCoworkId(row.getCoworkId());
        dto.setName(row.getName());
        return dto;
    }

    private CoworkPermissionDto toPermDto(CoworkPermissionEntity row) {
        CoworkPermissionDto dto = new CoworkPermissionDto();
        dto.setCoworkId(row.getCoworkId());
        dto.setLlm(row.getLlm());
        return dto;
    }
}
