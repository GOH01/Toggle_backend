package com.toggle.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toggle.dto.favorite.FavoriteStoreListItemResponse;
import com.toggle.dto.favorite.FavoriteStoreListResponse;
import com.toggle.dto.favorite.FavoriteStoreResponse;
import com.toggle.entity.Favorite;
import com.toggle.entity.PublicFavorite;
import com.toggle.entity.PublicInstitution;
import com.toggle.entity.Store;
import com.toggle.entity.User;
import com.toggle.global.exception.ApiException;
import com.toggle.repository.FavoriteRepository;
import com.toggle.repository.PublicFavoriteRepository;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final PublicFavoriteRepository publicFavoriteRepository;
    private final AuthService authService;
    private final StoreService storeService;
    private final PublicInstitutionService publicInstitutionService;
    private final ObjectMapper objectMapper;

    public FavoriteService(
        FavoriteRepository favoriteRepository,
        PublicFavoriteRepository publicFavoriteRepository,
        AuthService authService,
        StoreService storeService,
        PublicInstitutionService publicInstitutionService,
        ObjectMapper objectMapper
    ) {
        this.favoriteRepository = favoriteRepository;
        this.publicFavoriteRepository = publicFavoriteRepository;
        this.authService = authService;
        this.storeService = storeService;
        this.publicInstitutionService = publicInstitutionService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public FavoriteStoreResponse addFavorite(Long storeId) {
        User user = authService.getAuthenticatedUser();
        Store store = storeService.getStore(storeId);

        if (favoriteRepository.existsByUserIdAndStoreId(user.getId(), store.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "FAVORITE_ALREADY_EXISTS", "이미 즐겨찾기한 매장입니다.");
        }

        try {
            Favorite favorite = favoriteRepository.save(new Favorite(user, store));
            return new FavoriteStoreResponse(favorite.getId(), store.getId(), true, favorite.getCreatedAt());
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "FAVORITE_ALREADY_EXISTS", "이미 즐겨찾기한 매장입니다.");
        }
    }

    @Transactional
    public FavoriteStoreResponse removeFavorite(Long storeId) {
        User user = authService.getAuthenticatedUser();

        Favorite favorite = favoriteRepository.findByUserIdAndStoreId(user.getId(), storeId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FAVORITE_NOT_FOUND", "즐겨찾기 정보를 찾을 수 없습니다."));

        favoriteRepository.delete(favorite);
        return new FavoriteStoreResponse(favorite.getId(), storeId, false, favorite.getCreatedAt());
    }

    @Transactional
    public FavoriteStoreResponse addPublicFavorite(Long publicInstitutionId) {
        User user = authService.getAuthenticatedUser();
        PublicInstitution pi = publicInstitutionService.getInstitution(publicInstitutionId);

        if (publicFavoriteRepository.existsByUserIdAndPublicInstitutionId(user.getId(), pi.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "FAVORITE_ALREADY_EXISTS", "이미 즐겨찾기한 공공기관입니다.");
        }

        try {
            PublicFavorite favorite = publicFavoriteRepository.save(new PublicFavorite(user, pi));
            return new FavoriteStoreResponse(favorite.getId(), pi.getId(), true, favorite.getCreatedAt());
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "FAVORITE_ALREADY_EXISTS", "이미 즐겨찾기한 공공기관입니다.");
        }
    }

    @Transactional
    public FavoriteStoreResponse removePublicFavorite(Long publicInstitutionId) {
        User user = authService.getAuthenticatedUser();

        PublicFavorite favorite = publicFavoriteRepository.findByUserIdAndPublicInstitutionId(user.getId(), publicInstitutionId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FAVORITE_NOT_FOUND", "즐겨찾기 정보를 찾을 수 없습니다."));

        publicFavoriteRepository.delete(favorite);
        return new FavoriteStoreResponse(favorite.getId(), publicInstitutionId, false, favorite.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public FavoriteStoreListResponse getFavoriteStores() {
        User user = authService.getAuthenticatedUser();

        List<FavoriteStoreListItemResponse> items = favoriteRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId())
            .stream()
            .map(favorite -> new FavoriteStoreListItemResponse(
                favorite.getStore().getId(),
                favorite.getStore().getExternalPlaceId(),
                favorite.getStore().getName(),
                favorite.getStore().getCategoryName(),
                favorite.getStore().getAddress(),
                favorite.getStore().getRoadAddress(),
                favorite.getStore().getJibunAddress(),
                favorite.getStore().getPhone(),
                favorite.getStore().getBusinessStatus().name(),
                favorite.getStore().getLatitude().doubleValue(),
                favorite.getStore().getLongitude().doubleValue(),
                favorite.getStore().getOwnerNotice(),
                favorite.getStore().getOperatingOpenTime(),
                favorite.getStore().getOperatingCloseTime(),
                favorite.getStore().getBreakStartTime(),
                favorite.getStore().getBreakEndTime(),
                favorite.getStore().getRating() == null ? null : favorite.getStore().getRating().doubleValue(),
                deserializeImageUrls(favorite.getStore().getOwnerImageUrlsJson()),
                favorite.getCreatedAt()
            ))
            .toList();

        return new FavoriteStoreListResponse(items, 0, items.size(), items.size(), items.isEmpty() ? 0 : 1);
    }

    private List<String> deserializeImageUrls(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return List.of();
        }

        try {
            return objectMapper.readValue(
                rawJson,
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }
}
