package com.toggle.service;

import com.toggle.dto.store.StoreDetailResponse;
import com.toggle.dto.store.StoreMenuResponse;
import com.toggle.dto.store.StorePriceItemResponse;
import com.toggle.entity.Store;
import com.toggle.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreDetailService {

    private final StoreService storeService;
    private final StoreMenuService storeMenuService;
    private final StorePriceItemService storePriceItemService;
    private final StoreEligibilityService storeEligibilityService;

    public StoreDetailService(
        StoreService storeService,
        StoreMenuService storeMenuService,
        StorePriceItemService storePriceItemService,
        StoreEligibilityService storeEligibilityService
    ) {
        this.storeService = storeService;
        this.storeMenuService = storeMenuService;
        this.storePriceItemService = storePriceItemService;
        this.storeEligibilityService = storeEligibilityService;
    }

    @Transactional(readOnly = true)
    public StoreDetailResponse getStoreDetail(Long storeId) {
        Store store = storeService.getRegisteredStore(storeId);
        return toDetail(store, false,
            storeMenuService.getStoreMenus(storeId),
            storePriceItemService.getStorePriceItems(storeId));
    }

    @Transactional(readOnly = true)
    public StoreDetailResponse getMyStoreDetail(User ownerUser, Long storeId) {
        Store store = storeService.getRegisteredStore(storeId);
        return toDetail(store, true,
            storeMenuService.getMyStoreMenus(storeId, ownerUser),
            storePriceItemService.getMyStorePriceItems(storeId, ownerUser));
    }

    private StoreDetailResponse toDetail(
        Store store,
        boolean ownerLinked,
        StoreMenuResponse menuResponse,
        StorePriceItemResponse priceItemResponse
    ) {
        StoreEligibilityService.StoreEligibilitySnapshot menuEligibility = storeEligibilityService.describe(store, ownerLinked);
        StoreEligibilityService.StorePriceItemEligibilitySnapshot priceItemEligibility = storeEligibilityService.describePriceItems(store, ownerLinked);
        String storeType = menuEligibility.menuEligible() ? "FOOD_CAFE" : "NON_FOOD";

        return new StoreDetailResponse(
            store.getId(),
            store.getName(),
            store.getCategoryName(),
            storeType,
            menuResponse.items(),
            priceItemResponse.items(),
            menuEligibility.operationalState(),
            menuEligibility.closureRequestStatus(),
            menuEligibility.menuEligible(),
            menuEligibility.menuEditable(),
            priceItemEligibility.priceItemEligible(),
            priceItemEligibility.priceItemEditable(),
            menuEligibility.menuEligibilityReason(),
            priceItemEligibility.priceItemEligibilityReason()
        );
    }
}
