package com.toggle.repository;

import com.toggle.entity.UserMapLike;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserMapLikeRepository extends JpaRepository<UserMapLike, Long> {

    boolean existsByMapIdAndUserId(Long mapId, Long userId);

    Optional<UserMapLike> findByMapIdAndUserId(Long mapId, Long userId);

    long countByMapId(Long mapId);

    void deleteAllByMapId(Long mapId);

    List<UserMapLike> findAllByMapId(Long mapId);

    @EntityGraph(attributePaths = {"map", "map.ownerUser"})
    Page<UserMapLike> findAllByUserIdAndMapIsPublicTrueAndMapDeletedAtIsNullOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
