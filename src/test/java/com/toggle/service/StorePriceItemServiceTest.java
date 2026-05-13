package com.toggle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.toggle.dto.store.StorePriceItemUpsertItemRequest;
import com.toggle.dto.store.StorePriceItemUpsertRequest;
import com.toggle.entity.ExternalSource;
import com.toggle.entity.Store;
import com.toggle.entity.StorePriceItem;
import com.toggle.entity.User;
import com.toggle.entity.UserRole;
import com.toggle.entity.UserStatus;
import com.toggle.global.exception.ApiException;
import com.toggle.repository.OwnerStoreLinkRepository;
import com.toggle.repository.StorePriceItemRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class StorePriceItemServiceTest {

    @Mock
    private StoreService storeService;

    @Mock
    private OwnerStoreLinkRepository ownerStoreLinkRepository;

    @Mock
    private StorePriceItemRepository storePriceItemRepository;

    @Mock
    private StoreEligibilityService storeEligibilityService;

    @Mock
    private S3FileService s3FileService;

    @InjectMocks
    private StorePriceItemService storePriceItemService;

    @Test
    void getStorePriceItemsShouldHideMenuCategories() {
        Store store = store(1L, "미지원 매장", "카페");
        when(storeService.getRegisteredStore(1L)).thenReturn(store);
        when(storeEligibilityService.describe(store, false)).thenReturn(snapshot("ACTIVE", null, false, false, "MENU_CATEGORY_NOT_SUPPORTED", false, false, "PRICE_ITEM_CATEGORY_NOT_SUPPORTED"));

        assertThat(storePriceItemService.getStorePriceItems(1L).enabled()).isFalse();
        assertThat(storePriceItemService.getStorePriceItems(1L).items()).isEmpty();
    }

    @Test
    void getStorePriceItemsShouldReturnEmptyItemsForSupportedStores() {
        Store store = store(2L, "미용실", "미용실");
        when(storeService.getRegisteredStore(2L)).thenReturn(store);
        when(storeEligibilityService.describe(store, false)).thenReturn(snapshot("ACTIVE", null, false, false, "MENU_CATEGORY_NOT_SUPPORTED", true, false, null));
        when(storePriceItemRepository.findAllByStoreIdOrderByDisplayOrderAscIdAsc(2L)).thenReturn(List.of());

        assertThat(storePriceItemService.getStorePriceItems(2L).enabled()).isTrue();
        assertThat(storePriceItemService.getStorePriceItems(2L).items()).isEmpty();
    }

    @Test
    void replaceMyStorePriceItemsShouldRejectWhenUserIsNotOwner() {
        User user = new User("user@test.com", "encoded", "user", UserRole.USER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", 7L);

        assertThatThrownBy(() -> storePriceItemService.replaceMyStorePriceItems(1L, user, new StorePriceItemUpsertRequest(List.of())))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void replaceMyStorePriceItemsShouldPersistImageObjectKeyOnCreate() {
        Store store = store(3L, "미용실", "미용실");
        User owner = owner(99L, "owner@test.com");
        link(owner, store);

        when(storeEligibilityService.describe(store, true)).thenReturn(snapshot("ACTIVE", null, false, false, "MENU_CATEGORY_NOT_SUPPORTED", true, true, null));
        when(storePriceItemRepository.findAllByStoreIdOrderByDisplayOrderAscIdAsc(3L)).thenReturn(List.of());
        when(storePriceItemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        StorePriceItemUpsertItemRequest itemRequest = new StorePriceItemUpsertItemRequest(
            "컷트",
            22000,
            true,
            "기본 컷트",
            "https://sku-toggle.s3.ap-northeast-2.amazonaws.com/price-item/cut%20service.png",
            null,
            true
        );

        storePriceItemService.replaceMyStorePriceItems(3L, owner, new StorePriceItemUpsertRequest(List.of(itemRequest)));

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<List> savedItemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(storePriceItemRepository).saveAll(savedItemsCaptor.capture());

        StorePriceItem savedItem = (StorePriceItem) savedItemsCaptor.getValue().get(0);
        assertThat(savedItem.getImageUrl())
            .isEqualTo("https://sku-toggle.s3.ap-northeast-2.amazonaws.com/price-item/cut%20service.png");
        assertThat(savedItem.getImageObjectKey()).isEqualTo("price-item/cut service.png");
    }

    private Store store(Long id, String name, String categoryName) {
        Store store = new Store(
            ExternalSource.KAKAO,
            "store-" + id,
            name,
            "02-123-4567",
            "서울시 테스트구 테스트로 " + id,
            "서울시 테스트구 테스트로 " + id,
            new BigDecimal("37.1234567"),
            new BigDecimal("127.1234567")
        );
        ReflectionTestUtils.setField(store, "id", id);
        ReflectionTestUtils.setField(store, "categoryName", categoryName);
        ReflectionTestUtils.setField(store, "isVerified", true);
        return store;
    }

    private User owner(Long id, String email) {
        User owner = new User(email, "encoded", "owner", "owner", UserRole.OWNER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(owner, "id", id);
        return owner;
    }

    private void link(User owner, Store store) {
        when(ownerStoreLinkRepository.findByOwnerUserIdAndStoreIdAndStoreDeletedAtIsNull(owner.getId(), store.getId()))
            .thenReturn(Optional.of(new com.toggle.entity.OwnerStoreLink(owner, store, null, null, 0, null)));
    }

    private StoreEligibilityService.StoreEligibilitySnapshot snapshot(
        String operationalState,
        String closureRequestStatus,
        boolean menuEligible,
        boolean menuEditable,
        String menuEligibilityReason,
        boolean priceItemEligible,
        boolean priceItemEditable,
        String priceItemEligibilityReason
    ) {
        return new StoreEligibilityService.StoreEligibilitySnapshot(
            operationalState,
            closureRequestStatus,
            menuEligible,
            menuEditable,
            menuEligibilityReason,
            priceItemEligible,
            priceItemEditable,
            priceItemEligibilityReason
        );
    }
}
