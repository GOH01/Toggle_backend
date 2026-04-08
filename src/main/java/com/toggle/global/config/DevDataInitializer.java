package com.toggle.global.config;

import com.toggle.entity.BusinessStatus;
import com.toggle.entity.ExternalSource;
import com.toggle.entity.LiveStatusSource;
import com.toggle.entity.OwnerApplication;
import com.toggle.entity.OwnerApplicationReviewStatus;
import com.toggle.entity.OwnerStoreLink;
import com.toggle.entity.OwnerStoreMatchStatus;
import com.toggle.entity.Store;
import com.toggle.entity.User;
import com.toggle.entity.UserRole;
import com.toggle.entity.UserStatus;
import com.toggle.repository.OwnerApplicationRepository;
import com.toggle.repository.OwnerStoreLinkRepository;
import com.toggle.repository.StoreRepository;
import com.toggle.repository.UserRepository;
import com.toggle.service.AddressNormalizer;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DevDataInitializer {

    @Bean
    @Profile("!test")
    CommandLineRunner seedDemoUser(
        UserRepository userRepository,
        StoreRepository storeRepository,
        OwnerApplicationRepository ownerApplicationRepository,
        OwnerStoreLinkRepository ownerStoreLinkRepository,
        AddressNormalizer addressNormalizer,
        PasswordEncoder passwordEncoder
    ) {
        return args -> {
            seedUserIfMissing(
                userRepository,
                passwordEncoder,
                "demo@toggle.com",
                "password123!",
                "toggle-demo",
                UserRole.USER,
                UserStatus.ACTIVE
            );

            seedUserIfMissing(
                userRepository,
                passwordEncoder,
                "admin@toggle.com",
                "password123!",
                "toggle-admin",
                UserRole.ADMIN,
                UserStatus.ACTIVE
            );

            seedUserIfMissing(
                userRepository,
                passwordEncoder,
                "test@test.com",
                "test",
                "test-owner",
                UserRole.OWNER,
                UserStatus.ACTIVE
            );

            User ownerUser = userRepository.findByEmail("test@test.com").orElseThrow();
            seedMockStoresForOwner(storeRepository, ownerApplicationRepository, ownerStoreLinkRepository, addressNormalizer, ownerUser);
        };
    }

    private void seedMockStoresForOwner(
        StoreRepository storeRepository,
        OwnerApplicationRepository ownerApplicationRepository,
        OwnerStoreLinkRepository ownerStoreLinkRepository,
        AddressNormalizer addressNormalizer,
        User ownerUser
    ) {
        List<StoreSeed> storeSeeds = List.of(
            new StoreSeed("store-1", "맛있는 덮밥집", "서울시 강남구 테헤란로 123", "02-1234-5678", new BigDecimal("37.4980950"), new BigDecimal("127.0276100"), BusinessStatus.OPEN),
            new StoreSeed("store-2", "커피 한잔의 여유", "서울시 서초구 서초대로 456", "02-9876-5432", new BigDecimal("37.4950000"), new BigDecimal("127.0200000"), BusinessStatus.BREAK_TIME),
            new StoreSeed("store-3", "정통 수제버거", "서울시 강남구 논현로 789", "02-1111-2222", new BigDecimal("37.5021230"), new BigDecimal("127.0312340"), BusinessStatus.EARLY_CLOSED),
            new StoreSeed("store-4", "신선한 빵집", "서울시 서초구 강남대로 111", "02-3333-4444", new BigDecimal("37.5000100"), new BigDecimal("127.0250000"), BusinessStatus.OPEN),
            new StoreSeed("store-5", "동네 호프", "서울시 강남구 역삼로 222", "02-5555-6666", new BigDecimal("37.4967890"), new BigDecimal("127.0356780"), BusinessStatus.CLOSED),
            new StoreSeed("store-6", "부산 돼지국밥", "부산광역시 해운대구 우동 123", "051-123-4567", new BigDecimal("35.1587000"), new BigDecimal("129.1604000"), BusinessStatus.OPEN),
            new StoreSeed("store-7", "제주 흑돼지 구이", "제주특별자치도 제주시 연동 456", "064-123-1122", new BigDecimal("33.4890000"), new BigDecimal("126.4983000"), BusinessStatus.OPEN)
        );

        List<OwnerStoreLink> existingLinks = ownerStoreLinkRepository.findAllByOwnerUserId(ownerUser.getId());
        List<String> existingExternalPlaceIds = new ArrayList<>();
        for (OwnerStoreLink link : existingLinks) {
            existingExternalPlaceIds.add(link.getStore().getExternalPlaceId());
        }

        for (StoreSeed seed : storeSeeds) {
            Store store = storeRepository.findByExternalSourceAndExternalPlaceId(ExternalSource.KAKAO, seed.externalPlaceId())
                .orElseGet(() -> storeRepository.save(new Store(
                    ExternalSource.KAKAO,
                    seed.externalPlaceId(),
                    seed.name(),
                    seed.phone(),
                    seed.address(),
                    addressNormalizer.normalize(seed.address()),
                    seed.latitude(),
                    seed.longitude()
                )));

            store.updateLiveBusinessStatus(seed.liveBusinessStatus(), LiveStatusSource.OWNER_POS);
            storeRepository.save(store);

            if (existingExternalPlaceIds.contains(seed.externalPlaceId()) || ownerStoreLinkRepository.existsByStoreId(store.getId())) {
                continue;
            }

            OwnerApplication application = ownerApplicationRepository.save(new OwnerApplication(
                ownerUser,
                seed.name(),
                nextBusinessNumber(seed.externalPlaceId()),
                seed.address(),
                addressNormalizer.normalize(seed.address()),
                "/dev-seed/" + seed.externalPlaceId() + ".pdf",
                seed.externalPlaceId() + ".pdf",
                "application/pdf",
                OwnerApplicationReviewStatus.PENDING
            ));
            application.approve(LocalDateTime.now());
            ownerApplicationRepository.save(application);

            ownerStoreLinkRepository.save(new OwnerStoreLink(
                ownerUser,
                store,
                application,
                OwnerStoreMatchStatus.MANUALLY_CONFIRMED,
                100,
                "dev_seed_exact_match"
            ));
        }
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

    private String nextBusinessNumber(String externalPlaceId) {
        String digits = externalPlaceId.replaceAll("[^0-9]", "");
        String padded = (digits + "0000000000").substring(0, 10);
        return padded;
    }

    private record StoreSeed(
        String externalPlaceId,
        String name,
        String address,
        String phone,
        BigDecimal latitude,
        BigDecimal longitude,
        BusinessStatus liveBusinessStatus
    ) {
    }
}
