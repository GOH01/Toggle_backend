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
import com.toggle.entity.UserStatus;
import com.toggle.global.exception.ApiException;
import com.toggle.repository.MyMapPublicInstitutionRepository;
import com.toggle.repository.MyMapStoreRepository;
import com.toggle.repository.UserRepository;
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

    public MyMapService(
        AuthService authService,
        StoreService storeService,
        PublicInstitutionService publicInstitutionService,
        MyMapStoreRepository myMapStoreRepository,
        MyMapPublicInstitutionRepository myMapPublicInstitutionRepository,
        UserRepository userRepository
    ) {
        this.authService = authService;
        this.storeService = storeService;
        this.publicInstitutionService = publicInstitutionService;
        this.myMapStoreRepository = myMapStoreRepository;
        this.myMapPublicInstitutionRepository = myMapPublicInstitutionRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public MyMapResponse getMyMap() {
        User user = authService.getAuthenticatedUser();
        return new MyMapResponse(
            buildMapProfile(user),
            getMyMapStoreIds(user),
            getMyMapPublicIds(user)
        );
    }

    @Transactional
    public MyMapPlaceResponse addStore(Long storeId) {
        User user = authService.getAuthenticatedUser();
        Store store = storeService.getRegisteredStore(storeId);

        if (myMapStoreRepository.existsByUserIdAndStoreId(user.getId(), store.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "MY_MAP_PLACE_ALREADY_EXISTS", "이미 내 지도에 추가된 매장입니다.");
        }

        try {
            MyMapStore myMapStore = myMapStoreRepository.save(new MyMapStore(user, store));
            return new MyMapPlaceResponse("STORE", store.getId(), true, myMapStore.getCreatedAt());
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "MY_MAP_PLACE_ALREADY_EXISTS", "이미 내 지도에 추가된 매장입니다.");
        }
    }

    @Transactional
    public MyMapPlaceResponse removeStore(Long storeId) {
        User user = authService.getAuthenticatedUser();
        MyMapStore myMapStore = myMapStoreRepository.findByUserIdAndStoreId(user.getId(), storeId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MY_MAP_PLACE_NOT_FOUND", "내 지도에서 매장을 찾을 수 없습니다."));

        myMapStoreRepository.delete(myMapStore);
        return new MyMapPlaceResponse("STORE", storeId, false, myMapStore.getCreatedAt());
    }

    @Transactional
    public MyMapPlaceResponse addPublicInstitution(Long publicInstitutionId) {
        User user = authService.getAuthenticatedUser();
        PublicInstitution publicInstitution = publicInstitutionService.getInstitution(publicInstitutionId);

        if (myMapPublicInstitutionRepository.existsByUserIdAndPublicInstitutionId(user.getId(), publicInstitution.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "MY_MAP_PLACE_ALREADY_EXISTS", "이미 내 지도에 추가된 공공기관입니다.");
        }

        try {
            MyMapPublicInstitution myMapPublicInstitution = myMapPublicInstitutionRepository.save(new MyMapPublicInstitution(user, publicInstitution));
            return new MyMapPlaceResponse("PUBLIC", publicInstitution.getId(), true, myMapPublicInstitution.getCreatedAt());
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "MY_MAP_PLACE_ALREADY_EXISTS", "이미 내 지도에 추가된 공공기관입니다.");
        }
    }

    @Transactional
    public MyMapPlaceResponse removePublicInstitution(Long publicInstitutionId) {
        User user = authService.getAuthenticatedUser();
        MyMapPublicInstitution myMapPublicInstitution = myMapPublicInstitutionRepository.findByUserIdAndPublicInstitutionId(user.getId(), publicInstitutionId)
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
            getMyMapStoreIds(user),
            getMyMapPublicIds(user)
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

    private List<Long> getMyMapStoreIds(User user) {
        return myMapStoreRepository.findAllByUserIdAndStoreDeletedAtIsNullOrderByCreatedAtDesc(user.getId()).stream()
            .map(myMapStore -> myMapStore.getStore().getId())
            .toList();
    }

    private List<Long> getMyMapPublicIds(User user) {
        return myMapPublicInstitutionRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId()).stream()
            .map(myMapPublicInstitution -> myMapPublicInstitution.getPublicInstitution().getId())
            .toList();
    }

    private long getSavedPlaceCount(User user) {
        return myMapStoreRepository.countByUserIdAndStoreDeletedAtIsNull(user.getId())
            + myMapPublicInstitutionRepository.countByUserId(user.getId());
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
