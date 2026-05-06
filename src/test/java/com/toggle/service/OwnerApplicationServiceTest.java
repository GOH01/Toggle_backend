package com.toggle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.BDDMockito.given;

import com.toggle.dto.owner.OwnerApplicationRequest;
import com.toggle.dto.owner.OwnerApplicationUpdateRequest;
import com.toggle.dto.owner.NationalTaxVerificationResult;
import com.toggle.entity.ExternalSource;
import com.toggle.entity.OwnerApplication;
import com.toggle.entity.OwnerApplicationReviewStatus;
import com.toggle.entity.OwnerStoreLink;
import com.toggle.entity.OwnerStoreMatchStatus;
import com.toggle.entity.Store;
import com.toggle.entity.User;
import com.toggle.entity.UserRole;
import com.toggle.entity.UserStatus;
import com.toggle.repository.OwnerApplicationRepository;
import com.toggle.repository.StoreRepository;
import com.toggle.repository.OwnerStoreLinkRepository;
import com.toggle.repository.MapVerificationHistoryRepository;
import com.toggle.repository.BusinessVerificationHistoryRepository;
import com.toggle.repository.AdminReviewLogRepository;
import com.toggle.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
@ActiveProfiles("test")
class OwnerApplicationServiceTest {

    @Autowired
    private OwnerApplicationService ownerApplicationService;

    @Autowired
    private OwnerApplicationRepository ownerApplicationRepository;

    @Autowired
    private OwnerStoreLinkRepository ownerStoreLinkRepository;

    @Autowired
    private AdminReviewLogRepository adminReviewLogRepository;

    @Autowired
    private BusinessVerificationHistoryRepository businessVerificationHistoryRepository;

    @Autowired
    private MapVerificationHistoryRepository mapVerificationHistoryRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private UserRepository userRepository;

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
        ownerStoreLinkRepository.deleteAll();
        adminReviewLogRepository.deleteAll();
        businessVerificationHistoryRepository.deleteAll();
        mapVerificationHistoryRepository.deleteAll();
        ownerApplicationRepository.deleteAll();
        storeRepository.deleteAll();
        userRepository.deleteAll();

        given(kakaoPlaceClient.searchByKeyword(anyString())).willReturn(List.of());
        given(nationalTaxServiceClient.verifyBusiness(anyString(), anyString(), anyString()))
            .willReturn(new NationalTaxVerificationResult(
                true,
                "{}",
                "{}",
                "1234567890",
                "홍길동",
                "20210315",
                "서울특별시 강남구 테헤란로 123",
                null,
                null
            ));
    }

    @Test
    void createApplicationShouldStoreBusinessLicenseAsObjectKeyAndExposePresignedUrl() {
        User owner = userRepository.save(new User(
            "owner@toggle.com",
            passwordEncoder.encode("password123!"),
            "owner",
            UserRole.OWNER,
            UserStatus.ACTIVE
        ));

        given(s3FileService.uploadFile(org.mockito.ArgumentMatchers.any(MultipartFile.class), eq("business")))
            .willReturn(new S3FileService.StoredFile("https://sku-toggle.s3.ap-northeast-2.amazonaws.com/business/license.pdf", "business/license.pdf"));
        given(s3FileService.createPresignedGetUrl("business/license.pdf"))
            .willReturn("https://presigned.example/business/license.pdf");

        OwnerApplicationRequest request = new OwnerApplicationRequest(
            "owner-shop",
            "123-45-67890",
            "홍길동",
            LocalDate.of(2021, 3, 15),
            "서울특별시 강남구 테헤란로 123",
            "02-1234-5678"
        );

        ownerApplicationService.createApplication(owner, request, new MockMultipartFile(
            "businessLicenseFile",
            "license.pdf",
            "application/pdf",
            "pdf-bytes".getBytes(StandardCharsets.UTF_8)
        ));

        OwnerApplication stored = ownerApplicationRepository.findAllByUserIdOrderByCreatedAtDesc(owner.getId()).getFirst();
        assertThat(stored.getBusinessLicenseObjectKey()).isEqualTo("business/license.pdf");
        assertThat(stored.getDeletedAt()).isNull();

        var detail = ownerApplicationService.getApplicationDetail(stored.getId());
        assertThat(detail.application().businessLicenseObjectKey()).isEqualTo("business/license.pdf");
        assertThat(detail.businessLicensePresignedUrl()).isEqualTo("https://presigned.example/business/license.pdf");
    }

    @Test
    void updateApplicationShouldReplaceBusinessLicenseKeyAndDeleteOldFile() {
        User owner = userRepository.save(new User(
            "owner2@toggle.com",
            passwordEncoder.encode("password123!"),
            "owner2",
            UserRole.OWNER,
            UserStatus.ACTIVE
        ));

        given(s3FileService.uploadFile(org.mockito.ArgumentMatchers.any(MultipartFile.class), eq("business")))
            .willReturn(
                new S3FileService.StoredFile("https://sku-toggle.s3.ap-northeast-2.amazonaws.com/business/first.pdf", "business/first.pdf"),
                new S3FileService.StoredFile("https://sku-toggle.s3.ap-northeast-2.amazonaws.com/business/second.pdf", "business/second.pdf")
            );

        OwnerApplicationRequest request = new OwnerApplicationRequest(
            "owner-shop",
            "123-45-67890",
            "홍길동",
            LocalDate.of(2021, 3, 15),
            "서울특별시 강남구 테헤란로 123",
            "02-1234-5678"
        );

        ownerApplicationService.createApplication(owner, request, new MockMultipartFile(
            "businessLicenseFile",
            "first.pdf",
            "application/pdf",
            "first".getBytes(StandardCharsets.UTF_8)
        ));
        OwnerApplication stored = ownerApplicationRepository.findAllByUserIdOrderByCreatedAtDesc(owner.getId()).getFirst();
        assertThat(stored.getBusinessLicenseObjectKey()).isEqualTo("business/first.pdf");

        OwnerApplicationUpdateRequest updateRequest = new OwnerApplicationUpdateRequest(
            "owner-shop-updated",
            "123-45-67890",
            "홍길동",
            LocalDate.of(2021, 3, 15),
            "서울특별시 강남구 테헤란로 123",
            "02-1234-5678"
        );

        ownerApplicationService.updateApplication(
            owner,
            stored.getId(),
            updateRequest,
            new MockMultipartFile("businessLicenseFile", "second.pdf", "application/pdf", "second".getBytes(StandardCharsets.UTF_8))
        );

        OwnerApplication updated = ownerApplicationRepository.findById(stored.getId()).orElseThrow();
        assertThat(updated.getBusinessLicenseObjectKey()).isEqualTo("business/second.pdf");
        verify(s3FileService).deleteFile("business/first.pdf");
    }

    @Test
    void listLinkedStoresShouldExposeBrowserAccessibleImageUrls() {
        User owner = userRepository.save(new User(
            "owner3@toggle.com",
            passwordEncoder.encode("password123!"),
            "owner3",
            UserRole.OWNER,
            UserStatus.ACTIVE
        ));

        OwnerApplication application = ownerApplicationRepository.save(new OwnerApplication(
            owner,
            "owner-shop",
            "1234567890",
            "홍길동",
            LocalDate.of(2021, 3, 15),
            "서울특별시 강남구 테헤란로 123",
            "서울특별시 강남구 테헤란로 123",
            "02-1234-5678",
            "business/license.pdf"
        ));

        Store store = new Store(
            ExternalSource.KAKAO,
            "store-owner-1",
            "연결 매장",
            "02-111-2222",
            "서울특별시 강남구 테헤란로 123",
            "서울특별시 강남구 테헤란로 123",
            new BigDecimal("37.1234567"),
            new BigDecimal("127.1234567")
        );
        ReflectionTestUtils.setField(store, "ownerImageUrlsJson", "[\"https://sku-toggle.s3.ap-northeast-2.amazonaws.com/store/owner-photo.png\"]");
        store = storeRepository.save(store);

        ownerStoreLinkRepository.save(new OwnerStoreLink(
            owner,
            store,
            application,
            OwnerStoreMatchStatus.AUTO_MATCHED,
            100,
            "테스트 연결"
        ));

        assertThat(ownerApplicationService.listLinkedStores(owner.getId()))
            .singleElement()
            .satisfies(linked -> assertThat(linked.imageUrls())
                .containsExactly("/api/v1/files/view?key=store%2Fowner-photo.png"));
    }
}
