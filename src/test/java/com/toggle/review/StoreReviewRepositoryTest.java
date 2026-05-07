package com.toggle.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.toggle.entity.ExternalSource;
import com.toggle.entity.Store;
import com.toggle.entity.StoreReview;
import com.toggle.entity.User;
import com.toggle.entity.UserRole;
import com.toggle.entity.UserStatus;
import com.toggle.global.config.JpaAuditingConfig;
import com.toggle.repository.StoreRepository;
import com.toggle.repository.StoreReviewRepository;
import com.toggle.repository.UserMapLikeRepository;
import com.toggle.repository.UserMapRepository;
import com.toggle.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class StoreReviewRepositoryTest {

    @Autowired
    private StoreReviewRepository storeReviewRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserMapLikeRepository userMapLikeRepository;

    @Autowired
    private UserMapRepository userMapRepository;

    @Autowired
    private TestEntityManager entityManager;

    @BeforeEach
    void setUp() {
        storeReviewRepository.deleteAll();
        storeRepository.deleteAll();
        userMapLikeRepository.deleteAll();
        userMapRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void sameUserCanWriteMultipleReviewsToSameStore() {
        User user = userRepository.save(createUser("reviewer@toggle.com", "reviewer"));
        Store store = storeRepository.save(createStore("store-review-1"));

        persistReview(user, store, 5, "첫 번째 리뷰", LocalDateTime.of(2026, 4, 23, 10, 0));
        persistReview(user, store, 4, "두 번째 리뷰", LocalDateTime.of(2026, 4, 23, 11, 0));

        assertThat(storeReviewRepository.countByStoreId(store.getId())).isEqualTo(2L);
        assertThat(storeReviewRepository.findAllByStoreIdAndUserIdOrderByCreatedAtDesc(
            store.getId(),
            user.getId(),
            PageRequest.of(0, 10)
        ).getContent())
            .extracting(StoreReview::getContent)
            .containsExactly("두 번째 리뷰", "첫 번째 리뷰");
    }

    @Test
    void publicReviewListingShouldReturnNewestReviewsFirst() {
        User olderAuthor = userRepository.save(createUser("reviewer-2@toggle.com", "reviewer-two"));
        User newerAuthor = userRepository.save(createUser("reviewer-3@toggle.com", "reviewer-three"));
        Store store = storeRepository.save(createStore("store-review-2"));

        persistReview(olderAuthor, store, 3, "오래된 리뷰", LocalDateTime.of(2026, 4, 23, 9, 0));
        persistReview(newerAuthor, store, 5, "최신 리뷰", LocalDateTime.of(2026, 4, 23, 12, 0));

        assertThat(storeReviewRepository.findAllByStoreIdOrderByCreatedAtDesc(store.getId(), PageRequest.of(0, 10))
            .getContent())
            .extracting(StoreReview::getContent)
            .containsExactly("최신 리뷰", "오래된 리뷰");
    }

    @Test
    void mineListingShouldReturnOnlyTheAuthenticatedUsersReviewsNewestFirst() {
        User mine = userRepository.save(createUser("reviewer-4@toggle.com", "reviewer-four"));
        User other = userRepository.save(createUser("reviewer-5@toggle.com", "reviewer-five"));
        Store store = storeRepository.save(createStore("store-review-3"));

        persistReview(mine, store, 2, "내 첫 리뷰", LocalDateTime.of(2026, 4, 23, 8, 0));
        persistReview(other, store, 5, "남의 리뷰", LocalDateTime.of(2026, 4, 23, 9, 0));
        persistReview(mine, store, 4, "내 최신 리뷰", LocalDateTime.of(2026, 4, 23, 10, 0));

        assertThat(storeReviewRepository.findAllByStoreIdAndUserIdOrderByCreatedAtDesc(
            store.getId(),
            mine.getId(),
            PageRequest.of(0, 10)
        ).getContent())
            .extracting(StoreReview::getContent)
            .containsExactly("내 최신 리뷰", "내 첫 리뷰");
    }

    @Test
    void equalTimestampsShouldUseIdAsTheTieBreaker() {
        User author = userRepository.save(createUser("reviewer-6@toggle.com", "reviewer-six"));
        Store store = storeRepository.save(createStore("store-review-4"));
        LocalDateTime sameTimestamp = LocalDateTime.of(2026, 4, 23, 13, 0);

        StoreReview first = new StoreReview(author, store, 2, "첫 번째");
        ReflectionTestUtils.setField(first, "createdAt", sameTimestamp);
        ReflectionTestUtils.setField(first, "updatedAt", sameTimestamp);
        entityManager.persist(first);
        entityManager.flush();

        StoreReview second = new StoreReview(author, store, 5, "두 번째");
        ReflectionTestUtils.setField(second, "createdAt", sameTimestamp);
        ReflectionTestUtils.setField(second, "updatedAt", sameTimestamp);
        entityManager.persist(second);
        entityManager.flush();
        entityManager.clear();

        assertThat(storeReviewRepository.findAllByStoreIdOrderByCreatedAtDesc(store.getId(), PageRequest.of(0, 10))
            .getContent())
            .extracting(StoreReview::getContent)
            .containsExactly("두 번째", "첫 번째");
    }

    @Test
    void ratingDescSortShouldUseRatingThenCreatedAtThenId() {
        User author = userRepository.save(createUser("reviewer-7@toggle.com", "reviewer-seven"));
        Store store = storeRepository.save(createStore("store-review-5"));
        LocalDateTime baseTime = LocalDateTime.of(2026, 4, 23, 14, 0);

        persistReview(author, store, 3, "별점 3점 오래된", baseTime);
        persistReview(author, store, 5, "별점 5점", baseTime.plusMinutes(1));
        persistReview(author, store, 4, "별점 4점 최신", baseTime.plusMinutes(2));
        persistReview(author, store, 4, "별점 4점 오래된", baseTime.plusMinutes(2));

        assertThat(storeReviewRepository.findAllByStoreIdOrderByRatingDescCreatedAtDescIdDesc(store.getId(), PageRequest.of(0, 10))
            .getContent())
            .extracting(StoreReview::getContent)
            .containsExactly("별점 5점", "별점 4점 오래된", "별점 4점 최신", "별점 3점 오래된");
    }

    private User createUser(String email, String nickname) {
        return new User(email, "password123!", nickname, UserRole.USER, UserStatus.ACTIVE);
    }

    private Store createStore(String externalPlaceId) {
        return new Store(
            ExternalSource.KAKAO,
            externalPlaceId,
            "테스트 매장",
            "02-123-4567",
            "서울시 테스트구 테스트로 1",
            "서울시 테스트구 테스트로 1",
            new BigDecimal("37.1234567"),
            new BigDecimal("127.1234567")
        );
    }

    private void persistReview(User user, Store store, int rating, String content, LocalDateTime createdAt) {
        StoreReview review = new StoreReview(user, store, rating, content);
        ReflectionTestUtils.setField(review, "createdAt", createdAt);
        ReflectionTestUtils.setField(review, "updatedAt", createdAt);
        entityManager.persist(review);
        entityManager.flush();
        entityManager.clear();
    }
}
