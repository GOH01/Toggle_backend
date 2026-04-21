package com.toggle.controller;

import com.toggle.dto.auth.MeResponse;
import com.toggle.dto.user.UpdateMyMapProfileRequest;
import com.toggle.global.response.ApiResponse;
import com.toggle.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @PutMapping("/me/map-profile")
    public ApiResponse<MeResponse.MapProfile> updateMyMapProfile(@Valid @RequestBody UpdateMyMapProfileRequest request) {
        return ApiResponse.ok(authService.updateMyMapProfile(request));
    }
}
