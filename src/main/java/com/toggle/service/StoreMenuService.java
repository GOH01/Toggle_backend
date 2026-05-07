package com.toggle.service;

import com.toggle.dto.store.StoreMenuItemResponse;
import com.toggle.dto.store.StoreMenuResponse;
import com.toggle.dto.store.StoreMenuUpsertItemRequest;
import com.toggle.dto.store.StoreMenuUpsertRequest;
import com.toggle.entity.Store;
import com.toggle.entity.StoreMenu;
import com.toggle.entity.User;
import com.toggle.entity.UserRole;
import com.toggle.global.exception.ApiException;
import com.toggle.global.util.ImageUrlMapper;
import com.toggle.repository.OwnerStoreLinkRepository;
import com.toggle.repository.StoreMenuRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreMenuService {

    private static final String MENU_NOT_SUPPORTED_CODE = "MENU_NOT_SUPPORTED";
    private static final String MENU_NOT_EDITABLE_CODE = "MENU_NOT_EDITABLE";
    private static final String MENU_ACCESS_DENIED_CODE = "MENU_ACCESS_DENIED";

    private final StoreService storeService;
    private final OwnerStoreLinkRepository ownerStoreLinkRepository;
    private final StoreMenuRepository storeMenuRepository;
    private final StoreEligibilityService storeEligibilityService;
    private final S3FileService s3FileService;

    public StoreMenuService(
        StoreService storeService,
        OwnerStoreLinkRepository ownerStoreLinkRepository,
        StoreMenuRepository storeMenuRepository,
        StoreEligibilityService storeEligibilityService,
        S3FileService s3FileService
    ) {
        this.storeService = storeService;
        this.ownerStoreLinkRepository = ownerStoreLinkRepository;
        this.storeMenuRepository = storeMenuRepository;
        this.storeEligibilityService = storeEligibilityService;
        this.s3FileService = s3FileService;
    }

    @Transactional(readOnly = true)
    public StoreMenuResponse getStoreMenus(Long storeId) {
        Store store = storeService.getRegisteredStore(storeId);
        StoreEligibilityService.StoreEligibilitySnapshot eligibility = storeEligibilityService.describe(store, false);
        boolean enabled = eligibility.menuEligible();
        List<StoreMenuItemResponse> items = enabled
            ? storeMenuRepository.findAllByStoreIdOrderByDisplayOrderAscIdAsc(storeId).stream()
                .map(this::toResponse)
                .toList()
            : List.of();

        return new StoreMenuResponse(
            store.getId(),
            store.getName(),
            store.getCategoryName(),
            enabled,
            false,
            items,
            eligibility.operationalState(),
            eligibility.closureRequestStatus(),
            eligibility.menuEligible(),
            eligibility.menuEditable(),
            eligibility.menuEligibilityReason()
        );
    }

    @Transactional(readOnly = true)
    public StoreMenuResponse getMyStoreMenus(Long storeId, User ownerUser) {
        Store store = getOwnedStore(storeId, ownerUser);
        StoreEligibilityService.StoreEligibilitySnapshot eligibility = storeEligibilityService.describe(store, true);
        boolean enabled = eligibility.menuEligible();
        List<StoreMenuItemResponse> items = enabled
            ? storeMenuRepository.findAllByStoreIdOrderByDisplayOrderAscIdAsc(storeId).stream()
                .map(this::toResponse)
                .toList()
            : List.of();

        return new StoreMenuResponse(
            store.getId(),
            store.getName(),
            store.getCategoryName(),
            enabled,
            eligibility.menuEditable(),
            items,
            eligibility.operationalState(),
            eligibility.closureRequestStatus(),
            eligibility.menuEligible(),
            eligibility.menuEditable(),
            eligibility.menuEligibilityReason()
        );
    }

    @Transactional
    public StoreMenuResponse replaceMyStoreMenus(Long storeId, User ownerUser, StoreMenuUpsertRequest request) {
        Store store = getOwnedStore(storeId, ownerUser);
        StoreEligibilityService.StoreEligibilitySnapshot eligibility = storeEligibilityService.describe(store, true);
        if (!eligibility.menuEligible()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, MENU_NOT_SUPPORTED_CODE, "음식점/카페에서만 메뉴를 관리할 수 있습니다.");
        }
        if (!eligibility.menuEditable()) {
            throw new ApiException(HttpStatus.CONFLICT, MENU_NOT_EDITABLE_CODE, "운영 종료 요청이 처리 중인 매장은 메뉴를 수정할 수 없습니다.");
        }

        List<StoreMenuUpsertItemRequest> menuRequests = request.menus() == null ? List.of() : request.menus();
        List<StoreMenu> existingMenus = storeMenuRepository.findAllByStoreIdOrderByDisplayOrderAscIdAsc(storeId);
        storeMenuRepository.deleteAllByStoreId(storeId);

        List<StoreMenu> menus = new ArrayList<>();
        for (int index = 0; index < menuRequests.size(); index += 1) {
            StoreMenuUpsertItemRequest menuRequest = menuRequests.get(index);
            String imageUrl = normalizeImageUrl(menuRequest.imageUrl());
            menus.add(new StoreMenu(
                store,
                menuRequest.name().trim(),
                menuRequest.price(),
                Boolean.TRUE.equals(menuRequest.representative()),
                menuRequest.description() == null ? null : menuRequest.description().trim(),
                imageUrl,
                ImageUrlMapper.toObjectKey(imageUrl),
                menuRequest.displayOrder() == null ? index : menuRequest.displayOrder(),
                menuRequest.available() == null || menuRequest.available()
            ));
        }

        storeMenuRepository.saveAll(menus);
        scheduleMenuImageCleanup(extractMenuImageObjectKeys(existingMenus), extractMenuImageObjectKeys(menus));
        return getMyStoreMenus(storeId, ownerUser);
    }

    private Store getOwnedStore(Long storeId, User ownerUser) {
        if (ownerUser.getRole() != UserRole.OWNER) {
            throw new ApiException(HttpStatus.FORBIDDEN, MENU_ACCESS_DENIED_CODE, "점주만 메뉴를 관리할 수 있습니다.");
        }

        return ownerStoreLinkRepository.findByOwnerUserIdAndStoreIdAndStoreDeletedAtIsNull(ownerUser.getId(), storeId)
            .map(link -> link.getStore())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "STORE_NOT_FOUND", "매장을 찾을 수 없습니다."));
    }

    private StoreMenuItemResponse toResponse(StoreMenu menu) {
        String imageSource = menu.getImageUrl() != null ? menu.getImageUrl() : menu.getImageObjectKey();
        return new StoreMenuItemResponse(
            menu.getId(),
            menu.getName(),
            menu.getPrice(),
            menu.isRepresentative(),
            menu.getDescription(),
            ImageUrlMapper.toBrowserUrl(imageSource),
            menu.getDisplayOrder(),
            menu.isAvailable()
        );
    }

    private List<String> extractMenuImageObjectKeys(List<StoreMenu> menus) {
        if (menus == null || menus.isEmpty()) {
            return List.of();
        }

        return menus.stream()
            .map(this::resolveMenuImageObjectKey)
            .filter(key -> key != null && !key.isBlank())
            .map(String::trim)
            .distinct()
            .toList();
    }

    private void scheduleMenuImageCleanup(List<String> removedObjectKeys, List<String> nextObjectKeys) {
        if (removedObjectKeys == null || removedObjectKeys.isEmpty()) {
            return;
        }

        List<String> keysToDelete = removedObjectKeys.stream()
            .filter(key -> key != null && !key.isBlank())
            .map(String::trim)
            .filter(key -> nextObjectKeys == null || !nextObjectKeys.contains(key))
            .distinct()
            .toList();

        if (!keysToDelete.isEmpty()) {
            s3FileService.deleteFilesAfterCommit(keysToDelete);
        }
    }

    private String resolveMenuImageObjectKey(StoreMenu menu) {
        if (menu == null) {
            return null;
        }

        String storedKey = menu.getImageObjectKey();
        if (storedKey != null && !storedKey.isBlank()) {
            return storedKey.trim();
        }

        return ImageUrlMapper.toObjectKey(menu.getImageUrl());
    }

    private String normalizeImageUrl(String imageUrl) {
        if (imageUrl == null) {
            return null;
        }

        String normalized = imageUrl.trim();
        return normalized.isBlank() ? null : normalized;
    }
}
