package com.toggle.controller;

import com.toggle.dto.favorite.FavoriteStoreListResponse;
import com.toggle.dto.favorite.FavoriteStoreResponse;
import com.toggle.global.response.ApiResponse;
import com.toggle.service.FavoriteService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/favorites/stores")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @PostMapping("/{storeId}")
    public ApiResponse<FavoriteStoreResponse> addFavorite(
        @RequestHeader(value = "X-User-Id", required = false) Long userId,
        @PathVariable Long storeId
    ) {
        return ApiResponse.ok(favoriteService.addFavorite(userId, storeId));
    }

    @DeleteMapping("/{storeId}")
    public ApiResponse<FavoriteStoreResponse> removeFavorite(
        @RequestHeader(value = "X-User-Id", required = false) Long userId,
        @PathVariable Long storeId
    ) {
        return ApiResponse.ok(favoriteService.removeFavorite(userId, storeId));
    }

    @GetMapping
    public ApiResponse<FavoriteStoreListResponse> getFavoriteStores(
        @RequestHeader(value = "X-User-Id", required = false) Long userId
    ) {
        return ApiResponse.ok(favoriteService.getFavoriteStores(userId));
    }
}
