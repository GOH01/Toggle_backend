package com.toggle.service;

import com.toggle.entity.OwnerApplication;
import com.toggle.repository.OwnerApplicationRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessLicenseRetentionScheduler {

    private static final int RETENTION_DAYS = 7;
    private static final String RETENTION_DELETE_REASON = "RETENTION_EXPIRED_AFTER_7_DAYS";

    private final OwnerApplicationRepository ownerApplicationRepository;
    private final S3FileService s3FileService;

    public BusinessLicenseRetentionScheduler(OwnerApplicationRepository ownerApplicationRepository, S3FileService s3FileService) {
        this.ownerApplicationRepository = ownerApplicationRepository;
        this.s3FileService = s3FileService;
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void deleteExpiredBusinessDocuments() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
        List<OwnerApplication> expiredApplications = ownerApplicationRepository
            .findAllByReviewedAtIsNotNullAndDeletedAtIsNullAndReviewedAtBefore(cutoff);

        for (OwnerApplication application : expiredApplications) {
            String objectKey = application.getBusinessLicenseObjectKey();
            if (objectKey == null || objectKey.isBlank()) {
                continue;
            }

            s3FileService.deleteFile(objectKey);
            application.markBusinessLicenseDeleted(LocalDateTime.now(), RETENTION_DELETE_REASON);
        }

        ownerApplicationRepository.saveAll(expiredApplications);
    }
}
