package com.toggle.service;

import com.toggle.dto.store.StoreClosureRequestCreateRequest;
import com.toggle.dto.store.StoreClosureRequestRejectRequest;
import com.toggle.dto.store.StoreClosureRequestResponse;
import com.toggle.entity.Store;
import com.toggle.entity.StoreClosureRequest;
import com.toggle.entity.StoreClosureRequestStatus;
import com.toggle.entity.StoreOperationalState;
import com.toggle.entity.User;
import com.toggle.entity.UserRole;
import com.toggle.global.exception.ApiException;
import com.toggle.repository.OwnerStoreLinkRepository;
import com.toggle.repository.StoreClosureRequestRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreClosureRequestService {

    private static final String OWNER_ONLY_CODE = "OWNER_ONLY";
    private static final String ADMIN_ONLY_CODE = "ADMIN_ONLY";
    private static final String STORE_ACCESS_DENIED_CODE = "STORE_ACCESS_DENIED";
    private static final String STORE_CLOSURE_REQUEST_ALREADY_PENDING_CODE = "STORE_CLOSURE_REQUEST_ALREADY_PENDING";
    private static final String STORE_CLOSURE_REQUEST_NOT_FOUND_CODE = "STORE_CLOSURE_REQUEST_NOT_FOUND";
    private static final String STORE_CLOSURE_REQUEST_NOT_PENDING_CODE = "STORE_CLOSURE_REQUEST_NOT_PENDING";

    private final StoreService storeService;
    private final OwnerStoreLinkRepository ownerStoreLinkRepository;
    private final StoreClosureRequestRepository storeClosureRequestRepository;
    private final StoreEligibilityService storeEligibilityService;

    public StoreClosureRequestService(
        StoreService storeService,
        OwnerStoreLinkRepository ownerStoreLinkRepository,
        StoreClosureRequestRepository storeClosureRequestRepository,
        StoreEligibilityService storeEligibilityService
    ) {
        this.storeService = storeService;
        this.ownerStoreLinkRepository = ownerStoreLinkRepository;
        this.storeClosureRequestRepository = storeClosureRequestRepository;
        this.storeEligibilityService = storeEligibilityService;
    }

    @Transactional
    public StoreClosureRequestResponse createRequest(User ownerUser, Long storeId, StoreClosureRequestCreateRequest request) {
        assertOwner(ownerUser);

        Store store = storeService.getRegisteredStoreForUpdate(storeId);
        ownerStoreLinkRepository.findByOwnerUserIdAndStoreIdAndStoreDeletedAtIsNull(ownerUser.getId(), storeId)
            .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, STORE_ACCESS_DENIED_CODE, "해당 매장을 관리할 권한이 없습니다."));

        if (storeClosureRequestRepository.existsByStoreIdAndStatus(storeId, StoreClosureRequestStatus.PENDING)) {
            throw new ApiException(HttpStatus.CONFLICT, STORE_CLOSURE_REQUEST_ALREADY_PENDING_CODE, "이미 처리 중인 운영 종료 요청이 있습니다.");
        }

        StoreClosureRequest closureRequest = storeClosureRequestRepository.save(new StoreClosureRequest(
            store,
            ownerUser,
            normalizeReason(request.reason())
        ));
        return toResponse(closureRequest);
    }

    @Transactional(readOnly = true)
    public StoreClosureRequestResponse getLatestRequest(User ownerUser, Long storeId) {
        assertOwner(ownerUser);
        return storeClosureRequestRepository.findTopByOwnerUserIdAndStoreIdOrderByCreatedAtDesc(ownerUser.getId(), storeId)
            .map(this::toResponse)
            .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<StoreClosureRequestResponse> listRequests(User adminUser, StoreClosureRequestStatus status) {
        assertAdmin(adminUser);
        return storeClosureRequestRepository.findAllByStatusOrderByCreatedAtDesc(status).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public StoreClosureRequestResponse approveRequest(Long requestId, User adminUser) {
        assertAdmin(adminUser);
        StoreClosureRequest closureRequest = getPendingRequestForUpdate(requestId);

        storeService.softDeleteStore(
            closureRequest.getStore().getId(),
            adminUser,
            closureRequest.getRequestReason()
        );
        closureRequest.approve(adminUser, closureRequest.getRequestReason());
        return toResponse(storeClosureRequestRepository.save(closureRequest));
    }

    @Transactional
    public StoreClosureRequestResponse rejectRequest(Long requestId, User adminUser, StoreClosureRequestRejectRequest request) {
        assertAdmin(adminUser);
        StoreClosureRequest closureRequest = getPendingRequestForUpdate(requestId);
        closureRequest.reject(adminUser, normalizeReason(request.reason()));
        return toResponse(storeClosureRequestRepository.save(closureRequest));
    }

    private StoreClosureRequest getPendingRequestForUpdate(Long requestId) {
        StoreClosureRequest closureRequest = storeClosureRequestRepository.findByIdForUpdate(requestId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, STORE_CLOSURE_REQUEST_NOT_FOUND_CODE, "운영 종료 요청을 찾을 수 없습니다."));
        if (closureRequest.getStatus() != StoreClosureRequestStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, STORE_CLOSURE_REQUEST_NOT_PENDING_CODE, "처리 중인 요청만 승인 또는 반려할 수 있습니다.");
        }
        return closureRequest;
    }

    private StoreClosureRequestResponse toResponse(StoreClosureRequest closureRequest) {
        Store store = closureRequest.getStore();
        boolean ownerLinked = closureRequest.getOwnerUser() != null
            && ownerStoreLinkRepository.existsByStoreIdAndStoreDeletedAtIsNull(store.getId());
        StoreEligibilityService.StoreEligibilitySnapshot eligibility = storeEligibilityService.describe(store, ownerLinked);
        return new StoreClosureRequestResponse(
            closureRequest.getId(),
            store.getId(),
            store.getName(),
            closureRequest.getOwnerUser().getId(),
            closureRequest.getOwnerUser().getOwnerDisplayName() == null
                ? closureRequest.getOwnerUser().getNickname()
                : closureRequest.getOwnerUser().getOwnerDisplayName(),
            closureRequest.getOwnerUser().getEmail(),
            closureRequest.getRequestReason(),
            closureRequest.getStatus().name(),
            closureRequest.getReviewedBy() == null ? null : closureRequest.getReviewedBy().getId(),
            closureRequest.getReviewedBy() == null
                ? null
                : (closureRequest.getReviewedBy().getOwnerDisplayName() == null
                    ? closureRequest.getReviewedBy().getNickname()
                    : closureRequest.getReviewedBy().getOwnerDisplayName()),
            closureRequest.getReviewReason(),
            closureRequest.getCreatedAt(),
            closureRequest.getReviewedAt(),
            eligibility.operationalState(),
            eligibility.menuEligible(),
            eligibility.menuEditable(),
            eligibility.menuEligibilityReason()
        );
    }

    private String normalizeReason(String reason) {
        if (reason == null) {
            return null;
        }

        String trimmed = reason.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void assertOwner(User user) {
        if (user.getRole() != UserRole.OWNER) {
            throw new ApiException(HttpStatus.FORBIDDEN, OWNER_ONLY_CODE, "점주 계정만 처리할 수 있습니다.");
        }
    }

    private void assertAdmin(User user) {
        if (user.getRole() != UserRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, ADMIN_ONLY_CODE, "관리자만 처리할 수 있습니다.");
        }
    }
}
