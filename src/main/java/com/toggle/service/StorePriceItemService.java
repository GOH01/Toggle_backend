package com.toggle.service;

import com.toggle.dto.store.StorePriceItemItemResponse;
import com.toggle.dto.store.StorePriceItemResponse;
import com.toggle.dto.store.StorePriceItemUpsertItemRequest;
import com.toggle.dto.store.StorePriceItemUpsertRequest;
import com.toggle.entity.Store;
import com.toggle.entity.StorePriceItem;
import com.toggle.entity.User;
import com.toggle.entity.UserRole;
import com.toggle.global.exception.ApiException;
import com.toggle.global.util.ImageUrlMapper;
import com.toggle.repository.OwnerStoreLinkRepository;
import com.toggle.repository.StorePriceItemRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StorePriceItemService {

    private static final String PRICE_ITEM_NOT_SUPPORTED_CODE = "PRICE_ITEM_NOT_SUPPORTED";
    private static final String PRICE_ITEM_NOT_EDITABLE_CODE = "PRICE_ITEM_NOT_EDITABLE";
    private static final String PRICE_ITEM_ACCESS_DENIED_CODE = "PRICE_ITEM_ACCESS_DENIED";

    private final StoreService storeService;
    private final OwnerStoreLinkRepository ownerStoreLinkRepository;
    private final StorePriceItemRepository storePriceItemRepository;
    private final StoreEligibilityService storeEligibilityService;
    private final S3FileService s3FileService;

    public StorePriceItemService(
        StoreService storeService,
        OwnerStoreLinkRepository ownerStoreLinkRepository,
        StorePriceItemRepository storePriceItemRepository,
        StoreEligibilityService storeEligibilityService,
        S3FileService s3FileService
    ) {
        this.storeService = storeService;
        this.ownerStoreLinkRepository = ownerStoreLinkRepository;
        this.storePriceItemRepository = storePriceItemRepository;
        this.storeEligibilityService = storeEligibilityService;
        this.s3FileService = s3FileService;
    }

    @Transactional(readOnly = true)
    public StorePriceItemResponse getStorePriceItems(Long storeId) {
        Store store = storeService.getRegisteredStore(storeId);
        StoreEligibilityService.StoreEligibilitySnapshot eligibility = storeEligibilityService.describe(store, false);
        boolean enabled = eligibility.priceItemEligible();
        List<StorePriceItemItemResponse> items = enabled
            ? storePriceItemRepository.findAllByStoreIdOrderByDisplayOrderAscIdAsc(storeId).stream()
                .map(this::toResponse)
                .toList()
            : List.of();

        return new StorePriceItemResponse(
            store.getId(),
            store.getName(),
            store.getCategoryName(),
            enabled,
            false,
            items,
            eligibility.operationalState(),
            eligibility.closureRequestStatus(),
            eligibility.priceItemEligible(),
            eligibility.priceItemEditable(),
            eligibility.priceItemEligibilityReason()
        );
    }

    @Transactional(readOnly = true)
    public StorePriceItemResponse getMyStorePriceItems(Long storeId, User ownerUser) {
        Store store = getOwnedStore(storeId, ownerUser);
        StoreEligibilityService.StoreEligibilitySnapshot eligibility = storeEligibilityService.describe(store, true);
        boolean enabled = eligibility.priceItemEligible();
        List<StorePriceItemItemResponse> items = enabled
            ? storePriceItemRepository.findAllByStoreIdOrderByDisplayOrderAscIdAsc(storeId).stream()
                .map(this::toResponse)
                .toList()
            : List.of();

        return new StorePriceItemResponse(
            store.getId(),
            store.getName(),
            store.getCategoryName(),
            enabled,
            eligibility.priceItemEditable(),
            items,
            eligibility.operationalState(),
            eligibility.closureRequestStatus(),
            eligibility.priceItemEligible(),
            eligibility.priceItemEditable(),
            eligibility.priceItemEligibilityReason()
        );
    }

    @Transactional
    public StorePriceItemResponse replaceMyStorePriceItems(Long storeId, User ownerUser, StorePriceItemUpsertRequest request) {
        Store store = getOwnedStore(storeId, ownerUser);
        StoreEligibilityService.StoreEligibilitySnapshot eligibility = storeEligibilityService.describe(store, true);
        if (!eligibility.priceItemEligible()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, PRICE_ITEM_NOT_SUPPORTED_CODE, "가격 정보는 비음식/비카페 매장에서만 관리할 수 있습니다.");
        }
        if (!eligibility.priceItemEditable()) {
            throw new ApiException(HttpStatus.CONFLICT, PRICE_ITEM_NOT_EDITABLE_CODE, "운영 종료 요청이 처리 중인 매장은 가격 정보를 수정할 수 없습니다.");
        }

        List<StorePriceItemUpsertItemRequest> priceItemRequests = request.priceItems() == null ? List.of() : request.priceItems();
        List<StorePriceItem> existingItems = storePriceItemRepository.findAllByStoreIdOrderByDisplayOrderAscIdAsc(storeId);
        storePriceItemRepository.deleteAllByStoreId(storeId);

        List<StorePriceItem> items = new ArrayList<>();
        for (int index = 0; index < priceItemRequests.size(); index += 1) {
            StorePriceItemUpsertItemRequest priceItemRequest = priceItemRequests.get(index);
            String imageUrl = normalizeImageUrl(priceItemRequest.imageUrl());
            items.add(new StorePriceItem(
                store,
                priceItemRequest.name().trim(),
                priceItemRequest.price(),
                Boolean.TRUE.equals(priceItemRequest.representative()),
                priceItemRequest.description() == null ? null : priceItemRequest.description().trim(),
                imageUrl,
                ImageUrlMapper.toObjectKey(imageUrl),
                priceItemRequest.displayOrder() == null ? index : priceItemRequest.displayOrder(),
                priceItemRequest.available() == null || priceItemRequest.available()
            ));
        }

        storePriceItemRepository.saveAll(items);
        schedulePriceItemImageCleanup(extractPriceItemImageObjectKeys(existingItems), extractPriceItemImageObjectKeys(items));
        return getMyStorePriceItems(storeId, ownerUser);
    }

    private Store getOwnedStore(Long storeId, User ownerUser) {
        if (ownerUser.getRole() != UserRole.OWNER) {
            throw new ApiException(HttpStatus.FORBIDDEN, PRICE_ITEM_ACCESS_DENIED_CODE, "점주만 가격 정보를 관리할 수 있습니다.");
        }

        return ownerStoreLinkRepository.findByOwnerUserIdAndStoreIdAndStoreDeletedAtIsNull(ownerUser.getId(), storeId)
            .map(link -> link.getStore())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "STORE_NOT_FOUND", "매장을 찾을 수 없습니다."));
    }

    private StorePriceItemItemResponse toResponse(StorePriceItem priceItem) {
        String imageSource = priceItem.getImageUrl() != null ? priceItem.getImageUrl() : priceItem.getImageObjectKey();
        return new StorePriceItemItemResponse(
            priceItem.getId(),
            priceItem.getName(),
            priceItem.getPrice(),
            priceItem.isRepresentative(),
            priceItem.getDescription(),
            ImageUrlMapper.toBrowserUrl(imageSource),
            priceItem.getDisplayOrder(),
            priceItem.isAvailable()
        );
    }

    private List<String> extractPriceItemImageObjectKeys(List<StorePriceItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        return items.stream()
            .map(this::resolvePriceItemImageObjectKey)
            .filter(key -> key != null && !key.isBlank())
            .map(String::trim)
            .distinct()
            .toList();
    }

    private void schedulePriceItemImageCleanup(List<String> removedObjectKeys, List<String> nextObjectKeys) {
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

    private String resolvePriceItemImageObjectKey(StorePriceItem priceItem) {
        if (priceItem == null) {
            return null;
        }

        String storedKey = priceItem.getImageObjectKey();
        if (storedKey != null && !storedKey.isBlank()) {
            return storedKey.trim();
        }

        return ImageUrlMapper.toObjectKey(priceItem.getImageUrl());
    }

    private String normalizeImageUrl(String imageUrl) {
        if (imageUrl == null) {
            return null;
        }

        String normalized = imageUrl.trim();
        return normalized.isBlank() ? null : normalized;
    }
}
