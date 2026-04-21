package com.toggle.controller;

import com.toggle.dto.auth.MeResponse;
import com.toggle.dto.user.MyMapPlaceResponse;
import com.toggle.dto.user.MyMapResponse;
import com.toggle.dto.user.UpdateMyMapProfileRequest;
import com.toggle.dto.user.UserPublicMapResponse;
import com.toggle.global.response.ApiResponse;
import com.toggle.service.AuthService;
import com.toggle.service.MyMapService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MyMapController {

    private final AuthService authService;
    private final MyMapService myMapService;

    public MyMapController(AuthService authService, MyMapService myMapService) {
        this.authService = authService;
        this.myMapService = myMapService;
    }

    @GetMapping("/api/v1/my-map")
    public ApiResponse<MyMapResponse> getMyMap() {
        return ApiResponse.ok(myMapService.getMyMap());
    }

    @PutMapping("/api/v1/my-map/profile")
    public ApiResponse<MeResponse.MapProfile> updateMyMapProfile(@Valid @RequestBody UpdateMyMapProfileRequest request) {
        return ApiResponse.ok(authService.updateMyMapProfile(request));
    }

    @PostMapping("/api/v1/my-map/stores/{storeId}")
    public ApiResponse<MyMapPlaceResponse> addStore(@PathVariable Long storeId) {
        return ApiResponse.ok(myMapService.addStore(storeId));
    }

    @DeleteMapping("/api/v1/my-map/stores/{storeId}")
    public ApiResponse<MyMapPlaceResponse> removeStore(@PathVariable Long storeId) {
        return ApiResponse.ok(myMapService.removeStore(storeId));
    }

    @PostMapping("/api/v1/my-map/publics/{publicInstitutionId}")
    public ApiResponse<MyMapPlaceResponse> addPublicInstitution(@PathVariable Long publicInstitutionId) {
        return ApiResponse.ok(myMapService.addPublicInstitution(publicInstitutionId));
    }

    @DeleteMapping("/api/v1/my-map/publics/{publicInstitutionId}")
    public ApiResponse<MyMapPlaceResponse> removePublicInstitution(@PathVariable Long publicInstitutionId) {
        return ApiResponse.ok(myMapService.removePublicInstitution(publicInstitutionId));
    }

    @GetMapping({"/api/v1/public-maps/{publicMapId}", "/api/v1/users/public-maps/{publicMapId}"})
    public ApiResponse<UserPublicMapResponse> getPublicMap(@PathVariable String publicMapId) {
        return ApiResponse.ok(myMapService.getPublicMap(publicMapId));
    }
}
