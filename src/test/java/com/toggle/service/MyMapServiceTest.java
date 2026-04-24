package com.toggle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.toggle.dto.user.MyMapPlaceResponse;
import com.toggle.dto.user.PublicMapSearchResponse;
import com.toggle.entity.ExternalSource;
import com.toggle.entity.MyMapStore;
import com.toggle.entity.Store;
import com.toggle.entity.User;
import com.toggle.entity.UserRole;
import com.toggle.entity.UserStatus;
import com.toggle.global.exception.ApiException;
import com.toggle.repository.MyMapPublicInstitutionRepository;
import com.toggle.repository.MyMapStoreRepository;
import com.toggle.repository.UserRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MyMapServiceTest {

    @Mock
    private AuthService authService;

    @Mock
    private StoreService storeService;

    @Mock
    private PublicInstitutionService publicInstitutionService;

    @Mock
    private MyMapStoreRepository myMapStoreRepository;

    @Mock
    private MyMapPublicInstitutionRepository myMapPublicInstitutionRepository;

    @Mock
    private UserRepository userRepository;

    @Test
    void addStoreShouldAllowVerifiedStoreOnly() {
        MyMapService myMapService = new MyMapService(
            authService,
            storeService,
            publicInstitutionService,
            myMapStoreRepository,
            myMapPublicInstitutionRepository,
            userRepository
        );

        User user = new User("user@test.com", "password", "tester", UserRole.USER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", 1L);

        Store store = new Store(
            ExternalSource.KAKAO,
            "store-1",
            "내 지도 대상 매장",
            "02-123-4567",
            "서울시 테스트구 테스트로 1",
            "서울시 테스트구 테스트로 1",
            new BigDecimal("37.1234567"),
            new BigDecimal("127.1234567")
        );
        ReflectionTestUtils.setField(store, "id", 101L);
        store.markVerified("서울시 테스트구 테스트로 1", "서울시 테스트구 테스트로 1", "카페", "{}", null);

        MyMapStore myMapStore = new MyMapStore(user, store);
        ReflectionTestUtils.setField(myMapStore, "id", 11L);

        when(authService.getAuthenticatedUser()).thenReturn(user);
        when(storeService.getRegisteredStore(101L)).thenReturn(store);
        when(myMapStoreRepository.existsByUserIdAndStoreId(1L, 101L)).thenReturn(false);
        when(myMapStoreRepository.save(org.mockito.ArgumentMatchers.any(MyMapStore.class))).thenReturn(myMapStore);

        MyMapPlaceResponse response = myMapService.addStore(101L);

        assertThat(response.type()).isEqualTo("STORE");
        assertThat(response.placeId()).isEqualTo(101L);
        assertThat(response.inMyMap()).isTrue();
        verify(storeService).getRegisteredStore(101L);
    }

    @Test
    void addStoreShouldRejectPreviewStore() {
        MyMapService myMapService = new MyMapService(
            authService,
            storeService,
            publicInstitutionService,
            myMapStoreRepository,
            myMapPublicInstitutionRepository,
            userRepository
        );

        when(storeService.getRegisteredStore(202L)).thenThrow(new ApiException(
            HttpStatus.NOT_FOUND,
            "STORE_NOT_REGISTERED",
            "등록된 매장이 아닙니다."
        ));

        assertThatThrownBy(() -> myMapService.addStore(202L))
            .isInstanceOf(ApiException.class)
            .satisfies(throwable -> {
                ApiException ex = (ApiException) throwable;
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(ex.getCode()).isEqualTo("STORE_NOT_REGISTERED");
            });
    }

    @Test
    void searchPublicMapsShouldReturnSummaryFieldsAndSavedPlaceCount() {
        MyMapService myMapService = new MyMapService(
            authService,
            storeService,
            publicInstitutionService,
            myMapStoreRepository,
            myMapPublicInstitutionRepository,
            userRepository
        );

        User user = new User("user@test.com", "password", "toggle-demo", UserRole.USER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", 1L);
        ReflectionTestUtils.setField(user, "publicMap", true);
        ReflectionTestUtils.setField(user, "mapDescription", "설명 텍스트");
        ReflectionTestUtils.setField(user, "profileImageUrl", "https://cdn.example.com/profile.png");

        when(authService.getAuthenticatedUser()).thenReturn(user);
        when(userRepository.findTop20ByPublicMapTrueAndStatusAndNicknameContainingIgnoreCaseOrderByIdAsc(
            UserStatus.ACTIVE,
            "toggle"
        )).thenReturn(java.util.List.of(user));
        when(myMapStoreRepository.countByUserIdAndStoreDeletedAtIsNull(1L)).thenReturn(2L);
        when(myMapPublicInstitutionRepository.countByUserId(1L)).thenReturn(1L);

        PublicMapSearchResponse response = myMapService.searchPublicMaps("toggle");

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).publicMapUuid()).isEqualTo(user.getPublicMapUuid());
        assertThat(response.content().get(0).nickname()).isEqualTo("toggle-demo");
        assertThat(response.content().get(0).title()).isEqualTo("toggle-demo님의 지도");
        assertThat(response.content().get(0).description()).isEqualTo("설명 텍스트");
        assertThat(response.content().get(0).savedPlaceCount()).isEqualTo(3L);
        assertThat(response.content().get(0).profileImageUrl()).isEqualTo("https://cdn.example.com/profile.png");
    }

    @Test
    void getMyMapShouldSkipSoftDeletedStores() {
        MyMapService myMapService = new MyMapService(
            authService,
            storeService,
            publicInstitutionService,
            myMapStoreRepository,
            myMapPublicInstitutionRepository,
            userRepository
        );

        User user = new User("user@test.com", "password", "toggle-demo", UserRole.USER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", 1L);
        ReflectionTestUtils.setField(user, "publicMap", true);
        ReflectionTestUtils.setField(user, "publicMapUuid", "public-map-uuid");

        when(authService.getAuthenticatedUser()).thenReturn(user);
        when(authService.ensurePublicMapUuid(user)).thenReturn("public-map-uuid");
        when(myMapStoreRepository.findAllByUserIdAndStoreDeletedAtIsNullOrderByCreatedAtDesc(1L)).thenReturn(java.util.List.of());
        when(myMapPublicInstitutionRepository.findAllByUserIdOrderByCreatedAtDesc(1L)).thenReturn(java.util.List.of());

        assertThat(myMapService.getMyMap().stores()).isEmpty();
    }

    @Test
    void getPublicMapShouldRejectPrivateOrInactiveMaps() {
        MyMapService myMapService = new MyMapService(
            authService,
            storeService,
            publicInstitutionService,
            myMapStoreRepository,
            myMapPublicInstitutionRepository,
            userRepository
        );

        User privateUser = new User("user@test.com", "password", "toggle-demo", UserRole.USER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(privateUser, "id", 1L);
        ReflectionTestUtils.setField(privateUser, "publicMap", false);
        ReflectionTestUtils.setField(privateUser, "publicMapUuid", "public-map-uuid");

        when(userRepository.findByPublicMapUuid("public-map-uuid")).thenReturn(java.util.Optional.of(privateUser));

        assertThatThrownBy(() -> myMapService.getPublicMap("public-map-uuid"))
            .isInstanceOf(ApiException.class)
            .satisfies(throwable -> {
                ApiException ex = (ApiException) throwable;
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(ex.getCode()).isEqualTo("PUBLIC_MAP_NOT_FOUND");
            });

        User inactiveUser = new User("inactive@test.com", "password", "toggle-inactive", UserRole.USER, UserStatus.INACTIVE);
        ReflectionTestUtils.setField(inactiveUser, "id", 2L);
        ReflectionTestUtils.setField(inactiveUser, "publicMap", true);
        ReflectionTestUtils.setField(inactiveUser, "publicMapUuid", "inactive-map-uuid");

        when(userRepository.findByPublicMapUuid("inactive-map-uuid")).thenReturn(java.util.Optional.of(inactiveUser));

        assertThatThrownBy(() -> myMapService.getPublicMap("inactive-map-uuid"))
            .isInstanceOf(ApiException.class)
            .satisfies(throwable -> {
                ApiException ex = (ApiException) throwable;
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(ex.getCode()).isEqualTo("PUBLIC_MAP_NOT_FOUND");
            });
    }
}
