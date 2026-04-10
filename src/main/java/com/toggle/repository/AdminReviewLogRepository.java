package com.toggle.repository;

import com.toggle.entity.AdminReviewLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminReviewLogRepository extends JpaRepository<AdminReviewLog, Long> {

    List<AdminReviewLog> findAllByRequestIdOrderByCreatedAtDesc(Long requestId);
}
