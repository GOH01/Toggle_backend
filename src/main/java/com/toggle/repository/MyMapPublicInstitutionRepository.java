package com.toggle.repository;

import com.toggle.entity.MyMapPublicInstitution;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MyMapPublicInstitutionRepository extends JpaRepository<MyMapPublicInstitution, Long> {

    boolean existsByMapIdAndPublicInstitutionId(Long mapId, Long publicInstitutionId);

    Optional<MyMapPublicInstitution> findByMapIdAndPublicInstitutionId(Long mapId, Long publicInstitutionId);

    Optional<MyMapPublicInstitution> findByUserIdAndMapIsNullAndPublicInstitutionId(Long userId, Long publicInstitutionId);

    @EntityGraph(attributePaths = "publicInstitution")
    List<MyMapPublicInstitution> findAllByUserIdAndMapIsNullOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = "publicInstitution")
    List<MyMapPublicInstitution> findAllByMapIdOrderByCreatedAtDesc(Long mapId);

    long countByMapId(Long mapId);

    void deleteAllByMapId(Long mapId);
}
