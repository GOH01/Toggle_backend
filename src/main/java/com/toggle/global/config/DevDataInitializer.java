package com.toggle.global.config;

import com.toggle.entity.BusinessVerificationHistory;
import com.toggle.entity.BusinessVerificationType;
import com.toggle.entity.MapVerificationHistory;
import com.toggle.entity.MapVerificationQueryType;
import com.toggle.entity.VerificationRecordStatus;
import com.toggle.entity.OwnerApplication;
import com.toggle.entity.User;
import com.toggle.entity.UserRole;
import com.toggle.entity.UserStatus;
import com.toggle.repository.BusinessVerificationHistoryRepository;
import com.toggle.repository.MapVerificationHistoryRepository;
import com.toggle.repository.OwnerApplicationRepository;
import com.toggle.repository.UserRepository;
import com.toggle.service.AddressNormalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DevDataInitializer {

    @Bean
    @Profile("!test")
    @Order(Ordered.LOWEST_PRECEDENCE)
    CommandLineRunner seedDemoUser(
        UserRepository userRepository,
        OwnerApplicationRepository ownerApplicationRepository,
        BusinessVerificationHistoryRepository businessVerificationHistoryRepository,
        MapVerificationHistoryRepository mapVerificationHistoryRepository,
        AddressNormalizer addressNormalizer,
        PasswordEncoder passwordEncoder
    ) {
        return args -> {
            seedUserIfMissing(userRepository, passwordEncoder, "demo@toggle.com", "password123!", "toggle-demo", UserRole.USER, UserStatus.ACTIVE);
            seedUserIfMissing(userRepository, passwordEncoder, "admin@toggle.com", "password123!", "toggle-admin", UserRole.ADMIN, UserStatus.ACTIVE);
            seedUserIfMissing(userRepository, passwordEncoder, "test@test.com", "test", "test-owner", UserRole.OWNER, UserStatus.ACTIVE);

            User ownerUser = userRepository.findByEmail("test@test.com").orElseThrow();
            seedPendingOwnerApplication(
                ownerApplicationRepository,
                businessVerificationHistoryRepository,
                mapVerificationHistoryRepository,
                addressNormalizer,
                ownerUser
            );
        };
    }

    private void seedUserIfMissing(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        String email,
        String rawPassword,
        String nickname,
        UserRole role,
        UserStatus status
    ) {
        if (userRepository.findByEmail(email).isPresent()) {
            return;
        }

        userRepository.save(new User(
            email,
            passwordEncoder.encode(rawPassword),
            nickname,
            role,
            status
        ));
    }

    private void seedPendingOwnerApplication(
        OwnerApplicationRepository ownerApplicationRepository,
        BusinessVerificationHistoryRepository businessVerificationHistoryRepository,
        MapVerificationHistoryRepository mapVerificationHistoryRepository,
        AddressNormalizer addressNormalizer,
        User ownerUser
    ) {
        java.util.List<OwnerApplication> ownerApplications = ownerApplicationRepository.findAllByUserIdOrderByCreatedAtDesc(ownerUser.getId());
        OwnerApplication existingSeedApplication = ownerApplications.stream()
            .filter(application -> "/dev-seed/pending-owner-application.pdf".equals(application.getBusinessLicenseObjectKey()))
            .findFirst()
            .orElse(null);

        if (existingSeedApplication != null) {
            normalizeSeedApplication(existingSeedApplication, ownerApplicationRepository, businessVerificationHistoryRepository, mapVerificationHistoryRepository);
            return;
        }

        boolean hasPendingApplication = ownerApplications.stream()
            .anyMatch(application -> application.getReviewStatus().name().equals("PENDING") || application.getReviewStatus().name().equals("UNDER_REVIEW"));

        if (hasPendingApplication) {
            return;
        }

        OwnerApplication application = new OwnerApplication(
            ownerUser,
            "운영 확인용 토글 샵",
            "8888877777",
            "홍길동",
            LocalDate.of(2021, 3, 15),
            "서울특별시 강남구 선릉로 551",
            addressNormalizer.normalize("서울특별시 강남구 선릉로 551"),
            "02-555-1234",
            "/dev-seed/pending-owner-application.pdf"
        );
        application.markAutoVerificationUnavailable();
        application.markMapVerificationFailed();
        ownerApplicationRepository.save(application);

        businessVerificationHistoryRepository.save(new BusinessVerificationHistory(
            application,
            BusinessVerificationType.AUTO_NTS,
            VerificationRecordStatus.FAILED,
            null,
            null,
            application.getBusinessNumber(),
            application.getRepresentativeName(),
            application.getBusinessOpenDate().toString(),
            application.getBusinessAddressRaw(),
            "DEV_SEED_RETRY_REQUIRED",
            "개발 시드 신청입니다. 관리자 화면에서 자동 검증을 다시 실행해 주세요.",
            null,
            LocalDateTime.now()
        ));

        mapVerificationHistoryRepository.save(new MapVerificationHistory(
            application,
            application.getBusinessAddressRaw(),
            MapVerificationQueryType.ADDRESS_ONLY,
            VerificationRecordStatus.FAILED,
            0,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "DEV_SEED_RETRY_REQUIRED",
            "개발 시드 신청입니다. 지도 재검증을 실행해 최신 결과를 확인해 주세요.",
            LocalDateTime.now()
        ));
    }

    private void normalizeSeedApplication(
        OwnerApplication application,
        OwnerApplicationRepository ownerApplicationRepository,
        BusinessVerificationHistoryRepository businessVerificationHistoryRepository,
        MapVerificationHistoryRepository mapVerificationHistoryRepository
    ) {
        if (!application.getReviewStatus().name().equals("PENDING")
            && !application.getBusinessVerificationStatus().name().equals("NOT_STARTED")
            && !application.getMapVerificationStatus().name().equals("NOT_STARTED")) {
            return;
        }

        application.markAutoVerificationUnavailable();
        application.markMapVerificationFailed();
        ownerApplicationRepository.save(application);

        businessVerificationHistoryRepository.save(new BusinessVerificationHistory(
            application,
            BusinessVerificationType.AUTO_NTS,
            VerificationRecordStatus.FAILED,
            null,
            null,
            application.getBusinessNumber(),
            application.getRepresentativeName(),
            application.getBusinessOpenDate().toString(),
            application.getBusinessAddressRaw(),
            "DEV_SEED_RETRY_REQUIRED",
            "기존 개발 시드 신청을 최신 검증 흐름에 맞게 보정했습니다. 자동 검증을 다시 실행해 주세요.",
            null,
            LocalDateTime.now()
        ));

        mapVerificationHistoryRepository.save(new MapVerificationHistory(
            application,
            application.getBusinessAddressRaw(),
            MapVerificationQueryType.ADDRESS_ONLY,
            VerificationRecordStatus.FAILED,
            0,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "DEV_SEED_RETRY_REQUIRED",
            "기존 개발 시드 신청을 최신 검증 흐름에 맞게 보정했습니다. 지도 재검증을 실행해 주세요.",
            LocalDateTime.now()
        ));
    }
}
