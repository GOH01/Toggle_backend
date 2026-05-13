package com.toggle.controller;

import com.toggle.dto.auth.ChangePasswordRequest;
import com.toggle.dto.auth.MeResponse;
import com.toggle.dto.auth.SimpleMessageResponse;
import com.toggle.dto.auth.UpdateNicknameRequest;
import com.toggle.dto.auth.UserProfileResponse;
import com.toggle.dto.user.UpdateMyMapProfileRequest;
import com.toggle.global.response.ApiResponse;
import com.toggle.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @PatchMapping("/me/nickname")
    public ApiResponse<UserProfileResponse> updateNickname(@Valid @RequestBody UpdateNicknameRequest request) {
        return ApiResponse.ok(authService.updateNickname(request));
    }

    @PatchMapping("/me/password")
    public ApiResponse<SimpleMessageResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return ApiResponse.ok(authService.changePassword(request));
    }

    @PatchMapping(value = "/me/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UserProfileResponse> updateProfileImage(@RequestPart("profileImage") MultipartFile profileImage) {
        return ApiResponse.ok(authService.updateProfileImage(profileImage));
    }

    @DeleteMapping("/me")
    public ApiResponse<SimpleMessageResponse> deleteMe() {
        return ApiResponse.ok(authService.deactivateCurrentUser());
    }

    @PutMapping("/me/map-profile")
    public ApiResponse<MeResponse.MapProfile> updateMyMapProfile(@Valid @RequestBody UpdateMyMapProfileRequest request) {
        return ApiResponse.ok(authService.updateMyMapProfile(request));
    }
}
