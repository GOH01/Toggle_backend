package com.toggle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.toggle.dto.auth.ChangePasswordRequest;
import com.toggle.dto.auth.MeResponse;
import com.toggle.dto.auth.SignupRequest;
import com.toggle.dto.auth.UpdateNicknameRequest;
import com.toggle.entity.User;
import com.toggle.entity.UserRole;
import com.toggle.entity.UserStatus;
import com.toggle.global.config.JwtProperties;
import com.toggle.global.exception.ApiException;
import com.toggle.global.security.CustomUserPrincipal;
import com.toggle.global.security.JwtTokenProvider;
import com.toggle.repository.FavoriteRepository;
import com.toggle.repository.PublicFavoriteRepository;
import com.toggle.repository.UserMapRepository;
import com.toggle.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FavoriteRepository favoriteRepository;

    @Mock
    private PublicFavoriteRepository publicFavoriteRepository;

    @Mock
    private UserMapRepository userMapRepository;

    @Mock
    private S3FileService s3FileService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private JwtProperties jwtProperties;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
            userRepository,
            favoriteRepository,
            publicFavoriteRepository,
            userMapRepository,
            s3FileService,
            passwordEncoder,
            authenticationManager,
            jwtTokenProvider,
            jwtProperties
        );
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void updateNicknameShouldPersistNicknameAndReturnProfile() {
        User user = activeUser(1L, "user@test.com", "oldNick");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByNicknameAndIdNot("newNick", 1L)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        authenticate(user);

        var response = authService.updateNickname(new UpdateNicknameRequest("newNick"));

        assertThat(response.nickname()).isEqualTo("newNick");
        verify(userRepository).save(user);
    }

    @Test
    void updateNicknameShouldRejectDuplicateNickname() {
        User user = activeUser(1L, "user@test.com", "oldNick");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByNicknameAndIdNot("newNick", 1L)).thenReturn(true);
        authenticate(user);

        assertThatThrownBy(() -> authService.updateNickname(new UpdateNicknameRequest("newNick")))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void signupShouldNotCreateMapRow() {
        when(userRepository.findByEmail("new@test.com")).thenReturn(Optional.empty());
        when(userRepository.existsByNickname("tester")).thenReturn(false);
        when(passwordEncoder.encode("password123!")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = authService.signup(new SignupRequest(
            "new@test.com",
            "password123!",
            "tester",
            null,
            UserRole.USER
        ));

        assertThat(response.email()).isEqualTo("new@test.com");
        verify(userMapRepository, never()).save(any());
    }

    @Test
    void getCurrentUserProfileShouldNotSynthesizeMapProfileForFreshUser() {
        User user = activeUser(1L, "user@test.com", "nick");
        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        authenticate(user);

        MeResponse response = authService.getCurrentUserProfile();

        assertThat(response.mapProfile().publicMapUuid()).isNull();
        assertThat(response.mapProfile().title()).isNull();
        assertThat(response.mapProfile().description()).isNull();
        verify(userMapRepository, never()).save(any());
    }

    @Test
    void changePasswordShouldRequireCurrentPassword() {
        User user = activeUser(1L, "user@test.com", "nick");
        ReflectionTestUtils.setField(user, "password", "encoded-old");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old-pass", "encoded-old")).thenReturn(true);
        when(passwordEncoder.encode("new-pass")).thenReturn("encoded-new");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        authenticate(user);

        var response = authService.changePassword(new ChangePasswordRequest("old-pass", "new-pass"));

        assertThat(response.message()).isEqualTo("비밀번호가 변경되었습니다.");
        verify(userRepository).save(user);
        assertThat(user.getPassword()).isEqualTo("encoded-new");
    }

    @Test
    void changePasswordShouldRejectWrongCurrentPassword() {
        User user = activeUser(1L, "user@test.com", "nick");
        ReflectionTestUtils.setField(user, "password", "encoded-old");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded-old")).thenReturn(false);
        authenticate(user);

        assertThatThrownBy(() -> authService.changePassword(new ChangePasswordRequest("wrong", "new-pass")))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void updateProfileImageShouldPersistNewUrl() {
        User user = activeUser(1L, "user@test.com", "nick");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(s3FileService.uploadFile(any(MultipartFile.class), eq("user_profile")))
            .thenReturn(new S3FileService.StoredFile("/api/v1/files/view?key=user_profile%2Fnew.png", "user_profile/new.png"));
        authenticate(user);

        var response = authService.updateProfileImage(new MockMultipartFile("profileImage", "new.png", "image/png", new byte[] {1, 2, 3}));

        assertThat(response.profileImageUrl()).contains("user_profile");
        verify(userRepository).save(user);
    }

    @Test
    void deactivateCurrentUserShouldMarkInactive() {
        User user = activeUser(1L, "user@test.com", "nick");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        authenticate(user);

        var response = authService.deactivateCurrentUser();

        assertThat(response.message()).isEqualTo("회원 탈퇴가 완료되었습니다.");
        assertThat(user.getStatus()).isEqualTo(UserStatus.INACTIVE);
    }

    private User activeUser(Long id, String email, String nickname) {
        User user = new User(email, "encoded", nickname, UserRole.USER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private void authenticate(User user) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(new CustomUserPrincipal(user), null, new CustomUserPrincipal(user).getAuthorities())
        );
    }
}
