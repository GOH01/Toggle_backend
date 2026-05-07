package com.toggle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.toggle.entity.OwnerApplication;
import com.toggle.entity.User;
import com.toggle.entity.UserRole;
import com.toggle.entity.UserStatus;
import com.toggle.repository.UserMapLikeRepository;
import com.toggle.repository.UserMapRepository;
import com.toggle.repository.OwnerApplicationRepository;
import com.toggle.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class BusinessLicenseRetentionSchedulerTest {

    @Autowired
    private BusinessLicenseRetentionScheduler scheduler;

    @Autowired
    private OwnerApplicationRepository ownerApplicationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserMapLikeRepository userMapLikeRepository;

    @Autowired
    private UserMapRepository userMapRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private S3FileService s3FileService;

    @MockBean
    private NationalTaxServiceClient nationalTaxServiceClient;

    @MockBean
    private KakaoPlaceClient kakaoPlaceClient;

    @BeforeEach
    void setUp() {
        ownerApplicationRepository.deleteAll();
        userMapLikeRepository.deleteAll();
        userMapRepository.deleteAll();
        userRepository.deleteAll();
        given(kakaoPlaceClient.searchByKeyword(anyString())).willReturn(List.of());
    }

    @Test
    void schedulerShouldDeleteExpiredBusinessDocumentsAndMarkDatabaseRows() {
        User owner = userRepository.save(new User(
            "owner-retention@toggle.com",
            passwordEncoder.encode("password123!"),
            "owner-retention",
            UserRole.OWNER,
            UserStatus.ACTIVE
        ));

        OwnerApplication application = new OwnerApplication(
            owner,
            "retention-shop",
            "1234567890",
            "홍길동",
            LocalDate.of(2021, 3, 15),
            "서울특별시 강남구 테헤란로 123",
            "서울특별시 강남구 테헤란로 123",
            "02-1234-5678",
            "business/expired.pdf"
        );
        application.approve(owner, LocalDateTime.now().minusDays(8));
        ownerApplicationRepository.save(application);

        scheduler.deleteExpiredBusinessDocuments();

        verify(s3FileService).deleteFile("business/expired.pdf");

        OwnerApplication refreshed = ownerApplicationRepository.findById(application.getId()).orElseThrow();
        assertThat(refreshed.getDeletedAt()).isNotNull();
        assertThat(refreshed.getDeleteReason()).isEqualTo("RETENTION_EXPIRED_AFTER_7_DAYS");
    }
}
