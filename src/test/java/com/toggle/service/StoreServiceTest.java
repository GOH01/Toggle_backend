package com.toggle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toggle.dto.store.ResolveStoreRequest;
import com.toggle.dto.store.StoreLookupResponse;
import com.toggle.entity.BusinessStatus;
import com.toggle.entity.ExternalSource;
import com.toggle.entity.LiveStatusSource;
import com.toggle.entity.Store;
import com.toggle.global.exception.ApiException;
import org.springframework.test.util.ReflectionTestUtils;
import com.toggle.repository.FavoriteRepository;
import com.toggle.repository.StoreRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class StoreServiceTest {

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private FavoriteRepository favoriteRepository;

    @Mock
    private AddressNormalizer addressNormalizer;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private StoreService storeService;

    @BeforeEach
    void setUp() {
        lenient().when(addressNormalizer.normalize(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void getStoresByIdsShouldExposeReviewAverageAndCount() {
        Store store = new Store(
            ExternalSource.KAKAO,
            "store-average-1",
            "평균 별점 매장",
            "02-123-4567",
            "서울시 테스트구 테스트로 1",
            "서울시 테스트구 테스트로 1",
            new BigDecimal("37.1234567"),
            new BigDecimal("127.1234567")
        );
        ReflectionTestUtils.setField(store, "id", 1L);
        store.markVerified("서울시 테스트구 테스트로 1", "서울시 테스트구 테스트로 1", "카페", "{}", null);
        store.updateLiveBusinessStatus(BusinessStatus.OPEN, LiveStatusSource.SYSTEM);
        store.updateReviewSummary(new BigDecimal("4.7"), 12L);

        when(storeRepository.findAllByIdIn(anyList())).thenReturn(List.of(store));
        when(favoriteRepository.countByStoreId(1L)).thenReturn(34L);

        StoreLookupResponse response = storeService.getStoresByIds(List.of(1L));

        assertThat(response.stores()).hasSize(1);
        assertThat(response.stores().get(0).reviewAverageRating()).isEqualTo(4.7);
        assertThat(response.stores().get(0).reviewCount()).isEqualTo(12L);
        assertThat(response.stores().get(0).favoriteCount()).isEqualTo(34L);
    }

    @Test
    void getNearbyVerifiedStoresShouldReturnUpToRequestedLimit() {
        List<Store> stores = new ArrayList<>();
        for (int i = 1; i <= 31; i += 1) {
            Store store = new Store(
                ExternalSource.KAKAO,
                "nearby-store-" + i,
                "가까운 매장 " + i,
                "02-123-4567",
                "서울시 테스트구 테스트로 " + i,
                "서울시 테스트구 테스트로 " + i,
                new BigDecimal("37.1234567"),
                new BigDecimal("127.1234567")
            );
            ReflectionTestUtils.setField(store, "id", (long) i);
            store.markVerified("서울시 테스트구 테스트로 " + i, "서울시 테스트구 테스트로 " + i, "카페", "{}", null);
            stores.add(store);
        }

        when(storeRepository.findAllByIsVerifiedTrueAndLatitudeIsNotNullAndLongitudeIsNotNull()).thenReturn(stores);
        when(favoriteRepository.countByStoreId(anyLong())).thenReturn(0L);

        StoreLookupResponse response = storeService.getNearbyVerifiedStores(37.1234567, 127.1234567, 2000, 30);

        assertThat(response.stores()).hasSize(30);
    }

    @Test
    void getRegisteredStoreShouldReturnVerifiedStore() {
        Store store = new Store(
            ExternalSource.KAKAO,
            "store-registered-1",
            "등록된 매장",
            "02-123-4567",
            "서울시 테스트구 테스트로 1",
            "서울시 테스트구 테스트로 1",
            new BigDecimal("37.1234567"),
            new BigDecimal("127.1234567")
        );
        ReflectionTestUtils.setField(store, "id", 1L);
        store.markVerified("서울시 테스트구 테스트로 1", "서울시 테스트구 테스트로 1", "카페", "{}", null);

        when(storeRepository.findById(1L)).thenReturn(Optional.of(store));

        assertThat(storeService.getRegisteredStore(1L).getId()).isEqualTo(1L);
    }

    @Test
    void getRegisteredStoreShouldRejectPreviewStore() {
        Store store = new Store(
            ExternalSource.KAKAO,
            "store-preview-1",
            "미등록 매장",
            "02-123-4567",
            "서울시 테스트구 테스트로 2",
            "서울시 테스트구 테스트로 2",
            new BigDecimal("37.1234567"),
            new BigDecimal("127.1234567")
        );
        ReflectionTestUtils.setField(store, "id", 2L);

        when(storeRepository.findById(2L)).thenReturn(Optional.of(store));

        assertThatThrownBy(() -> storeService.getRegisteredStore(2L))
            .isInstanceOf(ApiException.class)
            .satisfies(throwable -> {
                ApiException ex = (ApiException) throwable;
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(ex.getCode()).isEqualTo("STORE_NOT_REGISTERED");
            });
    }

    @Test
    void resolveRegisteredStoreShouldReturnVerifiedStoreWithoutCreatingNewOne() {
        Store store = new Store(
            ExternalSource.KAKAO,
            "store-lookup-1",
            "조회용 매장",
            "02-123-4567",
            "서울시 테스트구 테스트로 3",
            "서울시 테스트구 테스트로 3",
            new BigDecimal("37.1234567"),
            new BigDecimal("127.1234567")
        );
        ReflectionTestUtils.setField(store, "id", 3L);
        store.markVerified("서울시 테스트구 테스트로 3", "서울시 테스트구 테스트로 3", "카페", "{}", null);

        when(storeRepository.findByExternalSourceAndExternalPlaceId(ExternalSource.KAKAO, "store-lookup-1"))
            .thenReturn(Optional.of(store));

        assertThat(storeService.resolveRegisteredStore(new ResolveStoreRequest(
            "KAKAO",
            "store-lookup-1",
            "조회용 매장",
            "서울시 테스트구 테스트로 3",
            "02-123-4567",
            new BigDecimal("37.1234567"),
            new BigDecimal("127.1234567")
        ))).satisfies(response -> {
            assertThat(response.storeId()).isEqualTo(3L);
            assertThat(response.resolved()).isTrue();
        });
    }

    @Test
    void resolveOrCreateStoreShouldCreateNewStoreWhenMissing() {
        when(storeRepository.findByExternalSourceAndExternalPlaceId(ExternalSource.KAKAO, "new-store-1"))
            .thenReturn(Optional.empty());
        when(storeRepository.save(any(Store.class))).thenAnswer(invocation -> {
            Store saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 99L);
            return saved;
        });

        assertThat(storeService.resolveOrCreateStore(new ResolveStoreRequest(
            "KAKAO",
            "new-store-1",
            "신규 매장",
            "서울시 테스트구 테스트로 9",
            "02-111-2222",
            new BigDecimal("37.1234567"),
            new BigDecimal("127.1234567")
        ))).satisfies(response -> {
            assertThat(response.storeId()).isEqualTo(99L);
            assertThat(response.resolved()).isTrue();
        });
    }
}
