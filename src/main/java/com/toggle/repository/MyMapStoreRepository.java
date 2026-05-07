package com.toggle.repository;

import com.toggle.entity.MyMapStore;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MyMapStoreRepository extends JpaRepository<MyMapStore, Long> {

    boolean existsByUserIdAndStoreId(Long userId, Long storeId);

    boolean existsByMapIdAndStoreId(Long mapId, Long storeId);

    Optional<MyMapStore> findByUserIdAndStoreId(Long userId, Long storeId);

    Optional<MyMapStore> findByMapIdAndStoreId(Long mapId, Long storeId);

    @EntityGraph(attributePaths = "store")
    List<MyMapStore> findAllByUserIdAndStoreDeletedAtIsNullOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = "store")
    List<MyMapStore> findAllByUserIdAndMapIsNullOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = "store")
    List<MyMapStore> findAllByMapIdOrderByCreatedAtDesc(Long mapId);

    long countByUserIdAndStoreDeletedAtIsNull(Long userId);

    long countByUserId(Long userId);

    void deleteAllByMapId(Long mapId);
}
