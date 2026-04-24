package com.toggle.controller;

import com.toggle.dto.review.StoreReviewCreateRequest;
import com.toggle.dto.review.StoreReviewItemResponse;
import com.toggle.dto.review.StoreReviewMineResponse;
import com.toggle.dto.review.StoreReviewPageResponse;
import com.toggle.dto.review.StoreReviewUpdateRequest;
import com.toggle.global.response.ApiResponse;
import com.toggle.service.StoreReviewService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class StoreReviewController {

    private static final String SORT_LATEST = "latest";
    private static final String SORT_RATING_DESC = "rating_desc";
    private static final String SORT_RATING_ASC = "rating_asc";

    private final StoreReviewService storeReviewService;

    public StoreReviewController(StoreReviewService storeReviewService) {
        this.storeReviewService = storeReviewService;
    }

    @GetMapping("/stores/{storeId}/reviews")
    public ApiResponse<StoreReviewPageResponse> getStoreReviews(
        @PathVariable Long storeId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "latest") String sort
    ) {
        validateSort(sort);
        return ApiResponse.ok(storeReviewService.getStoreReviews(storeId, page, size, sort));
    }

    @GetMapping("/stores/{storeId}/reviews/mine")
    public ApiResponse<StoreReviewMineResponse> getMyStoreReviews(
        @PathVariable Long storeId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "latest") String sort
    ) {
        validateSort(sort);
        return ApiResponse.ok(storeReviewService.getMyStoreReviews(storeId, page, size, sort));
    }

    @PostMapping("/stores/{storeId}/reviews")
    public ApiResponse<StoreReviewItemResponse> createReview(
        @PathVariable Long storeId,
        @Valid @RequestBody StoreReviewCreateRequest request
    ) {
        return ApiResponse.ok(storeReviewService.createReview(storeId, request));
    }

    @PatchMapping("/reviews/{reviewId}")
    public ApiResponse<StoreReviewItemResponse> updateReview(
        @PathVariable Long reviewId,
        @Valid @RequestBody StoreReviewUpdateRequest request
    ) {
        return ApiResponse.ok(storeReviewService.updateReview(reviewId, request));
    }

    @DeleteMapping("/reviews/{reviewId}")
    public ApiResponse<Void> deleteReview(@PathVariable Long reviewId) {
        storeReviewService.deleteReview(reviewId);
        return ApiResponse.ok(null);
    }

    private void validateSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return;
        }

        String normalized = sort.trim().toLowerCase();
        if (SORT_LATEST.equals(normalized) || SORT_RATING_DESC.equals(normalized) || SORT_RATING_ASC.equals(normalized)) {
            return;
        }

        throw new com.toggle.global.exception.ApiException(
            org.springframework.http.HttpStatus.BAD_REQUEST,
            "INVALID_REVIEW_SORT",
            "지원하지 않는 정렬 기준입니다."
        );
    }
}
