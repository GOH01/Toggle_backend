package com.toggle.review;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toggle.dto.review.StoreReviewCreateRequest;
import com.toggle.dto.review.StoreReviewItemResponse;
import com.toggle.dto.review.StoreReviewMineResponse;
import com.toggle.dto.review.StoreReviewPageResponse;
import com.toggle.dto.review.StoreReviewSummaryResponse;
import com.toggle.dto.review.StoreReviewUpdateRequest;
import com.toggle.service.StoreReviewService;
import java.time.LocalDateTime;
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
class StoreReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StoreReviewService storeReviewService;

    @Test
    void publicReviewListShouldBeAccessibleWithoutAuth() throws Exception {
        when(storeReviewService.getStoreReviews(anyLong(), anyInt(), anyInt(), any()))
            .thenReturn(new StoreReviewPageResponse(
                List.of(),
                0,
                20,
                0,
                0,
                new StoreReviewSummaryResponse(null, 0)
            ));

        mockMvc.perform(get("/api/v1/stores/{storeId}/reviews", 1L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.summary.reviewCount").value(0));
    }

    @Test
    void publicReviewListShouldRejectInvalidSortValues() throws Exception {
        mockMvc.perform(get("/api/v1/stores/{storeId}/reviews", 1L)
                .param("sort", "bad_sort"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void mineReviewListShouldRequireAuthentication() throws Exception {
        when(storeReviewService.getMyStoreReviews(anyLong(), anyInt(), anyInt(), any()))
            .thenReturn(new StoreReviewMineResponse(
                List.of(),
                0,
                20,
                0,
                0,
                new StoreReviewSummaryResponse(null, 0)
            ));

        mockMvc.perform(get("/api/v1/stores/{storeId}/reviews/mine", 1L))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/stores/{storeId}/reviews/mine", 1L).with(user("tester")))
            .andExpect(status().isOk());
    }

    @Test
    void writeRoutesShouldRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/stores/{storeId}/reviews", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new StoreReviewCreateRequest(5, "좋아요.", List.of()))))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/v1/reviews/{reviewId}", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new StoreReviewUpdateRequest(4, "수정", List.of()))))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/v1/reviews/{reviewId}", 1L))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedWriteRoutesShouldDelegateToService() throws Exception {
        when(storeReviewService.createReview(anyLong(), any(StoreReviewCreateRequest.class)))
            .thenReturn(new StoreReviewItemResponse(
                1L,
                1L,
                2L,
                "tester",
                5,
                "좋아요.",
                List.of("https://cdn.example.com/review/1.png"),
                LocalDateTime.now(),
                LocalDateTime.now()
            ));

        mockMvc.perform(post("/api/v1/stores/{storeId}/reviews", 1L)
                .with(user("tester"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new StoreReviewCreateRequest(5, "좋아요.", List.of("https://cdn.example.com/review/1.png")))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reviewId").value(1L))
            .andExpect(jsonPath("$.data.imageUrls[0]").value("https://cdn.example.com/review/1.png"));
    }
}
