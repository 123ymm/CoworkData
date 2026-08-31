package com.huawei.coworkdata.service;

import com.huawei.coworkdata.dto.AgentTemplateDto;

import java.util.List;

public interface AgentTemplateService {

    List<AgentTemplateDto> listAll();

    AgentTemplateDto get(String templateId);

    void save(AgentTemplateDto dto);

    boolean delete(String templateId);
}
