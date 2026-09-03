package com.huawei.coworkdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huawei.coworkdata.dto.AgentTemplateDto;
import com.huawei.coworkdata.entity.AgentTemplateEntity;
import com.huawei.coworkdata.mapper.AgentTemplateMapper;
import com.huawei.coworkdata.service.AgentTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentTemplateServiceImpl implements AgentTemplateService {

    private final AgentTemplateMapper mapper;

    @Override
    public List<AgentTemplateDto> listAll() {
        return mapper.selectList(new LambdaQueryWrapper<AgentTemplateEntity>()
                        .orderByAsc(AgentTemplateEntity::getName))
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public AgentTemplateDto get(String templateId) {
        AgentTemplateEntity row = mapper.selectById(templateId);
        return row == null ? null : toDto(row);
    }

    @Override
    @Transactional
    public void save(AgentTemplateDto dto) {
        AgentTemplateEntity existing = mapper.selectById(dto.getId());
        OffsetDateTime now = OffsetDateTime.now();
        if (existing != null) {
            existing.setName(dto.getName());
            existing.setVersion(dto.getVersion());
            existing.setDescription(dto.getDescription() != null ? dto.getDescription() : "");
            existing.setTemplateDir(dto.getTemplateDir());
            existing.setUpdatedAt(now);
            mapper.updateById(existing);
            return;
        }
        AgentTemplateEntity entity = new AgentTemplateEntity();
        entity.setId(dto.getId());
        entity.setName(dto.getName());
        entity.setVersion(dto.getVersion());
        entity.setDescription(dto.getDescription() != null ? dto.getDescription() : "");
        entity.setTemplateDir(dto.getTemplateDir());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        mapper.insert(entity);
    }

    @Override
    @Transactional
    public boolean delete(String templateId) {
        return mapper.deleteById(templateId) > 0;
    }

    private AgentTemplateDto toDto(AgentTemplateEntity row) {
        AgentTemplateDto dto = new AgentTemplateDto();
        dto.setId(row.getId());
        dto.setName(row.getName());
        dto.setVersion(row.getVersion());
        dto.setDescription(row.getDescription());
        dto.setTemplateDir(row.getTemplateDir());
        dto.setCreatedAt(row.getCreatedAt());
        dto.setUpdatedAt(row.getUpdatedAt());
        return dto;
    }
}
