package com.toggle.repository;

import com.toggle.entity.StoreReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreReviewRepository extends JpaRepository<StoreReview, Long> {

    @EntityGraph(attributePaths = {"user", "store"})
    @Query(
        value = """
            select r
            from StoreReview r
            where r.store.id = :storeId
              and r.store.deletedAt is null
            order by r.createdAt desc, r.id desc
            """,
        countQuery = """
            select count(r)
            from StoreReview r
            where r.store.id = :storeId
              and r.store.deletedAt is null
            """
    )
    Page<StoreReview> findAllByStoreIdOrderByCreatedAtDesc(@Param("storeId") Long storeId, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "store"})
    @Query(
        value = """
            select r
            from StoreReview r
            where r.store.id = :storeId
              and r.store.deletedAt is null
            order by r.rating desc, r.createdAt desc, r.id desc
            """,
        countQuery = """
            select count(r)
            from StoreReview r
            where r.store.id = :storeId
              and r.store.deletedAt is null
            """
    )
    Page<StoreReview> findAllByStoreIdOrderByRatingDescCreatedAtDescIdDesc(@Param("storeId") Long storeId, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "store"})
    @Query(
        value = """
            select r
            from StoreReview r
            where r.store.id = :storeId
              and r.store.deletedAt is null
            order by r.rating asc, r.createdAt desc, r.id desc
            """,
        countQuery = """
            select count(r)
            from StoreReview r
            where r.store.id = :storeId
              and r.store.deletedAt is null
            """
    )
    Page<StoreReview> findAllByStoreIdOrderByRatingAscCreatedAtDescIdDesc(@Param("storeId") Long storeId, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "store"})
    @Query(
        value = """
            select r
            from StoreReview r
            where r.store.id = :storeId
              and r.store.deletedAt is null
              and r.user.id = :userId
            order by r.createdAt desc, r.id desc
            """,
        countQuery = """
            select count(r)
            from StoreReview r
            where r.store.id = :storeId
              and r.store.deletedAt is null
              and r.user.id = :userId
            """
    )
    Page<StoreReview> findAllByStoreIdAndUserIdOrderByCreatedAtDesc(
        @Param("storeId") Long storeId,
        @Param("userId") Long userId,
        Pageable pageable
    );

    @EntityGraph(attributePaths = {"user", "store"})
    @Query(
        value = """
            select r
            from StoreReview r
            where r.store.id = :storeId
              and r.store.deletedAt is null
              and r.user.id = :userId
            order by r.rating desc, r.createdAt desc, r.id desc
            """,
        countQuery = """
            select count(r)
            from StoreReview r
            where r.store.id = :storeId
              and r.store.deletedAt is null
              and r.user.id = :userId
            """
    )
    Page<StoreReview> findAllByStoreIdAndUserIdOrderByRatingDescCreatedAtDescIdDesc(
        @Param("storeId") Long storeId,
        @Param("userId") Long userId,
        Pageable pageable
    );

    @EntityGraph(attributePaths = {"user", "store"})
    @Query(
        value = """
            select r
            from StoreReview r
            where r.store.id = :storeId
              and r.store.deletedAt is null
              and r.user.id = :userId
            order by r.rating asc, r.createdAt desc, r.id desc
            """,
        countQuery = """
            select count(r)
            from StoreReview r
            where r.store.id = :storeId
              and r.store.deletedAt is null
              and r.user.id = :userId
            """
    )
    Page<StoreReview> findAllByStoreIdAndUserIdOrderByRatingAscCreatedAtDescIdDesc(
        @Param("storeId") Long storeId,
        @Param("userId") Long userId,
        Pageable pageable
    );

    long countByStoreId(Long storeId);

    @Query("select avg(r.rating) from StoreReview r where r.store.id = :storeId")
    Double findAverageRatingByStoreId(@Param("storeId") Long storeId);
}
