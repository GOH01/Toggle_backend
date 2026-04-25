package com.toggle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.toggle.dto.store.StoreClosureRequestCreateRequest;
import com.toggle.dto.store.StoreClosureRequestRejectRequest;
import com.toggle.dto.store.StoreClosureRequestResponse;
import com.toggle.entity.ExternalSource;
import com.toggle.entity.Store;
import com.toggle.entity.StoreClosureRequest;
import com.toggle.entity.StoreClosureRequestStatus;
import com.toggle.entity.User;
import com.toggle.entity.UserRole;
import com.toggle.entity.UserStatus;
import com.toggle.global.exception.ApiException;
import com.toggle.repository.OwnerStoreLinkRepository;
import com.toggle.repository.StoreClosureRequestRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class StoreClosureRequestServiceTest {

    @Mock
    private StoreService storeService;

    @Mock
    private OwnerStoreLinkRepository ownerStoreLinkRepository;

    @Mock
    private StoreClosureRequestRepository storeClosureRequestRepository;

    @Mock
    private StoreEligibilityService storeEligibilityService;

    @InjectMocks
    private StoreClosureRequestService storeClosureRequestService;

    @Test
    void createRequestShouldPersistPendingRequestAndBlockDuplicates() {
        User owner = buildUser(1L, UserRole.OWNER);
        Store store = buildStore(10L, "폐업 요청 매장");
        StoreClosureRequestCreateRequest request = new StoreClosureRequestCreateRequest("운영 종료를 요청합니다.");
        StoreClosureRequest persistedRequest = new StoreClosureRequest(store, owner, request.reason());
        ReflectionTestUtils.setField(persistedRequest, "id", 99L);

        when(storeService.getRegisteredStoreForUpdate(10L)).thenReturn(store);
        when(ownerStoreLinkRepository.findByOwnerUserIdAndStoreIdAndStoreDeletedAtIsNull(1L, 10L))
            .thenReturn(Optional.of(new com.toggle.entity.OwnerStoreLink(owner, store, null, null, 0, null)));
        when(storeClosureRequestRepository.existsByStoreIdAndStatus(10L, StoreClosureRequestStatus.PENDING)).thenReturn(false);
        when(storeClosureRequestRepository.save(any(StoreClosureRequest.class))).thenReturn(persistedRequest);
        when(storeEligibilityService.describe(any(Store.class), anyBoolean()))
            .thenReturn(snapshot("ACTIVE", null, true, true, null));

        StoreClosureRequestResponse response = storeClosureRequestService.createRequest(owner, 10L, request);

        assertThat(response.requestId()).isEqualTo(99L);
        assertThat(response.requestStatus()).isEqualTo("PENDING");
        assertThat(response.requestReason()).isEqualTo("운영 종료를 요청합니다.");

        when(storeClosureRequestRepository.existsByStoreIdAndStatus(10L, StoreClosureRequestStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> storeClosureRequestService.createRequest(owner, 10L, request))
            .isInstanceOf(ApiException.class)
            .satisfies(throwable -> {
                ApiException ex = (ApiException) throwable;
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(ex.getCode()).isEqualTo("STORE_CLOSURE_REQUEST_ALREADY_PENDING");
            });
    }

    @Test
    void getLatestRequestShouldReturnLatestDecisionWithoutActiveOwnerLink() {
        User owner = buildUser(1L, UserRole.OWNER);
        Store store = buildStore(10L, "폐업 완료 매장");
        StoreClosureRequest request = new StoreClosureRequest(store, owner, "운영 종료를 요청합니다.");
        ReflectionTestUtils.setField(request, "id", 123L);
        request.approve(buildUser(2L, UserRole.ADMIN), "승인");

        when(storeClosureRequestRepository.findTopByOwnerUserIdAndStoreIdOrderByCreatedAtDesc(1L, 10L))
            .thenReturn(Optional.of(request));
        when(storeEligibilityService.describe(any(Store.class), anyBoolean()))
            .thenReturn(snapshot("APPROVED", "APPROVED", true, false, null));

        StoreClosureRequestResponse response = storeClosureRequestService.getLatestRequest(owner, 10L);

        assertThat(response.requestId()).isEqualTo(123L);
        assertThat(response.requestStatus()).isEqualTo("APPROVED");
        assertThat(response.reviewReason()).isEqualTo("승인");
    }

    @Test
    void approveAndRejectShouldPersistReviewState() {
        User owner = buildUser(1L, UserRole.OWNER);
        User admin = buildUser(2L, UserRole.ADMIN);
        Store store = buildStore(10L, "리뷰 대상 매장");
        StoreClosureRequest pendingRequest = new StoreClosureRequest(store, owner, "운영 종료를 요청합니다.");
        ReflectionTestUtils.setField(pendingRequest, "id", 321L);

        when(storeClosureRequestRepository.findByIdForUpdate(321L)).thenReturn(Optional.of(pendingRequest));
        when(storeClosureRequestRepository.save(any(StoreClosureRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(storeEligibilityService.describe(any(Store.class), anyBoolean()))
            .thenReturn(snapshot("ACTIVE", null, true, true, null));

        StoreClosureRequestResponse approved = storeClosureRequestService.approveRequest(321L, admin);

        assertThat(approved.requestStatus()).isEqualTo("APPROVED");
        assertThat(approved.reviewedByUserId()).isEqualTo(2L);
        assertThat(pendingRequest.getStatus()).isEqualTo(StoreClosureRequestStatus.APPROVED);
        verify(storeService).softDeleteStore(10L, admin, "운영 종료를 요청합니다.");

        StoreClosureRequest pendingRejectRequest = new StoreClosureRequest(store, owner, "운영 종료를 다시 요청합니다.");
        ReflectionTestUtils.setField(pendingRejectRequest, "id", 322L);
        when(storeClosureRequestRepository.findByIdForUpdate(322L)).thenReturn(Optional.of(pendingRejectRequest));

        StoreClosureRequestResponse rejected = storeClosureRequestService.rejectRequest(
            322L,
            admin,
            new StoreClosureRequestRejectRequest("반려 사유")
        );

        assertThat(rejected.requestStatus()).isEqualTo("REJECTED");
        assertThat(rejected.reviewReason()).isEqualTo("반려 사유");
        assertThat(pendingRejectRequest.getStatus()).isEqualTo(StoreClosureRequestStatus.REJECTED);
    }

    @Test
    void nonOwnerOrAdminShouldBeRejected() {
        User member = buildUser(3L, UserRole.USER);

        assertThatThrownBy(() -> storeClosureRequestService.createRequest(member, 10L, new StoreClosureRequestCreateRequest("요청")))
            .isInstanceOf(ApiException.class)
            .satisfies(throwable -> {
                ApiException ex = (ApiException) throwable;
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(ex.getCode()).isEqualTo("OWNER_ONLY");
            });

        assertThatThrownBy(() -> storeClosureRequestService.listRequests(member, StoreClosureRequestStatus.PENDING))
            .isInstanceOf(ApiException.class)
            .satisfies(throwable -> {
                ApiException ex = (ApiException) throwable;
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(ex.getCode()).isEqualTo("ADMIN_ONLY");
            });
        verifyNoInteractions(ownerStoreLinkRepository);
    }

    private User buildUser(Long id, UserRole role) {
        User user = new User(role == UserRole.ADMIN ? "admin@test.com" : "owner@test.com", "password", "nickname", role, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Store buildStore(Long id, String name) {
        Store store = new Store(
            ExternalSource.KAKAO,
            "external-" + id,
            name,
            "02-1234-5678",
            "서울시 테스트구 테스트로 " + id,
            "서울시 테스트구 테스트로 " + id,
            new BigDecimal("37.1234567"),
            new BigDecimal("127.1234567")
        );
        ReflectionTestUtils.setField(store, "id", id);
        ReflectionTestUtils.setField(store, "categoryName", "카페");
        return store;
    }

    private StoreEligibilityService.StoreEligibilitySnapshot snapshot(
        String operationalState,
        String closureRequestStatus,
        boolean menuEligible,
        boolean menuEditable,
        String menuEligibilityReason
    ) {
        return new StoreEligibilityService.StoreEligibilitySnapshot(
            operationalState,
            closureRequestStatus,
            menuEligible,
            menuEditable,
            menuEligibilityReason
        );
    }
}
