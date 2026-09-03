package com.huawei.coworkdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huawei.coworkdata.dto.UserProfileDto;
import com.huawei.coworkdata.entity.UserProfileEntity;
import com.huawei.coworkdata.mapper.UserProfileMapper;
import com.huawei.coworkdata.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private final UserProfileMapper mapper;

    @Override
    public List<UserProfileDto> listAll() {
        return mapper.selectList(new LambdaQueryWrapper<UserProfileEntity>()
                        .orderByAsc(UserProfileEntity::getUsername))
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public UserProfileDto get(String userId) {
        UserProfileEntity row = mapper.selectById(userId);
        return row == null ? null : toDto(row);
    }

    @Override
    @Transactional
    public void save(UserProfileDto dto) {
        UserProfileEntity existing = mapper.selectById(dto.getUserId());
        if (existing != null) {
            existing.setUsername(dto.getUsername());
            mapper.updateById(existing);
            return;
        }
        UserProfileEntity entity = new UserProfileEntity();
        entity.setUserId(dto.getUserId());
        entity.setUsername(dto.getUsername());
        mapper.insert(entity);
    }

    @Override
    @Transactional
    public boolean delete(String userId) {
        return mapper.deleteById(userId) > 0;
    }

    private UserProfileDto toDto(UserProfileEntity row) {
        UserProfileDto dto = new UserProfileDto();
        dto.setUserId(row.getUserId());
        dto.setUsername(row.getUsername());
        return dto;
    }
}
