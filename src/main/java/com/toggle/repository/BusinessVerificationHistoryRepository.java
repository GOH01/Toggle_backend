package com.toggle.repository;

import com.toggle.entity.BusinessVerificationHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessVerificationHistoryRepository extends JpaRepository<BusinessVerificationHistory, Long> {

    List<BusinessVerificationHistory> findAllByRequestIdOrderByCreatedAtDesc(Long requestId);
}
