package com.toggle.controller;

import com.toggle.dto.store.StoreLookupResponse;
import com.toggle.global.response.ApiResponse;
import com.toggle.service.StoreService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminStoreController {

    private final StoreService storeService;

    public AdminStoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    @GetMapping("/stores")
    public ApiResponse<StoreLookupResponse> listStores() {
        return ApiResponse.ok(storeService.listStoresForAdmin());
    }
}
