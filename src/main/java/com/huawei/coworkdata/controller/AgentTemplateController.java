package com.huawei.coworkdata.controller;

import com.huawei.coworkdata.dto.AgentTemplateDto;
import com.huawei.coworkdata.service.AgentTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 对应 Python {@code TemplateStore} 的 DB 模式。
 */
@RestController
@RequestMapping("/api/agent-templates")
@RequiredArgsConstructor
public class AgentTemplateController {

    private final AgentTemplateService templateService;

    @GetMapping
    public List<AgentTemplateDto> listAll() {
        return templateService.listAll();
    }

    @GetMapping("/{templateId}")
    public AgentTemplateDto get(@PathVariable String templateId) {
        AgentTemplateDto dto = templateService.get(templateId);
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found");
        }
        return dto;
    }

    @PutMapping("/{templateId}")
    public void save(@PathVariable String templateId, @RequestBody AgentTemplateDto body) {
        body.setId(templateId);
        templateService.save(body);
    }

    @DeleteMapping("/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String templateId) {
        if (!templateService.delete(templateId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found");
        }
    }
}
