package com.toggle.repository;

import com.toggle.entity.Favorite;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    boolean existsByUserIdAndStoreId(Long userId, Long storeId);

    Optional<Favorite> findByUserIdAndStoreId(Long userId, Long storeId);

    @EntityGraph(attributePaths = "store")
    List<Favorite> findAllByUserIdOrderByCreatedAtDesc(Long userId);
}
