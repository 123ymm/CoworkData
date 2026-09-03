package com.huawei.coworkdata.controller;

import com.huawei.coworkdata.dto.UserProfileDto;
import com.huawei.coworkdata.service.UserProfileService;
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
 * 地端 OAuth 用户字典：user_id ↔ username。
 */
@RestController
@RequestMapping("/api/user-profiles")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping
    public List<UserProfileDto> listAll() {
        return userProfileService.listAll();
    }

    @GetMapping("/{userId}")
    public UserProfileDto get(@PathVariable String userId) {
        UserProfileDto dto = userProfileService.get(userId);
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User profile not found");
        }
        return dto;
    }

    @PutMapping("/{userId}")
    public void save(@PathVariable String userId, @RequestBody UserProfileDto body) {
        body.setUserId(userId);
        if (body.getUsername() == null || body.getUsername().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username is required");
        }
        userProfileService.save(body);
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String userId) {
        if (!userProfileService.delete(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User profile not found");
        }
    }
}
