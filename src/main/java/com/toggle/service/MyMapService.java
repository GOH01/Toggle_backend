package com.toggle.service;

import com.toggle.dto.auth.MeResponse;
import com.toggle.dto.user.MyMapPlaceResponse;
import com.toggle.dto.user.MyMapResponse;
import com.toggle.dto.user.PublicMapSearchItemResponse;
import com.toggle.dto.user.PublicMapSearchResponse;
import com.toggle.dto.user.UserPublicMapResponse;
import com.toggle.entity.MyMapPublicInstitution;
import com.toggle.entity.MyMapStore;
import com.toggle.entity.PublicInstitution;
import com.toggle.entity.Store;
import com.toggle.entity.User;
import com.toggle.entity.UserMap;
import com.toggle.entity.UserStatus;
import com.toggle.global.exception.ApiException;
import com.toggle.repository.MyMapPublicInstitutionRepository;
import com.toggle.repository.MyMapStoreRepository;
import com.toggle.repository.UserRepository;
import com.toggle.repository.UserMapRepository;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MyMapService {

    private final AuthService authService;
    private final StoreService storeService;
    private final PublicInstitutionService publicInstitutionService;
    private final MyMapStoreRepository myMapStoreRepository;
    private final MyMapPublicInstitutionRepository myMapPublicInstitutionRepository;
    private final UserRepository userRepository;
    private final UserMapRepository userMapRepository;

    public MyMapService(
        AuthService authService,
        StoreService storeService,
        PublicInstitutionService publicInstitutionService,
        MyMapStoreRepository myMapStoreRepository,
        MyMapPublicInstitutionRepository myMapPublicInstitutionRepository,
        UserRepository userRepository,
        UserMapRepository userMapRepository
    ) {
        this.authService = authService;
        this.storeService = storeService;
        this.publicInstitutionService = publicInstitutionService;
        this.myMapStoreRepository = myMapStoreRepository;
        this.myMapPublicInstitutionRepository = myMapPublicInstitutionRepository;
        this.userRepository = userRepository;
        this.userMapRepository = userMapRepository;
    }

    @Transactional
    public MyMapResponse getMyMap() {
        User user = authService.getAuthenticatedUser();
        UserMap defaultMap = findDefaultMap(user);
        if (defaultMap != null) {
            migrateLegacyPlacesToDefaultMap(user, defaultMap);
        }
        return new MyMapResponse(
            buildMapProfile(user),
            getMyMapStoreIds(user, defaultMap),
            getMyMapPublicIds(user, defaultMap)
        );
    }

    @Transactional
    public MyMapPlaceResponse addStore(Long storeId) {
        User user = authService.getAuthenticatedUser();
        UserMap defaultMap = findDefaultMap(user);
        if (defaultMap != null) {
            migrateLegacyPlacesToDefaultMap(user, defaultMap);
        }
        Store store = storeService.getRegisteredStore(storeId);

        boolean exists = defaultMap == null
            ? myMapStoreRepository.findByUserIdAndMapIsNullAndStoreId(user.getId(), store.getId()).isPresent()
            : myMapStoreRepository.existsByMapIdAndStoreId(defaultMap.getId(), store.getId());
        if (exists) {
            throw new ApiException(HttpStatus.CONFLICT, "MY_MAP_PLACE_ALREADY_EXISTS", "이미 내 지도에 추가된 매장입니다.");
        }

        try {
            MyMapStore myMapStore = defaultMap == null
                ? myMapStoreRepository.save(new MyMapStore(user, store))
                : myMapStoreRepository.save(new MyMapStore(user, defaultMap, store));
            return new MyMapPlaceResponse("STORE", store.getId(), true, myMapStore.getCreatedAt());
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "MY_MAP_PLACE_ALREADY_EXISTS", "이미 내 지도에 추가된 매장입니다.");
        }
    }

    @Transactional
    public MyMapPlaceResponse removeStore(Long storeId) {
        User user = authService.getAuthenticatedUser();
        UserMap defaultMap = findDefaultMap(user);
        MyMapStore myMapStore = (defaultMap == null
            ? myMapStoreRepository.findByUserIdAndMapIsNullAndStoreId(user.getId(), storeId)
            : myMapStoreRepository.findByMapIdAndStoreId(defaultMap.getId(), storeId))
            .or(() -> myMapStoreRepository.findByUserIdAndMapIsNullAndStoreId(user.getId(), storeId)
                .filter(legacyStore -> legacyStore.getStore().getDeletedAt() == null))
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MY_MAP_PLACE_NOT_FOUND", "내 지도에서 매장을 찾을 수 없습니다."));

        myMapStoreRepository.delete(myMapStore);
        return new MyMapPlaceResponse("STORE", storeId, false, myMapStore.getCreatedAt());
    }

    @Transactional
    public MyMapPlaceResponse addPublicInstitution(Long publicInstitutionId) {
        User user = authService.getAuthenticatedUser();
        UserMap defaultMap = findDefaultMap(user);
        if (defaultMap != null) {
            migrateLegacyPlacesToDefaultMap(user, defaultMap);
        }
        PublicInstitution publicInstitution = publicInstitutionService.getInstitution(publicInstitutionId);

        boolean exists = defaultMap == null
            ? myMapPublicInstitutionRepository.findByUserIdAndMapIsNullAndPublicInstitutionId(user.getId(), publicInstitution.getId()).isPresent()
            : myMapPublicInstitutionRepository.existsByMapIdAndPublicInstitutionId(defaultMap.getId(), publicInstitution.getId());
        if (exists) {
            throw new ApiException(HttpStatus.CONFLICT, "MY_MAP_PLACE_ALREADY_EXISTS", "이미 내 지도에 추가된 공공기관입니다.");
        }

        try {
            MyMapPublicInstitution myMapPublicInstitution = defaultMap == null
                ? myMapPublicInstitutionRepository.save(new MyMapPublicInstitution(user, publicInstitution))
                : myMapPublicInstitutionRepository.save(new MyMapPublicInstitution(user, defaultMap, publicInstitution));
            return new MyMapPlaceResponse("PUBLIC", publicInstitution.getId(), true, myMapPublicInstitution.getCreatedAt());
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "MY_MAP_PLACE_ALREADY_EXISTS", "이미 내 지도에 추가된 공공기관입니다.");
        }
    }

    @Transactional
    public MyMapPlaceResponse removePublicInstitution(Long publicInstitutionId) {
        User user = authService.getAuthenticatedUser();
        UserMap defaultMap = findDefaultMap(user);
        MyMapPublicInstitution myMapPublicInstitution = (defaultMap == null
            ? myMapPublicInstitutionRepository.findByUserIdAndMapIsNullAndPublicInstitutionId(user.getId(), publicInstitutionId)
            : myMapPublicInstitutionRepository.findByMapIdAndPublicInstitutionId(defaultMap.getId(), publicInstitutionId))
            .or(() -> myMapPublicInstitutionRepository.findByUserIdAndMapIsNullAndPublicInstitutionId(user.getId(), publicInstitutionId))
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MY_MAP_PLACE_NOT_FOUND", "내 지도에서 공공기관을 찾을 수 없습니다."));

        myMapPublicInstitutionRepository.delete(myMapPublicInstitution);
        return new MyMapPlaceResponse("PUBLIC", publicInstitutionId, false, myMapPublicInstitution.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public PublicMapSearchResponse searchPublicMaps(String nickname) {
        authService.getAuthenticatedUser();

        String normalizedNickname = normalizeQuery(nickname);
        validateSearchNickname(normalizedNickname);

        List<PublicMapSearchItemResponse> content = userRepository
            .findTop20ByPublicMapTrueAndStatusAndNicknameContainingIgnoreCaseOrderByIdAsc(UserStatus.ACTIVE, normalizedNickname)
            .stream()
            .map(user -> new PublicMapSearchItemResponse(
                user.getPublicMapUuid(),
                user.getNickname(),
                resolveMapTitle(user),
                user.getMapDescription(),
                getSavedPlaceCount(user),
                user.getProfileImageUrl()
            ))
            .toList();

        return new PublicMapSearchResponse(content);
    }

    @Transactional(readOnly = true)
    public UserPublicMapResponse getPublicMap(String publicMapUuid) {
        User user = userRepository.findByPublicMapUuid(publicMapUuid)
            .filter(User::isPublicMap)
            .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PUBLIC_MAP_NOT_FOUND", "공개 지도를 찾을 수 없습니다."));

        return new UserPublicMapResponse(
            user.getPublicMapUuid(),
            user.getNickname(),
            resolveMapTitle(user),
            user.getMapDescription(),
            user.getProfileImageUrl(),
            getMyMapStoreIds(user, findDefaultMap(user)),
            getMyMapPublicIds(user, findDefaultMap(user))
        );
    }

    private MeResponse.MapProfile buildMapProfile(User user) {
        String publicMapUuid = authService.ensurePublicMapUuid(user);
        return new MeResponse.MapProfile(
            publicMapUuid,
            user.isPublicMap(),
            resolveMapTitle(user),
            user.getMapDescription(),
            user.getProfileImageUrl()
        );
    }

    private String resolveMapTitle(User user) {
        if (user.getMapTitle() != null && !user.getMapTitle().isBlank()) {
            return user.getMapTitle();
        }
        return user.getNickname() + "님의 지도";
    }

    private List<Long> getMyMapStoreIds(User user, UserMap defaultMap) {
        List<MyMapStore> mapStores = defaultMap == null
            ? List.of()
            : myMapStoreRepository.findAllByMapIdAndStoreDeletedAtIsNullOrderByCreatedAtDesc(defaultMap.getId());
        List<MyMapStore> legacyStores = myMapStoreRepository.findAllByUserIdAndMapIsNullOrderByCreatedAtDesc(user.getId());

        return mergePlaceIds(
            mapStores,
            legacyStores.stream().filter(myMapStore -> myMapStore.getStore().getDeletedAt() == null).toList(),
            myMapStore -> myMapStore.getStore().getId()
        );
    }

    private List<Long> getMyMapPublicIds(User user, UserMap defaultMap) {
        List<MyMapPublicInstitution> mapPublics = defaultMap == null
            ? List.of()
            : myMapPublicInstitutionRepository.findAllByMapIdOrderByCreatedAtDesc(defaultMap.getId());
        List<MyMapPublicInstitution> legacyPublics = myMapPublicInstitutionRepository.findAllByUserIdAndMapIsNullOrderByCreatedAtDesc(user.getId());

        return mergePlaceIds(mapPublics, legacyPublics, myMapPublicInstitution -> myMapPublicInstitution.getPublicInstitution().getId());
    }

    private long getSavedPlaceCount(User user) {
        UserMap defaultMap = findDefaultMap(user);
        return getMyMapStoreIds(user, defaultMap).size() + getMyMapPublicIds(user, defaultMap).size();
    }

    private UserMap findDefaultMap(User user) {
        if (user.getDefaultMapId() == null) {
            String publicMapUuid = user.getPublicMapUuid();
            if (publicMapUuid == null || publicMapUuid.isBlank()) {
                return null;
            }

            return userMapRepository.findByPublicMapUuidAndDeletedAtIsNull(publicMapUuid).orElse(null);
        }

        return userMapRepository.findByIdAndDeletedAtIsNull(user.getDefaultMapId()).orElse(null);
    }

    private void migrateLegacyPlacesToDefaultMap(User user, UserMap defaultMap) {
        List<MyMapStore> legacyStores = myMapStoreRepository.findAllByUserIdAndMapIsNullOrderByCreatedAtDesc(user.getId());
        for (MyMapStore legacyStore : legacyStores) {
            if (legacyStore.getStore().getDeletedAt() != null) {
                myMapStoreRepository.delete(legacyStore);
                continue;
            }
            Long storeId = legacyStore.getStore().getId();
            if (myMapStoreRepository.existsByMapIdAndStoreId(defaultMap.getId(), storeId)) {
                myMapStoreRepository.delete(legacyStore);
                continue;
            }
            legacyStore.setMap(defaultMap);
            myMapStoreRepository.save(legacyStore);
        }

        List<MyMapPublicInstitution> legacyPublics = myMapPublicInstitutionRepository.findAllByUserIdAndMapIsNullOrderByCreatedAtDesc(user.getId());
        for (MyMapPublicInstitution legacyPublic : legacyPublics) {
            Long publicInstitutionId = legacyPublic.getPublicInstitution().getId();
            if (myMapPublicInstitutionRepository.existsByMapIdAndPublicInstitutionId(defaultMap.getId(), publicInstitutionId)) {
                myMapPublicInstitutionRepository.delete(legacyPublic);
                continue;
            }
            legacyPublic.setMap(defaultMap);
            myMapPublicInstitutionRepository.save(legacyPublic);
        }
    }

    private <T> List<Long> mergePlaceIds(List<T> mapPlaces, List<T> legacyPlaces, java.util.function.Function<T, Long> idExtractor) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        mapPlaces.stream()
            .map(idExtractor)
            .forEach(ids::add);
        legacyPlaces.stream()
            .map(idExtractor)
            .forEach(ids::add);
        return List.copyOf(ids);
    }

    private String normalizeQuery(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void validateSearchNickname(String nickname) {
        if (nickname == null || nickname.length() < 2 || nickname.length() > 30) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PUBLIC_MAP_SEARCH_QUERY", "닉네임은 2자 이상 30자 이하여야 합니다.");
        }
    }
}
