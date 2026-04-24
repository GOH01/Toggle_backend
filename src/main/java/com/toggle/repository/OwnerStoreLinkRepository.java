package com.toggle.repository;

import com.toggle.entity.OwnerStoreLink;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OwnerStoreLinkRepository extends JpaRepository<OwnerStoreLink, Long> {

    @EntityGraph(attributePaths = {"ownerUser", "store"})
    List<OwnerStoreLink> findAllByOwnerUserIdAndStoreDeletedAtIsNull(Long ownerUserId);

    @EntityGraph(attributePaths = {"ownerUser", "store"})
    Optional<OwnerStoreLink> findByOwnerUserIdAndStoreIdAndStoreDeletedAtIsNull(Long ownerUserId, Long storeId);

    boolean existsByStoreIdAndStoreDeletedAtIsNull(Long storeId);

    void deleteByStoreId(Long storeId);
}
