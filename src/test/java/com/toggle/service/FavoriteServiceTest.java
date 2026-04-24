package com.toggle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toggle.dto.favorite.FavoriteStoreListResponse;
import com.toggle.entity.ExternalSource;
import com.toggle.entity.Favorite;
import com.toggle.entity.Store;
import com.toggle.entity.User;
import com.toggle.entity.UserRole;
import com.toggle.entity.UserStatus;
import com.toggle.global.exception.ApiException;
import com.toggle.repository.FavoriteRepository;
import com.toggle.repository.PublicFavoriteRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    @Mock
    private FavoriteRepository favoriteRepository;

    @Mock
    private PublicFavoriteRepository publicFavoriteRepository;

    @Mock
    private AuthService authService;

    @Mock
    private StoreService storeService;

    @Mock
    private PublicInstitutionService publicInstitutionService;

    @Mock
    private ObjectMapper objectMapper;

    @Test
    void getFavoriteStoresShouldExposeFavoriteCountForEachStore() {
        FavoriteService favoriteService = new FavoriteService(
            favoriteRepository,
            publicFavoriteRepository,
            authService,
            storeService,
            publicInstitutionService,
            objectMapper
        );

        User user = new User("user@test.com", "password", "tester", UserRole.USER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", 1L);

        Store store = new Store(
            ExternalSource.KAKAO,
            "store-1",
            "찜 많은 매장",
            "02-123-4567",
            "서울시 테스트구 테스트로 1",
            "서울시 테스트구 테스트로 1",
            new BigDecimal("37.1234567"),
            new BigDecimal("127.1234567")
        );
        ReflectionTestUtils.setField(store, "id", 101L);
        store.markVerified("서울시 테스트구 테스트로 1", "서울시 테스트구 테스트로 1", "카페", "{}", null);

        Favorite favorite = new Favorite(user, store);
        ReflectionTestUtils.setField(favorite, "id", 11L);
        ReflectionTestUtils.setField(favorite, "createdAt", LocalDateTime.now());

        when(authService.getAuthenticatedUser()).thenReturn(user);
        when(favoriteRepository.findAllByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(favorite));
        when(favoriteRepository.countByStoreId(101L)).thenReturn(27L);

        FavoriteStoreListResponse response = favoriteService.getFavoriteStores();

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).storeId()).isEqualTo(101L);
        assertThat(response.content().get(0).favoriteCount()).isEqualTo(27L);
    }

    @Test
    void addFavoriteShouldAllowVerifiedStoreOnly() {
        FavoriteService favoriteService = new FavoriteService(
            favoriteRepository,
            publicFavoriteRepository,
            authService,
            storeService,
            publicInstitutionService,
            objectMapper
        );

        User user = new User("user@test.com", "password", "tester", UserRole.USER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", 1L);

        Store store = new Store(
            ExternalSource.KAKAO,
            "store-1",
            "찜 대상 매장",
            "02-123-4567",
            "서울시 테스트구 테스트로 1",
            "서울시 테스트구 테스트로 1",
            new BigDecimal("37.1234567"),
            new BigDecimal("127.1234567")
        );
        ReflectionTestUtils.setField(store, "id", 101L);
        store.markVerified("서울시 테스트구 테스트로 1", "서울시 테스트구 테스트로 1", "카페", "{}", null);

        Favorite favorite = new Favorite(user, store);
        ReflectionTestUtils.setField(favorite, "id", 11L);
        ReflectionTestUtils.setField(favorite, "createdAt", LocalDateTime.now());

        when(authService.getAuthenticatedUser()).thenReturn(user);
        when(storeService.getRegisteredStore(101L)).thenReturn(store);
        when(favoriteRepository.existsByUserIdAndStoreId(1L, 101L)).thenReturn(false);
        when(favoriteRepository.save(org.mockito.ArgumentMatchers.any(Favorite.class))).thenReturn(favorite);

        assertThat(favoriteService.addFavorite(101L).storeId()).isEqualTo(101L);
        verify(storeService).getRegisteredStore(101L);
    }

    @Test
    void addFavoriteShouldRejectPreviewStore() {
        FavoriteService favoriteService = new FavoriteService(
            favoriteRepository,
            publicFavoriteRepository,
            authService,
            storeService,
            publicInstitutionService,
            objectMapper
        );

        when(storeService.getRegisteredStore(202L)).thenThrow(new ApiException(
            HttpStatus.NOT_FOUND,
            "STORE_NOT_REGISTERED",
            "등록된 매장이 아닙니다."
        ));

        assertThatThrownBy(() -> favoriteService.addFavorite(202L))
            .isInstanceOf(ApiException.class)
            .satisfies(throwable -> {
                ApiException ex = (ApiException) throwable;
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(ex.getCode()).isEqualTo("STORE_NOT_REGISTERED");
            });
    }
}
