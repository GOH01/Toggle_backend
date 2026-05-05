package com.toggle.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.toggle.global.config.S3Properties;
import com.toggle.service.S3FileService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class FileUploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private S3FileService s3FileService;

    @Test
    void ownerCanUploadBusinessFile() throws Exception {
        when(s3FileService.uploadFile(any(), eq("business")))
            .thenReturn(new S3FileService.StoredFile("https://sku-toggle.s3.ap-northeast-2.amazonaws.com/business/test.pdf", "business/test.pdf"));

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "license.pdf",
            "application/pdf",
            "pdf-bytes".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/files/business").file(file).with(user("owner@toggle.com").roles("OWNER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.url").value("https://sku-toggle.s3.ap-northeast-2.amazonaws.com/business/test.pdf"))
            .andExpect(jsonPath("$.data.key").value("business/test.pdf"));
    }

    @Test
    void userCanUploadReviewFile() throws Exception {
        when(s3FileService.uploadFile(any(), eq("review")))
            .thenReturn(new S3FileService.StoredFile("https://sku-toggle.s3.ap-northeast-2.amazonaws.com/review/test.png", "review/test.png"));

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "review.png",
            "image/png",
            "image-bytes".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/files/review").file(file).with(user("user@toggle.com").roles("USER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.key").value("review/test.png"));
    }

    @Test
    void nonOwnerCannotUploadBusinessFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "license.pdf",
            "application/pdf",
            "pdf-bytes".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/files/business").file(file).with(user("user@toggle.com").roles("USER")))
            .andExpect(status().isForbidden());
    }

    @Test
    void nonOwnerCannotUploadMenuFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "menu.png",
            "image/png",
            "image-bytes".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/files/menu").file(file).with(user("user@toggle.com").roles("USER")))
            .andExpect(status().isForbidden());
    }

    @Test
    void viewRouteShouldRedirectToFreshPresignedUrl() throws Exception {
        when(s3FileService.createPresignedGetUrl("review/test.png"))
            .thenReturn("https://presigned.example/review/test.png");

        mockMvc.perform(get("/api/v1/files/view").param("key", "review/test.png"))
            .andExpect(status().isFound())
            .andExpect(redirectedUrl("https://presigned.example/review/test.png"));
    }
}
