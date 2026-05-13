package com.toggle.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toggle.dto.store.StorePriceItemItemResponse;
import com.toggle.dto.store.StorePriceItemResponse;
import com.toggle.dto.store.StorePriceItemUpsertRequest;
import com.toggle.entity.User;
import com.toggle.entity.UserRole;
import com.toggle.entity.UserStatus;
import com.toggle.service.AuthService;
import com.toggle.service.StorePriceItemService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StorePriceItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StorePriceItemService storePriceItemService;

    @MockBean
    private AuthService authService;

    @Test
    void publicPriceItemListShouldBeAccessibleWithoutAuth() throws Exception {
        when(storePriceItemService.getStorePriceItems(anyLong()))
            .thenReturn(new StorePriceItemResponse(
                1L,
                "미용실",
                "미용실",
                true,
                false,
                List.of(),
                "ACTIVE",
                null,
                false,
                false,
                "PRICE_ITEM_CATEGORY_NOT_SUPPORTED"
            ));

        mockMvc.perform(get("/api/v1/stores/{storeId}/price-items", 1L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void ownerPriceItemRoutesShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/owner/stores/{storeId}/price-items", 1L))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/v1/owner/stores/{storeId}/price-items", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new StorePriceItemUpsertRequest(List.of()))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedOwnerPriceItemRoutesShouldDelegateToService() throws Exception {
        when(storePriceItemService.getMyStorePriceItems(anyLong(), any()))
            .thenReturn(new StorePriceItemResponse(
                1L,
                "미용실",
                "미용실",
                true,
                true,
                List.of(),
                "ACTIVE",
                null,
                true,
                true,
                null
            ));
        when(storePriceItemService.replaceMyStorePriceItems(anyLong(), any(), any()))
            .thenReturn(new StorePriceItemResponse(
                1L,
                "미용실",
                "미용실",
                true,
                true,
                List.of(new StorePriceItemItemResponse(1L, "컷트", 22000, true, null, null, 0, true)),
                "ACTIVE",
                null,
                true,
                true,
                null
            ));
        User owner = new User("owner@test.com", "encoded", "owner", "owner", UserRole.OWNER, UserStatus.ACTIVE);
        org.springframework.test.util.ReflectionTestUtils.setField(owner, "id", 99L);
        when(authService.getAuthenticatedUser()).thenReturn(owner);

        mockMvc.perform(get("/api/v1/owner/stores/{storeId}/price-items", 1L).with(user("owner")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.enabled").value(true));

        mockMvc.perform(put("/api/v1/owner/stores/{storeId}/price-items", 1L)
                .with(user("owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new StorePriceItemUpsertRequest(List.of()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isArray());
    }
}
