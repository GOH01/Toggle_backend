package com.toggle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.toggle.dto.store.StoreMenuUpsertItemRequest;
import com.toggle.dto.store.StoreMenuUpsertRequest;
import com.toggle.entity.ExternalSource;
import com.toggle.entity.Store;
import com.toggle.entity.StoreMenu;
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
import org.mockito.ArgumentCaptor;
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

    @Mock
    private StoreEligibilityService storeEligibilityService;

    @Mock
    private S3FileService s3FileService;

    @InjectMocks
    private StoreMenuService storeMenuService;

    @Test
    void getStoreMenusShouldHideUnsupportedCategories() {
        Store store = store(1L, "미지원 매장", "미용실");
        when(storeService.getRegisteredStore(1L)).thenReturn(store);
        when(storeEligibilityService.describe(store, false)).thenReturn(snapshot("ACTIVE", null, false, false, "MENU_CATEGORY_NOT_SUPPORTED"));

        assertThat(storeMenuService.getStoreMenus(1L).enabled()).isFalse();
        assertThat(storeMenuService.getStoreMenus(1L).items()).isEmpty();
    }

    @Test
    void replaceMyStoreMenusShouldPersistMenuImageObjectKeyOnCreate() {
        Store store = store(2L, "카페", "카페");
        User owner = owner(99L, "owner@test.com");
        link(owner, store);

        when(storeEligibilityService.describe(store, true)).thenReturn(snapshot("ACTIVE", null, true, true, null));
        when(storeMenuRepository.findAllByStoreIdOrderByDisplayOrderAscIdAsc(2L)).thenReturn(List.of());
        when(storeMenuRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        StoreMenuUpsertItemRequest menuRequest = new StoreMenuUpsertItemRequest(
            "아메리카노",
            4500,
            true,
            "진한 커피",
            "https://sku-toggle.s3.ap-northeast-2.amazonaws.com/menu/coffee%20beans.png",
            null,
            true
        );

        storeMenuService.replaceMyStoreMenus(2L, owner, new StoreMenuUpsertRequest(List.of(menuRequest)));

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<List> savedMenusCaptor = ArgumentCaptor.forClass(List.class);
        verify(storeMenuRepository).saveAll(savedMenusCaptor.capture());

        StoreMenu savedMenu = (StoreMenu) savedMenusCaptor.getValue().get(0);
        assertThat(savedMenu.getImageUrl())
            .isEqualTo("https://sku-toggle.s3.ap-northeast-2.amazonaws.com/menu/coffee%20beans.png");
        assertThat(savedMenu.getImageObjectKey()).isEqualTo("menu/coffee beans.png");
    }

    @Test
    void replaceMyStoreMenusShouldScheduleCleanupWhenImageIsReplaced() {
        Store store = store(3L, "카페", "카페");
        User owner = owner(100L, "owner2@test.com");
        link(owner, store);

        StoreMenu existingMenu = menu(store, "아메리카노", 4500, "https://sku-toggle.s3.ap-northeast-2.amazonaws.com/menu/old%20image.png", null);
        when(storeEligibilityService.describe(store, true)).thenReturn(snapshot("ACTIVE", null, true, true, null));
        when(storeMenuRepository.findAllByStoreIdOrderByDisplayOrderAscIdAsc(3L)).thenReturn(List.of(existingMenu));
        when(storeMenuRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        StoreMenuUpsertItemRequest menuRequest = new StoreMenuUpsertItemRequest(
            "아메리카노",
            4500,
            true,
            null,
            "https://sku-toggle.s3.ap-northeast-2.amazonaws.com/menu/new%20image.png",
            null,
            true
        );

        storeMenuService.replaceMyStoreMenus(3L, owner, new StoreMenuUpsertRequest(List.of(menuRequest)));

        verify(s3FileService).deleteFilesAfterCommit(List.of("menu/old image.png"));
    }

    @Test
    void replaceMyStoreMenusShouldScheduleCleanupWhenImageUrlIsRemoved() {
        Store store = store(4L, "카페", "카페");
        User owner = owner(101L, "owner3@test.com");
        link(owner, store);

        StoreMenu existingMenu = menu(store, "아메리카노", 4500, "https://sku-toggle.s3.ap-northeast-2.amazonaws.com/menu/old%20image.png", "menu/old image.png");
        when(storeEligibilityService.describe(store, true)).thenReturn(snapshot("ACTIVE", null, true, true, null));
        when(storeMenuRepository.findAllByStoreIdOrderByDisplayOrderAscIdAsc(4L)).thenReturn(List.of(existingMenu));
        when(storeMenuRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        StoreMenuUpsertItemRequest menuRequest = new StoreMenuUpsertItemRequest(
            "아메리카노",
            4500,
            true,
            null,
            null,
            null,
            true
        );

        storeMenuService.replaceMyStoreMenus(4L, owner, new StoreMenuUpsertRequest(List.of(menuRequest)));

        verify(s3FileService).deleteFilesAfterCommit(List.of("menu/old image.png"));
    }

    @Test
    void replaceMyStoreMenusShouldScheduleCleanupWhenMenusAreDeleted() {
        Store store = store(5L, "카페", "카페");
        User owner = owner(102L, "owner4@test.com");
        link(owner, store);

        StoreMenu existingMenu = menu(store, "아메리카노", 4500, "https://sku-toggle.s3.ap-northeast-2.amazonaws.com/menu/old%20image.png", null);
        when(storeEligibilityService.describe(store, true)).thenReturn(snapshot("ACTIVE", null, true, true, null));
        when(storeMenuRepository.findAllByStoreIdOrderByDisplayOrderAscIdAsc(5L)).thenReturn(List.of(existingMenu));
        when(storeMenuRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        storeMenuService.replaceMyStoreMenus(5L, owner, new StoreMenuUpsertRequest(List.of()));

        verify(s3FileService).deleteFilesAfterCommit(List.of("menu/old image.png"));
    }

    @Test
    void replaceMyStoreMenusShouldRejectWhenClosureRequestIsPending() {
        Store store = store(6L, "운영 종료 요청 매장", "카페");
        User owner = owner(103L, "owner5@test.com");
        link(owner, store);

        when(storeEligibilityService.describe(store, true)).thenReturn(snapshot("CLOSURE_REQUESTED", "PENDING", true, false, "CLOSURE_REQUEST_PENDING"));

        assertThatThrownBy(() -> storeMenuService.replaceMyStoreMenus(6L, owner, new StoreMenuUpsertRequest(List.of())))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void replaceMyStoreMenusShouldRejectWhenUserIsNotOwner() {
        User user = new User("user@test.com", "encoded", "user", UserRole.USER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", 7L);

        assertThatThrownBy(() -> storeMenuService.replaceMyStoreMenus(1L, user, new StoreMenuUpsertRequest(List.of())))
            .isInstanceOf(ApiException.class);
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

    private StoreMenu menu(Store store, String name, int price, String imageUrl, String imageObjectKey) {
        return new StoreMenu(store, name, price, true, null, imageUrl, imageObjectKey, 0, true);
    }

    private StoreEligibilityService.StoreEligibilitySnapshot snapshot(
        String operationalState,
        String closureRequestStatus,
        boolean menuEligible,
        boolean menuEditable,
        String menuEligibilityReason
    ) {
        return new StoreEligibilityService.StoreEligibilitySnapshot(
            operationalState,
            closureRequestStatus,
            menuEligible,
            menuEditable,
            menuEligibilityReason
        );
    }
}
