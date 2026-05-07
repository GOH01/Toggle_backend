package com.toggle.repository;

import com.toggle.entity.UserMapLike;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserMapLikeRepository extends JpaRepository<UserMapLike, Long> {

    boolean existsByMapIdAndUserId(Long mapId, Long userId);

    Optional<UserMapLike> findByMapIdAndUserId(Long mapId, Long userId);

    long countByMapId(Long mapId);

    void deleteAllByMapId(Long mapId);

    List<UserMapLike> findAllByMapId(Long mapId);
}
