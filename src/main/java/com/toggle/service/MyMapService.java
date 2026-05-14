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
        UserMap primaryMap = findPrimaryMap(user);
        if (primaryMap == null) {
            return new MyMapResponse(
                new MeResponse.MapProfile(null, false, null, null, null),
                List.of(),
                List.of()
            );
        }

        return new MyMapResponse(
            buildMapProfile(primaryMap),
            getMyMapStoreIds(primaryMap),
            getMyMapPublicIds(primaryMap)
        );
    }

    @Transactional
    public MyMapPlaceResponse addStore(Long storeId) {
        User user = authService.getAuthenticatedUser();
        Store store = storeService.getRegisteredStore(storeId);
        UserMap primaryMap = requirePrimaryMap(user);

        if (myMapStoreRepository.existsByMapIdAndStoreId(primaryMap.getId(), store.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "MY_MAP_PLACE_ALREADY_EXISTS", "이미 내 지도에 추가된 매장입니다.");
        }

        try {
            MyMapStore myMapStore = myMapStoreRepository.save(new MyMapStore(user, primaryMap, store));
            return new MyMapPlaceResponse("STORE", store.getId(), true, myMapStore.getCreatedAt());
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "MY_MAP_PLACE_ALREADY_EXISTS", "이미 내 지도에 추가된 매장입니다.");
        }
    }

    @Transactional
    public MyMapPlaceResponse removeStore(Long storeId) {
        User user = authService.getAuthenticatedUser();
        UserMap primaryMap = requirePrimaryMap(user);
        MyMapStore myMapStore = myMapStoreRepository.findByMapIdAndStoreId(primaryMap.getId(), storeId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MY_MAP_PLACE_NOT_FOUND", "내 지도에서 매장을 찾을 수 없습니다."));

        myMapStoreRepository.delete(myMapStore);
        return new MyMapPlaceResponse("STORE", storeId, false, myMapStore.getCreatedAt());
    }

    @Transactional
    public MyMapPlaceResponse addPublicInstitution(Long publicInstitutionId) {
        User user = authService.getAuthenticatedUser();
        PublicInstitution publicInstitution = publicInstitutionService.getInstitution(publicInstitutionId);
        UserMap primaryMap = requirePrimaryMap(user);

        if (myMapPublicInstitutionRepository.existsByMapIdAndPublicInstitutionId(primaryMap.getId(), publicInstitution.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "MY_MAP_PLACE_ALREADY_EXISTS", "이미 내 지도에 추가된 공공기관입니다.");
        }

        try {
            MyMapPublicInstitution myMapPublicInstitution = myMapPublicInstitutionRepository.save(
                new MyMapPublicInstitution(user, primaryMap, publicInstitution)
            );
            return new MyMapPlaceResponse("PUBLIC", publicInstitution.getId(), true, myMapPublicInstitution.getCreatedAt());
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "MY_MAP_PLACE_ALREADY_EXISTS", "이미 내 지도에 추가된 공공기관입니다.");
        }
    }

    @Transactional
    public MyMapPlaceResponse removePublicInstitution(Long publicInstitutionId) {
        User user = authService.getAuthenticatedUser();
        UserMap primaryMap = requirePrimaryMap(user);
        MyMapPublicInstitution myMapPublicInstitution = myMapPublicInstitutionRepository.findByMapIdAndPublicInstitutionId(primaryMap.getId(), publicInstitutionId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MY_MAP_PLACE_NOT_FOUND", "내 지도에서 공공기관을 찾을 수 없습니다."));

        myMapPublicInstitutionRepository.delete(myMapPublicInstitution);
        return new MyMapPlaceResponse("PUBLIC", publicInstitutionId, false, myMapPublicInstitution.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public PublicMapSearchResponse searchPublicMaps(String nickname) {
        authService.getAuthenticatedUser();

        String normalizedNickname = normalizeQuery(nickname);
        validateSearchNickname(normalizedNickname);

        List<Long> ownerUserIds = userRepository.findTop20ByStatusAndNicknameContainingIgnoreCaseOrderByIdAsc(
            UserStatus.ACTIVE,
            normalizedNickname
        ).stream().map(User::getId).toList();

        List<PublicMapSearchItemResponse> content = userMapRepository
            .findAllByOwnerUserIdInAndDeletedAtIsNullOrderByOwnerUserIdAscCreatedAtDescIdDesc(ownerUserIds)
            .stream()
            .filter(UserMap::isPublic)
            .map(map -> new PublicMapSearchItemResponse(
                map.getPublicMapUuid(),
                map.getOwnerUser().getNickname(),
                map.getTitle(),
                map.getDescription(),
                getSavedPlaceCount(map),
                map.getProfileImageUrl()
            ))
            .toList();

        return new PublicMapSearchResponse(content);
    }

    @Transactional(readOnly = true)
    public UserPublicMapResponse getPublicMap(String publicMapUuid) {
        UserMap map = userMapRepository.findByPublicMapUuidAndDeletedAtIsNull(publicMapUuid)
            .filter(UserMap::isPublic)
            .filter(candidate -> candidate.getOwnerUser().getStatus() == UserStatus.ACTIVE)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PUBLIC_MAP_NOT_FOUND", "공개 지도를 찾을 수 없습니다."));

        return new UserPublicMapResponse(
            map.getPublicMapUuid(),
            map.getOwnerUser().getNickname(),
            map.getTitle(),
            map.getDescription(),
            map.getProfileImageUrl(),
            getMyMapStoreIds(map),
            getMyMapPublicIds(map)
        );
    }

    private MeResponse.MapProfile buildMapProfile(UserMap map) {
        return new MeResponse.MapProfile(
            map.getPublicMapUuid(),
            map.isPublic(),
            map.getTitle(),
            map.getDescription(),
            map.getProfileImageUrl()
        );
    }

    private List<Long> getMyMapStoreIds(UserMap map) {
        return myMapStoreRepository.findAllByMapIdAndStoreDeletedAtIsNullOrderByCreatedAtDesc(map.getId()).stream()
            .map(myMapStore -> myMapStore.getStore().getId())
            .toList();
    }

    private List<Long> getMyMapPublicIds(UserMap map) {
        return myMapPublicInstitutionRepository.findAllByMapIdOrderByCreatedAtDesc(map.getId()).stream()
            .map(myMapPublicInstitution -> myMapPublicInstitution.getPublicInstitution().getId())
            .toList();
    }

    private long getSavedPlaceCount(UserMap map) {
        return getMyMapStoreIds(map).size() + getMyMapPublicIds(map).size();
    }

    private UserMap findPrimaryMap(User user) {
        return userMapRepository.findAllByOwnerUserIdAndDeletedAtIsNullOrderByPrimaryMapDescCreatedAtAscIdAsc(user.getId())
            .stream()
            .findFirst()
            .orElse(null);
    }

    private UserMap requirePrimaryMap(User user) {
        UserMap primaryMap = findPrimaryMap(user);
        if (primaryMap != null) {
            return primaryMap;
        }

        throw new ApiException(HttpStatus.NOT_FOUND, "MAP_NOT_FOUND", "지도를 찾을 수 없습니다.");
    }

    private <T> List<Long> mergePlaceIds(List<T> mapPlaces, java.util.function.Function<T, Long> idExtractor) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        mapPlaces.stream()
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
