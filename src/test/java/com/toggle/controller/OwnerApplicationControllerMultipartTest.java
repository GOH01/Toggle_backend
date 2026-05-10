package com.toggle.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toggle.dto.owner.OwnerApplicationRequest;
import com.toggle.dto.owner.OwnerApplicationResponse;
import com.toggle.entity.User;
import com.toggle.entity.UserRole;
import com.toggle.entity.UserStatus;
import com.toggle.global.security.JwtTokenProvider;
import com.toggle.service.AuthService;
import com.toggle.service.OwnerApplicationService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.multipart.MultipartFile;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class OwnerApplicationControllerMultipartTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OwnerApplicationService ownerApplicationService;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void createApplicationShouldAcceptMultipartJsonRequestPartAsPlainTextAndPreserveParsedValues() throws Exception {
        User owner = new User(
            "owner@toggle.com",
            "encoded-password",
            "owner",
            UserRole.OWNER,
            UserStatus.ACTIVE
        );

        when(authService.getAuthenticatedUser()).thenReturn(owner);
        when(ownerApplicationService.createApplication(eq(owner), any(OwnerApplicationRequest.class), any(MultipartFile.class)))
            .thenReturn(new OwnerApplicationResponse(
                1L,
                10L,
                "테스트상점",
                "UNDER_REVIEW",
                "AUTO_VERIFIED",
                "VERIFIED",
                LocalDateTime.parse("2026-05-11T10:15:30")
            ));

        String requestJson = objectMapper.writeValueAsString(new OwnerApplicationRequest(
            "테스트상점",
            "0123456789",
            "홍길동",
            LocalDate.of(2026, 5, 11),
            "서울특별시 강남구 테헤란로 123",
            "02-1234-5678"
        ));

        MockMultipartFile requestPart = new MockMultipartFile(
            "request",
            "request.json",
            MediaType.TEXT_PLAIN_VALUE,
            requestJson.getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile businessLicensePart = new MockMultipartFile(
            "businessLicenseFile",
            "license.pdf",
            "application/pdf",
            "fake-pdf-content".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(
                multipart("/api/v1/owner/store-applications")
                    .file(requestPart)
                    .file(businessLicensePart)
                    .with(user("owner@toggle.com").roles("OWNER"))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.storeName").value("테스트상점"))
            .andExpect(jsonPath("$.data.requestStatus").value("UNDER_REVIEW"));

        var requestCaptor = org.mockito.ArgumentCaptor.forClass(OwnerApplicationRequest.class);
        verify(ownerApplicationService).createApplication(eq(owner), requestCaptor.capture(), eq(businessLicensePart));

        assertThat(requestCaptor.getValue().businessOpenDate()).isEqualTo(LocalDate.of(2026, 5, 11));
        assertThat(requestCaptor.getValue().businessNumber()).isEqualTo("0123456789");
        assertThat(requestCaptor.getValue().storeName()).isEqualTo("테스트상점");
    }

    @Test
    void createApplicationShouldRejectMissingBusinessLicenseFilePart() throws Exception {
        User owner = new User(
            "owner@toggle.com",
            "encoded-password",
            "owner",
            UserRole.OWNER,
            UserStatus.ACTIVE
        );

        when(authService.getAuthenticatedUser()).thenReturn(owner);

        String requestJson = objectMapper.writeValueAsString(new OwnerApplicationRequest(
            "테스트상점",
            "0123456789",
            "홍길동",
            LocalDate.of(2026, 5, 11),
            "서울특별시 강남구 테헤란로 123",
            "02-1234-5678"
        ));

        MockMultipartFile requestPart = new MockMultipartFile(
            "request",
            "request.json",
            MediaType.TEXT_PLAIN_VALUE,
            requestJson.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(
                multipart("/api/v1/owner/store-applications")
                    .file(requestPart)
                    .with(user("owner@toggle.com").roles("OWNER"))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
