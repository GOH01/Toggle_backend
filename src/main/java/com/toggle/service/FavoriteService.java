package com.toggle.service;

import com.toggle.dto.favorite.FavoriteStoreListItemResponse;
import com.toggle.dto.favorite.FavoriteStoreListResponse;
import com.toggle.dto.favorite.FavoriteStoreResponse;
import com.toggle.entity.Favorite;
import com.toggle.entity.Store;
import com.toggle.entity.User;
import com.toggle.global.exception.ApiException;
import com.toggle.repository.FavoriteRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final AuthService authService;
    private final StoreService storeService;

    public FavoriteService(
        FavoriteRepository favoriteRepository,
        AuthService authService,
        StoreService storeService
    ) {
        this.favoriteRepository = favoriteRepository;
        this.authService = authService;
        this.storeService = storeService;
    }

    @Transactional
    public FavoriteStoreResponse addFavorite(Long userId, Long storeId) {
        User user = authService.getAuthenticatedUser(userId);
        Store store = storeService.getStore(storeId);

        if (favoriteRepository.existsByUserIdAndStoreId(user.getId(), store.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "FAVORITE_ALREADY_EXISTS", "이미 즐겨찾기한 매장입니다.");
        }

        Favorite favorite = favoriteRepository.save(new Favorite(user, store));
        return new FavoriteStoreResponse(favorite.getId(), store.getId(), true, favorite.getCreatedAt());
    }

    @Transactional
    public FavoriteStoreResponse removeFavorite(Long userId, Long storeId) {
        authService.getAuthenticatedUser(userId);

        Favorite favorite = favoriteRepository.findByUserIdAndStoreId(userId, storeId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FAVORITE_NOT_FOUND", "즐겨찾기 정보를 찾을 수 없습니다."));

        favoriteRepository.delete(favorite);
        return new FavoriteStoreResponse(favorite.getId(), storeId, false, favorite.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public FavoriteStoreListResponse getFavoriteStores(Long userId) {
        authService.getAuthenticatedUser(userId);

        List<FavoriteStoreListItemResponse> items = favoriteRepository.findAllByUserIdOrderByCreatedAtDesc(userId)
            .stream()
            .map(favorite -> new FavoriteStoreListItemResponse(
                favorite.getStore().getId(),
                favorite.getStore().getExternalPlaceId(),
                favorite.getStore().getName(),
                favorite.getStore().getAddress(),
                favorite.getStore().getPhone(),
                favorite.getStore().getBusinessStatus().name(),
                favorite.getStore().getLatitude().doubleValue(),
                favorite.getStore().getLongitude().doubleValue(),
                favorite.getCreatedAt()
            ))
            .toList();

        return new FavoriteStoreListResponse(items, 0, items.size(), items.size(), items.isEmpty() ? 0 : 1);
    }
}
