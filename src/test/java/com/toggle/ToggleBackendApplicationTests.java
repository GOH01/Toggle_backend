package com.toggle;

import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toggle.dto.auth.LoginRequest;
import com.toggle.dto.auth.RefreshTokenRequest;
import com.toggle.dto.auth.SignupRequest;
import com.toggle.dto.kakao.KakaoAddressSearchResponse;
import com.toggle.dto.kakao.KakaoKeywordSearchRequest;
import com.toggle.dto.kakao.KakaoPlaceSearchResponse;
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
import com.toggle.repository.MyMapPublicInstitutionRepository;
import com.toggle.repository.MyMapStoreRepository;
import com.toggle.repository.OwnerApplicationRepository;
import com.toggle.repository.OwnerStoreLinkRepository;
import com.toggle.repository.StoreRepository;
import com.toggle.repository.StoreReviewRepository;
import com.toggle.repository.UserMapLikeRepository;
import com.toggle.repository.UserMapRepository;
import com.toggle.repository.UserRepository;
import com.toggle.service.KakaoPlaceClient;
import com.toggle.service.S3FileService;
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

    @Autowired
    private MyMapStoreRepository myMapStoreRepository;

    @Autowired
    private MyMapPublicInstitutionRepository myMapPublicInstitutionRepository;

    @Autowired
    private StoreReviewRepository storeReviewRepository;

    @Autowired
    private UserMapRepository userMapRepository;

    @Autowired
    private UserMapLikeRepository userMapLikeRepository;

    @MockBean
    private KakaoPlaceClient kakaoPlaceClient;

    @MockBean
    private NationalTaxServiceClient nationalTaxServiceClient;

    @MockBean
    private S3FileService s3FileService;

    @BeforeEach
    void setUp() {
        favoriteRepository.deleteAll();
        myMapStoreRepository.deleteAll();
        myMapPublicInstitutionRepository.deleteAll();
        adminReviewLogRepository.deleteAll();
        businessVerificationHistoryRepository.deleteAll();
        mapVerificationHistoryRepository.deleteAll();
        storeReviewRepository.deleteAll();
        ownerStoreLinkRepository.deleteAll();
        ownerApplicationRepository.deleteAll();
        storeRepository.deleteAll();
        userMapLikeRepository.deleteAll();
        userMapRepository.deleteAll();
        userRepository.deleteAll();
        given(kakaoPlaceClient.searchByAddress(anyString())).willReturn(new KakaoAddressSearchResponse(java.util.List.of()));
        given(kakaoPlaceClient.searchKeyword(any())).willAnswer(invocation -> defaultKeywordSearchResponse(invocation.getArgument(0)));
        given(nationalTaxServiceClient.verifyBusiness(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
            .willReturn(new NationalTaxVerificationResult(false, "{}", "{}", null, null, null, null, "NTS_VERIFICATION_FAILED", "국세청 진위확인 결과가 일치하지 않습니다."));
        given(s3FileService.uploadFile(any(), eq("business")))
            .willReturn(new S3FileService.StoredFile("https://sku-toggle.s3.ap-northeast-2.amazonaws.com/business/dev-seed.pdf", "business/dev-seed.pdf"));
        given(s3FileService.createPresignedGetUrl(anyString())).willReturn("https://presigned.example/business-license");
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
        SignupRequest signupRequest = new SignupRequest("tester@toggle.com", "password123!", "tester", null, UserRole.USER);

        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.email").value("tester@toggle.com"))
            .andExpect(jsonPath("$.data.role").value("USER"))
            .andExpect(jsonPath("$.data.displayName").value("tester"));

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
            .andExpect(jsonPath("$.data.email").value("tester@toggle.com"))
            .andExpect(jsonPath("$.data.displayName").value("tester"))
            .andExpect(jsonPath("$.data.mapProfile.publicMapUuid").isNotEmpty())
            .andExpect(jsonPath("$.data.mapProfile.isPublic").value(false))
            .andExpect(jsonPath("$.data.mapProfile.title").value("tester님의 지도"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshTokenRequest(refreshToken))))
            .andExpect(status().isOk());
    }

    @Test
    void duplicateUserNicknameShouldBeRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SignupRequest(
                    "alpha@toggle.com",
                    "password123!",
                    "dup-user",
                    null,
                    UserRole.USER
                ))))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SignupRequest(
                    "beta@toggle.com",
                    "password123!",
                    "dup-user",
                    null,
                    UserRole.USER
                ))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("NICKNAME_ALREADY_EXISTS"));
    }

    @Test
    void ownerSignupShouldAllowDuplicateOwnerDisplayName() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SignupRequest(
                    "owner-alpha@toggle.com",
                    "password123!",
                    null,
                    "홍길동",
                    UserRole.OWNER
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.displayName").value("홍길동"));

        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SignupRequest(
                    "owner-beta@toggle.com",
                    "password123!",
                    null,
                    "홍길동",
                    UserRole.OWNER
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.displayName").value("홍길동"));
    }

    @Test
    void myMapShouldStaySeparateFromFavoritesAndDrivePublicMapApis() throws Exception {
        String accessToken = signupAndLoginMember("public-map@toggle.com");
        Long favoriteStoreId = createStore("favorite-store", "찜 가능한 매장", "음식점", "37.4980950", "127.0276100");
        Long myMapStoreId = createStore("my-map-store", "내 지도 매장", "카페", "37.4981950", "127.0277100");

        mockMvc.perform(post("/api/v1/favorites/stores/{storeId}", favoriteStoreId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/my-map/stores/{storeId}", myMapStoreId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.placeId").value(myMapStoreId.intValue()))
            .andExpect(jsonPath("$.data.type").value("STORE"))
            .andExpect(jsonPath("$.data.inMyMap").value(true));

        String updateResponse = mockMvc.perform(put("/api/v1/my-map/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "isPublic": true,
                      "title": "테스터의 실전 지도",
                      "description": "실데이터 기반으로 공개된 지도입니다.",
                      "profileImageUrl": "https://example.com/avatar.png"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.isPublic").value(true))
            .andExpect(jsonPath("$.data.title").value("테스터의 실전 지도"))
            .andExpect(jsonPath("$.data.publicMapUuid").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();

        String publicMapUuid = objectMapper.readTree(updateResponse).path("data").path("publicMapUuid").asText();

        mockMvc.perform(get("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.favorites.stores.length()").value(1))
            .andExpect(jsonPath("$.data.favorites.stores[0]").value(favoriteStoreId.intValue()))
            .andExpect(jsonPath("$.data.mapProfile.isPublic").value(true))
            .andExpect(jsonPath("$.data.mapProfile.title").value("테스터의 실전 지도"))
            .andExpect(jsonPath("$.data.mapProfile.description").value("실데이터 기반으로 공개된 지도입니다."))
            .andExpect(jsonPath("$.data.mapProfile.profileImageUrl").value("https://example.com/avatar.png"));

        mockMvc.perform(get("/api/v1/my-map")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.stores.length()").value(1))
            .andExpect(jsonPath("$.data.stores[0]").value(myMapStoreId.intValue()))
            .andExpect(jsonPath("$.data.publics").isArray());

        mockMvc.perform(get("/api/v1/public-maps/{publicMapUuid}", publicMapUuid))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.publicMapUuid").value(publicMapUuid))
            .andExpect(jsonPath("$.data.nickname").value("tester"))
            .andExpect(jsonPath("$.data.title").value("테스터의 실전 지도"))
            .andExpect(jsonPath("$.data.stores.length()").value(1))
            .andExpect(jsonPath("$.data.stores[0]").value(myMapStoreId.intValue()))
            .andExpect(jsonPath("$.data.publics").isArray());

        mockMvc.perform(get("/api/v1/public-maps/search")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .param("nickname", "test"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(1))
            .andExpect(jsonPath("$.data.content[0].publicMapUuid").value(publicMapUuid))
            .andExpect(jsonPath("$.data.content[0].nickname").value("tester"))
            .andExpect(jsonPath("$.data.content[0].title").value("테스터의 실전 지도"));
    }

    @Test
    void publicMapLookupShouldRejectPrivateUsers() throws Exception {
        String accessToken = signupAndLoginMember("private-map@toggle.com");

        String meResponse = mockMvc.perform(get("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        String publicMapUuid = objectMapper.readTree(meResponse).path("data").path("mapProfile").path("publicMapUuid").asText();

        mockMvc.perform(get("/api/v1/public-maps/{publicMapUuid}", publicMapUuid))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("PUBLIC_MAP_NOT_FOUND"));
    }

    @Test
    void publicMapSearchShouldRequireLoginAndValidateNicknameLength() throws Exception {
        mockMvc.perform(get("/api/v1/public-maps/search")
                .param("nickname", "tester"))
            .andExpect(status().isUnauthorized());

        String accessToken = signupAndLoginMember("searcher@toggle.com");

        mockMvc.perform(get("/api/v1/public-maps/search")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .param("nickname", "a"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_PUBLIC_MAP_SEARCH_QUERY"));

        mockMvc.perform(get("/api/v1/public-maps/search")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .param("nickname", "테스터"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void myMapEndpointsShouldAddAndRemovePlacesIndependently() throws Exception {
        String accessToken = signupAndLoginMember("my-map@toggle.com");
        Long storeId = createStore("my-map-target", "내 지도 추가 대상", "카페", "37.4980950", "127.0276100");

        mockMvc.perform(post("/api/v1/my-map/stores/{storeId}", storeId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.inMyMap").value(true));

        mockMvc.perform(get("/api/v1/my-map")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.stores.length()").value(1))
            .andExpect(jsonPath("$.data.stores[0]").value(storeId.intValue()));

        mockMvc.perform(delete("/api/v1/my-map/stores/{storeId}", storeId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.inMyMap").value(false));

        mockMvc.perform(get("/api/v1/my-map")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.stores").isArray())
            .andExpect(jsonPath("$.data.stores.length()").value(0));
    }

    @Test
    void myMapsShouldAllowSameStoreAcrossDifferentMapsButBlockSameMapDuplicates() throws Exception {
        String accessToken = signupAndLoginMember("multi-map@toggle.com");
        Long storeId = createStore("multi-map-target", "다중 지도 저장 대상", "카페", "37.4980950", "127.0276100");

        String mapAResponse = mockMvc.perform(post("/api/v1/my-maps")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "데이트 지도",
                      "description": "첫번째 지도",
                      "isPublic": true,
                      "profileImageUrl": null
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value("데이트 지도"))
            .andReturn()
            .getResponse()
            .getContentAsString();
        long mapAId = objectMapper.readTree(mapAResponse).path("data").path("mapId").asLong();

        String mapBResponse = mockMvc.perform(post("/api/v1/my-maps")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "카페 지도",
                      "description": "두번째 지도",
                      "isPublic": false,
                      "profileImageUrl": null
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value("카페 지도"))
            .andReturn()
            .getResponse()
            .getContentAsString();
        long mapBId = objectMapper.readTree(mapBResponse).path("data").path("mapId").asLong();

        mockMvc.perform(post("/api/v1/my-maps/{mapId}/stores/{storeId}", mapAId, storeId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.inMyMap").value(true));

        mockMvc.perform(post("/api/v1/my-maps/{mapId}/stores/{storeId}", mapBId, storeId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.inMyMap").value(true));

        mockMvc.perform(post("/api/v1/my-maps/{mapId}/stores/{storeId}", mapAId, storeId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("MY_MAP_PLACE_ALREADY_EXISTS"));

        mockMvc.perform(delete("/api/v1/my-maps/{mapId}/stores/{storeId}", mapAId, storeId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.inMyMap").value(false));

        mockMvc.perform(post("/api/v1/my-maps/{mapId}/stores/{storeId}", mapAId, storeId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.inMyMap").value(true));
    }

    @Test
    void favoriteEndpointsShouldRequireAuthenticationAndWorkWithJwt() throws Exception {
        String accessToken = signupAndLoginMember("tester@toggle.com");
        Long storeId = createStore("favorite-target", "찜 대상", "음식점", "37.4980950", "127.0276100");
        com.toggle.entity.Store store = storeRepository.findById(storeId).orElseThrow();
        store.updateLiveBusinessStatus(BusinessStatus.OPEN, LiveStatusSource.OWNER_POS);
        storeRepository.save(store);

        mockMvc.perform(get("/api/v1/favorites/stores"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/favorites/stores/{storeId}", storeId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.favorited").value(true));

        mockMvc.perform(get("/api/v1/favorites/stores")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].storeId").value(storeId.intValue()))
            .andExpect(jsonPath("$.data.content[0].liveBusinessStatus").value("OPEN"))
            .andExpect(jsonPath("$.data.content[0].liveStatusSource").value("OWNER_POS"));
    }

    @Test
    void previewStoreActionsShouldBeRejectedForFavoritesMyMapAndReviews() throws Exception {
        String accessToken = signupAndLoginMember("preview-guard@toggle.com");
        Long previewStoreId = createStore("preview-store");

        mockMvc.perform(post("/api/v1/favorites/stores/{storeId}", previewStoreId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("STORE_NOT_REGISTERED"));

        mockMvc.perform(post("/api/v1/my-map/stores/{storeId}", previewStoreId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("STORE_NOT_REGISTERED"));

        mockMvc.perform(post("/api/v1/stores/{storeId}/reviews", previewStoreId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "rating": 5,
                      "content": "미등록 매장 리뷰"
                    }
                    """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("STORE_NOT_REGISTERED"));
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
    void adminShouldApproveEvenWhenBusinessAndMapVerificationFailed() throws Exception {
        String ownerToken = signupAndLoginOwner("owner@toggle.com");
        given(nationalTaxServiceClient.verifyBusiness(anyString(), anyString(), anyString()))
            .willThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "NATIONAL_TAX_API_ERROR", "국세청 API 호출에 실패했습니다."));
        given(kakaoPlaceClient.searchByAddress(anyString())).willReturn(new KakaoAddressSearchResponse(java.util.List.of()));
        Long applicationId = createOwnerApplication(ownerToken, "서울특별시 강남구 테헤란로 123");
        String adminToken = createAdminAndLogin();

        mockMvc.perform(post("/api/v1/admin/store-registration-requests/{applicationId}/approve", applicationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new OwnerApplicationApproveRequest(true))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.requestStatus").value("APPROVED"))
            .andExpect(jsonPath("$.data.businessVerificationStatus").value("AUTO_VERIFICATION_UNAVAILABLE"))
            .andExpect(jsonPath("$.data.mapVerificationStatus").value("FAILED"))
            .andExpect(jsonPath("$.data.verifiedStoreId").value(nullValue()))
            .andExpect(jsonPath("$.data.linkedStoreId").value(nullValue()));
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
        KakaoAddressSearchResponse.KakaoAddressDocument weak = new KakaoAddressSearchResponse.KakaoAddressDocument(
            "same-place",
            "다른상호",
            "서울특별시 강남구 역삼로 999",
            "서울특별시 강남구 역삼로 999",
            "02-0000-0000",
            "음식점 > 한식",
            new BigDecimal("127.0276100"),
            new BigDecimal("37.4980950")
        );
        KakaoAddressSearchResponse.KakaoAddressDocument strong = new KakaoAddressSearchResponse.KakaoAddressDocument(
            "same-place",
            "owner-shop",
            "서울특별시 강남구 테헤란로 123",
            "서울특별시 강남구 테헤란로 123",
            "02-1234-5678",
            "음식점 > 한식",
            new BigDecimal("127.0276100"),
            new BigDecimal("37.4980950")
        );
        given(kakaoPlaceClient.searchByAddress("서울특별시 강남구 테헤란로 123"))
            .willReturn(new KakaoAddressSearchResponse(java.util.List.of(weak, strong)));

        mockMvc.perform(multipart("/api/v1/owner/store-registration-requests")
                .file(ownerApplicationRequestPart("서울특별시 강남구 테헤란로 123"))
                .file(ownerLicenseFile())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mapVerificationStatus").value("VERIFIED"));
    }

    @Test
    void mapVerificationShouldSucceedWhenMultipleAddressDocumentsAreReturned() throws Exception {
        String ownerToken = signupAndLoginOwner("owner@toggle.com");
        mockAutomaticBusinessVerificationSuccess();
        KakaoAddressSearchResponse.KakaoAddressDocument first = new KakaoAddressSearchResponse.KakaoAddressDocument(
            "place-1",
            "owner-shop-a",
            "서울특별시 강남구 테헤란로 123",
            "서울특별시 강남구 테헤란로 123",
            "",
            "음식점 > 한식",
            new BigDecimal("127.0276100"),
            new BigDecimal("37.4980950")
        );
        KakaoAddressSearchResponse.KakaoAddressDocument second = new KakaoAddressSearchResponse.KakaoAddressDocument(
            "place-2",
            "owner-shop-b",
            "서울특별시 강남구 테헤란로 123",
            "서울특별시 강남구 테헤란로 123",
            "",
            "음식점 > 한식",
            new BigDecimal("127.0276100"),
            new BigDecimal("37.4980950")
        );
        given(kakaoPlaceClient.searchByAddress("서울특별시 강남구 테헤란로 123"))
            .willReturn(new KakaoAddressSearchResponse(java.util.List.of(first, second)));

        mockMvc.perform(multipart("/api/v1/owner/store-registration-requests")
                .file(ownerApplicationRequestPart("서울특별시 강남구 테헤란로 123"))
                .file(ownerLicenseFile())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mapVerificationStatus").value("VERIFIED"));
    }

    @Test
    void mapVerificationShouldResolveUniqueNameMatchAmongExactAddressResults() throws Exception {
        String ownerToken = signupAndLoginOwner("owner@toggle.com");
        mockAutomaticBusinessVerificationSuccess();
        KakaoAddressSearchResponse.KakaoAddressDocument other = new KakaoAddressSearchResponse.KakaoAddressDocument(
            "place-1",
            "골프존파크 안양명학역점",
            "경기 안양시 만안구 만안로 35",
            "경기 안양시 만안구 만안로 35",
            "031-111-1111",
            "스포츠,오락",
            new BigDecimal("126.9281000"),
            new BigDecimal("37.3833000")
        );
        KakaoAddressSearchResponse.KakaoAddressDocument target = new KakaoAddressSearchResponse.KakaoAddressDocument(
            "place-2",
            "하삼동커피 안양명학역점",
            "경기 안양시 만안구 만안로 35",
            "경기 안양시 만안구 만안로 35",
            "031-222-2222",
            "카페",
            new BigDecimal("126.9281000"),
            new BigDecimal("37.3833000")
        );
        given(kakaoPlaceClient.searchByAddress("경기도 안양시 만안구 만안로 35"))
            .willReturn(new KakaoAddressSearchResponse(java.util.List.of(other, target)));

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
    void duplicateKakaoPlaceShouldFailDuringMapVerification() throws Exception {
        String firstOwnerToken = signupAndLoginOwner("owner-one@toggle.com");
        String secondOwnerToken = signupAndLoginOwner("owner-two@toggle.com");
        mockAutomaticBusinessVerificationSuccessFor("1234567890", "홍길동", "20210315", "경기도 안양시 만안구 만안로 35");
        mockMatchingPlace("shared-place-id", "경기도 안양시 만안구 만안로 35");

        mockMvc.perform(multipart("/api/v1/owner/store-registration-requests")
                .file(ownerApplicationRequestPart("경기도 안양시 만안구 만안로 35"))
                .file(ownerLicenseFile())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstOwnerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mapVerificationStatus").value("VERIFIED"));

        mockMvc.perform(multipart("/api/v1/owner/store-registration-requests")
                .file(ownerApplicationRequestPart("경기도 안양시 만안구 만안로 35"))
                .file(ownerLicenseFile())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + secondOwnerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mapVerificationStatus").value("FAILED"));

        Assertions.assertThat(storeRepository.findAll()).hasSize(1);
        Assertions.assertThat(
            mapVerificationHistoryRepository.findAllByRequestIdOrderByCreatedAtDesc(
                ownerApplicationRepository.findAllByUserIdOrderByCreatedAtDesc(
                    userRepository.findByEmail("owner-two@toggle.com").orElseThrow().getId()
                ).get(0).getId()
            ).get(0).getFailureCode()
        ).isEqualTo("KAKAO_PLACE_ALREADY_REGISTERED");
    }

    @Test
    void mapVerificationShouldFailEvenWhenOnlyOneExactAddressCandidateMatchesPhone() throws Exception {
        String ownerToken = signupAndLoginOwner("owner@toggle.com");
        mockAutomaticBusinessVerificationSuccess();
        KakaoAddressSearchResponse.KakaoAddressDocument phoneMatched = new KakaoAddressSearchResponse.KakaoAddressDocument(
            "place-1",
            "owner-shop-a",
            "서울특별시 강남구 테헤란로 123",
            "서울특별시 강남구 테헤란로 123",
            "02-1234-5678",
            "음식점 > 한식",
            new BigDecimal("127.0276100"),
            new BigDecimal("37.4980950")
        );
        KakaoAddressSearchResponse.KakaoAddressDocument other = new KakaoAddressSearchResponse.KakaoAddressDocument(
            "place-2",
            "owner-shop-b",
            "서울특별시 강남구 테헤란로 123",
            "서울특별시 강남구 테헤란로 123",
            "02-9999-0000",
            "음식점 > 한식",
            new BigDecimal("127.0276100"),
            new BigDecimal("37.4980950")
        );
        given(kakaoPlaceClient.searchByAddress("서울특별시 강남구 테헤란로 123"))
            .willReturn(new KakaoAddressSearchResponse(java.util.List.of(phoneMatched, other)));

        mockMvc.perform(multipart("/api/v1/owner/store-registration-requests")
                .file(ownerApplicationRequestPart("서울특별시 강남구 테헤란로 123"))
                .file(ownerLicenseFile())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mapVerificationStatus").value("VERIFIED"));
    }

    @Test
    void jibunAddressExactMatchShouldVerifyEvenWhenRoadAddressDiffers() throws Exception {
        String ownerToken = signupAndLoginOwner("owner@toggle.com");
        mockAutomaticBusinessVerificationSuccess();
        KakaoAddressSearchResponse.KakaoAddressDocument place = new KakaoAddressSearchResponse.KakaoAddressDocument(
            "place-jibun",
            "owner-shop",
            "서울특별시 강남구 테헤란로 123",
            "서울특별시 강남구 역삼동 123-45",
            "02-1234-5678",
            "음식점 > 한식",
            new BigDecimal("127.0276100"),
            new BigDecimal("37.4980950")
        );
        given(kakaoPlaceClient.searchByAddress("서울특별시 강남구 역삼동 123-45"))
            .willReturn(new KakaoAddressSearchResponse(java.util.List.of(place)));

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
            .andExpect(jsonPath("$.data.mapVerificationHistories[0].failureCode").value("KAKAO_NO_DOCUMENTS"))
            .andExpect(jsonPath("$.data.mapVerificationHistories[0].failureMessage").value("카카오 주소 검색 결과가 없습니다."));
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

        verify(kakaoPlaceClient, never()).searchByAddress(anyString());
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
        com.toggle.entity.Store existingStore = storeRepository.save(new com.toggle.entity.Store(
            ExternalSource.KAKAO,
            "1234567890",
            "맛있는 덮밥집",
            "02-1234-5678",
            "서울시 강남구 테헤란로 123",
            "서울시 강남구 테헤란로 123",
            new BigDecimal("37.4980950"),
            new BigDecimal("127.0276100")
        ));
        existingStore.markVerified(
            "서울시 강남구 테헤란로 123",
            "서울시 강남구 테헤란로 123",
            "음식점",
            "{}",
            java.time.LocalDateTime.now()
        );
        storeRepository.save(existingStore);

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
    void nearbyStoresShouldReturnOnlyVerifiedStoresWithinRadiusOrderedByDistance() throws Exception {
        Long nearestStoreId = createStore("near-1", "가까운 매장", "카페", "37.4981000", "127.0276000");
        Long fartherStoreId = createStore("near-2", "조금 먼 매장", "음식점", "37.4995000", "127.0295000");
        createStore("far-1", "먼 매장", "음식점", "37.5300000", "127.0700000");
        createStore("draft-1", "미인증 매장", null, "37.4982000", "127.0277000");

        mockMvc.perform(get("/api/v1/stores/nearby")
                .param("latitude", "37.4980950")
                .param("longitude", "127.0276100")
                .param("radiusMeters", "400")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.stores.length()").value(2))
            .andExpect(jsonPath("$.data.stores[0].storeId").value(nearestStoreId))
            .andExpect(jsonPath("$.data.stores[0].name").value("가까운 매장"))
            .andExpect(jsonPath("$.data.stores[1].storeId").value(fartherStoreId))
            .andExpect(jsonPath("$.data.stores[1].name").value("조금 먼 매장"));
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
            .andExpect(jsonPath("$.data.imageUrls[0]").value("/api/v1/files/view?key=data%3Aimage%2Fpng%3Bbase64%2Cabc123"));

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
            .andExpect(jsonPath("$.data.stores[0].imageUrls[0]").value("/api/v1/files/view?key=data%3Aimage%2Fpng%3Bbase64%2Cabc123"));
    }

    @Test
    void adminStoreDeleteShouldArchiveStoreUnlinkOwnerAndHideItEverywhere() throws Exception {
        String ownerToken = signupAndLoginOwner("owner-delete@toggle.com");
        String userToken = signupAndLoginMember("user-delete@toggle.com");
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

        mockMvc.perform(post("/api/v1/favorites/stores/{storeId}", storeId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/my-map/stores/{storeId}", storeId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/stores/{storeId}/reviews", storeId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "rating": 5,
                      "content": "삭제 전 리뷰"
                    }
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/stores/{storeId}", storeId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .param("reason", "운영 종료"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        Assertions.assertThat(storeRepository.findById(storeId).orElseThrow().isDeleted()).isTrue();

        mockMvc.perform(get("/api/v1/owner/stores")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(get("/api/v1/favorites/stores")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isEmpty());

        mockMvc.perform(get("/api/v1/my-map")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.stores").isEmpty());

        mockMvc.perform(get("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.favorites.stores").isEmpty());

        mockMvc.perform(post("/api/v1/stores/lookup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "externalSource": "KAKAO",
                      "externalPlaceIds": ["1234567890"]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.stores").isEmpty());

        mockMvc.perform(get("/api/v1/stores/nearby")
                .param("latitude", "37.4980950")
                .param("longitude", "127.0276100")
                .param("radiusMeters", "2000")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.stores").isEmpty());

        mockMvc.perform(get("/api/v1/stores")
                .param("ids", String.valueOf(storeId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.stores").isEmpty());

        mockMvc.perform(post("/api/v1/stores/{storeId}/reviews", storeId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "rating": 4,
                      "content": "삭제 후 리뷰 시도"
                    }
                    """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("STORE_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/stores/{storeId}/reviews", storeId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("STORE_NOT_FOUND"));
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
                .content(objectMapper.writeValueAsString(new SignupRequest(email, "password123!", "tester", null, UserRole.USER))))
            .andExpect(status().isOk());
        return loginAndReturnAccessToken(email, "password123!");
    }

    private String signupAndLoginOwner(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SignupRequest(email, "password123!", null, "owner", UserRole.OWNER))))
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
        return createStore("1234567890");
    }

    private Long createStore(String externalPlaceId) throws Exception {
        return createStore(externalPlaceId, "맛있는 덮밥집", null, "37.4980950", "127.0276100");
    }

    private Long createStore(
        String externalPlaceId,
        String name,
        String categoryName,
        String latitude,
        String longitude
    ) throws Exception {
        com.toggle.entity.Store store = storeRepository.save(new com.toggle.entity.Store(
            ExternalSource.KAKAO,
            externalPlaceId.trim(),
            name,
            "02-1234-5678",
            "서울시 강남구 테헤란로 123",
            "서울시 강남구 테헤란로 123",
            new BigDecimal(latitude),
            new BigDecimal(longitude)
        ));
        Long storeId = store.getId();

        if (categoryName != null) {
            store.markVerified(
                "서울시 강남구 테헤란로 123",
                "서울시 강남구 테헤란로 123",
                categoryName,
                "{}",
                java.time.LocalDateTime.now()
            );
            storeRepository.save(store);
        }

        return storeId;
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
        KakaoAddressSearchResponse.KakaoAddressDocument place = new KakaoAddressSearchResponse.KakaoAddressDocument(
            externalPlaceId,
            "owner-shop",
            address,
            address,
            "02-1234-5678",
            "음식점 > 한식",
            new BigDecimal("127.0276100"),
            new BigDecimal("37.4980950")
        );
        given(kakaoPlaceClient.searchByAddress(org.mockito.ArgumentMatchers.anyString())).willReturn(new KakaoAddressSearchResponse(java.util.List.of(place)));
        given(kakaoPlaceClient.searchKeyword(any())).willAnswer(invocation -> {
            KakaoKeywordSearchRequest request = invocation.getArgument(0);
            String query = request == null || request.query() == null ? "owner-shop" : request.query();
            return new KakaoPlaceSearchResponse(
                new KakaoPlaceSearchResponse.KakaoPlaceSearchMeta(1, 1, true, null),
                java.util.List.of(new KakaoPlaceSearchResponse.KakaoPlaceDocument(
                    externalPlaceId,
                    query,
                    "음식점 > 한식",
                    "FD6",
                    "음식점",
                    "02-1234-5678",
                    address,
                    address,
                    String.valueOf(request == null || request.longitude() == null ? 127.0276100d : request.longitude()),
                    String.valueOf(request == null || request.latitude() == null ? 37.4980950d : request.latitude()),
                    "http://place.map.kakao.com/" + externalPlaceId,
                    "0"
                ))
            );
        });
    }

    private KakaoPlaceSearchResponse defaultKeywordSearchResponse(KakaoKeywordSearchRequest request) {
        String query = request == null || request.query() == null ? "owner-shop" : request.query();
        double latitude = request == null || request.latitude() == null ? 37.0d : request.latitude();
        double longitude = request == null || request.longitude() == null ? 127.0d : request.longitude();
        String normalizedQuery = query == null ? "keyword-place" : query.replaceAll("[^0-9A-Za-z가-힣]+", "-");
        return new KakaoPlaceSearchResponse(
            new KakaoPlaceSearchResponse.KakaoPlaceSearchMeta(1, 1, true, null),
            java.util.List.of(new KakaoPlaceSearchResponse.KakaoPlaceDocument(
                "keyword-" + normalizedQuery,
                query,
                "음식점 > 한식",
                "FD6",
                "음식점",
                "02-1234-5678",
                "서울특별시 강남구 테헤란로 123",
                "서울특별시 강남구 테헤란로 123",
                String.valueOf(longitude),
                String.valueOf(latitude),
                "http://place.map.kakao.com/keyword-" + normalizedQuery,
                "10"
            ))
        );
    }

    private void mockNameQueryFallbackScenario() {
        KakaoAddressSearchResponse.KakaoAddressDocument noisyNameQueryResult = new KakaoAddressSearchResponse.KakaoAddressDocument(
            "noise-1",
            "디캠프",
            "서울 강남구 선릉로 553",
            "서울 강남구 선릉로 553",
            "",
            "공유오피스",
            new BigDecimal("127.0450000"),
            new BigDecimal("37.5060000")
        );
        KakaoAddressSearchResponse.KakaoAddressDocument addressQueryResult = new KakaoAddressSearchResponse.KakaoAddressDocument(
            "match-1",
            "owner-shop",
            "서울 강남구 선릉로 551",
            "서울 강남구 선릉로 551",
            "02-1234-5678",
            "음식점 > 한식",
            new BigDecimal("127.0450000"),
            new BigDecimal("37.5060000")
        );
        given(kakaoPlaceClient.searchByAddress("서울특별시 강남구 선릉로 551"))
            .willReturn(new KakaoAddressSearchResponse(java.util.List.of(noisyNameQueryResult, addressQueryResult)));
    }
}
