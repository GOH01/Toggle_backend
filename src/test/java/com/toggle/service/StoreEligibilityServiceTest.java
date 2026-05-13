package com.toggle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.toggle.entity.ExternalSource;
import com.toggle.entity.Store;
import com.toggle.repository.StoreClosureRequestRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class StoreEligibilityServiceTest {

    @Mock
    private StoreClosureRequestRepository storeClosureRequestRepository;

    @Test
    void describeShouldExposeMenuAndPriceItemCategoriesSeparately() {
        StoreEligibilityService service = new StoreEligibilityService(storeClosureRequestRepository);

        Store cafeStore = store(1L, "카페", "카페");
        when(storeClosureRequestRepository.findTopByStoreIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.empty());

        StoreEligibilityService.StoreEligibilitySnapshot cafeSnapshot = service.describe(cafeStore, true);
        assertThat(cafeSnapshot.menuEligible()).isTrue();
        assertThat(cafeSnapshot.menuEditable()).isTrue();
        assertThat(cafeSnapshot.priceItemEligible()).isFalse();
        assertThat(cafeSnapshot.priceItemEditable()).isFalse();
        assertThat(cafeSnapshot.priceItemEligibilityReason()).isEqualTo("PRICE_ITEM_CATEGORY_NOT_SUPPORTED");

        Store hairStore = store(2L, "미용실", "미용실");
        when(storeClosureRequestRepository.findTopByStoreIdOrderByCreatedAtDesc(2L)).thenReturn(Optional.empty());

        StoreEligibilityService.StoreEligibilitySnapshot hairSnapshot = service.describe(hairStore, true);
        assertThat(hairSnapshot.menuEligible()).isFalse();
        assertThat(hairSnapshot.menuEditable()).isFalse();
        assertThat(hairSnapshot.menuEligibilityReason()).isEqualTo("MENU_CATEGORY_NOT_SUPPORTED");
        assertThat(hairSnapshot.priceItemEligible()).isTrue();
        assertThat(hairSnapshot.priceItemEditable()).isTrue();
        assertThat(hairSnapshot.priceItemEligibilityReason()).isNull();
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
}
