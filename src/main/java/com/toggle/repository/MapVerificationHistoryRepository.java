package com.toggle.repository;

import com.toggle.entity.MapVerificationHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MapVerificationHistoryRepository extends JpaRepository<MapVerificationHistory, Long> {

    List<MapVerificationHistory> findAllByRequestIdOrderByCreatedAtDesc(Long requestId);
}
