package com.toggle;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toggle.dto.auth.LoginRequest;
import com.toggle.dto.auth.RefreshTokenRequest;
import com.toggle.dto.auth.SignupRequest;
import com.toggle.dto.owner.OwnerApplicationRequest;
import com.toggle.dto.owner.OwnerApplicationReviewRequest;
import com.toggle.dto.store.ResolveStoreRequest;
import com.toggle.entity.ExternalSource;
import com.toggle.entity.User;
import com.toggle.entity.UserRole;
import com.toggle.entity.UserStatus;
import com.toggle.repository.FavoriteRepository;
import com.toggle.repository.OwnerApplicationRepository;
import com.toggle.repository.OwnerStoreLinkRepository;
import com.toggle.repository.StoreRepository;
import com.toggle.repository.UserRepository;
import java.math.BigDecimal;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
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

    @BeforeEach
    void setUp() {
        favoriteRepository.deleteAll();
        ownerStoreLinkRepository.deleteAll();
        storeRepository.deleteAll();
        ownerApplicationRepository.deleteAll();
        userRepository.deleteAll();
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
        SignupRequest signupRequest = new SignupRequest(
            "tester@toggle.com",
            "password123!",
            "tester",
            UserRole.USER
        );

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
            .andExpect(jsonPath("$.data.user.email").value("tester@toggle.com"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String accessToken = objectMapper.readTree(loginResponse).path("data").path("accessToken").asText();
        String refreshToken = objectMapper.readTree(loginResponse).path("data").path("refreshToken").asText();

        mockMvc.perform(get("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.email").value("tester@toggle.com"))
            .andExpect(jsonPath("$.data.role").value("USER"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshTokenRequest(refreshToken))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").isString())
            .andExpect(jsonPath("$.data.refreshToken").isString());
    }

    @Test
    void signupShouldNormalizeEmailAndRejectCaseInsensitiveDuplicate() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SignupRequest(
                    "  Tester@Toggle.com ",
                    "password123!",
                    "tester",
                    UserRole.USER
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.email").value("tester@toggle.com"));

        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SignupRequest(
                    "tester@toggle.com",
                    "password123!",
                    "tester2",
                    UserRole.USER
                ))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void duplicateEmailSignupShouldReturnConflict() throws Exception {
        SignupRequest signupRequest = new SignupRequest(
            "tester@toggle.com",
            "password123!",
            "tester",
            UserRole.USER
        );

        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void invalidCredentialsShouldReturnUnauthorized() throws Exception {
        SignupRequest signupRequest = new SignupRequest(
            "tester@toggle.com",
            "password123!",
            "tester",
            UserRole.USER
        );

        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest("tester@toggle.com", "wrong-password"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void favoriteEndpointsShouldRequireAuthenticationAndWorkWithJwt() throws Exception {
        String accessToken = signupAndLoginMember("tester@toggle.com");
        Long storeId = createStore();

        mockMvc.perform(get("/api/v1/favorites/stores"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        mockMvc.perform(post("/api/v1/favorites/stores/{storeId}", storeId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.favorited").value(true));

        mockMvc.perform(get("/api/v1/favorites/stores")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.content[0].storeId").value(storeId));
    }

    @Test
    void ownerShouldSignupLoginAndCreateStoreApplication() throws Exception {
        String ownerToken = signupAndLoginOwner("owner@toggle.com");
        MockMultipartFile requestPart = ownerApplicationRequestPart();
        MockMultipartFile licenseFile = ownerLicenseFile();

        mockMvc.perform(multipart("/api/v1/owner/store-applications")
                .file(requestPart)
                .file(licenseFile)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ownerUserId").exists())
            .andExpect(jsonPath("$.data.businessName").value("owner-shop"))
            .andExpect(jsonPath("$.data.reviewStatus").value("PENDING"));
    }

    @Test
    void approvedOwnerApplicationShouldCreateLinkedStore() throws Exception {
        String ownerToken = signupAndLoginOwner("owner@toggle.com");
        Long applicationId = createOwnerApplication(ownerToken);
        Long storeId = createStore();
        String adminToken = createAdminAndLogin();

        mockMvc.perform(post("/api/v1/admin/owner-store-applications/{applicationId}/approve", applicationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"storeId\":" + storeId + "}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reviewStatus").value("APPROVED"))
            .andExpect(jsonPath("$.data.linkedStoreId").value(storeId));

        mockMvc.perform(get("/api/v1/owner/stores")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].storeId").value(storeId));
    }

    @Test
    void adminShouldBeAbleToRejectOwnerApplication() throws Exception {
        String ownerToken = signupAndLoginOwner("owner@toggle.com");
        Long applicationId = createOwnerApplication(ownerToken);
        String adminToken = createAdminAndLogin();

        mockMvc.perform(post("/api/v1/admin/owner-store-applications/{applicationId}/reject", applicationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new OwnerApplicationReviewRequest("서류 식별 불가"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reviewStatus").value("REJECTED"))
            .andExpect(jsonPath("$.data.rejectReason").value("서류 식별 불가"));
    }

    @Test
    void adminShouldListOwnerApplications() throws Exception {
        String ownerToken = signupAndLoginOwner("owner@toggle.com");
        createOwnerApplication(ownerToken);
        String adminToken = createAdminAndLogin();

        mockMvc.perform(get("/api/v1/admin/owner-store-applications")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].ownerEmail").value("owner@toggle.com"))
            .andExpect(jsonPath("$.data[0].businessName").value("owner-shop"))
            .andExpect(jsonPath("$.data[0].businessAddressRaw").value("서울특별시 강남구 테헤란로 123"))
            .andExpect(jsonPath("$.data[0].reviewStatus").value("PENDING"));
    }

    @Test
    void adminShouldListMatchCandidatesAndConfirmOwnerStoreLink() throws Exception {
        String ownerToken = signupAndLoginOwner("owner@toggle.com");
        Long applicationId = createOwnerApplication(ownerToken);
        Long storeId = createStore();
        String adminToken = createAdminAndLogin();

        mockMvc.perform(get("/api/v1/admin/owner-store-applications/{applicationId}/match-candidates", applicationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].storeId").value(storeId))
            .andExpect(jsonPath("$.data[0].score").isNumber());

        mockMvc.perform(post("/api/v1/admin/owner-store-applications/{applicationId}/approve", applicationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"storeId\":" + storeId + "}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.linkedStoreId").value(storeId))
            .andExpect(jsonPath("$.data.reviewStatus").value("APPROVED"));
    }

    @Test
    void linkedOwnerShouldUpdateStoreStatus() throws Exception {
        String ownerToken = signupAndLoginOwner("owner@toggle.com");
        Long applicationId = createOwnerApplication(ownerToken);
        Long storeId = createStore();
        String adminToken = createAdminAndLogin();

        mockMvc.perform(post("/api/v1/admin/owner-store-applications/{applicationId}/approve", applicationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"storeId\":" + storeId + "}"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/owner/stores/{storeId}/status", storeId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"BREAK_TIME\",\"comment\":\"브레이크타임입니다.\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.storeId").value(storeId))
            .andExpect(jsonPath("$.data.liveBusinessStatus").value("BREAK_TIME"))
            .andExpect(jsonPath("$.data.statusSource").value("OWNER_POS"));

        Assertions.assertThat(storeRepository.findById(storeId).orElseThrow().getLiveBusinessStatus().name())
            .isEqualTo("BREAK_TIME");
    }

    @Test
    void ownerShouldNotUpdateUnlinkedStoreStatus() throws Exception {
        String ownerToken = signupAndLoginOwner("owner@toggle.com");
        Long storeId = createStore();

        mockMvc.perform(post("/api/v1/owner/stores/{storeId}/status", storeId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"OPEN\",\"comment\":\"무단 변경\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("STORE_ACCESS_DENIED"));
    }

    @Test
    void nonAdminShouldNotAccessOwnerApplicationAdminEndpoints() throws Exception {
        String ownerToken = signupAndLoginOwner("owner@toggle.com");
        Long applicationId = createOwnerApplication(ownerToken);
        String userToken = signupAndLoginMember("tester@toggle.com");

        mockMvc.perform(get("/api/v1/admin/owner-store-applications")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/v1/admin/owner-store-applications/{applicationId}/approve", applicationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void blockedUserTokenShouldNotAccessProtectedApi() throws Exception {
        String accessToken = signupAndLoginMember("tester@toggle.com");
        Long storeId = createStore();

        User user = userRepository.findByEmail("tester@toggle.com").orElseThrow();
        user.changeStatus(UserStatus.BLOCKED);
        userRepository.save(user);

        mockMvc.perform(post("/api/v1/favorites/stores/{storeId}", storeId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void logoutShouldRejectInvalidRefreshToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshTokenRequest("invalid-token"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
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

    private String signupAndLoginMember(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SignupRequest(
                    email,
                    "password123!",
                    "tester",
                    UserRole.USER
                ))))
            .andExpect(status().isOk());

        return loginAndReturnAccessToken(email, "password123!");
    }

    private String signupAndLoginOwner(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SignupRequest(
                    email,
                    "password123!",
                    "owner",
                    UserRole.OWNER
                ))))
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

    private Long createOwnerApplication(String ownerToken) throws Exception {
        String response = mockMvc.perform(multipart("/api/v1/owner/store-applications")
                .file(ownerApplicationRequestPart())
                .file(ownerLicenseFile())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        return objectMapper.readTree(response).path("data").path("applicationId").asLong();
    }

    private MockMultipartFile ownerApplicationRequestPart() throws Exception {
        return new MockMultipartFile(
            "request",
            "",
            MediaType.APPLICATION_JSON_VALUE,
            objectMapper.writeValueAsBytes(new OwnerApplicationRequest(
                "owner-shop",
                "123-45-67890",
                "서울특별시 강남구 테헤란로 123"
            ))
        );
    }

    private MockMultipartFile ownerLicenseFile() {
        return new MockMultipartFile(
            "businessLicenseFile",
            "license.pdf",
            "application/pdf",
            "fake-pdf-content".getBytes()
        );
    }

    private String createAdminAndLogin() throws Exception {
        userRepository.save(new User(
            "admin@toggle.com",
            passwordEncoder.encode("password123!"),
            "admin",
            UserRole.ADMIN,
            UserStatus.ACTIVE
        ));

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
}
