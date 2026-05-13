package com.toggle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.toggle.dto.user.MyMapPlaceResponse;
import com.toggle.dto.user.PublicMapSearchResponse;
import com.toggle.entity.ExternalSource;
import com.toggle.entity.MyMapPublicInstitution;
import com.toggle.entity.MyMapStore;
import com.toggle.entity.PublicInstitution;
import com.toggle.entity.Store;
import com.toggle.entity.User;
import com.toggle.entity.UserMap;
import com.toggle.entity.UserRole;
import com.toggle.entity.UserStatus;
import com.toggle.global.exception.ApiException;
import com.toggle.repository.MyMapPublicInstitutionRepository;
import com.toggle.repository.MyMapStoreRepository;
import com.toggle.repository.UserRepository;
import com.toggle.repository.UserMapRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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

    @Mock
    private UserMapRepository userMapRepository;

    @Test
    void addStoreShouldUseDefaultMapAndAllowVerifiedStoreOnly() {
        MyMapService myMapService = new MyMapService(
            authService,
            storeService,
            publicInstitutionService,
            myMapStoreRepository,
            myMapPublicInstitutionRepository,
            userRepository,
            userMapRepository
        );

        User user = new User("user@test.com", "password", "tester", UserRole.USER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", 1L);
        ReflectionTestUtils.setField(user, "defaultMapId", 10L);

        UserMap defaultMap = buildMap(user, 10L, true, "map-uuid");

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

        when(authService.getAuthenticatedUser()).thenReturn(user);
        when(userMapRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(java.util.Optional.of(defaultMap));
        when(storeService.getRegisteredStore(101L)).thenReturn(store);
        when(myMapStoreRepository.findAllByUserIdAndMapIsNullOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        when(myMapPublicInstitutionRepository.findAllByUserIdAndMapIsNullOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        when(myMapStoreRepository.existsByMapIdAndStoreId(10L, 101L)).thenReturn(false);
        when(myMapStoreRepository.save(org.mockito.ArgumentMatchers.any(MyMapStore.class))).thenAnswer(invocation -> {
            MyMapStore saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 11L);
            return saved;
        });

        MyMapPlaceResponse response = myMapService.addStore(101L);

        assertThat(response.type()).isEqualTo("STORE");
        assertThat(response.placeId()).isEqualTo(101L);
        assertThat(response.inMyMap()).isTrue();
        verify(storeService).getRegisteredStore(101L);
    }

    @Test
    void addStoreShouldRejectDuplicateOnSameDefaultMap() {
        MyMapService myMapService = new MyMapService(
            authService,
            storeService,
            publicInstitutionService,
            myMapStoreRepository,
            myMapPublicInstitutionRepository,
            userRepository,
            userMapRepository
        );

        User user = new User("user@test.com", "password", "tester", UserRole.USER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", 1L);
        ReflectionTestUtils.setField(user, "defaultMapId", 10L);

        UserMap defaultMap = buildMap(user, 10L, true, "map-uuid");

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

        when(authService.getAuthenticatedUser()).thenReturn(user);
        when(userMapRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(java.util.Optional.of(defaultMap));
        when(storeService.getRegisteredStore(101L)).thenReturn(store);
        when(myMapStoreRepository.findAllByUserIdAndMapIsNullOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        when(myMapPublicInstitutionRepository.findAllByUserIdAndMapIsNullOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        when(myMapStoreRepository.existsByMapIdAndStoreId(10L, 101L)).thenReturn(true);

        assertThatThrownBy(() -> myMapService.addStore(101L))
            .isInstanceOf(ApiException.class)
            .satisfies(throwable -> {
                ApiException ex = (ApiException) throwable;
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(ex.getCode()).isEqualTo("MY_MAP_PLACE_ALREADY_EXISTS");
            });
    }

    @Test
    void addPublicInstitutionShouldUseDefaultMapAndRejectDuplicateOnSameMap() {
        MyMapService myMapService = new MyMapService(
            authService,
            storeService,
            publicInstitutionService,
            myMapStoreRepository,
            myMapPublicInstitutionRepository,
            userRepository,
            userMapRepository
        );

        User user = new User("user@test.com", "password", "tester", UserRole.USER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", 1L);
        ReflectionTestUtils.setField(user, "defaultMapId", 10L);

        UserMap defaultMap = buildMap(user, 10L, true, "map-uuid");
        PublicInstitution publicInstitution = new PublicInstitution(ExternalSource.KAKAO, "pi-201", "공공기관 대상");
        ReflectionTestUtils.setField(publicInstitution, "id", 201L);

        when(authService.getAuthenticatedUser()).thenReturn(user);
        when(userMapRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(java.util.Optional.of(defaultMap));
        when(publicInstitutionService.getInstitution(201L)).thenReturn(publicInstitution);
        when(myMapStoreRepository.findAllByUserIdAndMapIsNullOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        when(myMapPublicInstitutionRepository.findAllByUserIdAndMapIsNullOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        when(myMapPublicInstitutionRepository.existsByMapIdAndPublicInstitutionId(10L, 201L)).thenReturn(false);
        when(myMapPublicInstitutionRepository.save(org.mockito.ArgumentMatchers.any(MyMapPublicInstitution.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MyMapPlaceResponse response = myMapService.addPublicInstitution(201L);

        assertThat(response.type()).isEqualTo("PUBLIC");
        assertThat(response.placeId()).isEqualTo(201L);
        assertThat(response.inMyMap()).isTrue();

        when(myMapPublicInstitutionRepository.existsByMapIdAndPublicInstitutionId(10L, 201L)).thenReturn(true);
        assertThatThrownBy(() -> myMapService.addPublicInstitution(201L))
            .isInstanceOf(ApiException.class)
            .satisfies(throwable -> {
                ApiException ex = (ApiException) throwable;
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(ex.getCode()).isEqualTo("MY_MAP_PLACE_ALREADY_EXISTS");
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
            userRepository,
            userMapRepository
        );

        User user = new User("user@test.com", "password", "toggle-demo", UserRole.USER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", 1L);
        ReflectionTestUtils.setField(user, "defaultMapId", 10L);
        ReflectionTestUtils.setField(user, "publicMap", true);
        ReflectionTestUtils.setField(user, "mapDescription", "설명 텍스트");
        ReflectionTestUtils.setField(user, "profileImageUrl", "https://cdn.example.com/profile.png");

        when(authService.getAuthenticatedUser()).thenReturn(user);
        when(userRepository.findTop20ByPublicMapTrueAndStatusAndNicknameContainingIgnoreCaseOrderByIdAsc(
            UserStatus.ACTIVE,
            "toggle"
        )).thenReturn(List.of(user));
        when(userMapRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(java.util.Optional.of(buildMap(user, 10L, true, "map-uuid")));
        when(myMapStoreRepository.findAllByMapIdAndStoreDeletedAtIsNullOrderByCreatedAtDesc(10L)).thenReturn(List.of(
            buildStore(user, 101L),
            buildStore(user, 102L)
        ));
        when(myMapStoreRepository.findAllByUserIdAndMapIsNullOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        when(myMapPublicInstitutionRepository.findAllByMapIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(
            buildPublicInstitution(user, 201L)
        ));
        when(myMapPublicInstitutionRepository.findAllByUserIdAndMapIsNullOrderByCreatedAtDesc(1L)).thenReturn(List.of());

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
    void getMyMapShouldUseDefaultMapAndSkipSoftDeletedStores() {
        MyMapService myMapService = new MyMapService(
            authService,
            storeService,
            publicInstitutionService,
            myMapStoreRepository,
            myMapPublicInstitutionRepository,
            userRepository,
            userMapRepository
        );

        User user = new User("user@test.com", "password", "toggle-demo", UserRole.USER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", 1L);
        ReflectionTestUtils.setField(user, "defaultMapId", 10L);
        ReflectionTestUtils.setField(user, "publicMap", true);
        ReflectionTestUtils.setField(user, "publicMapUuid", "public-map-uuid");

        UserMap defaultMap = buildMap(user, 10L, true, "public-map-uuid");

        when(authService.getAuthenticatedUser()).thenReturn(user);
        when(authService.ensurePublicMapUuid(user)).thenReturn("public-map-uuid");
        when(userMapRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(java.util.Optional.of(defaultMap));
        when(myMapStoreRepository.findAllByMapIdAndStoreDeletedAtIsNullOrderByCreatedAtDesc(10L)).thenReturn(List.of());
        when(myMapStoreRepository.findAllByUserIdAndMapIsNullOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        when(myMapPublicInstitutionRepository.findAllByMapIdOrderByCreatedAtDesc(10L)).thenReturn(List.of());
        when(myMapPublicInstitutionRepository.findAllByUserIdAndMapIsNullOrderByCreatedAtDesc(1L)).thenReturn(List.of());

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
            userRepository,
            userMapRepository
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

    private UserMap buildMap(User owner, Long id, boolean isPublic, String publicMapUuid) {
        UserMap map = new UserMap(owner, publicMapUuid, "지도", "설명", null, isPublic, false);
        ReflectionTestUtils.setField(map, "id", id);
        ReflectionTestUtils.setField(map, "createdAt", LocalDateTime.of(2024, 1, 1, 12, 0));
        ReflectionTestUtils.setField(map, "updatedAt", LocalDateTime.of(2024, 1, 1, 12, 0));
        return map;
    }

    private MyMapStore buildStore(User owner, Long storeId) {
        Store store = new Store(
            ExternalSource.KAKAO,
            "external-" + storeId,
            "store-" + storeId,
            null,
            null,
            null,
            new BigDecimal("37.1234567"),
            new BigDecimal("127.1234567")
        );
        ReflectionTestUtils.setField(store, "id", storeId);
        store.markVerified("서울시 테스트구 테스트로 1", "서울시 테스트구 테스트로 1", "카페", "{}", null);

        UserMap map = buildMap(owner, 10L, true, "map-uuid");
        MyMapStore myMapStore = new MyMapStore(owner, map, store);
        ReflectionTestUtils.setField(myMapStore, "id", storeId + 1000);
        ReflectionTestUtils.setField(myMapStore, "createdAt", LocalDateTime.of(2024, 1, 1, 12, 0).plusSeconds(storeId));
        return myMapStore;
    }

    private MyMapPublicInstitution buildPublicInstitution(User owner, Long publicInstitutionId) {
        PublicInstitution institution = new PublicInstitution(
            ExternalSource.KAKAO,
            "external-" + publicInstitutionId,
            "institution-" + publicInstitutionId
        );
        ReflectionTestUtils.setField(institution, "id", publicInstitutionId);

        UserMap map = buildMap(owner, 10L, true, "map-p-uuid");
        MyMapPublicInstitution myMapPublicInstitution = new MyMapPublicInstitution(owner, map, institution);
        ReflectionTestUtils.setField(myMapPublicInstitution, "id", publicInstitutionId + 1000);
        ReflectionTestUtils.setField(myMapPublicInstitution, "createdAt", LocalDateTime.of(2024, 1, 1, 12, 0).plusSeconds(publicInstitutionId));
        return myMapPublicInstitution;
    }
}
