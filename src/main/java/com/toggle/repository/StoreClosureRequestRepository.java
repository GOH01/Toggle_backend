package com.toggle.repository;

import com.toggle.entity.StoreClosureRequest;
import com.toggle.entity.StoreClosureRequestStatus;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreClosureRequestRepository extends JpaRepository<StoreClosureRequest, Long> {

    @EntityGraph(attributePaths = {"store", "ownerUser", "reviewedBy"})
    Optional<StoreClosureRequest> findTopByStoreIdOrderByCreatedAtDesc(Long storeId);

    @EntityGraph(attributePaths = {"store", "ownerUser", "reviewedBy"})
    Optional<StoreClosureRequest> findTopByOwnerUserIdAndStoreIdOrderByCreatedAtDesc(Long ownerUserId, Long storeId);

    @EntityGraph(attributePaths = {"store", "ownerUser", "reviewedBy"})
    Optional<StoreClosureRequest> findTopByStoreIdAndStatusOrderByCreatedAtDesc(Long storeId, StoreClosureRequestStatus status);

    @EntityGraph(attributePaths = {"store", "ownerUser", "reviewedBy"})
    List<StoreClosureRequest> findAllByStatusOrderByCreatedAtDesc(StoreClosureRequestStatus status);

    boolean existsByStoreIdAndStatus(Long storeId, StoreClosureRequestStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from StoreClosureRequest r where r.id = :id")
    Optional<StoreClosureRequest> findByIdForUpdate(@Param("id") Long id);
}
