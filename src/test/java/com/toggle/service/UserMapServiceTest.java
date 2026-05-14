package com.toggle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.toggle.dto.map.CreateMyMapRequest;
import com.toggle.dto.map.PublicMapListResponse;
import com.toggle.dto.map.UpdateUserMapMetadataRequest;
import com.toggle.dto.map.UserMapSummaryResponse;
import com.toggle.dto.user.UserNicknameSearchResponse;
import com.toggle.entity.ExternalSource;
import com.toggle.entity.MyMapPublicInstitution;
import com.toggle.entity.MyMapStore;
import com.toggle.entity.PublicInstitution;
import com.toggle.entity.Store;
import com.toggle.entity.User;
import com.toggle.entity.UserMap;
import com.toggle.entity.UserMapLike;
import com.toggle.entity.UserRole;
import com.toggle.entity.UserStatus;
import com.toggle.global.exception.ApiException;
import com.toggle.repository.MyMapPublicInstitutionRepository;
import com.toggle.repository.MyMapStoreRepository;
import com.toggle.repository.PublicInstitutionRepository;
import com.toggle.repository.StoreRepository;
import com.toggle.repository.UserMapLikeRepository;
import com.toggle.repository.UserMapRepository;
import com.toggle.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserMapServiceTest {

    @Mock
    private AuthService authService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapRepository userMapRepository;

    @Mock
    private UserMapLikeRepository userMapLikeRepository;

    @Mock
    private MyMapStoreRepository myMapStoreRepository;

    @Mock
    private MyMapPublicInstitutionRepository myMapPublicInstitutionRepository;

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private PublicInstitutionRepository publicInstitutionRepository;

    @Mock
    private S3FileService s3FileService;

    @Test
    void createMyMapShouldAllowMultipleMapsForOneUser() {
        UserMapService service = new UserMapService(
            authService,
            userRepository,
            userMapRepository,
            userMapLikeRepository,
            myMapStoreRepository,
            myMapPublicInstitutionRepository,
            storeRepository,
            publicInstitutionRepository,
            s3FileService
        );

        User user = new User("user@test.com", "password", "tester", UserRole.USER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", 1L);
        when(authService.getAuthenticatedUser()).thenReturn(user);
        AtomicLong idSequence = new AtomicLong(100L);
        when(userMapRepository.findAllByOwnerUserIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(1L))
            .thenReturn(List.of(), List.of(buildMap(user, 999L, false, "existing-uuid")));
        when(userMapRepository.save(any(UserMap.class))).thenAnswer(invocation -> {
            UserMap map = invocation.getArgument(0);
            ReflectionTestUtils.setField(map, "id", idSequence.incrementAndGet());
            return map;
        });

        UserMapSummaryResponse first = service.createMyMap(user, new CreateMyMapRequest("데이트 지도", "첫번째"));
        UserMapSummaryResponse second = service.createMyMap(user, new CreateMyMapRequest("맛집 지도", "두번째"));

        assertThat(first.mapId()).isEqualTo(101L);
        assertThat(second.mapId()).isEqualTo(102L);
        ArgumentCaptor<UserMap> mapCaptor = ArgumentCaptor.forClass(UserMap.class);
        verify(userMapRepository, org.mockito.Mockito.times(2)).save(mapCaptor.capture());
        List<UserMap> savedMaps = mapCaptor.getAllValues();
        assertThat(savedMaps).hasSize(2);
        assertThat(savedMaps.get(0).isPrimary()).isTrue();
        assertThat(savedMaps.get(1).isPrimary()).isFalse();
        assertThat(savedMaps.get(0).getPublicMapUuid()).isNotBlank();
        assertThat(savedMaps.get(1).getPublicMapUuid()).isNotBlank();
        assertThat(savedMaps.get(0).getPublicMapUuid()).isNotEqualTo(savedMaps.get(1).getPublicMapUuid());
    }

    @Test
    void createMyMapShouldNotWriteBackToUserBridgeColumns() {
        UserMapService service = new UserMapService(
            authService,
            userRepository,
            userMapRepository,
            userMapLikeRepository,
            myMapStoreRepository,
            myMapPublicInstitutionRepository,
            storeRepository,
            publicInstitutionRepository,
            s3FileService
        );

        User user = new User("user@test.com", "password", "tester", UserRole.USER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", 1L);
        when(authService.getAuthenticatedUser()).thenReturn(user);
        when(userMapRepository.save(any(UserMap.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createMyMap(user, new CreateMyMapRequest("카페 지도", "설명"));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void searchUsersByNicknameShouldReturnUsersWithMultipleMaps() {
        UserMapService service = new UserMapService(
            authService,
            userRepository,
            userMapRepository,
            userMapLikeRepository,
            myMapStoreRepository,
            myMapPublicInstitutionRepository,
            storeRepository,
            publicInstitutionRepository,
            s3FileService
        );

        User currentUser = new User("searcher@test.com", "password", "searcher", UserRole.USER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(currentUser, "id", 1L);
        when(authService.getAuthenticatedUser()).thenReturn(currentUser);

        User targetUser = new User("target@test.com", "password", "민경", UserRole.USER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(targetUser, "id", 10L);
        ReflectionTestUtils.setField(targetUser, "profileImageUrl", "https://cdn.example.com/user.png");

        UserMap firstMap = buildMap(targetUser, 201L, true, "uuid-201");
        ReflectionTestUtils.setField(firstMap, "title", "민경의 카페 지도");
        ReflectionTestUtils.setField(firstMap, "description", "카페 모음");
        ReflectionTestUtils.setField(firstMap, "profileImageUrl", "https://cdn.example.com/map-201.png");

        UserMap secondMap = buildMap(targetUser, 202L, true, "uuid-202");
        ReflectionTestUtils.setField(secondMap, "title", "민경의 맛집 지도");
        ReflectionTestUtils.setField(secondMap, "description", "맛집 모음");
        ReflectionTestUtils.setField(secondMap, "profileImageUrl", "https://cdn.example.com/map-202.png");

        when(userRepository.findTop20ByStatusAndNicknameContainingIgnoreCaseOrderByIdAsc(UserStatus.ACTIVE, "민")).thenReturn(List.of(targetUser));
        when(userMapRepository.findAllByOwnerUserIdInAndDeletedAtIsNullOrderByOwnerUserIdAscCreatedAtDescIdDesc(List.of(10L)))
            .thenReturn(List.of(secondMap, firstMap));

        var response = service.searchUsersByNickname("민");

        assertThat(response.nickname()).isEqualTo("민");
        assertThat(response.users()).hasSize(1);
        assertThat(response.users().get(0).nickname()).isEqualTo("민경");
        assertThat(response.users().get(0).maps()).hasSize(2);
        assertThat(response.users().get(0).maps().get(0).title()).isEqualTo("민경의 맛집 지도");
        assertThat(response.users().get(0).maps().get(1).title()).isEqualTo("민경의 카페 지도");
    }

    @Test
    void addStoreToMapShouldAllowSameStoreAcrossDifferentMapsAndRejectSameMapDuplicate() {
        UserMapService service = new UserMapService(
            authService,
            userRepository,
            userMapRepository,
            userMapLikeRepository,
            myMapStoreRepository,
            myMapPublicInstitutionRepository,
            storeRepository,
            publicInstitutionRepository,
            s3FileService
        );

        User currentUser = new User("owner@test.com", "password", "owner", UserRole.USER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(currentUser, "id", 1L);

        UserMap mapA = buildMap(currentUser, 20L, true, "uuid-a");
        UserMap mapB = buildMap(currentUser, 21L, true, "uuid-b");

        Store store = new Store(
            ExternalSource.KAKAO,
            "store-101",
            "공통 저장 대상",
            "02-123-4567",
            "서울시 테스트구 테스트로 1",
            "서울시 테스트구 테스트로 1",
            new BigDecimal("37.1234567"),
            new BigDecimal("127.1234567")
        );
        ReflectionTestUtils.setField(store, "id", 101L);
        store.markVerified("서울시 테스트구 테스트로 1", "서울시 테스트구 테스트로 1", "카페", "{}", null);

        when(authService.getAuthenticatedUser()).thenReturn(currentUser);
        when(userMapRepository.findByIdAndOwnerUserIdAndDeletedAtIsNull(20L, 1L)).thenReturn(Optional.of(mapA));
        when(userMapRepository.findByIdAndOwnerUserIdAndDeletedAtIsNull(21L, 1L)).thenReturn(Optional.of(mapB));
        when(storeRepository.findByIdAndDeletedAtIsNull(101L)).thenReturn(Optional.of(store));
        when(myMapStoreRepository.save(any(MyMapStore.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(myMapStoreRepository.existsByMapIdAndStoreId(20L, 101L)).thenReturn(false);
        when(myMapStoreRepository.existsByMapIdAndStoreId(21L, 101L)).thenReturn(false);

        assertThat(service.addStoreToMap(20L, 101L, currentUser).inMyMap()).isTrue();
        assertThat(service.addStoreToMap(21L, 101L, currentUser).inMyMap()).isTrue();

        when(myMapStoreRepository.existsByMapIdAndStoreId(20L, 101L)).thenReturn(true);
        assertThatThrownBy(() -> service.addStoreToMap(20L, 101L, currentUser))
            .isInstanceOf(ApiException.class)
            .satisfies(throwable -> {
                ApiException ex = (ApiException) throwable;
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(ex.getCode()).isEqualTo("MY_MAP_PLACE_ALREADY_EXISTS");
            });
    }

    @Test
    void addStoreToMapShouldAllowAnotherUsersOwnMap() {
        UserMapService service = new UserMapService(
            authService,
            userRepository,
            userMapRepository,
            userMapLikeRepository,
            myMapStoreRepository,
            myMapPublicInstitutionRepository,
            storeRepository,
            publicInstitutionRepository,
            s3FileService
        );

        User owner = new User("other-owner@test.com", "password", "other-owner", UserRole.USER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(owner, "id", 2L);

        UserMap map = buildMap(owner, 30L, true, "uuid-other");

        Store store = new Store(
            ExternalSource.KAKAO,
            "store-202",
            "다른 유저의 지도 대상",
            "02-123-4567",
            "서울시 테스트구 테스트로 2",
            "서울시 테스트구 테스트로 2",
            new BigDecimal("37.2234567"),
            new BigDecimal("127.2234567")
        );
        ReflectionTestUtils.setField(store, "id", 202L);
        store.markVerified("서울시 테스트구 테스트로 2", "서울시 테스트구 테스트로 2", "카페", "{}", null);

        when(authService.getAuthenticatedUser()).thenReturn(owner);
        when(userMapRepository.findByIdAndOwnerUserIdAndDeletedAtIsNull(30L, 2L)).thenReturn(Optional.of(map));
        when(storeRepository.findByIdAndDeletedAtIsNull(202L)).thenReturn(Optional.of(store));
        when(myMapStoreRepository.existsByMapIdAndStoreId(30L, 202L)).thenReturn(false);
        when(myMapStoreRepository.save(any(MyMapStore.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.addStoreToMap(30L, 202L, owner).inMyMap()).isTrue();
    }

    @Test
    void deleteMyMapShouldRemoveChildrenAndPromoteReplacement() {
        UserMapService service = new UserMapService(
            authService,
            userRepository,
            userMapRepository,
            userMapLikeRepository,
            myMapStoreRepository,
            myMapPublicInstitutionRepository,
            storeRepository,
            publicInstitutionRepository,
            s3FileService
        );

        User user = new User("owner@test.com", "password", "owner", UserRole.USER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", 1L);
        when(authService.getAuthenticatedUser()).thenReturn(user);
        UserMap deletedMap = buildMap(user, 100L, true, "uuid-1");
        ReflectionTestUtils.setField(deletedMap, "primaryMap", true);
        UserMap replacementMap = buildMap(user, 101L, false, "uuid-2");
        ReflectionTestUtils.setField(replacementMap, "primaryMap", false);
        when(userMapRepository.findByIdAndOwnerUserIdAndDeletedAtIsNull(100L, 1L)).thenReturn(Optional.of(deletedMap));
        when(userMapRepository.findAllByOwnerUserIdAndDeletedAtIsNullOrderByPrimaryMapDescCreatedAtAscIdAsc(1L))
            .thenReturn(List.of(replacementMap));
        when(userMapRepository.save(any(UserMap.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.deleteMyMap(100L, user);

        verify(myMapStoreRepository).deleteAllByMapId(100L);
        verify(myMapPublicInstitutionRepository).deleteAllByMapId(100L);
        verify(userMapLikeRepository).deleteAllByMapId(100L);
        assertThat(replacementMap.isPrimary()).isTrue();
    }

    @Test
    void deleteMyMapShouldRejectOtherUsers() {
        UserMapService service = new UserMapService(
            authService,
            userRepository,
            userMapRepository,
            userMapLikeRepository,
            myMapStoreRepository,
            myMapPublicInstitutionRepository,
            storeRepository,
            publicInstitutionRepository,
            s3FileService
        );

        User currentUser = new User("current@test.com", "password", "current", UserRole.USER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(currentUser, "id", 1L);
        User ownerUser = new User("owner@test.com", "password", "owner", UserRole.USER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(ownerUser, "id", 2L);
        when(authService.getAuthenticatedUser()).thenReturn(currentUser);

        assertThatThrownBy(() -> service.deleteMyMap(10L, ownerUser))
            .isInstanceOf(ApiException.class)
            .satisfies(throwable -> {
                ApiException ex = (ApiException) throwable;
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(ex.getCode()).isEqualTo("MAP_ACCESS_DENIED");
            });
        verify(userMapRepository, never()).delete(any());
    }

    @Test
    void likeAndUnlikeShouldRespectPolicyAndUniqueness() {
        UserMapService service = new UserMapService(
            authService,
            userRepository,
            userMapRepository,
            userMapLikeRepository,
            myMapStoreRepository,
            myMapPublicInstitutionRepository,
            storeRepository,
            publicInstitutionRepository,
            s3FileService
        );

        User currentUser = new User("user@test.com", "password", "tester", UserRole.USER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(currentUser, "id", 1L);
        User ownerUser = new User("owner@test.com", "password", "owner", UserRole.USER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(ownerUser, "id", 2L);
        UserMap map = buildMap(ownerUser, 20L, true, "map-uuid");
        when(authService.getAuthenticatedUser()).thenReturn(currentUser);
        when(userMapRepository.findByIdAndDeletedAtIsNull(20L)).thenReturn(Optional.of(map));
        when(userMapLikeRepository.existsByMapIdAndUserId(20L, 1L)).thenReturn(false);
        when(userMapLikeRepository.countByMapId(20L)).thenReturn(1L, 0L);
        when(userMapLikeRepository.findByMapIdAndUserId(20L, 1L)).thenReturn(Optional.of(new UserMapLike(map, currentUser)));

        assertThat(service.likeMap(20L, currentUser).likedByMe()).isTrue();
        verify(userMapLikeRepository).save(any(UserMapLike.class));

        assertThat(service.unlikeMap(20L, currentUser).likedByMe()).isFalse();
        verify(userMapLikeRepository).delete(any(UserMapLike.class));
    }

    @Test
    void likeMapShouldRejectSelfLike() {
        UserMapService service = new UserMapService(
            authService,
            userRepository,
            userMapRepository,
            userMapLikeRepository,
            myMapStoreRepository,
            myMapPublicInstitutionRepository,
            storeRepository,
            publicInstitutionRepository,
            s3FileService
        );

        User currentUser = new User("user@test.com", "password", "tester", UserRole.USER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(currentUser, "id", 1L);
        UserMap map = buildMap(currentUser, 21L, true, "map-uuid-21");
        when(authService.getAuthenticatedUser()).thenReturn(currentUser);
        when(userMapRepository.findByIdAndDeletedAtIsNull(21L)).thenReturn(Optional.of(map));

        assertThatThrownBy(() -> service.likeMap(21L, currentUser))
            .isInstanceOf(ApiException.class)
            .satisfies(throwable -> {
                ApiException ex = (ApiException) throwable;
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(ex.getCode()).isEqualTo("MAP_LIKE_NOT_ALLOWED");
            });
        verify(userMapLikeRepository, never()).save(any());
    }

    @Test
    void likeMapShouldRejectDuplicateLike() {
        UserMapService service = new UserMapService(
            authService,
            userRepository,
            userMapRepository,
            userMapLikeRepository,
            myMapStoreRepository,
            myMapPublicInstitutionRepository,
            storeRepository,
            publicInstitutionRepository,
            s3FileService
        );

        User currentUser = new User("user@test.com", "password", "tester", UserRole.USER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(currentUser, "id", 1L);
        User ownerUser = new User("owner@test.com", "password", "owner", UserRole.USER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(ownerUser, "id", 2L);
        UserMap map = buildMap(ownerUser, 22L, true, "map-uuid-22");
        when(authService.getAuthenticatedUser()).thenReturn(currentUser);
        when(userMapRepository.findByIdAndDeletedAtIsNull(22L)).thenReturn(Optional.of(map));
        when(userMapLikeRepository.existsByMapIdAndUserId(22L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.likeMap(22L, currentUser))
            .isInstanceOf(ApiException.class)
            .satisfies(throwable -> {
                ApiException ex = (ApiException) throwable;
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(ex.getCode()).isEqualTo("MAP_LIKE_ALREADY_EXISTS");
            });
        verify(userMapLikeRepository, never()).save(any());
    }

    @Test
    void listPublicMapsShouldSortByLikesThenLatest() {
        UserMapService service = new UserMapService(
            authService,
            userRepository,
            userMapRepository,
            userMapLikeRepository,
            myMapStoreRepository,
            myMapPublicInstitutionRepository,
            storeRepository,
            publicInstitutionRepository,
            s3FileService
        );

        User owner1 = new User("owner1@test.com", "password", "alpha", UserRole.USER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(owner1, "id", 1L);
        User owner2 = new User("owner2@test.com", "password", "beta", UserRole.USER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(owner2, "id", 2L);

        UserMap older = buildMap(owner1, 30L, true, "uuid-30");
        ReflectionTestUtils.setField(older, "createdAt", LocalDateTime.of(2024, 1, 1, 12, 0));
        UserMap newer = buildMap(owner2, 31L, true, "uuid-31");
        ReflectionTestUtils.setField(newer, "createdAt", LocalDateTime.of(2024, 1, 2, 12, 0));

        when(userMapRepository.findAllByIsPublicTrueAndDeletedAtIsNull(any())).thenReturn(new PageImpl<>(List.of(older, newer)));
        when(userMapLikeRepository.countByMapId(30L)).thenReturn(10L);
        when(userMapLikeRepository.countByMapId(31L)).thenReturn(10L);

        PublicMapListResponse response = service.listPublicMaps("지도", "likes", 0, 20);

        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0).mapId()).isEqualTo(31L);
        assertThat(response.content().get(1).mapId()).isEqualTo(30L);
    }

    @Test
    void publicDetailShouldRejectPrivateMaps() {
        UserMapService service = new UserMapService(
            authService,
            userRepository,
            userMapRepository,
            userMapLikeRepository,
            myMapStoreRepository,
            myMapPublicInstitutionRepository,
            storeRepository,
            publicInstitutionRepository,
            s3FileService
        );

        User owner = new User("owner@test.com", "password", "owner", UserRole.USER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(owner, "id", 1L);
        UserMap privateMap = buildMap(owner, 40L, false, "uuid-40");
        when(userMapRepository.findByIdAndDeletedAtIsNull(40L)).thenReturn(Optional.of(privateMap));

        assertThatThrownBy(() -> service.getPublicMap(40L))
            .isInstanceOf(ApiException.class)
            .satisfies(throwable -> {
                ApiException ex = (ApiException) throwable;
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(ex.getCode()).isEqualTo("MAP_NOT_FOUND");
            });
    }

    @Test
    void deletedPublicMapShouldNotBeVisible() {
        UserMapService service = new UserMapService(
            authService,
            userRepository,
            userMapRepository,
            userMapLikeRepository,
            myMapStoreRepository,
            myMapPublicInstitutionRepository,
            storeRepository,
            publicInstitutionRepository,
            s3FileService
        );

        User owner = new User("owner@test.com", "password", "owner", UserRole.USER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(owner, "id", 1L);
        UserMap deletedMap = buildMap(owner, 41L, true, "uuid-41");
        ReflectionTestUtils.setField(deletedMap, "deletedAt", LocalDateTime.now());
        when(userMapRepository.findByIdAndDeletedAtIsNull(41L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPublicMap(41L))
            .isInstanceOf(ApiException.class)
            .satisfies(throwable -> {
                ApiException ex = (ApiException) throwable;
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(ex.getCode()).isEqualTo("MAP_NOT_FOUND");
            });
    }

    @Test
    void updateMyMapMetadataShouldPersistNameAndDescriptionForOwner() {
        UserMapService service = new UserMapService(
            authService,
            userRepository,
            userMapRepository,
            userMapLikeRepository,
            myMapStoreRepository,
            myMapPublicInstitutionRepository,
            storeRepository,
            publicInstitutionRepository,
            s3FileService
        );

        User user = new User("owner@test.com", "password", "owner", UserRole.USER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", 1L);
        when(authService.getAuthenticatedUser()).thenReturn(user);
        UserMap map = buildMap(user, 101L, true, "uuid-101");
        ReflectionTestUtils.setField(map, "primaryMap", true);
        when(userMapRepository.findByIdAndOwnerUserIdAndDeletedAtIsNull(101L, 1L)).thenReturn(Optional.of(map));
        when(userMapRepository.save(any(UserMap.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapLikeRepository.countByMapId(101L)).thenReturn(0L);

        UserMapSummaryResponse response = service.updateMyMapMetadata(
            101L,
            user,
            new UpdateUserMapMetadataRequest("카페 투어 지도", "주말 모음")
        );

        assertThat(response.title()).isEqualTo("카페 투어 지도");
        assertThat(response.description()).isEqualTo("주말 모음");
        assertThat(ReflectionTestUtils.getField(map, "title")).isEqualTo("카페 투어 지도");
    }

    @Test
    void updateMyMapProfileImageShouldPersistImageAndSyncPrimaryMap() {
        UserMapService service = new UserMapService(
            authService,
            userRepository,
            userMapRepository,
            userMapLikeRepository,
            myMapStoreRepository,
            myMapPublicInstitutionRepository,
            storeRepository,
            publicInstitutionRepository,
            s3FileService
        );

        User user = new User("owner@test.com", "password", "owner", UserRole.USER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", 1L);
        ReflectionTestUtils.setField(user, "profileImageUrl", "/api/v1/files/view?key=map_profile%2Fold.png");
        when(authService.getAuthenticatedUser()).thenReturn(user);
        UserMap map = buildMap(user, 101L, true, "uuid-101");
        ReflectionTestUtils.setField(map, "primaryMap", true);
        ReflectionTestUtils.setField(map, "profileImageUrl", "/api/v1/files/view?key=map_profile%2Fold.png");
        when(userMapRepository.findByIdAndOwnerUserIdAndDeletedAtIsNull(101L, 1L)).thenReturn(Optional.of(map));
        when(userMapRepository.save(any(UserMap.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapLikeRepository.countByMapId(101L)).thenReturn(0L);
        when(s3FileService.uploadFile(any(MockMultipartFile.class), org.mockito.ArgumentMatchers.eq("map_profile")))
            .thenReturn(new S3FileService.StoredFile("/api/v1/files/view?key=map_profile%2Fnew.png", "map_profile/new.png"));

        UserMapSummaryResponse response = service.updateMyMapProfileImage(
            101L,
            user,
            new MockMultipartFile("profileImage", "new.png", "image/png", new byte[] {1, 2, 3})
        );

        assertThat(response.profileImageUrl()).contains("map_profile");
        assertThat(user.getProfileImageUrl()).contains("map_profile");
        verify(s3FileService).deleteFilesAfterCommit(List.of("map_profile/old.png"));
    }

    private UserMap buildMap(User owner, Long id, boolean isPublic, String publicMapUuid) {
        UserMap map = new UserMap(owner, publicMapUuid, "지도", "설명", null, isPublic, false);
        ReflectionTestUtils.setField(map, "id", id);
        ReflectionTestUtils.setField(map, "createdAt", LocalDateTime.of(2024, 1, 1, 12, 0));
        ReflectionTestUtils.setField(map, "updatedAt", LocalDateTime.of(2024, 1, 1, 12, 0));
        return map;
    }
}
