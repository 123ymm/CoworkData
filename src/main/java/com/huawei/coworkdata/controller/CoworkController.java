package com.huawei.coworkdata.controller;

import com.huawei.coworkdata.dto.CoworkDto;
import com.huawei.coworkdata.dto.CoworkPermissionDto;
import com.huawei.coworkdata.service.CoworkService;
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

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CoworkController {

    private final CoworkService coworkService;

    @GetMapping("/coworks")
    public List<CoworkDto> listCoworks() {
        return coworkService.listAll();
    }

    @GetMapping("/coworks/{coworkId}")
    public CoworkDto getCowork(@PathVariable String coworkId) {
        CoworkDto dto = coworkService.get(coworkId);
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cowork not found");
        }
        return dto;
    }

    @PutMapping("/coworks/{coworkId}")
    public void saveCowork(@PathVariable String coworkId, @RequestBody CoworkDto body) {
        body.setCoworkId(coworkId);
        if (body.getName() == null) {
            body.setName("");
        }
        coworkService.save(body);
    }

    @DeleteMapping("/coworks/{coworkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCowork(@PathVariable String coworkId) {
        if (!coworkService.delete(coworkId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cowork not found");
        }
    }

    @GetMapping("/cowork-permissions/{coworkId}")
    public CoworkPermissionDto getPermission(@PathVariable String coworkId) {
        CoworkPermissionDto dto = coworkService.getPermission(coworkId);
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cowork permission not found");
        }
        return dto;
    }

    @PutMapping("/cowork-permissions/{coworkId}")
    public void savePermission(@PathVariable String coworkId, @RequestBody CoworkPermissionDto body) {
        body.setCoworkId(coworkId);
        if (body.getLlm() == null || body.getLlm().trim().isEmpty()) {
            body.setLlm("[]");
        }
        coworkService.savePermission(body);
    }

    @DeleteMapping("/cowork-permissions/{coworkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePermission(@PathVariable String coworkId) {
        if (!coworkService.deletePermission(coworkId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cowork permission not found");
        }
    }
}
