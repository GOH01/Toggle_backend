package com.toggle.service;

import com.toggle.dto.map.MapLikeResponse;
import com.toggle.dto.map.PublicMapListItemResponse;
import com.toggle.dto.map.PublicMapListResponse;
import com.toggle.dto.map.UserMapDetailResponse;
import com.toggle.dto.map.UserMapSummaryResponse;
import com.toggle.dto.map.UserMapUpsertRequest;
import com.toggle.dto.user.MyMapPlaceResponse;
import com.toggle.entity.MyMapPublicInstitution;
import com.toggle.entity.MyMapStore;
import com.toggle.entity.PublicInstitution;
import com.toggle.entity.Store;
import com.toggle.entity.User;
import com.toggle.entity.UserMap;
import com.toggle.entity.UserMapLike;
import com.toggle.global.exception.ApiException;
import com.toggle.global.util.ImageUrlMapper;
import com.toggle.repository.MyMapPublicInstitutionRepository;
import com.toggle.repository.MyMapStoreRepository;
import com.toggle.repository.PublicInstitutionRepository;
import com.toggle.repository.StoreRepository;
import com.toggle.repository.UserMapLikeRepository;
import com.toggle.repository.UserMapRepository;
import com.toggle.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserMapService {

    private static final String MAP_NOT_FOUND_CODE = "MAP_NOT_FOUND";
    private static final String MAP_ACCESS_DENIED_CODE = "MAP_ACCESS_DENIED";
    private static final String MAP_LIKE_NOT_ALLOWED_CODE = "MAP_LIKE_NOT_ALLOWED";
    private static final String MAP_LIKE_ALREADY_EXISTS_CODE = "MAP_LIKE_ALREADY_EXISTS";
    private static final String INVALID_MAP_SORT_CODE = "INVALID_MAP_SORT";
    private static final String SORT_LATEST = "latest";
    private static final String SORT_LIKES = "likes";

    private final AuthService authService;
    private final UserRepository userRepository;
    private final UserMapRepository userMapRepository;
    private final UserMapLikeRepository userMapLikeRepository;
    private final MyMapStoreRepository myMapStoreRepository;
    private final MyMapPublicInstitutionRepository myMapPublicInstitutionRepository;
    private final StoreRepository storeRepository;
    private final PublicInstitutionRepository publicInstitutionRepository;

    public UserMapService(
        AuthService authService,
        UserRepository userRepository,
        UserMapRepository userMapRepository,
        UserMapLikeRepository userMapLikeRepository,
        MyMapStoreRepository myMapStoreRepository,
        MyMapPublicInstitutionRepository myMapPublicInstitutionRepository,
        StoreRepository storeRepository,
        PublicInstitutionRepository publicInstitutionRepository
    ) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.userMapRepository = userMapRepository;
        this.userMapLikeRepository = userMapLikeRepository;
        this.myMapStoreRepository = myMapStoreRepository;
        this.myMapPublicInstitutionRepository = myMapPublicInstitutionRepository;
        this.storeRepository = storeRepository;
        this.publicInstitutionRepository = publicInstitutionRepository;
    }

    @Transactional
    public UserMapSummaryResponse createMyMap(User ownerUser, UserMapUpsertRequest request) {
        User user = authService.getAuthenticatedUser();
        ensureOwner(ownerUser, user);

        String title = normalizeTitle(request.title());
        String description = normalizeNullableText(request.description());
        String profileImageUrl = normalizeNullableText(request.profileImageUrl());
        boolean isPublic = Boolean.TRUE.equals(request.isPublic());
        String publicMapUuid;
        if (user.getDefaultMapId() == null) {
            publicMapUuid = user.getPublicMapUuid();
            if (publicMapUuid == null || publicMapUuid.isBlank()) {
                publicMapUuid = buildPublicMapUuid();
            }
        } else {
            publicMapUuid = buildPublicMapUuid();
        }

        UserMap map = userMapRepository.save(new UserMap(
            user,
            publicMapUuid,
            title,
            description,
            profileImageUrl,
            isPublic,
            false
        ));

        if (user.getDefaultMapId() == null) {
            map.markPrimary(true);
            user.setDefaultMapId(map.getId());
            syncUserBridge(user, map);
            userRepository.save(user);
        }

        return toSummary(map, 0L);
    }

    @Transactional(readOnly = true)
    public List<UserMapSummaryResponse> getMyMaps(User ownerUser) {
        User user = authService.getAuthenticatedUser();
        ensureOwner(ownerUser, user);

        return userMapRepository.findAllByOwnerUserIdAndDeletedAtIsNullOrderByPrimaryMapDescCreatedAtAscIdAsc(user.getId())
            .stream()
            .map(map -> toSummary(map, userMapLikeRepository.countByMapId(map.getId())))
            .toList();
    }

    @Transactional(readOnly = true)
    public UserMapDetailResponse getMyMap(Long mapId, User ownerUser) {
        User user = authService.getAuthenticatedUser();
        ensureOwner(ownerUser, user);

        UserMap map = getOwnedMap(mapId, user);
        return new UserMapDetailResponse(
            toSummary(map, userMapLikeRepository.countByMapId(map.getId())),
            getStoreIds(map.getId()),
            getPublicInstitutionIds(map.getId())
        );
    }

    @Transactional
    public UserMapSummaryResponse updateMyMap(Long mapId, User ownerUser, UserMapUpsertRequest request) {
        User user = authService.getAuthenticatedUser();
        ensureOwner(ownerUser, user);

        UserMap map = getOwnedMap(mapId, user);
        map.update(
            request.isPublic(),
            normalizeTitle(request.title()),
            normalizeNullableText(request.description()),
            normalizeNullableText(request.profileImageUrl())
        );

        if (user.getDefaultMapId() != null && user.getDefaultMapId().equals(map.getId())) {
            syncUserBridge(user, map);
            userRepository.save(user);
        }

        userMapRepository.save(map);
        return toSummary(map, userMapLikeRepository.countByMapId(map.getId()));
    }

    @Transactional
    public void deleteMyMap(Long mapId, User ownerUser) {
        User user = authService.getAuthenticatedUser();
        ensureOwner(ownerUser, user);

        UserMap map = getOwnedMap(mapId, user);
        boolean wasPrimary = user.getDefaultMapId() != null && user.getDefaultMapId().equals(map.getId());

        myMapStoreRepository.deleteAllByMapId(map.getId());
        myMapPublicInstitutionRepository.deleteAllByMapId(map.getId());
        userMapLikeRepository.deleteAllByMapId(map.getId());
        map.markDeleted(user, LocalDateTime.now());
        userMapRepository.save(map);

        if (wasPrimary) {
            promotePrimaryMapOrCreateReplacement(user);
        }
    }

    @Transactional
    public MyMapPlaceResponse addStoreToMap(Long mapId, Long storeId, User ownerUser) {
        User user = authService.getAuthenticatedUser();
        ensureOwner(ownerUser, user);
        UserMap map = getOwnedMap(mapId, user);
        Store store = storeRepository.findByIdAndDeletedAtIsNull(storeId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "STORE_NOT_FOUND", "매장을 찾을 수 없습니다."));

        if (myMapStoreRepository.existsByMapIdAndStoreId(map.getId(), store.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "MY_MAP_PLACE_ALREADY_EXISTS", "이미 지도에 추가된 매장입니다.");
        }

        MyMapStore myMapStore = myMapStoreRepository.save(new MyMapStore(user, map, store));
        return new MyMapPlaceResponse("STORE", store.getId(), true, myMapStore.getCreatedAt());
    }

    @Transactional
    public MyMapPlaceResponse removeStoreFromMap(Long mapId, Long storeId, User ownerUser) {
        User user = authService.getAuthenticatedUser();
        ensureOwner(ownerUser, user);
        UserMap map = getOwnedMap(mapId, user);

        MyMapStore myMapStore = myMapStoreRepository.findByMapIdAndStoreId(map.getId(), storeId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MY_MAP_PLACE_NOT_FOUND", "지도에서 매장을 찾을 수 없습니다."));

        myMapStoreRepository.delete(myMapStore);
        return new MyMapPlaceResponse("STORE", storeId, false, myMapStore.getCreatedAt());
    }

    @Transactional
    public MyMapPlaceResponse addPublicInstitutionToMap(Long mapId, Long publicInstitutionId, User ownerUser) {
        User user = authService.getAuthenticatedUser();
        ensureOwner(ownerUser, user);
        UserMap map = getOwnedMap(mapId, user);
        PublicInstitution publicInstitution = publicInstitutionRepository.findById(publicInstitutionId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PUBLIC_INSTITUTION_NOT_FOUND", "공공기관을 찾을 수 없습니다."));

        if (myMapPublicInstitutionRepository.existsByMapIdAndPublicInstitutionId(map.getId(), publicInstitution.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "MY_MAP_PLACE_ALREADY_EXISTS", "이미 지도에 추가된 공공기관입니다.");
        }

        MyMapPublicInstitution myMapPublicInstitution = myMapPublicInstitutionRepository.save(new MyMapPublicInstitution(user, map, publicInstitution));
        return new MyMapPlaceResponse("PUBLIC", publicInstitution.getId(), true, myMapPublicInstitution.getCreatedAt());
    }

    @Transactional
    public MyMapPlaceResponse removePublicInstitutionFromMap(Long mapId, Long publicInstitutionId, User ownerUser) {
        User user = authService.getAuthenticatedUser();
        ensureOwner(ownerUser, user);
        UserMap map = getOwnedMap(mapId, user);

        MyMapPublicInstitution myMapPublicInstitution = myMapPublicInstitutionRepository.findByMapIdAndPublicInstitutionId(map.getId(), publicInstitutionId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MY_MAP_PLACE_NOT_FOUND", "지도에서 공공기관을 찾을 수 없습니다."));

        myMapPublicInstitutionRepository.delete(myMapPublicInstitution);
        return new MyMapPlaceResponse("PUBLIC", publicInstitutionId, false, myMapPublicInstitution.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public UserMapDetailResponse getPublicMap(Long mapId) {
        UserMap map = userMapRepository.findByIdAndDeletedAtIsNull(mapId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MAP_NOT_FOUND_CODE, "공개 지도를 찾을 수 없습니다."));
        if (!map.isPublic()) {
            throw new ApiException(HttpStatus.NOT_FOUND, MAP_NOT_FOUND_CODE, "공개 지도를 찾을 수 없습니다.");
        }

        return new UserMapDetailResponse(
            toSummary(map, userMapLikeRepository.countByMapId(map.getId())),
            getStoreIds(map.getId()),
            getPublicInstitutionIds(map.getId())
        );
    }

    @Transactional(readOnly = true)
    public PublicMapListResponse listPublicMaps(String keyword, String sort, int page, int size) {
        validatePageRequest(page, size);
        String normalizedSort = normalizeSort(sort);
        String normalizedKeyword = normalizeNullableText(keyword);

        List<UserMap> maps = userMapRepository.findAllByIsPublicTrueAndDeletedAtIsNull(Pageable.unpaged()).stream()
            .filter(map -> normalizedKeyword == null || matchesKeyword(map, normalizedKeyword))
            .sorted(publicMapComparator(normalizedSort))
            .toList();

        List<UserMap> pageContent = maps.stream()
            .skip((long) page * size)
            .limit(size)
            .toList();

        List<PublicMapListItemResponse> content = pageContent.stream()
            .map(map -> new PublicMapListItemResponse(
                map.getId(),
                map.getPublicMapUuid(),
                map.getOwnerUser().getNickname(),
                map.getTitle(),
                map.getDescription(),
                map.getProfileImageUrl(),
                userMapLikeRepository.countByMapId(map.getId())
            ))
            .toList();

        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) maps.size() / size);
        return new PublicMapListResponse(content, page, size, maps.size(), totalPages);
    }

    @Transactional
    public MapLikeResponse likeMap(Long mapId, User ownerUser) {
        User user = authService.getAuthenticatedUser();
        ensureOwnerNotSelf(ownerUser, user);
        UserMap map = getPublicMapOrThrow(mapId);

        if (map.getOwnerUser().getId().equals(user.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, MAP_LIKE_NOT_ALLOWED_CODE, "자기 지도에는 좋아요를 누를 수 없습니다.");
        }
        if (userMapLikeRepository.existsByMapIdAndUserId(mapId, user.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, MAP_LIKE_ALREADY_EXISTS_CODE, "이미 좋아요를 누른 지도입니다.");
        }

        userMapLikeRepository.save(new UserMapLike(map, user));
        return new MapLikeResponse(map.getId(), userMapLikeRepository.countByMapId(map.getId()), true);
    }

    @Transactional
    public MapLikeResponse unlikeMap(Long mapId, User ownerUser) {
        User user = authService.getAuthenticatedUser();
        ensureOwnerNotSelf(ownerUser, user);
        UserMap map = getPublicMapOrThrow(mapId);

        userMapLikeRepository.findByMapIdAndUserId(map.getId(), user.getId())
            .ifPresent(userMapLikeRepository::delete);
        return new MapLikeResponse(map.getId(), userMapLikeRepository.countByMapId(map.getId()), false);
    }

    @Transactional(readOnly = true)
    public MapLikeResponse getLikes(Long mapId) {
        UserMap map = getPublicMapOrThrow(mapId);
        return new MapLikeResponse(map.getId(), userMapLikeRepository.countByMapId(map.getId()), false);
    }

    private void syncUserBridge(User user, UserMap map) {
        user.updateMapProfile(map.isPublic(), map.getTitle(), map.getDescription(), map.getProfileImageUrl());
        user.setPublicMapUuid(map.getPublicMapUuid());
    }

    private void promotePrimaryMapOrCreateReplacement(User user) {
        List<UserMap> remaining = userMapRepository.findAllByOwnerUserIdAndDeletedAtIsNullOrderByPrimaryMapDescCreatedAtAscIdAsc(user.getId());
        if (!remaining.isEmpty()) {
            UserMap nextPrimary = remaining.get(0);
            nextPrimary.markPrimary(true);
            user.setDefaultMapId(nextPrimary.getId());
            syncUserBridge(user, nextPrimary);
            userRepository.save(user);
            userMapRepository.save(nextPrimary);
            return;
        }

        UserMap replacement = userMapRepository.save(new UserMap(
            user,
            buildPublicMapUuid(),
            normalizeTitle(user.getNickname() + "님의 지도"),
            null,
            null,
            false,
            true
        ));
        user.setDefaultMapId(replacement.getId());
        syncUserBridge(user, replacement);
        userRepository.save(user);
    }

    private UserMap getOwnedMap(Long mapId, User user) {
        return userMapRepository.findByIdAndOwnerUserIdAndDeletedAtIsNull(mapId, user.getId())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MAP_NOT_FOUND_CODE, "지도를 찾을 수 없습니다."));
    }

    private UserMap getPublicMapOrThrow(Long mapId) {
        return userMapRepository.findByIdAndDeletedAtIsNull(mapId)
            .filter(map -> map.isPublic())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MAP_NOT_FOUND_CODE, "공개 지도를 찾을 수 없습니다."));
    }

    private List<Long> getStoreIds(Long mapId) {
        return myMapStoreRepository.findAllByMapIdOrderByCreatedAtDesc(mapId).stream()
            .map(myMapStore -> myMapStore.getStore().getId())
            .toList();
    }

    private List<Long> getPublicInstitutionIds(Long mapId) {
        return myMapPublicInstitutionRepository.findAllByMapIdOrderByCreatedAtDesc(mapId).stream()
            .map(myMapPublicInstitution -> myMapPublicInstitution.getPublicInstitution().getId())
            .toList();
    }

    private boolean matchesKeyword(UserMap map, String keyword) {
        String normalized = keyword.toLowerCase(Locale.ROOT);
        return containsIgnoreCase(map.getTitle(), normalized)
            || containsIgnoreCase(map.getDescription(), normalized)
            || containsIgnoreCase(map.getOwnerUser().getNickname(), normalized);
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        if (value == null || keyword == null) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private Comparator<UserMap> publicMapComparator(String sort) {
        if (SORT_LIKES.equals(sort)) {
            return Comparator
                .comparingLong((UserMap map) -> userMapLikeRepository.countByMapId(map.getId())).reversed()
                .thenComparing(UserMap::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(UserMap::getId, Comparator.nullsLast(Comparator.reverseOrder()));
        }

        return Comparator
            .comparing(UserMap::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(UserMap::getId, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private UserMapSummaryResponse toSummary(UserMap map, long likeCount) {
        return new UserMapSummaryResponse(
            map.getId(),
            map.getPublicMapUuid(),
            map.getTitle(),
            map.getDescription(),
            map.getProfileImageUrl(),
            map.isPublic(),
            likeCount,
            map.getCreatedAt(),
            map.getUpdatedAt()
        );
    }

    private String buildPublicMapUuid() {
        return java.util.UUID.randomUUID().toString();
    }

    private String normalizeTitle(String value) {
        String normalized = normalizeNullableText(value);
        if (normalized == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MAP_TITLE", "지도 제목을 입력해 주세요.");
        }
        return normalized;
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return SORT_LATEST;
        }

        String normalized = sort.trim().toLowerCase(Locale.ROOT);
        if (SORT_LATEST.equals(normalized) || SORT_LIKES.equals(normalized)) {
            return normalized;
        }

        throw new ApiException(HttpStatus.BAD_REQUEST, INVALID_MAP_SORT_CODE, "지원하지 않는 정렬 기준입니다.");
    }

    private void validatePageRequest(int page, int size) {
        if (page < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "페이지는 0 이상이어야 합니다.");
        }
        if (size <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE_SIZE", "페이지 크기는 1 이상이어야 합니다.");
        }
    }

    private void ensureOwner(User ownerUser, User currentUser) {
        if (!ownerUser.getId().equals(currentUser.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, MAP_ACCESS_DENIED_CODE, "본인만 지도를 관리할 수 있습니다.");
        }
    }

    private void ensureOwnerNotSelf(User ownerUser, User currentUser) {
        ensureOwner(ownerUser, currentUser);
    }
}
