package com.toggle.service;

import com.toggle.entity.Store;
import com.toggle.entity.StoreClosureRequestStatus;
import com.toggle.entity.StoreOperationalState;
import com.toggle.repository.StoreClosureRequestRepository;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreEligibilityService {

    private static final String MENU_NOT_REGISTERED_REASON = "STORE_NOT_REGISTERED";
    private static final String MENU_NOT_ACTIVE_REASON = "STORE_INACTIVE";
    private static final String MENU_NOT_SUPPORTED_REASON = "MENU_CATEGORY_NOT_SUPPORTED";
    private static final String MENU_EDITING_DISABLED_REASON = "CLOSURE_REQUEST_PENDING";

    private final StoreClosureRequestRepository storeClosureRequestRepository;

    public StoreEligibilityService(StoreClosureRequestRepository storeClosureRequestRepository) {
        this.storeClosureRequestRepository = storeClosureRequestRepository;
    }

    @Transactional(readOnly = true)
    public StoreEligibilitySnapshot describe(Store store, boolean ownerLinked) {
        StoreOperationalState operationalState = resolveOperationalState(store);
        String closureRequestStatus = resolveClosureRequestStatus(store);
        boolean menuEligible = store.isVerified() && !store.isDeleted() && supportsMenus(store);
        boolean menuEditable = menuEligible && ownerLinked && operationalState == StoreOperationalState.ACTIVE;
        String menuEligibilityReason = resolveMenuEligibilityReason(store, ownerLinked, operationalState, menuEditable);

        return new StoreEligibilitySnapshot(
            operationalState.name(),
            closureRequestStatus,
            menuEligible,
            menuEditable,
            menuEligibilityReason
        );
    }

    @Transactional(readOnly = true)
    public StoreOperationalState resolveOperationalState(Store store) {
        if (store.isDeleted()) {
            return StoreOperationalState.INACTIVE;
        }

        return storeClosureRequestRepository.findTopByStoreIdOrderByCreatedAtDesc(store.getId())
            .filter(request -> request.getStatus() == StoreClosureRequestStatus.PENDING)
            .map(request -> StoreOperationalState.CLOSURE_REQUESTED)
            .orElse(StoreOperationalState.ACTIVE);
    }

    @Transactional(readOnly = true)
    public String resolveClosureRequestStatus(Store store) {
        return storeClosureRequestRepository.findTopByStoreIdOrderByCreatedAtDesc(store.getId())
            .map(request -> request.getStatus().name())
            .orElse(null);
    }

    private String resolveMenuEligibilityReason(
        Store store,
        boolean ownerLinked,
        StoreOperationalState operationalState,
        boolean menuEditable
    ) {
        if (!store.isVerified()) {
            return MENU_NOT_REGISTERED_REASON;
        }
        if (store.isDeleted()) {
            return MENU_NOT_ACTIVE_REASON;
        }
        if (!supportsMenus(store)) {
            return MENU_NOT_SUPPORTED_REASON;
        }
        if (ownerLinked && !menuEditable && operationalState == StoreOperationalState.CLOSURE_REQUESTED) {
            return MENU_EDITING_DISABLED_REASON;
        }
        return null;
    }

    private boolean supportsMenus(Store store) {
        String categoryName = store.getCategoryName();
        if (categoryName == null || categoryName.isBlank()) {
            return false;
        }

        String normalized = categoryName.toLowerCase(Locale.ROOT);
        return normalized.contains("음식점")
            || normalized.contains("카페")
            || normalized.contains("restaurant")
            || normalized.contains("cafe")
            || normalized.contains("coffee")
            || normalized.contains("food");
    }

    public record StoreEligibilitySnapshot(
        String operationalState,
        String closureRequestStatus,
        boolean menuEligible,
        boolean menuEditable,
        String menuEligibilityReason
    ) {
    }
}
