package com.toggle.repository;

import com.toggle.entity.OwnerApplication;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OwnerApplicationRepository extends JpaRepository<OwnerApplication, Long> {

    @EntityGraph(attributePaths = "user")
    List<OwnerApplication> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = "user")
    List<OwnerApplication> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = "user")
    Optional<OwnerApplication> findById(Long id);
}
