package com.toggle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
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
import com.toggle.repository.UserMapRepository;
import com.toggle.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
    void addStoreShouldUsePrimaryMapAndAllowVerifiedStoreOnly() {
        MyMapService myMapService = new MyMapService(
            authService,
            storeService,
            publicInstitutionService,
            myMapStoreRepository,
            myMapPublicInstitutionRepository,
            userRepository,
            userMapRepository
        );

        User user = user(1L, "user@test.com", "tester", UserStatus.ACTIVE);
        UserMap primaryMap = map(user, 10L, true, true, "map-uuid");

        Store store = store(101L, "store-1", "내 지도 대상 매장");
        store.markVerified("서울시 테스트구 테스트로 1", "서울시 테스트구 테스트로 1", "카페", "{}", null);

        when(authService.getAuthenticatedUser()).thenReturn(user);
        when(userMapRepository.findAllByOwnerUserIdAndDeletedAtIsNullOrderByPrimaryMapDescCreatedAtAscIdAsc(1L))
            .thenReturn(List.of(primaryMap));
        when(storeService.getRegisteredStore(101L)).thenReturn(store);
        when(myMapStoreRepository.existsByMapIdAndStoreId(10L, 101L)).thenReturn(false);
        when(myMapStoreRepository.save(any(MyMapStore.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MyMapPlaceResponse response = myMapService.addStore(101L);

        assertThat(response.type()).isEqualTo("STORE");
        assertThat(response.placeId()).isEqualTo(101L);
        assertThat(response.inMyMap()).isTrue();
        verify(storeService).getRegisteredStore(101L);
    }

    @Test
    void addStoreShouldRejectDuplicateOnSamePrimaryMap() {
        MyMapService myMapService = new MyMapService(
            authService,
            storeService,
            publicInstitutionService,
            myMapStoreRepository,
            myMapPublicInstitutionRepository,
            userRepository,
            userMapRepository
        );

        User user = user(1L, "user@test.com", "tester", UserStatus.ACTIVE);
        UserMap primaryMap = map(user, 10L, true, true, "map-uuid");
        Store store = store(101L, "store-1", "내 지도 대상 매장");
        store.markVerified("서울시 테스트구 테스트로 1", "서울시 테스트구 테스트로 1", "카페", "{}", null);

        when(authService.getAuthenticatedUser()).thenReturn(user);
        when(userMapRepository.findAllByOwnerUserIdAndDeletedAtIsNullOrderByPrimaryMapDescCreatedAtAscIdAsc(1L))
            .thenReturn(List.of(primaryMap));
        when(storeService.getRegisteredStore(101L)).thenReturn(store);
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
    void addPublicInstitutionShouldUsePrimaryMapAndRejectDuplicateOnSameMap() {
        MyMapService myMapService = new MyMapService(
            authService,
            storeService,
            publicInstitutionService,
            myMapStoreRepository,
            myMapPublicInstitutionRepository,
            userRepository,
            userMapRepository
        );

        User user = user(1L, "user@test.com", "tester", UserStatus.ACTIVE);
        UserMap primaryMap = map(user, 10L, true, true, "map-uuid");
        PublicInstitution publicInstitution = new PublicInstitution(ExternalSource.KAKAO, "pi-201", "공공기관 대상");
        ReflectionTestUtils.setField(publicInstitution, "id", 201L);

        when(authService.getAuthenticatedUser()).thenReturn(user);
        when(userMapRepository.findAllByOwnerUserIdAndDeletedAtIsNullOrderByPrimaryMapDescCreatedAtAscIdAsc(1L))
            .thenReturn(List.of(primaryMap));
        when(publicInstitutionService.getInstitution(201L)).thenReturn(publicInstitution);
        when(myMapPublicInstitutionRepository.existsByMapIdAndPublicInstitutionId(10L, 201L)).thenReturn(false);
        when(myMapPublicInstitutionRepository.save(any(MyMapPublicInstitution.class))).thenAnswer(invocation -> invocation.getArgument(0));

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

        User user = user(1L, "user@test.com", "toggle-demo", UserStatus.ACTIVE);
        UserMap firstMap = map(user, 10L, true, false, "map-uuid-10");
        ReflectionTestUtils.setField(firstMap, "title", "카페 지도");
        ReflectionTestUtils.setField(firstMap, "description", "설명 텍스트");
        ReflectionTestUtils.setField(firstMap, "profileImageUrl", "https://cdn.example.com/profile.png");

        UserMap secondMap = map(user, 11L, true, false, "map-uuid-11");
        ReflectionTestUtils.setField(secondMap, "title", "맛집 지도");
        ReflectionTestUtils.setField(secondMap, "description", "두번째 설명");
        ReflectionTestUtils.setField(secondMap, "profileImageUrl", "https://cdn.example.com/profile-2.png");

        User currentUser = user(2L, "searcher@test.com", "searcher", UserStatus.ACTIVE);
        when(authService.getAuthenticatedUser()).thenReturn(currentUser);
        when(userRepository.findTop20ByStatusAndNicknameContainingIgnoreCaseOrderByIdAsc(UserStatus.ACTIVE, "toggle"))
            .thenReturn(List.of(user));
        when(userMapRepository.findAllByOwnerUserIdInAndDeletedAtIsNullOrderByOwnerUserIdAscCreatedAtDescIdDesc(List.of(1L)))
            .thenReturn(List.of(secondMap, firstMap));
        when(myMapStoreRepository.findAllByMapIdAndStoreDeletedAtIsNullOrderByCreatedAtDesc(10L)).thenReturn(List.of(storeItem(user, 101L)));
        when(myMapStoreRepository.findAllByMapIdAndStoreDeletedAtIsNullOrderByCreatedAtDesc(11L)).thenReturn(List.of(storeItem(user, 102L)));
        when(myMapPublicInstitutionRepository.findAllByMapIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(publicInstitutionItem(user, 201L)));
        when(myMapPublicInstitutionRepository.findAllByMapIdOrderByCreatedAtDesc(11L)).thenReturn(List.of());

        PublicMapSearchResponse response = myMapService.searchPublicMaps("toggle");

        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0).publicMapUuid()).isEqualTo("map-uuid-11");
        assertThat(response.content().get(0).nickname()).isEqualTo("toggle-demo");
        assertThat(response.content().get(0).title()).isEqualTo("맛집 지도");
        assertThat(response.content().get(0).savedPlaceCount()).isEqualTo(1L);
        assertThat(response.content().get(1).publicMapUuid()).isEqualTo("map-uuid-10");
        assertThat(response.content().get(1).savedPlaceCount()).isEqualTo(2L);
    }

    @Test
    void getPublicMapShouldResolveFromMapRowsWithoutUserBridgeLookup() {
        MyMapService myMapService = new MyMapService(
            authService,
            storeService,
            publicInstitutionService,
            myMapStoreRepository,
            myMapPublicInstitutionRepository,
            userRepository,
            userMapRepository
        );

        User owner = user(1L, "user@test.com", "toggle-demo", UserStatus.ACTIVE);
        UserMap map = map(owner, 10L, true, false, "public-map-uuid");
        ReflectionTestUtils.setField(map, "title", "공개 지도");
        ReflectionTestUtils.setField(map, "description", "설명");
        ReflectionTestUtils.setField(map, "profileImageUrl", "https://cdn.example.com/profile.png");

        when(userMapRepository.findByPublicMapUuidAndDeletedAtIsNull("public-map-uuid"))
            .thenReturn(Optional.of(map));

        var response = myMapService.getPublicMap("public-map-uuid");

        assertThat(response.publicMapUuid()).isEqualTo("public-map-uuid");
        assertThat(response.title()).isEqualTo("공개 지도");
        verify(userRepository, never()).findTop20ByStatusAndNicknameContainingIgnoreCaseOrderByIdAsc(any(), anyString());
    }

    @Test
    void getMyMapShouldUsePrimaryMapAndSkipSoftDeletedStores() {
        MyMapService myMapService = new MyMapService(
            authService,
            storeService,
            publicInstitutionService,
            myMapStoreRepository,
            myMapPublicInstitutionRepository,
            userRepository,
            userMapRepository
        );

        User user = user(1L, "user@test.com", "toggle-demo", UserStatus.ACTIVE);
        UserMap primaryMap = map(user, 10L, true, true, "public-map-uuid");
        ReflectionTestUtils.setField(primaryMap, "profileImageUrl", "https://cdn.example.com/profile.png");

        when(authService.getAuthenticatedUser()).thenReturn(user);
        when(userMapRepository.findAllByOwnerUserIdAndDeletedAtIsNullOrderByPrimaryMapDescCreatedAtAscIdAsc(1L))
            .thenReturn(List.of(primaryMap));
        when(myMapStoreRepository.findAllByMapIdAndStoreDeletedAtIsNullOrderByCreatedAtDesc(10L)).thenReturn(List.of());
        when(myMapPublicInstitutionRepository.findAllByMapIdOrderByCreatedAtDesc(10L)).thenReturn(List.of());

        assertThat(myMapService.getMyMap().stores()).isEmpty();
        assertThat(myMapService.getMyMap().mapProfile().publicMapUuid()).isEqualTo("public-map-uuid");
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

        User activeOwner = user(1L, "owner@test.com", "toggle-demo", UserStatus.ACTIVE);
        UserMap privateMap = map(activeOwner, 10L, false, false, "public-map-uuid");
        when(userMapRepository.findByPublicMapUuidAndDeletedAtIsNull("public-map-uuid")).thenReturn(Optional.of(privateMap));

        assertThatThrownBy(() -> myMapService.getPublicMap("public-map-uuid"))
            .isInstanceOf(ApiException.class)
            .satisfies(throwable -> {
                ApiException ex = (ApiException) throwable;
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(ex.getCode()).isEqualTo("PUBLIC_MAP_NOT_FOUND");
            });

        User inactiveOwner = user(2L, "inactive@test.com", "toggle-inactive", UserStatus.INACTIVE);
        UserMap inactiveMap = map(inactiveOwner, 11L, true, false, "inactive-map-uuid");
        when(userMapRepository.findByPublicMapUuidAndDeletedAtIsNull("inactive-map-uuid")).thenReturn(Optional.of(inactiveMap));

        assertThatThrownBy(() -> myMapService.getPublicMap("inactive-map-uuid"))
            .isInstanceOf(ApiException.class)
            .satisfies(throwable -> {
                ApiException ex = (ApiException) throwable;
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(ex.getCode()).isEqualTo("PUBLIC_MAP_NOT_FOUND");
            });
    }

    private User user(Long id, String email, String nickname, UserStatus status) {
        User user = new User(email, "password", nickname, UserRole.USER, status);
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "profileImageUrl", "https://cdn.example.com/user-" + id + ".png");
        return user;
    }

    private UserMap map(User owner, Long id, boolean isPublic, boolean primaryMap, String publicMapUuid) {
        UserMap map = new UserMap(owner, publicMapUuid, "지도", "설명", null, isPublic, primaryMap);
        ReflectionTestUtils.setField(map, "id", id);
        ReflectionTestUtils.setField(map, "createdAt", LocalDateTime.of(2024, 1, 1, 12, 0));
        ReflectionTestUtils.setField(map, "updatedAt", LocalDateTime.of(2024, 1, 1, 12, 0));
        return map;
    }

    private MyMapStore storeItem(User owner, Long storeId) {
        Store store = store(storeId, "external-" + storeId, "store-" + storeId);
        store.markVerified("서울시 테스트구 테스트로 1", "서울시 테스트구 테스트로 1", "카페", "{}", null);
        UserMap map = map(owner, 10L, true, false, "map-uuid");
        MyMapStore myMapStore = new MyMapStore(owner, map, store);
        ReflectionTestUtils.setField(myMapStore, "id", storeId + 1000);
        ReflectionTestUtils.setField(myMapStore, "createdAt", LocalDateTime.of(2024, 1, 1, 12, 0).plusSeconds(storeId));
        return myMapStore;
    }

    private MyMapPublicInstitution publicInstitutionItem(User owner, Long publicInstitutionId) {
        PublicInstitution institution = new PublicInstitution(ExternalSource.KAKAO, "external-" + publicInstitutionId, "institution-" + publicInstitutionId);
        ReflectionTestUtils.setField(institution, "id", publicInstitutionId);
        UserMap map = map(owner, 10L, true, false, "map-p-uuid");
        MyMapPublicInstitution myMapPublicInstitution = new MyMapPublicInstitution(owner, map, institution);
        ReflectionTestUtils.setField(myMapPublicInstitution, "id", publicInstitutionId + 1000);
        ReflectionTestUtils.setField(myMapPublicInstitution, "createdAt", LocalDateTime.of(2024, 1, 1, 12, 0).plusSeconds(publicInstitutionId));
        return myMapPublicInstitution;
    }

    private Store store(Long id, String externalId, String name) {
        Store store = new Store(
            ExternalSource.KAKAO,
            externalId,
            name,
            null,
            null,
            null,
            new BigDecimal("37.1234567"),
            new BigDecimal("127.1234567")
        );
        ReflectionTestUtils.setField(store, "id", id);
        return store;
    }
}
