package com.toggle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;

import com.toggle.dto.owner.OwnerApplicationRequest;
import com.toggle.dto.owner.OwnerApplicationUpdateRequest;
import com.toggle.dto.owner.OwnerStoreProfileUpdateRequest;
import com.toggle.dto.owner.NationalTaxVerificationResult;
import com.toggle.dto.kakao.KakaoKeywordSearchResponse;
import com.toggle.global.exception.ApiException;
import com.toggle.entity.ExternalSource;
import com.toggle.entity.BusinessVerificationStatus;
import com.toggle.entity.OwnerApplication;
import com.toggle.entity.OwnerApplicationReviewStatus;
import com.toggle.entity.OwnerStoreLink;
import com.toggle.entity.OwnerStoreMatchStatus;
import com.toggle.entity.MapVerificationStatus;
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
import com.toggle.repository.UserMapLikeRepository;
import com.toggle.repository.UserMapRepository;
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
        ownerStoreLinkRepository.deleteAll();
        adminReviewLogRepository.deleteAll();
        businessVerificationHistoryRepository.deleteAll();
        mapVerificationHistoryRepository.deleteAll();
        ownerApplicationRepository.deleteAll();
        storeRepository.deleteAll();
        userMapLikeRepository.deleteAll();
        userMapRepository.deleteAll();
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
        assertThat(stored.getBusinessLicenseOriginalName()).isEqualTo("license.pdf");
        assertThat(stored.getBusinessLicenseOriginalFilename()).isEqualTo("license.pdf");
        assertThat(stored.getBusinessLicenseStoredPath()).isEqualTo("business/license.pdf");
        assertThat(stored.getBusinessLicenseObjectKey()).isEqualTo("business/license.pdf");
        assertThat(stored.getBusinessLicenseContentType()).isEqualTo("application/pdf");
        assertThat(stored.getBusinessLicenseSize()).isEqualTo((long) "pdf-bytes".getBytes(StandardCharsets.UTF_8).length);
        assertThat(stored.getBusinessLicenseUploadedAt()).isNotNull();
        assertThat(stored.getBusinessLicenseExpiresAt()).isEqualTo(stored.getBusinessLicenseUploadedAt().plusDays(7));
        assertThat(stored.getDeletedAt()).isNull();

        var detail = ownerApplicationService.getApplicationDetail(stored.getId());
        assertThat(detail.application().businessLicenseObjectKey()).isEqualTo("business/license.pdf");
        assertThat(detail.businessLicensePresignedUrl()).isEqualTo("https://presigned.example/business/license.pdf");
    }

    @Test
    void createApplicationShouldPersistImageBusinessLicenseMetadata() {
        User owner = userRepository.save(new User(
            "owner-image@toggle.com",
            passwordEncoder.encode("password123!"),
            "owner-image",
            UserRole.OWNER,
            UserStatus.ACTIVE
        ));

        given(s3FileService.uploadFile(org.mockito.ArgumentMatchers.any(MultipartFile.class), eq("business")))
            .willReturn(new S3FileService.StoredFile("https://sku-toggle.s3.ap-northeast-2.amazonaws.com/business/license.jpg", "business/license.jpg"));

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
            "license.jpg",
            "image/jpeg",
            "jpeg-bytes".getBytes(StandardCharsets.UTF_8)
        ));

        OwnerApplication stored = ownerApplicationRepository.findAllByUserIdOrderByCreatedAtDesc(owner.getId()).getFirst();
        assertThat(stored.getBusinessLicenseOriginalName()).isEqualTo("license.jpg");
        assertThat(stored.getBusinessLicenseOriginalFilename()).isEqualTo("license.jpg");
        assertThat(stored.getBusinessLicenseStoredPath()).isEqualTo("business/license.jpg");
        assertThat(stored.getBusinessLicenseObjectKey()).isEqualTo("business/license.jpg");
        assertThat(stored.getBusinessLicenseContentType()).isEqualTo("image/jpeg");
        assertThat(stored.getBusinessLicenseSize()).isEqualTo((long) "jpeg-bytes".getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    void createApplicationShouldRejectMissingBusinessLicenseContentType() {
        User owner = userRepository.save(new User(
            "owner-null-type@toggle.com",
            passwordEncoder.encode("password123!"),
            "owner-null-type",
            UserRole.OWNER,
            UserStatus.ACTIVE
        ));

        OwnerApplicationRequest request = new OwnerApplicationRequest(
            "owner-shop",
            "123-45-67890",
            "홍길동",
            LocalDate.of(2021, 3, 15),
            "서울특별시 강남구 테헤란로 123",
            "02-1234-5678"
        );

        assertThatThrownBy(() -> ownerApplicationService.createApplication(owner, request, new MockMultipartFile(
            "businessLicenseFile",
            "license.pdf",
            null,
            "pdf-bytes".getBytes(StandardCharsets.UTF_8)
        )))
            .isInstanceOf(ApiException.class)
            .extracting(throwable -> ((ApiException) throwable).getCode())
            .isEqualTo("INVALID_FILE_CONTENT_TYPE");
    }

    @Test
    void createApplicationShouldRejectMissingBusinessLicenseOriginalName() {
        User owner = userRepository.save(new User(
            "owner-no-name@toggle.com",
            passwordEncoder.encode("password123!"),
            "owner-no-name",
            UserRole.OWNER,
            UserStatus.ACTIVE
        ));

        OwnerApplicationRequest request = new OwnerApplicationRequest(
            "owner-shop",
            "123-45-67890",
            "홍길동",
            LocalDate.of(2021, 3, 15),
            "서울특별시 강남구 테헤란로 123",
            "02-1234-5678"
        );

        assertThatThrownBy(() -> ownerApplicationService.createApplication(owner, request, new MockMultipartFile(
            "businessLicenseFile",
            "",
            "application/pdf",
            "pdf-bytes".getBytes(StandardCharsets.UTF_8)
        )))
            .isInstanceOf(ApiException.class)
            .extracting(throwable -> ((ApiException) throwable).getCode())
            .isEqualTo("INVALID_FILE_NAME");
    }

    @Test
    void createApplicationShouldRejectUnsupportedBusinessLicenseContentType() {
        User owner = userRepository.save(new User(
            "owner-bad-type@toggle.com",
            passwordEncoder.encode("password123!"),
            "owner-bad-type",
            UserRole.OWNER,
            UserStatus.ACTIVE
        ));

        OwnerApplicationRequest request = new OwnerApplicationRequest(
            "owner-shop",
            "123-45-67890",
            "홍길동",
            LocalDate.of(2021, 3, 15),
            "서울특별시 강남구 테헤란로 123",
            "02-1234-5678"
        );

        assertThatThrownBy(() -> ownerApplicationService.createApplication(owner, request, new MockMultipartFile(
            "businessLicenseFile",
            "license.gif",
            "image/gif",
            "gif-bytes".getBytes(StandardCharsets.UTF_8)
        )))
            .isInstanceOf(ApiException.class)
            .extracting(throwable -> ((ApiException) throwable).getCode())
            .isEqualTo("INVALID_FILE_TYPE");
    }

    @Test
    void createApplicationShouldRejectEmptyBusinessLicenseFile() {
        User owner = userRepository.save(new User(
            "owner-empty@toggle.com",
            passwordEncoder.encode("password123!"),
            "owner-empty",
            UserRole.OWNER,
            UserStatus.ACTIVE
        ));

        OwnerApplicationRequest request = new OwnerApplicationRequest(
            "owner-shop",
            "123-45-67890",
            "홍길동",
            LocalDate.of(2021, 3, 15),
            "서울특별시 강남구 테헤란로 123",
            "02-1234-5678"
        );

        assertThatThrownBy(() -> ownerApplicationService.createApplication(owner, request, new MockMultipartFile(
            "businessLicenseFile",
            "license.pdf",
            "application/pdf",
            new byte[0]
        )))
            .isInstanceOf(ApiException.class)
            .extracting(throwable -> ((ApiException) throwable).getCode())
            .isEqualTo("FILE_REQUIRED");
    }

    @Test
    void createApplicationShouldRejectBlankS3KeyBeforeInsert() {
        User owner = userRepository.save(new User(
            "owner-blank-key@toggle.com",
            passwordEncoder.encode("password123!"),
            "owner-blank-key",
            UserRole.OWNER,
            UserStatus.ACTIVE
        ));

        given(s3FileService.uploadFile(org.mockito.ArgumentMatchers.any(MultipartFile.class), eq("business")))
            .willReturn(new S3FileService.StoredFile("https://sku-toggle.s3.ap-northeast-2.amazonaws.com/business/license.pdf", "   "));

        OwnerApplicationRequest request = new OwnerApplicationRequest(
            "owner-shop",
            "123-45-67890",
            "홍길동",
            LocalDate.of(2021, 3, 15),
            "서울특별시 강남구 테헤란로 123",
            "02-1234-5678"
        );

        assertThatThrownBy(() -> ownerApplicationService.createApplication(owner, request, new MockMultipartFile(
            "businessLicenseFile",
            "license.pdf",
            "application/pdf",
            "pdf-bytes".getBytes(StandardCharsets.UTF_8)
        )))
            .isInstanceOf(ApiException.class)
            .extracting(throwable -> ((ApiException) throwable).getCode())
            .isEqualTo("FILE_STORAGE_FAILED");
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
        assertThat(updated.getBusinessLicenseOriginalName()).isEqualTo("second.pdf");
        assertThat(updated.getBusinessLicenseOriginalFilename()).isEqualTo("second.pdf");
        assertThat(updated.getBusinessLicenseStoredPath()).isEqualTo("business/second.pdf");
        assertThat(updated.getBusinessLicenseObjectKey()).isEqualTo("business/second.pdf");
        assertThat(updated.getBusinessLicenseContentType()).isEqualTo("application/pdf");
        assertThat(updated.getBusinessLicenseSize()).isEqualTo((long) "second".getBytes(StandardCharsets.UTF_8).length);
        assertThat(updated.getBusinessLicenseUploadedAt()).isNotNull();
        assertThat(updated.getBusinessLicenseExpiresAt()).isEqualTo(updated.getBusinessLicenseUploadedAt().plusDays(7));
        verify(s3FileService).deleteFile("business/first.pdf");
    }

    @Test
    void createApplicationShouldNotBubbleUnexpectedBusinessVerificationRuntimeFailure() {
        User owner = userRepository.save(new User(
            "owner5@toggle.com",
            passwordEncoder.encode("password123!"),
            "owner5",
            UserRole.OWNER,
            UserStatus.ACTIVE
        ));

        given(s3FileService.uploadFile(org.mockito.ArgumentMatchers.any(), eq("business")))
            .willReturn(new S3FileService.StoredFile("https://sku-toggle.s3.ap-northeast-2.amazonaws.com/business/license.pdf", "business/license.pdf"));
        given(nationalTaxServiceClient.verifyBusiness(anyString(), anyString(), anyString()))
            .willThrow(new RuntimeException("national-tax-exploded"));

        OwnerApplicationRequest request = new OwnerApplicationRequest(
            "owner-shop",
            "123-45-67890",
            "홍길동",
            LocalDate.of(2021, 3, 15),
            "서울특별시 강남구 테헤란로 123",
            "02-1234-5678"
        );

        assertThatCode(() -> ownerApplicationService.createApplication(owner, request, new MockMultipartFile(
            "businessLicenseFile",
            "license.pdf",
            "application/pdf",
            "pdf-bytes".getBytes(StandardCharsets.UTF_8)
        ))).doesNotThrowAnyException();

        OwnerApplication stored = ownerApplicationRepository.findAllByUserIdOrderByCreatedAtDesc(owner.getId()).getFirst();
        assertThat(stored.getBusinessVerificationStatus()).isEqualTo(BusinessVerificationStatus.AUTO_VERIFICATION_FAILED);
        assertThat(stored.getMapVerificationStatus()).isEqualTo(MapVerificationStatus.FAILED);
    }

    @Test
    void createApplicationShouldNotBubbleUnexpectedMapVerificationRuntimeFailure() {
        User owner = userRepository.save(new User(
            "owner6@toggle.com",
            passwordEncoder.encode("password123!"),
            "owner6",
            UserRole.OWNER,
            UserStatus.ACTIVE
        ));

        given(s3FileService.uploadFile(org.mockito.ArgumentMatchers.any(), eq("business")))
            .willReturn(new S3FileService.StoredFile("https://sku-toggle.s3.ap-northeast-2.amazonaws.com/business/license.pdf", "business/license.pdf"));
        given(kakaoPlaceClient.searchByKeyword(anyString()))
            .willThrow(new RuntimeException("kakao-exploded"));

        OwnerApplicationRequest request = new OwnerApplicationRequest(
            "owner-shop",
            "123-45-67890",
            "홍길동",
            LocalDate.of(2021, 3, 15),
            "서울특별시 강남구 테헤란로 123",
            "02-1234-5678"
        );

        assertThatCode(() -> ownerApplicationService.createApplication(owner, request, new MockMultipartFile(
            "businessLicenseFile",
            "license.pdf",
            "application/pdf",
            "pdf-bytes".getBytes(StandardCharsets.UTF_8)
        ))).doesNotThrowAnyException();

        OwnerApplication stored = ownerApplicationRepository.findAllByUserIdOrderByCreatedAtDesc(owner.getId()).getFirst();
        assertThat(stored.getBusinessVerificationStatus()).isEqualTo(BusinessVerificationStatus.AUTO_VERIFIED);
        assertThat(stored.getMapVerificationStatus()).isEqualTo(MapVerificationStatus.FAILED);
    }

    @Test
    void createApplicationShouldQueryKakaoWithAddressOnly() {
        User owner = userRepository.save(new User(
            "owner-address-only@toggle.com",
            passwordEncoder.encode("password123!"),
            "owner-address-only",
            UserRole.OWNER,
            UserStatus.ACTIVE
        ));

        given(s3FileService.uploadFile(org.mockito.ArgumentMatchers.any(), eq("business")))
            .willReturn(new S3FileService.StoredFile("https://sku-toggle.s3.ap-northeast-2.amazonaws.com/business/license.pdf", "business/license.pdf"));
        given(kakaoPlaceClient.searchByKeyword("경기 안양시 만안구 안양로 96"))
            .willReturn(List.of());

        OwnerApplicationRequest request = new OwnerApplicationRequest(
            "bbq",
            "123-45-67890",
            "홍길동",
            LocalDate.of(2021, 3, 15),
            " 경기   안양시 만안구 안양로 96 ",
            "031-123-4567"
        );

        assertThatCode(() -> ownerApplicationService.createApplication(owner, request, new MockMultipartFile(
            "businessLicenseFile",
            "license.pdf",
            "application/pdf",
            "pdf-bytes".getBytes(StandardCharsets.UTF_8)
        ))).doesNotThrowAnyException();

        verify(kakaoPlaceClient).searchByKeyword("경기 안양시 만안구 안양로 96");
        verify(kakaoPlaceClient, never()).searchByKeyword("bbq 경기 안양시 만안구 안양로 96");
    }

    @Test
    void createApplicationShouldResolveAmbiguousAddressByStoreNameCaseInsensitive() {
        User owner = userRepository.save(new User(
            "owner-store-name-match@toggle.com",
            passwordEncoder.encode("password123!"),
            "owner-store-name-match",
            UserRole.OWNER,
            UserStatus.ACTIVE
        ));

        given(s3FileService.uploadFile(org.mockito.ArgumentMatchers.any(), eq("business")))
            .willReturn(new S3FileService.StoredFile("https://sku-toggle.s3.ap-northeast-2.amazonaws.com/business/license.pdf", "business/license.pdf"));
        given(kakaoPlaceClient.searchByKeyword("서울특별시 강남구 테헤란로 123"))
            .willReturn(List.of(
                new KakaoKeywordSearchResponse.KakaoPlaceDocument(
                    "place-1",
                    "BBQ CHICKEN GANGNAM",
                    "서울특별시 강남구 테헤란로 123",
                    "서울특별시 강남구 테헤란로 123",
                    "02-1111-1111",
                    "음식점 > 치킨",
                    new BigDecimal("127.0276100"),
                    new BigDecimal("37.4980950")
                ),
                new KakaoKeywordSearchResponse.KakaoPlaceDocument(
                    "place-2",
                    "OTHER STORE",
                    "서울특별시 강남구 테헤란로 123",
                    "서울특별시 강남구 테헤란로 123",
                    "02-2222-2222",
                    "음식점 > 한식",
                    new BigDecimal("127.0276100"),
                    new BigDecimal("37.4980950")
                )
            ));

        OwnerApplicationRequest request = new OwnerApplicationRequest(
            "bbq chicken gangnam",
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
        assertThat(stored.getMapVerificationStatus()).isEqualTo(MapVerificationStatus.VERIFIED);
        var detail = ownerApplicationService.getApplicationDetail(stored.getId());
        assertThat(detail.application().verifiedStoreId()).isNotNull();
        assertThat(detail.application().verifiedStoreName()).isEqualTo("BBQ CHICKEN GANGNAM");
    }

    @Test
    void createApplicationShouldMarkAddressSearchFailureWhenKakaoBadRequestOccurs() {
        User owner = userRepository.save(new User(
            "owner-address-fail@toggle.com",
            passwordEncoder.encode("password123!"),
            "owner-address-fail",
            UserRole.OWNER,
            UserStatus.ACTIVE
        ));

        given(s3FileService.uploadFile(org.mockito.ArgumentMatchers.any(), eq("business")))
            .willReturn(new S3FileService.StoredFile("https://sku-toggle.s3.ap-northeast-2.amazonaws.com/business/license.pdf", "business/license.pdf"));
        given(kakaoPlaceClient.searchByKeyword("경기 안양시 만안구 안양로 96"))
            .willThrow(new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "KAKAO_LOCAL_BAD_REQUEST", "잘못된 요청"));

        OwnerApplicationRequest request = new OwnerApplicationRequest(
            "bbq",
            "123-45-67890",
            "홍길동",
            LocalDate.of(2021, 3, 15),
            "경기 안양시 만안구 안양로 96",
            "031-123-4567"
        );

        assertThatCode(() -> ownerApplicationService.createApplication(owner, request, new MockMultipartFile(
            "businessLicenseFile",
            "license.pdf",
            "application/pdf",
            "pdf-bytes".getBytes(StandardCharsets.UTF_8)
        ))).doesNotThrowAnyException();

        OwnerApplication stored = ownerApplicationRepository.findAllByUserIdOrderByCreatedAtDesc(owner.getId()).getFirst();
        assertThat(stored.getMapVerificationStatus()).isEqualTo(MapVerificationStatus.FAILED);
        assertThat(mapVerificationHistoryRepository.findAllByRequestIdOrderByCreatedAtDesc(stored.getId()).getFirst().getFailureCode())
            .isEqualTo("KAKAO_ADDRESS_SEARCH_FAILED");
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

    @Test
    void updateOwnerStoreProfileShouldCleanupRemovedBackendOwnedImagesAfterCommit() {
        User owner = userRepository.save(new User(
            "owner4@toggle.com",
            passwordEncoder.encode("password123!"),
            "owner4",
            UserRole.OWNER,
            UserStatus.ACTIVE
        ));

        Store store = new Store(
            ExternalSource.KAKAO,
            "store-owner-2",
            "연결 매장 2",
            "02-111-2222",
            "서울특별시 강남구 테헤란로 123",
            "서울특별시 강남구 테헤란로 123",
            new BigDecimal("37.1234567"),
            new BigDecimal("127.1234567")
        );
        ReflectionTestUtils.setField(store, "ownerImageUrlsJson", "[\"https://sku-toggle.s3.ap-northeast-2.amazonaws.com/store/keep.png\",\"https://sku-toggle.s3.ap-northeast-2.amazonaws.com/store/remove.png\"]");
        store = storeRepository.save(store);

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

        ownerStoreLinkRepository.save(new OwnerStoreLink(
            owner,
            store,
            application,
            OwnerStoreMatchStatus.AUTO_MATCHED,
            100,
            "테스트 연결"
        ));

        OwnerStoreProfileUpdateRequest request = new OwnerStoreProfileUpdateRequest(
            "공지",
            "09:00",
            "18:00",
            null,
            null,
            List.of("https://sku-toggle.s3.ap-northeast-2.amazonaws.com/store/keep.png")
        );

        ownerApplicationService.updateOwnerStoreProfile(owner, store.getId(), request);

        verify(s3FileService, never()).deleteFile("store/remove.png");
        verify(s3FileService).deleteFilesAfterCommit(List.of("store/remove.png"));
    }
}
