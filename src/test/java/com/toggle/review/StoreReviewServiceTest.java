package com.toggle.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.toggle.dto.review.StoreReviewCreateRequest;
import com.toggle.dto.review.StoreReviewItemResponse;
import com.toggle.dto.review.StoreReviewMineResponse;
import com.toggle.dto.review.StoreReviewPageResponse;
import com.toggle.dto.review.StoreReviewUpdateRequest;
import com.toggle.entity.ExternalSource;
import com.toggle.entity.Store;
import com.toggle.entity.StoreReview;
import com.toggle.entity.User;
import com.toggle.entity.UserRole;
import com.toggle.entity.UserStatus;
import com.toggle.global.exception.ApiException;
import com.toggle.repository.StoreRepository;
import com.toggle.repository.StoreReviewRepository;
import com.toggle.service.AuthService;
import com.toggle.service.StoreService;
import com.toggle.service.StoreReviewService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class StoreReviewServiceTest {

    @Mock
    private StoreReviewRepository storeReviewRepository;

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private StoreService storeService;

    @Mock
    private AuthService authService;

    @InjectMocks
    private StoreReviewService storeReviewService;

    private final List<StoreReview> reviews = new ArrayList<>();
    private final AtomicLong reviewIdSequence = new AtomicLong(1L);
    private final AtomicLong timestampSequence = new AtomicLong(1L);

    private User author;
    private User otherUser;
    private Store store;

    @BeforeEach
    void setUp() {
        reviews.clear();

        author = buildUser(1L, "author@toggle.com", "author");
        otherUser = buildUser(2L, "other@toggle.com", "other");
        store = buildStore(10L, "review-store-10");

        lenient().when(authService.getAuthenticatedUser()).thenReturn(author);
        lenient().when(storeService.getRegisteredStore(store.getId())).thenReturn(store);
        lenient().when(storeService.getRegisteredStoreForUpdate(store.getId())).thenReturn(store);
        lenient().when(storeRepository.save(any(Store.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(storeReviewRepository.save(any(StoreReview.class))).thenAnswer(invocation -> registerReview(invocation.getArgument(0)));
        lenient().when(storeReviewRepository.findById(anyLong())).thenAnswer(invocation -> findReviewById(invocation.getArgument(0)));
        lenient().when(storeReviewRepository.findAllByStoreIdOrderByCreatedAtDesc(anyLong(), any(Pageable.class))).thenAnswer(invocation -> pageForStore(invocation.getArgument(0), null));
        lenient().when(storeReviewRepository.findAllByStoreIdAndUserIdOrderByCreatedAtDesc(anyLong(), anyLong(), any(Pageable.class))).thenAnswer(invocation -> pageForStore(invocation.getArgument(0), invocation.getArgument(1)));
        lenient().when(storeReviewRepository.findAllByStoreIdOrderByRatingDescCreatedAtDescIdDesc(anyLong(), any(Pageable.class))).thenAnswer(invocation -> pageForStoreBySort(invocation.getArgument(0), null, "rating_desc"));
        lenient().when(storeReviewRepository.findAllByStoreIdAndUserIdOrderByRatingDescCreatedAtDescIdDesc(anyLong(), anyLong(), any(Pageable.class))).thenAnswer(invocation -> pageForStoreBySort(invocation.getArgument(0), invocation.getArgument(1), "rating_desc"));
        lenient().when(storeReviewRepository.findAllByStoreIdOrderByRatingAscCreatedAtDescIdDesc(anyLong(), any(Pageable.class))).thenAnswer(invocation -> pageForStoreBySort(invocation.getArgument(0), null, "rating_asc"));
        lenient().when(storeReviewRepository.findAllByStoreIdAndUserIdOrderByRatingAscCreatedAtDescIdDesc(anyLong(), anyLong(), any(Pageable.class))).thenAnswer(invocation -> pageForStoreBySort(invocation.getArgument(0), invocation.getArgument(1), "rating_asc"));
        lenient().when(storeReviewRepository.countByStoreId(anyLong())).thenAnswer(invocation -> countForStore(invocation.getArgument(0)));
        lenient().when(storeReviewRepository.findAverageRatingByStoreId(anyLong())).thenAnswer(invocation -> averageForStore(invocation.getArgument(0)));
        lenient().doAnswer(invocation -> {
            StoreReview review = invocation.getArgument(0);
            Long reviewId = review.getId();
            reviews.removeIf(existing -> reviewId.equals(existing.getId()));
            return null;
        }).when(storeReviewRepository).delete(any(StoreReview.class));
    }

    @Test
    void createReviewShouldAllowMultipleReviewsFromSameUserAndStoreAndRefreshSummary() {
        storeReviewService.createReview(store.getId(), new StoreReviewCreateRequest(5, "첫 번째 리뷰", List.of("https://cdn.example.com/review/1.png")));
        storeReviewService.createReview(store.getId(), new StoreReviewCreateRequest(4, "두 번째 리뷰", List.of("https://cdn.example.com/review/2.png")));

        ArgumentCaptor<StoreReview> reviewCaptor = ArgumentCaptor.forClass(StoreReview.class);
        verify(storeReviewRepository, atLeastOnce()).save(reviewCaptor.capture());
        assertThat(reviewCaptor.getAllValues())
            .hasSize(2)
            .allSatisfy(review -> {
                assertThat(review.getUser().getId()).isEqualTo(author.getId());
                assertThat(review.getStore().getId()).isEqualTo(store.getId());
            });
        assertThat(reviewCaptor.getAllValues())
            .extracting(StoreReview::getContent)
            .containsExactly("첫 번째 리뷰", "두 번째 리뷰");
        assertThat(reviewCaptor.getAllValues())
            .extracting(StoreReview::getImageUrlsJson)
            .containsExactly("[\"https://cdn.example.com/review/1.png\"]", "[\"https://cdn.example.com/review/2.png\"]");

        assertLatestSavedStoreSummary("4.5", 2L);
    }

    @Test
    void createReviewShouldPersistAndReturnUploadedImageUrls() {
        StoreReviewItemResponse response = storeReviewService.createReview(
            store.getId(),
            new StoreReviewCreateRequest(
                5,
                "사진이 포함된 리뷰",
                List.of(
                    "https://toggle-bucket.s3.ap-northeast-2.amazonaws.com/review/1.png",
                    "https://toggle-bucket.s3.ap-northeast-2.amazonaws.com/review/2.png"
                )
            )
        );

        assertThat(response.imageUrls())
            .containsExactly(
                "/api/v1/files/view?key=review%2F1.png",
                "/api/v1/files/view?key=review%2F2.png"
            );
    }

    @Test
    void createReviewShouldRejectPreviewStore() {
        Store previewStore = buildStore(11L, "review-preview-11");
        when(storeService.getRegisteredStoreForUpdate(previewStore.getId())).thenThrow(new ApiException(
            HttpStatus.NOT_FOUND,
            "STORE_NOT_REGISTERED",
            "등록된 매장이 아닙니다."
        ));

        assertThatThrownBy(() -> storeReviewService.createReview(previewStore.getId(), new StoreReviewCreateRequest(5, "미등록 매장 리뷰", List.of())))
            .isInstanceOf(ApiException.class)
            .satisfies(throwable -> {
                ApiException ex = (ApiException) throwable;
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(ex.getCode()).isEqualTo("STORE_NOT_REGISTERED");
            });

        verify(storeReviewRepository, never()).save(any(StoreReview.class));
        verify(storeRepository, never()).save(any(Store.class));
    }

    @Test
    void getStoreReviewsShouldUseLatestSortByDefault() {
        seedReview(author, 4, "중간 리뷰");
        seedReview(author, 5, "최신 리뷰");
        seedReview(author, 2, "오래된 리뷰");

        StoreReviewPageResponse response = storeReviewService.getStoreReviews(store.getId(), 0, 20, "latest");

        assertThat(response.content())
            .extracting(StoreReviewItemResponse::content)
            .containsExactly("오래된 리뷰", "최신 리뷰", "중간 리뷰");
    }

    @Test
    void getStoreReviewsShouldSortByRatingDescWhenRequested() {
        seedReview(author, 4, "별점 4점");
        seedReview(author, 5, "별점 5점");
        seedReview(author, 4, "별점 4점 최신");

        StoreReviewPageResponse response = storeReviewService.getStoreReviews(store.getId(), 0, 20, "rating_desc");

        assertThat(response.content())
            .extracting(StoreReviewItemResponse::content)
            .containsExactly("별점 5점", "별점 4점 최신", "별점 4점");
    }

    @Test
    void getStoreReviewsShouldSortByRatingAscWhenRequested() {
        seedReview(author, 4, "별점 4점");
        seedReview(author, 5, "별점 5점");
        seedReview(author, 2, "별점 2점");

        StoreReviewPageResponse response = storeReviewService.getStoreReviews(store.getId(), 0, 20, "rating_asc");

        assertThat(response.content())
            .extracting(StoreReviewItemResponse::content)
            .containsExactly("별점 2점", "별점 4점", "별점 5점");
    }

    @Test
    void getStoreReviewsShouldRejectPreviewStore() {
        Store previewStore = buildStore(11L, "review-preview-11");
        when(storeService.getRegisteredStore(previewStore.getId())).thenThrow(new ApiException(
            HttpStatus.NOT_FOUND,
            "STORE_NOT_REGISTERED",
            "등록된 매장이 아닙니다."
        ));

        assertThatThrownBy(() -> storeReviewService.getStoreReviews(previewStore.getId(), 0, 20, "latest"))
            .isInstanceOf(ApiException.class)
            .satisfies(throwable -> {
                ApiException ex = (ApiException) throwable;
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(ex.getCode()).isEqualTo("STORE_NOT_REGISTERED");
            });
    }

    @Test
    void getMyStoreReviewsShouldSortByRatingDescWhenRequested() {
        seedReview(author, 2, "내 리뷰 2점");
        seedReview(otherUser, 5, "남의 리뷰");
        seedReview(author, 4, "내 리뷰 4점");

        StoreReviewMineResponse response = storeReviewService.getMyStoreReviews(store.getId(), 0, 20, "rating_desc");

        assertThat(response.content())
            .extracting(StoreReviewItemResponse::content)
            .containsExactly("내 리뷰 4점", "내 리뷰 2점");
    }

    @Test
    void updateReviewShouldRefreshSummaryAndRejectNonOwnerWith403AndMissingWith404() {
        StoreReview firstReview = seedReview(author, 5, "첫 번째 리뷰");
        StoreReview secondReview = seedReview(author, 1, "수정 대상 리뷰");

        storeReviewService.updateReview(secondReview.getId(), new StoreReviewUpdateRequest(
            3,
            "수정된 리뷰",
            List.of("https://cdn.example.com/review/updated.png")
        ));

        assertLatestSavedStoreSummary("4.0", 2L);

        lenient().when(authService.getAuthenticatedUser()).thenReturn(author);
        StoreReview otherOwnersReview = seedReview(otherUser, 4, "남의 리뷰");
        ReflectionTestUtils.setField(otherOwnersReview, "id", firstReview.getId());
        lenient().when(storeReviewRepository.findById(firstReview.getId())).thenReturn(Optional.of(otherOwnersReview));

        assertThatThrownBy(() -> storeReviewService.updateReview(firstReview.getId(), new StoreReviewUpdateRequest(2, "시도", List.of())))
            .isInstanceOf(ApiException.class)
            .satisfies(throwable -> {
                ApiException ex = (ApiException) throwable;
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(ex.getCode()).isEqualTo("REVIEW_ACCESS_DENIED");
            });

        lenient().when(storeReviewRepository.findById(9999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> storeReviewService.updateReview(9999L, new StoreReviewUpdateRequest(2, "없는 리뷰", List.of())))
            .isInstanceOf(ApiException.class)
            .satisfies(throwable -> {
                ApiException ex = (ApiException) throwable;
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(ex.getCode()).isEqualTo("REVIEW_NOT_FOUND");
            });
    }

    @Test
    void deleteReviewShouldRefreshSummaryAndRejectNonOwnerWith403AndMissingWith404() {
        StoreReview firstReview = seedReview(author, 5, "첫 번째 리뷰");
        StoreReview secondReview = seedReview(author, 3, "삭제 대상 리뷰");

        storeReviewService.deleteReview(secondReview.getId());

        assertLatestSavedStoreSummary("5.0", 1L);

        lenient().when(authService.getAuthenticatedUser()).thenReturn(author);
        StoreReview otherOwnersReview = seedReview(otherUser, 4, "남의 리뷰");
        ReflectionTestUtils.setField(otherOwnersReview, "id", firstReview.getId());
        lenient().when(storeReviewRepository.findById(firstReview.getId())).thenReturn(Optional.of(otherOwnersReview));

        assertThatThrownBy(() -> storeReviewService.deleteReview(firstReview.getId()))
            .isInstanceOf(ApiException.class)
            .satisfies(throwable -> {
                ApiException ex = (ApiException) throwable;
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(ex.getCode()).isEqualTo("REVIEW_ACCESS_DENIED");
            });

        lenient().when(storeReviewRepository.findById(9999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> storeReviewService.deleteReview(9999L))
            .isInstanceOf(ApiException.class)
            .satisfies(throwable -> {
                ApiException ex = (ApiException) throwable;
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(ex.getCode()).isEqualTo("REVIEW_NOT_FOUND");
            });
    }

    @Test
    void createReviewShouldRejectInvalidRatingAndBlankContent() {
        assertThatThrownBy(() -> storeReviewService.createReview(store.getId(), new StoreReviewCreateRequest(0, "유효하지 않은 평점", List.of())))
            .isInstanceOf(ApiException.class)
            .satisfies(throwable -> {
                ApiException ex = (ApiException) throwable;
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(ex.getCode()).isEqualTo("INVALID_REVIEW_RATING");
            });

        assertThatThrownBy(() -> storeReviewService.createReview(store.getId(), new StoreReviewCreateRequest(4, "   ", List.of())))
            .isInstanceOf(ApiException.class)
            .satisfies(throwable -> {
                ApiException ex = (ApiException) throwable;
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(ex.getCode()).isEqualTo("INVALID_REVIEW_CONTENT");
            });

        verify(storeReviewRepository, never()).save(any(StoreReview.class));
        verify(storeRepository, never()).save(any(Store.class));
    }

    private User buildUser(Long id, String email, String nickname) {
        User user = new User(email, "password123!", nickname, UserRole.USER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Store buildStore(Long id, String externalPlaceId) {
        Store builtStore = new Store(
            ExternalSource.KAKAO,
            externalPlaceId,
            "테스트 매장",
            "02-123-4567",
            "서울시 테스트구 테스트로 1",
            "서울시 테스트구 테스트로 1",
            new BigDecimal("37.1234567"),
            new BigDecimal("127.1234567")
        );
        ReflectionTestUtils.setField(builtStore, "id", id);
        return builtStore;
    }

    private StoreReview seedReview(User user, int rating, String content) {
        StoreReview review = new StoreReview(user, store, rating, content);
        ReflectionTestUtils.setField(review, "id", reviewIdSequence.getAndIncrement());
        ReflectionTestUtils.setField(review, "createdAt", LocalDateTime.of(2026, 4, 23, 12, 0).plusMinutes(timestampSequence.getAndIncrement()));
        ReflectionTestUtils.setField(review, "updatedAt", LocalDateTime.of(2026, 4, 23, 12, 0).plusMinutes(timestampSequence.getAndIncrement()));
        reviews.add(review);
        return review;
    }

    private StoreReview registerReview(StoreReview review) {
        if (ReflectionTestUtils.getField(review, "id") == null) {
            ReflectionTestUtils.setField(review, "id", reviewIdSequence.getAndIncrement());
        }
        if (ReflectionTestUtils.getField(review, "createdAt") == null) {
            LocalDateTime timestamp = LocalDateTime.of(2026, 4, 23, 12, 0).plusMinutes(timestampSequence.getAndIncrement());
            ReflectionTestUtils.setField(review, "createdAt", timestamp);
            ReflectionTestUtils.setField(review, "updatedAt", timestamp);
        }

        Long reviewId = (Long) ReflectionTestUtils.getField(review, "id");
        reviews.removeIf(existing -> reviewId.equals(existing.getId()));
        reviews.add(review);
        return review;
    }

    private Optional<StoreReview> findReviewById(Object reviewId) {
        Long id = (Long) reviewId;
        return reviews.stream()
            .filter(review -> id.equals(review.getId()))
            .findFirst();
    }

    private void assertLatestSavedStoreSummary(String averageRating, long reviewCount) {
        ArgumentCaptor<Store> storeCaptor = ArgumentCaptor.forClass(Store.class);
        verify(storeRepository, atLeastOnce()).save(storeCaptor.capture());

        Store latestSavedStore = storeCaptor.getAllValues().get(storeCaptor.getAllValues().size() - 1);
        assertThat(latestSavedStore.getReviewAverageRating()).isEqualByComparingTo(new BigDecimal(averageRating));
        assertThat(latestSavedStore.getReviewCount()).isEqualTo(reviewCount);
    }

    private PageImpl<StoreReview> pageForStore(Object storeId, Object userId) {
        return pageForStoreBySort(storeId, userId, "latest");
    }

    private PageImpl<StoreReview> pageForStoreBySort(Object storeId, Object userId, String sort) {
        Long requestedStoreId = (Long) storeId;
        Long requestedUserId = userId == null ? null : (Long) userId;

        java.util.stream.Stream<StoreReview> stream = reviews.stream()
            .filter(review -> review.getStore().getId().equals(requestedStoreId))
            .filter(review -> requestedUserId == null || review.getUser().getId().equals(requestedUserId));

        Comparator<StoreReview> latestComparator = Comparator
            .comparing(StoreReview::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(StoreReview::getId, Comparator.reverseOrder());

        Comparator<StoreReview> ratingComparator = Comparator
            .comparing(StoreReview::getRating, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(StoreReview::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(StoreReview::getId, Comparator.reverseOrder());

        Comparator<StoreReview> ratingAscComparator = Comparator
            .comparing(StoreReview::getRating, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(StoreReview::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(StoreReview::getId, Comparator.reverseOrder());

        List<StoreReview> filtered = switch (sort) {
            case "rating_desc" -> stream.sorted(ratingComparator).toList();
            case "rating_asc" -> stream.sorted(ratingAscComparator).toList();
            default -> stream.sorted(latestComparator).toList();
        };

        return new PageImpl<>(filtered, PageRequest.of(0, Math.max(filtered.size(), 1)), filtered.size());
    }

    private long countForStore(Object storeId) {
        Long requestedStoreId = (Long) storeId;
        return reviews.stream().filter(review -> review.getStore().getId().equals(requestedStoreId)).count();
    }

    private Double averageForStore(Object storeId) {
        Long requestedStoreId = (Long) storeId;
        double average = reviews.stream()
            .filter(review -> review.getStore().getId().equals(requestedStoreId))
            .mapToInt(StoreReview::getRating)
            .average()
            .orElse(Double.NaN);
        return Double.isNaN(average) ? null : average;
    }
}
