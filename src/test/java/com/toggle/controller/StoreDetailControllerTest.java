package com.toggle.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.toggle.dto.store.StoreDetailResponse;
import com.toggle.dto.store.StoreMenuItemResponse;
import com.toggle.dto.store.StorePriceItemItemResponse;
import com.toggle.entity.User;
import com.toggle.entity.UserRole;
import com.toggle.entity.UserStatus;
import com.toggle.service.AuthService;
import com.toggle.service.StoreDetailService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StoreDetailControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StoreDetailService storeDetailService;

    @MockBean
    private AuthService authService;

    @Test
    void publicStoreDetailShouldExposeMenuAndPriceItemSections() throws Exception {
        when(storeDetailService.getStoreDetail(anyLong())).thenReturn(new StoreDetailResponse(
            1L,
            "미용실",
            "미용실",
            "NON_FOOD",
            List.of(new StoreMenuItemResponse(10L, "아메리카노", 4500, true, null, null, 0, true)),
            List.of(new StorePriceItemItemResponse(20L, "컷트", 22000, true, null, null, 0, true)),
            "ACTIVE",
            null,
            false,
            false,
            true,
            true,
            "MENU_CATEGORY_NOT_SUPPORTED",
            null
        ));

        mockMvc.perform(get("/api/v1/stores/{storeId}", 1L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.storeId").value(1L))
            .andExpect(jsonPath("$.data.menus[0].menuId").value(10L))
            .andExpect(jsonPath("$.data.priceItems[0].priceItemId").value(20L))
            .andExpect(jsonPath("$.data.priceItemEligible").value(true));
    }

    @Test
    void ownerStoreDetailShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/owner/stores/{storeId}", 1L))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedOwnerStoreDetailShouldDelegateToService() throws Exception {
        User owner = new User("owner@test.com", "encoded", "owner", "owner", UserRole.OWNER, UserStatus.ACTIVE);
        org.springframework.test.util.ReflectionTestUtils.setField(owner, "id", 99L);
        when(authService.getAuthenticatedUser()).thenReturn(owner);
        when(storeDetailService.getMyStoreDetail(owner, 1L)).thenReturn(new StoreDetailResponse(
            1L,
            "미용실",
            "미용실",
            "NON_FOOD",
            List.of(),
            List.of(),
            "ACTIVE",
            null,
            false,
            false,
            true,
            true,
            "MENU_CATEGORY_NOT_SUPPORTED",
            null
        ));

        mockMvc.perform(get("/api/v1/owner/stores/{storeId}", 1L).with(user("owner")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.storeId").value(1L))
            .andExpect(jsonPath("$.data.priceItems").isArray());
    }
}
