package com.toggle.service;

import com.toggle.dto.auth.AuthTokenResponse;
import com.toggle.dto.auth.AuthUserResponse;
import com.toggle.dto.auth.LogoutResponse;
import com.toggle.dto.auth.LoginRequest;
import com.toggle.dto.auth.MeResponse;
import com.toggle.dto.auth.RefreshTokenRequest;
import com.toggle.dto.auth.SignupRequest;
import com.toggle.dto.auth.SignupResponse;
import com.toggle.dto.user.UpdateMyMapProfileRequest;
import com.toggle.entity.User;
import com.toggle.entity.UserRole;
import com.toggle.entity.UserStatus;
import com.toggle.global.config.JwtProperties;
import com.toggle.global.exception.ApiException;
import com.toggle.global.security.CustomUserPrincipal;
import com.toggle.global.security.JwtTokenProvider;
import com.toggle.repository.FavoriteRepository;
import com.toggle.repository.PublicFavoriteRepository;
import com.toggle.repository.UserRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final FavoriteRepository favoriteRepository;
    private final PublicFavoriteRepository publicFavoriteRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;

    public AuthService(
        UserRepository userRepository,
        FavoriteRepository favoriteRepository,
        PublicFavoriteRepository publicFavoriteRepository,
        PasswordEncoder passwordEncoder,
        AuthenticationManager authenticationManager,
        JwtTokenProvider jwtTokenProvider,
        JwtProperties jwtProperties
    ) {
        this.userRepository = userRepository;
        this.favoriteRepository = favoriteRepository;
        this.publicFavoriteRepository = publicFavoriteRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        UserRole role = resolveSignupRole(request.role());
        String normalizedNickname = normalizeOptionalText(request.nickname());
        String normalizedOwnerDisplayName = normalizeOptionalText(request.ownerDisplayName());

        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "이미 존재하는 이메일입니다.");
        }
        if (role == UserRole.USER) {
            validateNickname(normalizedNickname);
            if (userRepository.existsByNickname(normalizedNickname)) {
                throw new ApiException(HttpStatus.CONFLICT, "NICKNAME_ALREADY_EXISTS", "이미 사용 중인 닉네임입니다.");
            }
        } else if (role == UserRole.OWNER) {
            validateOwnerDisplayName(normalizedOwnerDisplayName);
        }

        User user = userRepository.save(new User(
            normalizedEmail,
            passwordEncoder.encode(request.password()),
            role == UserRole.USER ? normalizedNickname : null,
            role == UserRole.OWNER ? normalizedOwnerDisplayName : null,
            role,
            UserStatus.ACTIVE
        ));

        return new SignupResponse(
            user.getId(),
            user.getEmail(),
            user.getNickname(),
            resolveDisplayName(user),
            user.getRole().name(),
            user.getStatus().name(),
            user.getCreatedAt()
        );
    }

    public AuthTokenResponse login(LoginRequest request) {
        try {
            String normalizedEmail = normalizeEmail(request.email());
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, request.password())
            );
            CustomUserPrincipal principal = (CustomUserPrincipal) authentication.getPrincipal();
            User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "유효한 사용자 정보가 없습니다."));
            assertLoginAllowed(user);

            return buildTokenResponse(user);
        } catch (ApiException ex) {
            throw ex;
        } catch (BadCredentialsException ex) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다.");
        }
    }

    public AuthTokenResponse refresh(RefreshTokenRequest request) {
        if (!jwtTokenProvider.isValidRefreshToken(request.refreshToken())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "유효하지 않은 refresh token 입니다.");
        }

        return buildTokenResponse(requireActiveUser(jwtTokenProvider.getUserId(request.refreshToken())));
    }

    public LogoutResponse logout(RefreshTokenRequest request) {
        if (!jwtTokenProvider.isValidRefreshToken(request.refreshToken())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "유효하지 않은 refresh token 입니다.");
        }
        return new LogoutResponse(true);
    }

    public MeResponse getCurrentUserProfile() {
        User user = getAuthenticatedUser();
        return new MeResponse(
            user.getId(),
            user.getEmail(),
            user.getNickname(),
            resolveDisplayName(user),
            user.getRole().name(),
            user.getStatus().name(),
            new MeResponse.Favorites(getFavoriteStoreIds(user), getFavoritePublicIds(user)),
            buildMapProfile(user)
        );
    }

    @Transactional
    public MeResponse.MapProfile updateMyMapProfile(UpdateMyMapProfileRequest request) {
        User user = getAuthenticatedUser();
        user.updateMapProfile(
            request.isPublic(),
            normalizeNullableText(request.title()),
            normalizeNullableText(request.description()),
            normalizeNullableText(request.profileImageUrl())
        );
        userRepository.save(user);
        return buildMapProfile(user);
    }

    public User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserPrincipal principal)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다.");
        }

        return requireActiveUser(principal.getId());
    }

    private AuthTokenResponse buildTokenResponse(User user) {
        CustomUserPrincipal principal = new CustomUserPrincipal(user);
        return new AuthTokenResponse(
            jwtTokenProvider.createAccessToken(principal),
            jwtTokenProvider.createRefreshToken(principal),
            "Bearer",
            jwtProperties.accessTokenExpirationSeconds(),
            new AuthUserResponse(
                principal.getId(),
                principal.getUsername(),
                user.getNickname(),
                resolveDisplayName(user),
                principal.getAuthorities().iterator().next().getAuthority().replace("ROLE_", ""),
                principal.getStatus()
            )
        );
    }

    private List<Long> getFavoriteStoreIds(User user) {
        return favoriteRepository.findAllByUserIdAndStoreDeletedAtIsNullOrderByCreatedAtDesc(user.getId())
            .stream()
            .map(favorite -> favorite.getStore().getId())
            .toList();
    }

    private List<Long> getFavoritePublicIds(User user) {
        return publicFavoriteRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId())
            .stream()
            .map(favorite -> favorite.getPublicInstitution().getId())
            .toList();
    }

    private MeResponse.MapProfile buildMapProfile(User user) {
        String publicMapUuid = ensurePublicMapUuid(user);
        return new MeResponse.MapProfile(
            publicMapUuid,
            user.isPublicMap(),
            resolveMapTitle(user),
            user.getMapDescription(),
            user.getProfileImageUrl()
        );
    }

    private String resolveMapTitle(User user) {
        String storedTitle = normalizeNullableText(user.getMapTitle());
        if (storedTitle != null) {
            return storedTitle;
        }

        return user.getNickname() + "님의 지도";
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeOptionalText(String value) {
        return normalizeNullableText(value);
    }

    private void validateNickname(String nickname) {
        if (nickname == null || nickname.length() < 2 || nickname.length() > 30) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_NICKNAME", "닉네임은 2자 이상 30자 이하여야 합니다.");
        }
    }

    private void validateOwnerDisplayName(String ownerDisplayName) {
        if (ownerDisplayName == null || ownerDisplayName.length() < 2 || ownerDisplayName.length() > 30) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_OWNER_DISPLAY_NAME", "점주 표시명은 2자 이상 30자 이하여야 합니다.");
        }
    }

    private String resolveDisplayName(User user) {
        String nickname = normalizeOptionalText(user.getNickname());
        if (nickname != null) {
            return nickname;
        }

        String ownerDisplayName = normalizeOptionalText(user.getOwnerDisplayName());
        if (ownerDisplayName != null) {
            return ownerDisplayName;
        }

        String email = normalizeOptionalText(user.getEmail());
        if (email == null) {
            return "사용자";
        }
        int atIndex = email.indexOf('@');
        return atIndex > 0 ? email.substring(0, atIndex) : email;
    }

    public String ensurePublicMapUuid(User user) {
        if (!user.ensurePublicMapUuid()) {
            return user.getPublicMapUuid();
        }

        userRepository.save(user);
        return user.getPublicMapUuid();
    }

    private User requireActiveUser(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "유효한 사용자 정보가 없습니다."));

        assertActiveForApi(user);

        return user;
    }

    private void assertActiveForApi(User user) {
        if (user.getStatus() == UserStatus.ACTIVE) {
            return;
        }

        throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "비활성 사용자입니다.");
    }

    private void assertLoginAllowed(User user) {
        if (user.getStatus() == UserStatus.ACTIVE) {
            return;
        }

        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_BLOCKED", "차단된 계정입니다.");
        }

        throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "비활성 사용자입니다.");
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private UserRole resolveSignupRole(UserRole requestedRole) {
        if (requestedRole == null) {
            return UserRole.USER;
        }

        if (requestedRole == UserRole.ADMIN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SIGNUP_ROLE", "관리자 계정은 직접 가입할 수 없습니다.");
        }

        return requestedRole;
    }
}
