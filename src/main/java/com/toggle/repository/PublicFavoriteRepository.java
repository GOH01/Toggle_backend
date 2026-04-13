package com.toggle.repository;

import com.toggle.entity.PublicFavorite;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicFavoriteRepository extends JpaRepository<PublicFavorite, Long> {

    Optional<PublicFavorite> findByUserIdAndPublicInstitutionId(Long userId, Long publicInstitutionId);

    List<PublicFavorite> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByUserIdAndPublicInstitutionId(Long userId, Long publicInstitutionId);
}
