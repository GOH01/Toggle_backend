package com.toggle.repository;

import com.toggle.entity.MyMapStore;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MyMapStoreRepository extends JpaRepository<MyMapStore, Long> {

    boolean existsByMapIdAndStoreId(Long mapId, Long storeId);

    Optional<MyMapStore> findByMapIdAndStoreId(Long mapId, Long storeId);

    Optional<MyMapStore> findByUserIdAndMapIsNullAndStoreId(Long userId, Long storeId);

    @EntityGraph(attributePaths = "store")
    List<MyMapStore> findAllByUserIdAndMapIsNullOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = "store")
    List<MyMapStore> findAllByMapIdOrderByCreatedAtDesc(Long mapId);

    @EntityGraph(attributePaths = "store")
    List<MyMapStore> findAllByMapIdAndStoreDeletedAtIsNullOrderByCreatedAtDesc(Long mapId);

    long countByMapIdAndStoreDeletedAtIsNull(Long mapId);

    void deleteAllByMapId(Long mapId);
}
