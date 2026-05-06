package com.toggle.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toggle.dto.review.StoreReviewCreateRequest;
import com.toggle.dto.review.StoreReviewItemResponse;
import com.toggle.dto.review.StoreReviewMineResponse;
import com.toggle.dto.review.StoreReviewPageResponse;
import com.toggle.dto.review.StoreReviewSummaryResponse;
import com.toggle.dto.review.StoreReviewUpdateRequest;
import com.toggle.entity.Store;
import com.toggle.entity.StoreReview;
import com.toggle.entity.User;
import com.toggle.global.exception.ApiException;
import com.toggle.global.util.ImageUrlMapper;
import com.toggle.repository.StoreRepository;
import com.toggle.repository.StoreReviewRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class StoreReviewService {

    private static final Logger log = LoggerFactory.getLogger(StoreReviewService.class);
    private static final String INVALID_REVIEW_RATING_CODE = "INVALID_REVIEW_RATING";
    private static final String INVALID_REVIEW_CONTENT_CODE = "INVALID_REVIEW_CONTENT";
    private static final String REVIEW_NOT_FOUND_CODE = "REVIEW_NOT_FOUND";
    private static final String REVIEW_ACCESS_DENIED_CODE = "REVIEW_ACCESS_DENIED";
    private static final String INVALID_REVIEW_SORT_CODE = "INVALID_REVIEW_SORT";
    private static final String SORT_LATEST = "latest";
    private static final String SORT_RATING_DESC = "rating_desc";
    private static final String SORT_RATING_ASC = "rating_asc";
    private static final int MAX_CONTENT_LENGTH = 2000;
    private static final int MAX_IMAGE_COUNT = 5;

    private final StoreReviewRepository storeReviewRepository;
    private final StoreRepository storeRepository;
    private final StoreService storeService;
    private final AuthService authService;
    private final S3FileService s3FileService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StoreReviewService(
        StoreReviewRepository storeReviewRepository,
        StoreRepository storeRepository,
        StoreService storeService,
        AuthService authService,
        S3FileService s3FileService
    ) {
        this.storeReviewRepository = storeReviewRepository;
        this.storeRepository = storeRepository;
        this.storeService = storeService;
        this.authService = authService;
        this.s3FileService = s3FileService;
    }

    @Transactional
    public StoreReviewItemResponse createReview(Long storeId, StoreReviewCreateRequest request) {
        int rating = validateRating(request.rating());
        String content = normalizeContent(request.content());
        ReviewImagePayload imagePayload = resolveReviewImagePayload(request.imageUrls());

        User user = authService.getAuthenticatedUser();
        Store store = lockStore(storeId);

        StoreReview review = storeReviewRepository.save(new StoreReview(
            user,
            store,
            rating,
            content,
            imagePayload.imageUrlsJson(),
            imagePayload.imageKeysJson()
        ));
        refreshStoreReviewSummary(store);
        return toItemResponse(review);
    }

    @Transactional
    public StoreReviewItemResponse updateReview(Long reviewId, StoreReviewUpdateRequest request) {
        int rating = validateRating(request.rating());
        String content = normalizeContent(request.content());

        User user = authService.getAuthenticatedUser();
        StoreReview review = getReviewOrThrow(reviewId);
        ensureOwnedByCurrentUser(review, user);
        Store store = lockStore(review.getStore().getId());

        ReviewImagePayload imagePayload = request.imageUrls() == null
            ? new ReviewImagePayload(
                review.getImageUrlsJson(),
                review.getImageKeysJson(),
                resolveStoredReviewObjectKeys(review.getImageKeysJson(), review.getImageUrlsJson())
            )
            : resolveReviewImagePayload(request.imageUrls());

        if (request.imageUrls() != null) {
            scheduleReviewImageCleanup(
                resolveStoredReviewObjectKeys(review.getImageKeysJson(), review.getImageUrlsJson()),
                imagePayload.imageKeys()
            );
        }

        review.updateReview(rating, content, imagePayload.imageUrlsJson(), imagePayload.imageKeysJson());
        storeReviewRepository.save(review);
        refreshStoreReviewSummary(store);
        return toItemResponse(review);
    }

    @Transactional
    public void deleteReview(Long reviewId) {
        User user = authService.getAuthenticatedUser();
        StoreReview review = getReviewOrThrow(reviewId);
        ensureOwnedByCurrentUser(review, user);

        Store store = lockStore(review.getStore().getId());
        scheduleReviewImageCleanup(resolveStoredReviewObjectKeys(review.getImageKeysJson(), review.getImageUrlsJson()));
        storeReviewRepository.delete(review);
        refreshStoreReviewSummary(store);
    }

    @Transactional(readOnly = true)
    public StoreReviewPageResponse getStoreReviews(Long storeId, int page, int size) {
        return getStoreReviews(storeId, page, size, SORT_LATEST);
    }

    @Transactional(readOnly = true)
    public StoreReviewPageResponse getStoreReviews(Long storeId, int page, int size, String sort) {
        validatePageRequest(page, size);
        String normalizedSort = normalizeSort(sort);
        Store store = storeService.getRegisteredStore(storeId);
        Page<StoreReview> reviews = fetchStoreReviews(storeId, PageRequest.of(page, size), normalizedSort, null);
        return new StoreReviewPageResponse(
            toItemResponses(reviews.getContent()),
            reviews.getNumber(),
            reviews.getSize(),
            reviews.getTotalElements(),
            reviews.getTotalPages(),
            toSummaryResponse(store)
        );
    }

    @Transactional(readOnly = true)
    public StoreReviewMineResponse getMyStoreReviews(Long storeId, int page, int size) {
        return getMyStoreReviews(storeId, page, size, SORT_LATEST);
    }

    @Transactional(readOnly = true)
    public StoreReviewMineResponse getMyStoreReviews(Long storeId, int page, int size, String sort) {
        validatePageRequest(page, size);
        String normalizedSort = normalizeSort(sort);
        User user = authService.getAuthenticatedUser();
        Store store = storeService.getRegisteredStore(storeId);
        Page<StoreReview> reviews = fetchStoreReviews(storeId, PageRequest.of(page, size), normalizedSort, user.getId());
        return new StoreReviewMineResponse(
            toItemResponses(reviews.getContent()),
            reviews.getNumber(),
            reviews.getSize(),
            reviews.getTotalElements(),
            reviews.getTotalPages(),
            toSummaryResponse(store)
        );
    }

    private void refreshStoreReviewSummary(Store store) {
        long reviewCount = storeReviewRepository.countByStoreId(store.getId());
        Double averageRating = storeReviewRepository.findAverageRatingByStoreId(store.getId());
        BigDecimal roundedAverage = averageRating == null ? null : BigDecimal.valueOf(averageRating).setScale(1, RoundingMode.HALF_UP);
        store.updateReviewSummary(roundedAverage, reviewCount);
        storeRepository.save(store);
    }

    private Store lockStore(Long storeId) {
        return storeService.getRegisteredStoreForUpdate(storeId);
    }

    private StoreReview getReviewOrThrow(Long reviewId) {
        return storeReviewRepository.findById(reviewId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, REVIEW_NOT_FOUND_CODE, "리뷰를 찾을 수 없습니다."));
    }

    private void ensureOwnedByCurrentUser(StoreReview review, User currentUser) {
        if (!review.getUser().getId().equals(currentUser.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, REVIEW_ACCESS_DENIED_CODE, "본인 리뷰만 수정하거나 삭제할 수 있습니다.");
        }
    }

    private int validateRating(int rating) {
        if (rating < 1 || rating > 5) {
            throw new ApiException(HttpStatus.BAD_REQUEST, INVALID_REVIEW_RATING_CODE, "평점은 1점부터 5점 사이여야 합니다.");
        }
        return rating;
    }

    private String normalizeContent(String content) {
        if (content == null) {
            throw invalidContent();
        }

        String normalized = content.trim();
        if (normalized.isBlank() || normalized.length() > MAX_CONTENT_LENGTH) {
            throw invalidContent();
        }
        return normalized;
    }

    private ApiException invalidContent() {
        return new ApiException(HttpStatus.BAD_REQUEST, INVALID_REVIEW_CONTENT_CODE, "리뷰 내용을 확인해주세요.");
    }

    private void validatePageRequest(int page, int size) {
        if (page < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "페이지는 0 이상이어야 합니다.");
        }
        if (size <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE_SIZE", "페이지 크기는 1 이상이어야 합니다.");
        }
    }

    private String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return SORT_LATEST;
        }

        String normalized = sort.trim().toLowerCase();
        if (SORT_LATEST.equals(normalized) || SORT_RATING_DESC.equals(normalized) || SORT_RATING_ASC.equals(normalized)) {
            return normalized;
        }

        throw new ApiException(HttpStatus.BAD_REQUEST, INVALID_REVIEW_SORT_CODE, "지원하지 않는 정렬 기준입니다.");
    }

    private Page<StoreReview> fetchStoreReviews(Long storeId, PageRequest pageRequest, String sort, Long userId) {
        if (SORT_RATING_DESC.equals(sort)) {
            if (userId == null) {
                return storeReviewRepository.findAllByStoreIdOrderByRatingDescCreatedAtDescIdDesc(storeId, pageRequest);
            }
            return storeReviewRepository.findAllByStoreIdAndUserIdOrderByRatingDescCreatedAtDescIdDesc(storeId, userId, pageRequest);
        }

        if (SORT_RATING_ASC.equals(sort)) {
            if (userId == null) {
                return storeReviewRepository.findAllByStoreIdOrderByRatingAscCreatedAtDescIdDesc(storeId, pageRequest);
            }
            return storeReviewRepository.findAllByStoreIdAndUserIdOrderByRatingAscCreatedAtDescIdDesc(storeId, userId, pageRequest);
        }

        if (userId == null) {
            return storeReviewRepository.findAllByStoreIdOrderByCreatedAtDesc(storeId, pageRequest);
        }
        return storeReviewRepository.findAllByStoreIdAndUserIdOrderByCreatedAtDesc(storeId, userId, pageRequest);
    }

    private StoreReviewItemResponse toItemResponse(StoreReview review) {
        return new StoreReviewItemResponse(
            review.getId(),
            review.getStore().getId(),
            review.getUser().getId(),
            resolveAuthorNickname(review.getUser()),
            review.getRating(),
            review.getContent(),
            deserializeImageUrls(review.getImageUrlsJson()),
            review.getCreatedAt(),
            review.getUpdatedAt()
        );
    }

    private List<StoreReviewItemResponse> toItemResponses(List<StoreReview> reviews) {
        return reviews.stream()
            .map(this::toItemResponse)
            .toList();
    }

    private StoreReviewSummaryResponse toSummaryResponse(Store store) {
        return new StoreReviewSummaryResponse(store.getReviewAverageRating(), store.getReviewCount());
    }

    private String resolveAuthorNickname(User user) {
        if (user.getNickname() != null && !user.getNickname().isBlank()) {
            return user.getNickname();
        }
        if (user.getOwnerDisplayName() != null && !user.getOwnerDisplayName().isBlank()) {
            return user.getOwnerDisplayName();
        }
        String email = user.getEmail();
        if (email == null || email.isBlank()) {
            return "사용자";
        }

        int atIndex = email.indexOf('@');
        return atIndex > 0 ? email.substring(0, atIndex) : email;
    }

    private ReviewImagePayload resolveReviewImagePayload(List<String> imageUrls) {
        List<String> normalized = normalizeImageUrls(imageUrls);
        return new ReviewImagePayload(serializeRawJson(normalized), serializeObjectKeysJson(normalized), extractReviewObjectKeys(normalized));
    }

    private String serializeRawJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "REVIEW_IMAGE_SERIALIZATION_FAILED", "리뷰 이미지를 저장하지 못했습니다.");
        }
    }

    private String serializeObjectKeysJson(List<String> imageUrls) {
        List<String> objectKeys = extractReviewObjectKeys(imageUrls);
        if (objectKeys.isEmpty()) {
            return null;
        }

        return serializeRawJson(objectKeys);
    }

    private List<String> extractReviewObjectKeys(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return List.of();
        }

        List<String> objectKeys = new ArrayList<>();
        for (String imageUrl : imageUrls) {
            String objectKey = extractReviewObjectKey(imageUrl);
            if (objectKey != null) {
                objectKeys.add(objectKey);
            }
        }

        return List.copyOf(objectKeys);
    }

    private List<String> resolveStoredReviewObjectKeys(String imageKeysJson, String imageUrlsJson) {
        if (imageKeysJson != null && !imageKeysJson.isBlank()) {
            return deserializeStringList(imageKeysJson);
        }

        return extractReviewObjectKeys(deserializeStringList(imageUrlsJson));
    }

    private List<String> deserializeStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }

        try {
            List<String> parsed = objectMapper.readValue(
                json,
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
            return parsed.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private void scheduleReviewImageCleanup(List<String> removedObjectKeys, List<String> nextObjectKeys) {
        if (removedObjectKeys == null || removedObjectKeys.isEmpty()) {
            return;
        }

        Set<String> retainedKeys = nextObjectKeys == null || nextObjectKeys.isEmpty()
            ? Set.of()
            : new LinkedHashSet<>(nextObjectKeys);

        List<String> keysToDelete = removedObjectKeys.stream()
            .filter(key -> key != null && !key.isBlank())
            .map(String::trim)
            .filter(key -> !retainedKeys.contains(key))
            .distinct()
            .toList();

        if (keysToDelete.isEmpty()) {
            return;
        }

        runAfterCommit(() -> keysToDelete.forEach(this::deleteReviewImageSafely));
    }

    private void scheduleReviewImageCleanup(List<String> removedObjectKeys) {
        scheduleReviewImageCleanup(removedObjectKeys, List.of());
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private void deleteReviewImageSafely(String objectKey) {
        try {
            s3FileService.deleteFile(objectKey);
        } catch (RuntimeException ex) {
            log.warn("리뷰 이미지 삭제에 실패했습니다. key={}", objectKey, ex);
        }
    }

    private String extractReviewObjectKey(String imageUrl) {
        if (imageUrl == null) {
            return null;
        }

        String normalized = imageUrl.trim();
        if (normalized.isBlank()) {
            return null;
        }

        try {
            URI uri = URI.create(normalized);
            String host = uri.getHost();
            String path = uri.getPath();
            if (host != null && host.contains("amazonaws.com") && path != null && path.startsWith("/review/")) {
                String key = path.substring(1);
                return key.isBlank() ? null : key;
            }

            String query = uri.getRawQuery();
            if (query != null && query.startsWith("key=")) {
                String key = decodeQueryValue(query.substring(4));
                return key != null && key.startsWith("review/") ? key : null;
            }
        } catch (IllegalArgumentException ignored) {
            // Fall through to the lightweight relative-path checks below.
        }

        if (normalized.startsWith("/api/v1/files/view?key=")) {
            String key = decodeQueryValue(normalized.substring("/api/v1/files/view?key=".length()));
            return key != null && key.startsWith("review/") ? key : null;
        }

        if (normalized.startsWith("api/v1/files/view?key=")) {
            String key = decodeQueryValue(normalized.substring("api/v1/files/view?key=".length()));
            return key != null && key.startsWith("review/") ? key : null;
        }

        return normalized.startsWith("review/") ? normalized : null;
    }

    private String decodeQueryValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String decoded = URLDecoder.decode(value, StandardCharsets.UTF_8);
        return decoded.isBlank() ? null : decoded;
    }

    private record ReviewImagePayload(String imageUrlsJson, String imageKeysJson, List<String> imageKeys) {
    }

    private List<String> deserializeImageUrls(String imageUrlsJson) {
        if (imageUrlsJson == null || imageUrlsJson.isBlank()) {
            return List.of();
        }

        try {
            List<String> parsed = objectMapper.readValue(
                imageUrlsJson,
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
            return ImageUrlMapper.toBrowserUrls(normalizeImageUrls(parsed));
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private List<String> normalizeImageUrls(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return List.of();
        }

        List<String> normalized = new ArrayList<>();
        for (String imageUrl : imageUrls) {
            if (imageUrl == null) {
                continue;
            }

            String trimmed = imageUrl.trim();
            if (trimmed.isBlank()) {
                continue;
            }

            normalized.add(trimmed);
            if (normalized.size() > MAX_IMAGE_COUNT) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REVIEW_IMAGE_COUNT", "리뷰 이미지는 최대 5장까지 등록할 수 있습니다.");
            }
        }

        return List.copyOf(normalized);
    }

}
