package com.toggle;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toggle.dto.auth.LoginRequest;
import com.toggle.dto.auth.RefreshTokenRequest;
import com.toggle.dto.auth.SignupRequest;
import com.toggle.dto.kakao.KakaoKeywordSearchResponse;
import com.toggle.dto.owner.ManualBusinessVerificationRequest;
import com.toggle.dto.owner.NationalTaxVerificationResult;
import com.toggle.dto.owner.OwnerApplicationApproveRequest;
import com.toggle.dto.owner.OwnerApplicationRequest;
import com.toggle.dto.owner.OwnerApplicationReviewRequest;
import com.toggle.dto.store.ResolveStoreRequest;
import com.toggle.entity.BusinessStatus;
import com.toggle.global.exception.ApiException;
import com.toggle.entity.ExternalSource;
import com.toggle.entity.LiveStatusSource;
import com.toggle.entity.User;
import com.toggle.entity.UserRole;
import com.toggle.entity.UserStatus;
import com.toggle.repository.AdminReviewLogRepository;
import com.toggle.repository.BusinessVerificationHistoryRepository;
import com.toggle.repository.FavoriteRepository;
import com.toggle.repository.MapVerificationHistoryRepository;
import com.toggle.repository.OwnerApplicationRepository;
import com.toggle.repository.OwnerStoreLinkRepository;
import com.toggle.repository.StoreRepository;
import com.toggle.repository.UserRepository;
import com.toggle.service.KakaoPlaceClient;
import com.toggle.service.NationalTaxServiceClient;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ToggleBackendApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FavoriteRepository favoriteRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private OwnerApplicationRepository ownerApplicationRepository;

    @Autowired
    private OwnerStoreLinkRepository ownerStoreLinkRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private BusinessVerificationHistoryRepository businessVerificationHistoryRepository;

    @Autowired
    private MapVerificationHistoryRepository mapVerificationHistoryRepository;

    @Autowired
    private AdminReviewLogRepository adminReviewLogRepository;

    @MockBean
    private KakaoPlaceClient kakaoPlaceClient;

    @MockBean
    private NationalTaxServiceClient nationalTaxServiceClient;

    @BeforeEach
    void setUp() {
        favoriteRepository.deleteAll();
        adminReviewLogRepository.deleteAll();
        businessVerificationHistoryRepository.deleteAll();
        mapVerificationHistoryRepository.deleteAll();
        ownerStoreLinkRepository.deleteAll();
        ownerApplicationRepository.deleteAll();
        storeRepository.deleteAll();
        userRepository.deleteAll();
        given(kakaoPlaceClient.searchByKeyword(anyString())).willReturn(java.util.List.of());
        given(nationalTaxServiceClient.verifyBusiness(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
            .willReturn(new NationalTaxVerificationResult(false, "{}", "{}", null, null, null, null, "NTS_VERIFICATION_FAILED", "국세청 진위확인 결과가 일치하지 않습니다."));
    }

    @Test
    void healthEndpointShouldBePublic() throws Exception {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("ok"));
    }

    @Test
    void signupLoginAndMeFlowShouldWork() throws Exception {
        SignupRequest signupRequest = new SignupRequest("tester@toggle.com", "password123!", "tester", UserRole.USER);

        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.email").value("tester@toggle.com"))
            .andExpect(jsonPath("$.data.role").value("USER"));

        LoginRequest loginRequest = new LoginRequest("tester@toggle.com", "password123!");

        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String accessToken = objectMapper.readTree(loginResponse).path("data").path("accessToken").asText();
        String refreshToken = objectMapper.readTree(loginResponse).path("data").path("refreshToken").asText();

        mockMvc.perform(get("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.email").value("tester@toggle.com"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshTokenRequest(refreshToken))))
            .andExpect(status().isOk());
    }

    @Test
    void favoriteEndpointsShouldRequireAuthenticationAndWorkWithJwt() throws Exception {
        String accessToken = signupAndLoginMember("tester@toggle.com");
        Long storeId = createStore();

        mockMvc.perform(get("/api/v1/favorites/stores"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/favorites/stores/{storeId}", storeId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.favorited").value(true));
    }

    @Test
    void seoulOwnerApplicationShouldRunAutomaticBusinessAndMapVerification() throws Exception {
        String ownerToken = signupAndLoginOwner("owner@toggle.com");
        mockAutomaticBusinessVerificationSuccess();
        mockMatchingPlace("1234567890", "서울특별시 강남구 테헤란로 123");

        mockMvc.perform(multipart("/api/v1/owner/store-registration-requests")
                .file(ownerApplicationRequestPart("서울특별시 강남구 테헤란로 123"))
                .file(ownerLicenseFile())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.requestStatus").value("UNDER_REVIEW"))
            .andExpect(jsonPath("$.data.businessVerificationStatus").value("AUTO_VERIFIED"))
            .andExpect(jsonPath("$.data.mapVerificationStatus").value("VERIFIED"));

        mockMvc.perform(get("/api/v1/admin/store-registration-requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + createAdminAndLogin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].storeName").value("owner-shop"))
            .andExpect(jsonPath("$.data[0].verifiedStoreId").isNumber());
    }

    @Test
    void nonSeoulOwnerApplicationShouldRunAutomaticBusinessVerificationToo() throws Exception {
        String ownerToken = signupAndLoginOwner("owner@toggle.com");
        mockAutomaticBusinessVerificationSuccessFor("1234567890", "홍길동", "20210315", "부산광역시 해운대구 우동 123");
        mockMatchingPlace("5555555555", "부산광역시 해운대구 우동 123");

        mockMvc.perform(multipart("/api/v1/owner/store-registration-requests")
                .file(ownerApplicationRequestPart("부산광역시 해운대구 우동 123"))
                .file(ownerLicenseFile())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.businessVerificationStatus").value("AUTO_VERIFIED"))
            .andExpect(jsonPath("$.data.mapVerificationStatus").value("VERIFIED"));
    }

    @Test
    void adminShouldManuallyVerifyNonSeoulApplicationAndApprove() throws Exception {
        String ownerToken = signupAndLoginOwner("owner@toggle.com");
        given(nationalTaxServiceClient.verifyBusiness(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
            .willThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "NATIONAL_TAX_API_ERROR", "국세청 API 호출에 실패했습니다."));
        mockMatchingPlace("5555555555", "부산광역시 해운대구 우동 123");
        Long applicationId = createOwnerApplication(ownerToken, "부산광역시 해운대구 우동 123");
        String adminToken = createAdminAndLogin();

        mockMvc.perform(post("/api/v1/admin/store-registration-requests/{applicationId}/business-verifications/manual", applicationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ManualBusinessVerificationRequest(true, "사업자등록정보 수동 확인 완료"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.businessVerificationStatus").value("MANUAL_VERIFIED"));

        String response = mockMvc.perform(post("/api/v1/admin/store-registration-requests/{applicationId}/approve", applicationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new OwnerApplicationApproveRequest(true))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.requestStatus").value("APPROVED"))
            .andExpect(jsonPath("$.data.mapVerificationStatus").value("VERIFIED"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        long storeId = objectMapper.readTree(response).path("data").path("linkedStoreId").asLong();

        mockMvc.perform(get("/api/v1/owner/stores")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].storeId").value(storeId));
    }

    @Test
    void adminShouldNotApproveWhenMapVerificationFailed() throws Exception {
        String ownerToken = signupAndLoginOwner("owner@toggle.com");
        mockAutomaticBusinessVerificationSuccess();
        Long applicationId = createOwnerApplication(ownerToken, "서울특별시 강남구 테헤란로 123");
        String adminToken = createAdminAndLogin();

        mockMvc.perform(post("/api/v1/admin/store-registration-requests/{applicationId}/approve", applicationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new OwnerApplicationApproveRequest(true))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("STORE_REGISTRATION_NOT_APPROVABLE"));
    }

    @Test
    void seoulAddressNormalizationShouldAllowMapVerificationWithShortRoadAddress() throws Exception {
        String ownerToken = signupAndLoginOwner("owner@toggle.com");
        mockAutomaticBusinessVerificationSuccess();
        mockMatchingPlace("1234567890", "서울 강남구 테헤란로 123");

        mockMvc.perform(multipart("/api/v1/owner/store-registration-requests")
                .file(ownerApplicationRequestPart("서울특별시 강남구 테헤란로 123"))
                .file(ownerLicenseFile())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mapVerificationStatus").value("VERIFIED"));
    }

    @Test
    void mapVerificationShouldFallbackToAddressSearchWhenNameQueryDoesNotHaveExactAddressMatch() throws Exception {
        String ownerToken = signupAndLoginOwner("owner@toggle.com");
        mockAutomaticBusinessVerificationSuccess();
        mockNameQueryFallbackScenario();

        mockMvc.perform(multipart("/api/v1/owner/store-registration-requests")
                .file(ownerApplicationRequestPart("서울특별시 강남구 선릉로 551"))
                .file(ownerLicenseFile())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mapVerificationStatus").value("VERIFIED"));
    }

    @Test
    void exactAddressMatchesShouldDeduplicateSamePlaceIdAcrossQueriesAndStillVerify() throws Exception {
        String ownerToken = signupAndLoginOwner("owner@toggle.com");
        mockAutomaticBusinessVerificationSuccess();
        KakaoKeywordSearchResponse.KakaoPlaceDocument weak = new KakaoKeywordSearchResponse.KakaoPlaceDocument(
            "same-place",
            "다른상호",
            "서울특별시 강남구 역삼로 999",
            "서울특별시 강남구 역삼로 999",
            "02-0000-0000",
            "음식점 > 한식",
            new BigDecimal("127.0276100"),
            new BigDecimal("37.4980950")
        );
        KakaoKeywordSearchResponse.KakaoPlaceDocument strong = new KakaoKeywordSearchResponse.KakaoPlaceDocument(
            "same-place",
            "owner-shop",
            "서울특별시 강남구 테헤란로 123",
            "서울특별시 강남구 테헤란로 123",
            "02-1234-5678",
            "음식점 > 한식",
            new BigDecimal("127.0276100"),
            new BigDecimal("37.4980950")
        );
        given(kakaoPlaceClient.searchByKeyword("owner-shop 서울특별시 강남구 테헤란로 123"))
            .willReturn(java.util.List.of(weak));
        given(kakaoPlaceClient.searchByKeyword("서울특별시 강남구 테헤란로 123"))
            .willReturn(java.util.List.of(strong));

        mockMvc.perform(multipart("/api/v1/owner/store-registration-requests")
                .file(ownerApplicationRequestPart("서울특별시 강남구 테헤란로 123"))
                .file(ownerLicenseFile())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mapVerificationStatus").value("VERIFIED"));
    }

    @Test
    void mapVerificationShouldFailWhenMultipleExactAddressMatchesRemain() throws Exception {
        String ownerToken = signupAndLoginOwner("owner@toggle.com");
        mockAutomaticBusinessVerificationSuccess();
        KakaoKeywordSearchResponse.KakaoPlaceDocument first = new KakaoKeywordSearchResponse.KakaoPlaceDocument(
            "place-1",
            "owner-shop-a",
            "서울특별시 강남구 테헤란로 123",
            "서울특별시 강남구 테헤란로 123",
            "",
            "음식점 > 한식",
            new BigDecimal("127.0276100"),
            new BigDecimal("37.4980950")
        );
        KakaoKeywordSearchResponse.KakaoPlaceDocument second = new KakaoKeywordSearchResponse.KakaoPlaceDocument(
            "place-2",
            "owner-shop-b",
            "서울특별시 강남구 테헤란로 123",
            "서울특별시 강남구 테헤란로 123",
            "",
            "음식점 > 한식",
            new BigDecimal("127.0276100"),
            new BigDecimal("37.4980950")
        );
        given(kakaoPlaceClient.searchByKeyword("owner-shop 서울특별시 강남구 테헤란로 123"))
            .willReturn(java.util.List.of(first, second));

        String response = mockMvc.perform(multipart("/api/v1/owner/store-registration-requests")
                .file(ownerApplicationRequestPart("서울특별시 강남구 테헤란로 123"))
                .file(ownerLicenseFile())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mapVerificationStatus").value("FAILED"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        Long applicationId = objectMapper.readTree(response).path("data").path("applicationId").asLong();
        String adminToken = createAdminAndLogin();

        mockMvc.perform(get("/api/v1/admin/store-registration-requests/{applicationId}", applicationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mapVerificationHistories[0].failureMessage").value("실영업주소가 정확히 일치하는 카카오 매장이 여러 개입니다. 상호명까지 확인해도 자동 확정할 수 없습니다."));
    }

    @Test
    void mapVerificationShouldResolveUniqueNameMatchAmongExactAddressResults() throws Exception {
        String ownerToken = signupAndLoginOwner("owner@toggle.com");
        mockAutomaticBusinessVerificationSuccess();
        KakaoKeywordSearchResponse.KakaoPlaceDocument other = new KakaoKeywordSearchResponse.KakaoPlaceDocument(
            "place-1",
            "골프존파크 안양명학역점",
            "경기 안양시 만안구 만안로 35",
            "경기 안양시 만안구 만안로 35",
            "031-111-1111",
            "스포츠,오락",
            new BigDecimal("126.9281000"),
            new BigDecimal("37.3833000")
        );
        KakaoKeywordSearchResponse.KakaoPlaceDocument target = new KakaoKeywordSearchResponse.KakaoPlaceDocument(
            "place-2",
            "하삼동커피 안양명학역점",
            "경기 안양시 만안구 만안로 35",
            "경기 안양시 만안구 만안로 35",
            "031-222-2222",
            "카페",
            new BigDecimal("126.9281000"),
            new BigDecimal("37.3833000")
        );
        given(kakaoPlaceClient.searchByKeyword("하삼동 커피 경기도 안양시 만안구 만안로 35"))
            .willReturn(java.util.List.of(other, target));

        mockMvc.perform(multipart("/api/v1/owner/store-registration-requests")
                .file(ownerApplicationRequestPart("경기도 안양시 만안구 만안로 35", "하삼동 커피"))
                .file(ownerLicenseFile())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mapVerificationStatus").value("VERIFIED"));
    }

    @Test
    void duplicateActiveApplicationShouldBeRejected() throws Exception {
        String ownerToken = signupAndLoginOwner("owner@toggle.com");
        mockAutomaticBusinessVerificationSuccess();
        mockMatchingPlace("1234567890", "경기도 안양시 만안구 만안로 35");

        mockMvc.perform(multipart("/api/v1/owner/store-registration-requests")
                .file(ownerApplicationRequestPart("경기도 안양시 만안구 만안로 35", "하삼동 커피"))
                .file(ownerLicenseFile())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
            .andExpect(status().isOk());

        mockMvc.perform(multipart("/api/v1/owner/store-registration-requests")
                .file(ownerApplicationRequestPart("경기도 안양시 만안구 만안로 35", "하삼동 커피"))
                .file(ownerLicenseFile())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("OWNER_APPLICATION_DUPLICATED"));
    }

    @Test
    void mapVerificationShouldFailEvenWhenOnlyOneExactAddressCandidateMatchesPhone() throws Exception {
        String ownerToken = signupAndLoginOwner("owner@toggle.com");
        mockAutomaticBusinessVerificationSuccess();
        KakaoKeywordSearchResponse.KakaoPlaceDocument phoneMatched = new KakaoKeywordSearchResponse.KakaoPlaceDocument(
            "place-1",
            "owner-shop-a",
            "서울특별시 강남구 테헤란로 123",
            "서울특별시 강남구 테헤란로 123",
            "02-1234-5678",
            "음식점 > 한식",
            new BigDecimal("127.0276100"),
            new BigDecimal("37.4980950")
        );
        KakaoKeywordSearchResponse.KakaoPlaceDocument other = new KakaoKeywordSearchResponse.KakaoPlaceDocument(
            "place-2",
            "owner-shop-b",
            "서울특별시 강남구 테헤란로 123",
            "서울특별시 강남구 테헤란로 123",
            "02-9999-0000",
            "음식점 > 한식",
            new BigDecimal("127.0276100"),
            new BigDecimal("37.4980950")
        );
        given(kakaoPlaceClient.searchByKeyword("owner-shop 서울특별시 강남구 테헤란로 123"))
            .willReturn(java.util.List.of(phoneMatched, other));

        mockMvc.perform(multipart("/api/v1/owner/store-registration-requests")
                .file(ownerApplicationRequestPart("서울특별시 강남구 테헤란로 123"))
                .file(ownerLicenseFile())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mapVerificationStatus").value("FAILED"));
    }

    @Test
    void jibunAddressExactMatchShouldVerifyEvenWhenRoadAddressDiffers() throws Exception {
        String ownerToken = signupAndLoginOwner("owner@toggle.com");
        mockAutomaticBusinessVerificationSuccess();
        KakaoKeywordSearchResponse.KakaoPlaceDocument place = new KakaoKeywordSearchResponse.KakaoPlaceDocument(
            "place-jibun",
            "owner-shop",
            "서울특별시 강남구 테헤란로 123",
            "서울특별시 강남구 역삼동 123-45",
            "02-1234-5678",
            "음식점 > 한식",
            new BigDecimal("127.0276100"),
            new BigDecimal("37.4980950")
        );
        given(kakaoPlaceClient.searchByKeyword("owner-shop 서울특별시 강남구 역삼동 123-45"))
            .willReturn(java.util.List.of(place));

        mockMvc.perform(multipart("/api/v1/owner/store-registration-requests")
                .file(ownerApplicationRequestPart("서울특별시 강남구 역삼동 123-45"))
                .file(ownerLicenseFile())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mapVerificationStatus").value("VERIFIED"));
    }

    @Test
    void mapVerificationFailureShouldExposeFailureMessageInDetailResponse() throws Exception {
        String ownerToken = signupAndLoginOwner("owner@toggle.com");
        mockAutomaticBusinessVerificationSuccess();
        Long applicationId = createOwnerApplication(ownerToken, "서울특별시 강남구 테헤란로 123");
        String adminToken = createAdminAndLogin();

        mockMvc.perform(get("/api/v1/admin/store-registration-requests/{applicationId}", applicationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mapVerificationHistories[0].failureMessage").value("카카오맵에서 실영업주소가 정확히 일치하는 매장을 찾지 못했습니다."));
    }

    @Test
    void nationalTaxConfigurationErrorShouldBeRetryableNotBusinessMismatch() throws Exception {
        String ownerToken = signupAndLoginOwner("owner@toggle.com");
        reset(nationalTaxServiceClient);
        given(nationalTaxServiceClient.verifyBusiness(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
            .willThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "NATIONAL_TAX_NOT_CONFIGURED", "국세청 API 설정이 필요합니다."));
        mockMatchingPlace("1234567890", "서울 강남구 테헤란로 123");

        mockMvc.perform(multipart("/api/v1/owner/store-registration-requests")
                .file(ownerApplicationRequestPart("서울특별시 강남구 테헤란로 123"))
                .file(ownerLicenseFile())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.businessVerificationStatus").value("AUTO_VERIFICATION_UNAVAILABLE"));
    }

    @Test
    void mapVerificationShouldHonorForceRefreshFalse() throws Exception {
        String ownerToken = signupAndLoginOwner("owner@toggle.com");
        mockAutomaticBusinessVerificationSuccess();
        mockMatchingPlace("1234567890", "서울 강남구 테헤란로 123");
        Long applicationId = createOwnerApplication(ownerToken, "서울특별시 강남구 테헤란로 123");
        String adminToken = createAdminAndLogin();

        reset(kakaoPlaceClient);

        mockMvc.perform(post("/api/v1/admin/store-registration-requests/{applicationId}/map-verifications/execute", applicationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"forceRefresh\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mapVerificationStatus").value("VERIFIED"));

        verify(kakaoPlaceClient, never()).searchByKeyword(anyString());
    }

    @Test
    void legacyAdminVerificationEndpointsShouldStillWork() throws Exception {
        String ownerToken = signupAndLoginOwner("owner@toggle.com");
        mockMatchingPlace("5555555555", "부산광역시 해운대구 우동 123");
        Long applicationId = createOwnerApplication(ownerToken, "부산광역시 해운대구 우동 123");
        String adminToken = createAdminAndLogin();

        mockMvc.perform(post("/api/v1/admin/owner-store-applications/{applicationId}/business-verifications/manual", applicationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ManualBusinessVerificationRequest(true, "레거시 경로 확인"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.businessVerificationStatus").value("MANUAL_VERIFIED"));
    }

    @Test
    void approvedApplicationShouldNotAllowBusinessVerificationRerun() throws Exception {
        String ownerToken = signupAndLoginOwner("owner@toggle.com");
        mockMatchingPlace("5555555555", "부산광역시 해운대구 우동 123");
        Long applicationId = createOwnerApplication(ownerToken, "부산광역시 해운대구 우동 123");
        String adminToken = createAdminAndLogin();

        mockMvc.perform(post("/api/v1/admin/store-registration-requests/{applicationId}/business-verifications/manual", applicationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ManualBusinessVerificationRequest(true, "확인 완료"))))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/store-registration-requests/{applicationId}/approve", applicationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new OwnerApplicationApproveRequest(true))))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/store-registration-requests/{applicationId}/map-verifications/execute", applicationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"forceRefresh\":true}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("OWNER_APPLICATION_ALREADY_REVIEWED"));
    }

    @Test
    void rejectedApplicationShouldNotAllowManualVerification() throws Exception {
        String ownerToken = signupAndLoginOwner("owner@toggle.com");
        mockMatchingPlace("5555555555", "부산광역시 해운대구 우동 123");
        Long applicationId = createOwnerApplication(ownerToken, "부산광역시 해운대구 우동 123");
        String adminToken = createAdminAndLogin();

        mockMvc.perform(post("/api/v1/admin/store-registration-requests/{applicationId}/reject", applicationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new OwnerApplicationReviewRequest("반려"))))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/store-registration-requests/{applicationId}/business-verifications/manual", applicationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ManualBusinessVerificationRequest(true, "다시 확인"))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("OWNER_APPLICATION_ALREADY_REVIEWED"));
    }

    @Test
    void rejectedApplicationShouldNotAllowBusinessVerificationRerun() throws Exception {
        String ownerToken = signupAndLoginOwner("owner@toggle.com");
        mockAutomaticBusinessVerificationSuccess();
        mockMatchingPlace("1234567890", "서울특별시 강남구 테헤란로 123");
        Long applicationId = createOwnerApplication(ownerToken, "서울특별시 강남구 테헤란로 123");
        String adminToken = createAdminAndLogin();

        mockMvc.perform(post("/api/v1/admin/store-registration-requests/{applicationId}/reject", applicationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new OwnerApplicationReviewRequest("반려"))))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/store-registration-requests/{applicationId}/business-verifications/execute", applicationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("OWNER_APPLICATION_ALREADY_REVIEWED"));
    }

    @Test
    void adminVerificationRerunsShouldBeRecordedInReviewLog() throws Exception {
        String ownerToken = signupAndLoginOwner("owner@toggle.com");
        mockAutomaticBusinessVerificationSuccess();
        mockMatchingPlace("1234567890", "서울 강남구 테헤란로 123");
        Long applicationId = createOwnerApplication(ownerToken, "서울특별시 강남구 테헤란로 123");
        String adminToken = createAdminAndLogin();

        long before = adminReviewLogRepository.count();

        mockMvc.perform(post("/api/v1/admin/store-registration-requests/{applicationId}/business-verifications/execute", applicationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/store-registration-requests/{applicationId}/map-verifications/execute", applicationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"forceRefresh\":true}"))
            .andExpect(status().isOk());

        Assertions.assertThat(adminReviewLogRepository.count()).isEqualTo(before + 2);
    }

    @Test
    void ownerUpdateShouldResetVerificationAndRerunChecks() throws Exception {
        String ownerToken = signupAndLoginOwner("owner@toggle.com");
        mockAutomaticBusinessVerificationSuccess();
        mockMatchingPlace("1234567890", "서울특별시 강남구 테헤란로 123");
        Long applicationId = createOwnerApplication(ownerToken, "서울특별시 강남구 테헤란로 123");
        mockMatchingPlace("9999999999", "서울특별시 강남구 선릉로 551");

        mockMvc.perform(multipart("/api/v1/owner/store-registration-requests/{applicationId}", applicationId)
                .file(ownerApplicationUpdatePart("서울특별시 강남구 선릉로 551"))
                .with(request -> {
                    request.setMethod("PATCH");
                    return request;
                })
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.businessVerificationStatus").value("AUTO_VERIFIED"))
            .andExpect(jsonPath("$.data.mapVerificationStatus").value("VERIFIED"));
    }

    @Test
    void nonAdminShouldNotAccessAdminEndpoints() throws Exception {
        String ownerToken = signupAndLoginOwner("owner@toggle.com");
        Long applicationId = createOwnerApplication(ownerToken, "부산광역시 해운대구 우동 123");
        String userToken = signupAndLoginMember("tester@toggle.com");

        mockMvc.perform(get("/api/v1/admin/store-registration-requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/store-registration-requests/{applicationId}/approve", applicationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new OwnerApplicationApproveRequest(true))))
            .andExpect(status().isForbidden());
    }

    @Test
    void linkedOwnerShouldUpdateStoreStatus() throws Exception {
        String ownerToken = signupAndLoginOwner("owner@toggle.com");
        mockMatchingPlace("5555555555", "부산광역시 해운대구 우동 123");
        Long applicationId = createOwnerApplication(ownerToken, "부산광역시 해운대구 우동 123");
        String adminToken = createAdminAndLogin();

        mockMvc.perform(post("/api/v1/admin/store-registration-requests/{applicationId}/business-verifications/manual", applicationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ManualBusinessVerificationRequest(true, "확인 완료"))))
            .andExpect(status().isOk());

        String response = mockMvc.perform(post("/api/v1/admin/store-registration-requests/{applicationId}/approve", applicationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new OwnerApplicationApproveRequest(true))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        long storeId = objectMapper.readTree(response).path("data").path("linkedStoreId").asLong();

        mockMvc.perform(post("/api/v1/owner/stores/{storeId}/status", storeId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"BREAK_TIME\",\"comment\":\"브레이크타임입니다.\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.liveBusinessStatus").value("BREAK_TIME"));

        Assertions.assertThat(storeRepository.findById(storeId).orElseThrow().getLiveBusinessStatus().name())
            .isEqualTo("BREAK_TIME");
    }

    @Test
    void resolveShouldNormalizeExternalPlaceIdAndReuseStore() throws Exception {
        ResolveStoreRequest firstRequest = new ResolveStoreRequest(
            " kakao ",
            " 1234567890 ",
            "맛있는 덮밥집",
            "서울시 강남구 테헤란로 123",
            "02-1234-5678",
            new BigDecimal("37.4980950"),
            new BigDecimal("127.0276100")
        );

        ResolveStoreRequest secondRequest = new ResolveStoreRequest(
            "KAKAO",
            "1234567890",
            "맛있는 덮밥집 리뉴얼",
            "서울시 강남구 테헤란로 123",
            "02-9999-0000",
            new BigDecimal("37.4980950"),
            new BigDecimal("127.0276100")
        );

        String firstResponse = mockMvc.perform(post("/api/v1/stores/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(firstRequest)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        String secondResponse = mockMvc.perform(post("/api/v1/stores/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(secondRequest)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        long firstStoreId = objectMapper.readTree(firstResponse).path("data").path("storeId").asLong();
        long secondStoreId = objectMapper.readTree(secondResponse).path("data").path("storeId").asLong();

        Assertions.assertThat(secondStoreId).isEqualTo(firstStoreId);
        Assertions.assertThat(storeRepository.count()).isEqualTo(1);
    }

    @Test
    void lookupStoresShouldReturnLiveStatusForMatchedExternalPlaceIds() throws Exception {
        Long storeId = createStore();
        com.toggle.entity.Store store = storeRepository.findById(storeId).orElseThrow();
        store.markVerified(
            "서울시 강남구 테헤란로 123",
            "서울시 강남구 테헤란로 123",
            "음식점",
            "{}",
            java.time.LocalDateTime.now()
        );
        store.updateLiveBusinessStatus(BusinessStatus.OPEN, LiveStatusSource.OWNER_POS);
        storeRepository.save(store);

        mockMvc.perform(post("/api/v1/stores/lookup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "externalSource": "KAKAO",
                      "externalPlaceIds": ["1234567890", "9999999999"]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.stores[0].storeId").value(storeId))
            .andExpect(jsonPath("$.data.stores[0].externalPlaceId").value("1234567890"))
            .andExpect(jsonPath("$.data.stores[0].liveBusinessStatus").value("OPEN"))
            .andExpect(jsonPath("$.data.stores[0].liveStatusSource").value("OWNER_POS"));
    }

    @Test
    void lookupStoresShouldExcludeUnverifiedStores() throws Exception {
        createStore();

        mockMvc.perform(post("/api/v1/stores/lookup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "externalSource": "KAKAO",
                      "externalPlaceIds": ["1234567890"]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.stores").isArray())
            .andExpect(jsonPath("$.data.stores").isEmpty());
    }

    @Test
    void ownerStoreProfileShouldPersistAndBeVisibleInOwnerAndLookupResponses() throws Exception {
        String ownerToken = signupAndLoginOwner("owner@toggle.com");
        mockAutomaticBusinessVerificationSuccess();
        mockMatchingPlace("1234567890", "서울특별시 강남구 테헤란로 123");
        Long applicationId = createOwnerApplication(ownerToken, "서울특별시 강남구 테헤란로 123");
        String adminToken = createAdminAndLogin();

        String approvalResponse = mockMvc.perform(post("/api/v1/admin/store-registration-requests/{applicationId}/approve", applicationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new OwnerApplicationApproveRequest(true))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        long storeId = objectMapper.readTree(approvalResponse).path("data").path("linkedStoreId").asLong();

        mockMvc.perform(put("/api/v1/owner/stores/{storeId}/profile", storeId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "ownerNotice": "재료 소진 시 조기 마감될 수 있어요.",
                      "openTime": "10:00",
                      "closeTime": "22:00",
                      "breakStart": "15:00",
                      "breakEnd": "16:00",
                      "imageUrls": [
                        "data:image/png;base64,abc123",
                        "https://images.example.com/store-1.jpg"
                      ]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.storeId").value(storeId))
            .andExpect(jsonPath("$.data.ownerNotice").value("재료 소진 시 조기 마감될 수 있어요."))
            .andExpect(jsonPath("$.data.openTime").value("10:00"))
            .andExpect(jsonPath("$.data.imageUrls[0]").value("data:image/png;base64,abc123"));

        mockMvc.perform(get("/api/v1/owner/stores")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].storeId").value(storeId))
            .andExpect(jsonPath("$.data[0].ownerNotice").value("재료 소진 시 조기 마감될 수 있어요."))
            .andExpect(jsonPath("$.data[0].closeTime").value("22:00"))
            .andExpect(jsonPath("$.data[0].imageUrls[1]").value("https://images.example.com/store-1.jpg"));

        mockMvc.perform(post("/api/v1/stores/lookup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "externalSource": "KAKAO",
                      "externalPlaceIds": ["1234567890"]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.stores[0].ownerNotice").value("재료 소진 시 조기 마감될 수 있어요."))
            .andExpect(jsonPath("$.data.stores[0].openTime").value("10:00"))
            .andExpect(jsonPath("$.data.stores[0].breakEnd").value("16:00"))
            .andExpect(jsonPath("$.data.stores[0].imageUrls[0]").value("data:image/png;base64,abc123"));
    }

    @Test
    void ownerStoreProfilePreflightShouldAllowPutFromFrontendOrigin() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/v1/owner/stores/1/profile")
                .header(HttpHeaders.ORIGIN, "http://127.0.0.1:4173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:4173"))
            .andExpect(header().string("Access-Control-Allow-Methods", org.hamcrest.Matchers.containsString("PUT")));
    }

    private String signupAndLoginMember(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SignupRequest(email, "password123!", "tester", UserRole.USER))))
            .andExpect(status().isOk());
        return loginAndReturnAccessToken(email, "password123!");
    }

    private String signupAndLoginOwner(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SignupRequest(email, "password123!", "owner", UserRole.OWNER))))
            .andExpect(status().isOk());
        return loginAndReturnAccessToken(email, "password123!");
    }

    private String loginAndReturnAccessToken(String email, String password) throws Exception {
        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        return objectMapper.readTree(loginResponse).path("data").path("accessToken").asText();
    }

    private Long createOwnerApplication(String ownerToken, String address) throws Exception {
        String response = mockMvc.perform(multipart("/api/v1/owner/store-registration-requests")
                .file(ownerApplicationRequestPart(address))
                .file(ownerLicenseFile())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        return objectMapper.readTree(response).path("data").path("applicationId").asLong();
    }

    private MockMultipartFile ownerApplicationRequestPart(String address) throws Exception {
        return ownerApplicationRequestPart(address, "owner-shop");
    }

    private MockMultipartFile ownerApplicationRequestPart(String address, String storeName) throws Exception {
        return new MockMultipartFile(
            "request",
            "",
            MediaType.APPLICATION_JSON_VALUE,
            objectMapper.writeValueAsBytes(new OwnerApplicationRequest(
                storeName,
                "123-45-67890",
                "홍길동",
                LocalDate.of(2021, 3, 15),
                address,
                "02-1234-5678"
            ))
        );
    }

    private MockMultipartFile ownerApplicationUpdatePart(String address) throws Exception {
        return new MockMultipartFile(
            "request",
            "",
            MediaType.APPLICATION_JSON_VALUE,
            objectMapper.writeValueAsBytes(new com.toggle.dto.owner.OwnerApplicationUpdateRequest(
                "owner-shop",
                "123-45-67890",
                "홍길동",
                LocalDate.of(2021, 3, 15),
                address,
                "02-1234-5678"
            ))
        );
    }

    private MockMultipartFile ownerLicenseFile() {
        return new MockMultipartFile("businessLicenseFile", "license.pdf", "application/pdf", "fake-pdf-content".getBytes());
    }

    private String createAdminAndLogin() throws Exception {
        userRepository.save(new User("admin@toggle.com", passwordEncoder.encode("password123!"), "admin", UserRole.ADMIN, UserStatus.ACTIVE));
        return loginAndReturnAccessToken("admin@toggle.com", "password123!");
    }

    private Long createStore() throws Exception {
        ResolveStoreRequest request = new ResolveStoreRequest(
            ExternalSource.KAKAO.name(),
            "1234567890",
            "맛있는 덮밥집",
            "서울시 강남구 테헤란로 123",
            "02-1234-5678",
            new BigDecimal("37.4980950"),
            new BigDecimal("127.0276100")
        );

        String response = mockMvc.perform(post("/api/v1/stores/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        return objectMapper.readTree(response).path("data").path("storeId").asLong();
    }

    private void mockAutomaticBusinessVerificationSuccess() {
        mockAutomaticBusinessVerificationSuccessFor("1234567890", "홍길동", "20210315", "서울특별시 강남구 테헤란로 123");
    }

    private void mockAutomaticBusinessVerificationSuccessFor(
        String businessNumber,
        String representativeName,
        String openDate,
        String address
    ) {
        given(nationalTaxServiceClient.verifyBusiness(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
            .willReturn(new NationalTaxVerificationResult(
                true,
                "{\"businesses\":[]}",
                "{\"data\":[{\"valid\":\"01\"}]}",
                businessNumber,
                representativeName,
                openDate,
                address,
                null,
                null
            ));
    }

    private void mockMatchingPlace(String externalPlaceId, String address) {
        KakaoKeywordSearchResponse.KakaoPlaceDocument place = new KakaoKeywordSearchResponse.KakaoPlaceDocument(
            externalPlaceId,
            "owner-shop",
            address,
            address,
            "02-1234-5678",
            "음식점 > 한식",
            new BigDecimal("127.0276100"),
            new BigDecimal("37.4980950")
        );
        given(kakaoPlaceClient.searchByKeyword(org.mockito.ArgumentMatchers.anyString())).willReturn(java.util.List.of(place));
    }

    private void mockNameQueryFallbackScenario() {
        KakaoKeywordSearchResponse.KakaoPlaceDocument noisyNameQueryResult = new KakaoKeywordSearchResponse.KakaoPlaceDocument(
            "noise-1",
            "디캠프",
            "서울 강남구 선릉로 553",
            "서울 강남구 선릉로 553",
            "",
            "공유오피스",
            new BigDecimal("127.0450000"),
            new BigDecimal("37.5060000")
        );
        KakaoKeywordSearchResponse.KakaoPlaceDocument addressQueryResult = new KakaoKeywordSearchResponse.KakaoPlaceDocument(
            "match-1",
            "owner-shop",
            "서울 강남구 선릉로 551",
            "서울 강남구 선릉로 551",
            "02-1234-5678",
            "음식점 > 한식",
            new BigDecimal("127.0450000"),
            new BigDecimal("37.5060000")
        );
        given(kakaoPlaceClient.searchByKeyword("owner-shop 서울특별시 강남구 선릉로 551"))
            .willReturn(java.util.List.of(noisyNameQueryResult));
        given(kakaoPlaceClient.searchByKeyword("서울특별시 강남구 선릉로 551"))
            .willReturn(java.util.List.of(addressQueryResult));
    }
}
