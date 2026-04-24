package com.toggle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.toggle.dto.store.StoreMenuUpsertRequest;
import com.toggle.entity.ExternalSource;
import com.toggle.entity.Store;
import com.toggle.entity.User;
import com.toggle.entity.UserRole;
import com.toggle.entity.UserStatus;
import com.toggle.global.exception.ApiException;
import com.toggle.repository.OwnerStoreLinkRepository;
import com.toggle.repository.StoreMenuRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class StoreMenuServiceTest {

    @Mock
    private StoreService storeService;

    @Mock
    private OwnerStoreLinkRepository ownerStoreLinkRepository;

    @Mock
    private StoreMenuRepository storeMenuRepository;

    @InjectMocks
    private StoreMenuService storeMenuService;

    @Test
    void getStoreMenusShouldHideUnsupportedCategories() {
        Store store = new Store(
            ExternalSource.KAKAO,
            "store-1",
            "미지원 매장",
            "02-123-4567",
            "서울시 테스트구 테스트로 1",
            "서울시 테스트구 테스트로 1",
            new BigDecimal("37.1234567"),
            new BigDecimal("127.1234567")
        );
        ReflectionTestUtils.setField(store, "id", 1L);
        ReflectionTestUtils.setField(store, "categoryName", "미용실");

        when(storeService.getRegisteredStore(1L)).thenReturn(store);

        assertThat(storeMenuService.getStoreMenus(1L).enabled()).isFalse();
        assertThat(storeMenuService.getStoreMenus(1L).items()).isEmpty();
    }

    @Test
    void replaceMyStoreMenusShouldPersistMenusForFoodStores() {
        Store store = new Store(
            ExternalSource.KAKAO,
            "store-2",
            "카페",
            "02-123-4567",
            "서울시 테스트구 테스트로 2",
            "서울시 테스트구 테스트로 2",
            new BigDecimal("37.1234567"),
            new BigDecimal("127.1234567")
        );
        ReflectionTestUtils.setField(store, "id", 2L);
        ReflectionTestUtils.setField(store, "categoryName", "카페");

        User owner = new User("owner@test.com", "encoded", "owner", "owner", UserRole.OWNER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(owner, "id", 99L);

        when(ownerStoreLinkRepository.findByOwnerUserIdAndStoreIdAndStoreDeletedAtIsNull(99L, 2L))
            .thenReturn(Optional.of(new com.toggle.entity.OwnerStoreLink(owner, store, null, null, 0, null)));
        when(storeMenuRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        StoreMenuUpsertRequest request = new StoreMenuUpsertRequest(List.of());

        assertThat(storeMenuService.replaceMyStoreMenus(2L, owner, request).enabled()).isTrue();
        verify(storeMenuRepository).deleteAllByStoreId(2L);
        verify(storeMenuRepository).saveAll(anyList());
    }

    @Test
    void replaceMyStoreMenusShouldRejectWhenUserIsNotOwner() {
        User user = new User("user@test.com", "encoded", "user", UserRole.USER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", 7L);

        assertThatThrownBy(() -> storeMenuService.replaceMyStoreMenus(1L, user, new StoreMenuUpsertRequest(List.of())))
            .isInstanceOf(ApiException.class);
    }
}
