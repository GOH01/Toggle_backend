package com.toggle.controller;

import com.toggle.dto.auth.AuthTokenResponse;
import com.toggle.dto.auth.LogoutResponse;
import com.toggle.dto.auth.LoginRequest;
import com.toggle.dto.auth.MeResponse;
import com.toggle.dto.auth.RefreshTokenRequest;
import com.toggle.dto.auth.SignupRequest;
import com.toggle.dto.auth.SignupResponse;
import com.toggle.global.response.ApiResponse;
import com.toggle.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.ok(authService.signup(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthTokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ApiResponse<LogoutResponse> logout(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.ok(authService.logout(request));
    }

    @GetMapping("/me")
    public ApiResponse<MeResponse> me() {
        return ApiResponse.ok(authService.getCurrentUserProfile());
    }
}
