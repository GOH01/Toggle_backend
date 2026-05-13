package com.toggle.controller;

import com.toggle.dto.map.MapLikeResponse;
import com.toggle.dto.map.PublicMapListResponse;
import com.toggle.dto.map.CreateMyMapRequest;
import com.toggle.dto.map.UpdateUserMapMetadataRequest;
import com.toggle.dto.map.UserMapDetailResponse;
import com.toggle.dto.map.UserMapSummaryResponse;
import com.toggle.dto.map.UserMapUpsertRequest;
import com.toggle.dto.user.MyMapPlaceResponse;
import com.toggle.dto.user.UserNicknameSearchResponse;
import com.toggle.entity.User;
import com.toggle.global.response.ApiResponse;
import com.toggle.service.AuthService;
import com.toggle.service.UserMapService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class UserMapController {

    private final AuthService authService;
    private final UserMapService userMapService;

    public UserMapController(AuthService authService, UserMapService userMapService) {
        this.authService = authService;
        this.userMapService = userMapService;
    }

    @PostMapping("/my-maps")
    public ApiResponse<UserMapSummaryResponse> createMyMap(@Valid @RequestBody CreateMyMapRequest request) {
        User user = authService.getAuthenticatedUser();
        return ApiResponse.ok(userMapService.createMyMap(user, request));
    }

    @GetMapping("/my-maps")
    public ApiResponse<java.util.List<UserMapSummaryResponse>> getMyMaps() {
        User user = authService.getAuthenticatedUser();
        return ApiResponse.ok(userMapService.getMyMaps(user));
    }

    @GetMapping("/my-maps/{mapId}")
    public ApiResponse<UserMapDetailResponse> getMyMap(@PathVariable Long mapId) {
        User user = authService.getAuthenticatedUser();
        return ApiResponse.ok(userMapService.getMyMap(mapId, user));
    }

    @PutMapping("/my-maps/{mapId}")
    public ApiResponse<UserMapSummaryResponse> updateMyMap(
        @PathVariable Long mapId,
        @Valid @RequestBody UserMapUpsertRequest request
    ) {
        User user = authService.getAuthenticatedUser();
        return ApiResponse.ok(userMapService.updateMyMap(mapId, user, request));
    }

    @PatchMapping("/maps/{mapId}")
    public ApiResponse<UserMapSummaryResponse> updateMapMetadata(
        @PathVariable Long mapId,
        @Valid @RequestBody UpdateUserMapMetadataRequest request
    ) {
        User user = authService.getAuthenticatedUser();
        return ApiResponse.ok(userMapService.updateMyMapMetadata(mapId, user, request));
    }

    @PatchMapping(value = "/maps/{mapId}/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UserMapSummaryResponse> updateMapProfileImage(
        @PathVariable Long mapId,
        @RequestPart("profileImage") MultipartFile profileImage
    ) {
        User user = authService.getAuthenticatedUser();
        return ApiResponse.ok(userMapService.updateMyMapProfileImage(mapId, user, profileImage));
    }

    @DeleteMapping("/my-maps/{mapId}")
    public ApiResponse<Void> deleteMyMap(@PathVariable Long mapId) {
        User user = authService.getAuthenticatedUser();
        userMapService.deleteMyMap(mapId, user);
        return ApiResponse.ok(null);
    }

    @PostMapping("/my-maps/{mapId}/stores/{storeId}")
    public ApiResponse<MyMapPlaceResponse> addStoreToMap(@PathVariable Long mapId, @PathVariable Long storeId) {
        User user = authService.getAuthenticatedUser();
        return ApiResponse.ok(userMapService.addStoreToMap(mapId, storeId, user));
    }

    @DeleteMapping("/my-maps/{mapId}/stores/{storeId}")
    public ApiResponse<MyMapPlaceResponse> removeStoreFromMap(@PathVariable Long mapId, @PathVariable Long storeId) {
        User user = authService.getAuthenticatedUser();
        return ApiResponse.ok(userMapService.removeStoreFromMap(mapId, storeId, user));
    }

    @PostMapping("/my-maps/{mapId}/public-institutions/{publicInstitutionId}")
    public ApiResponse<MyMapPlaceResponse> addPublicInstitutionToMap(
        @PathVariable Long mapId,
        @PathVariable Long publicInstitutionId
    ) {
        User user = authService.getAuthenticatedUser();
        return ApiResponse.ok(userMapService.addPublicInstitutionToMap(mapId, publicInstitutionId, user));
    }

    @DeleteMapping("/my-maps/{mapId}/public-institutions/{publicInstitutionId}")
    public ApiResponse<MyMapPlaceResponse> removePublicInstitutionFromMap(
        @PathVariable Long mapId,
        @PathVariable Long publicInstitutionId
    ) {
        User user = authService.getAuthenticatedUser();
        return ApiResponse.ok(userMapService.removePublicInstitutionFromMap(mapId, publicInstitutionId, user));
    }

    @GetMapping("/maps")
    public ApiResponse<PublicMapListResponse> listPublicMaps(
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "latest") String sort,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(userMapService.listPublicMaps(keyword, sort, page, size));
    }

    @GetMapping("/maps/{mapId}")
    public ApiResponse<UserMapDetailResponse> getPublicMap(@PathVariable Long mapId) {
        return ApiResponse.ok(userMapService.getPublicMap(mapId));
    }

    @GetMapping("/maps/search/users")
    public ApiResponse<UserNicknameSearchResponse> searchUsersByNickname(@RequestParam String nickname) {
        return ApiResponse.ok(userMapService.searchUsersByNickname(nickname));
    }

    @PostMapping("/maps/{mapId}/likes")
    public ApiResponse<MapLikeResponse> likeMap(@PathVariable Long mapId) {
        User user = authService.getAuthenticatedUser();
        return ApiResponse.ok(userMapService.likeMap(mapId, user));
    }

    @DeleteMapping("/maps/{mapId}/likes")
    public ApiResponse<MapLikeResponse> unlikeMap(@PathVariable Long mapId) {
        User user = authService.getAuthenticatedUser();
        return ApiResponse.ok(userMapService.unlikeMap(mapId, user));
    }

    @GetMapping("/maps/{mapId}/likes")
    public ApiResponse<MapLikeResponse> getLikes(@PathVariable Long mapId) {
        return ApiResponse.ok(userMapService.getLikes(mapId));
    }
}
