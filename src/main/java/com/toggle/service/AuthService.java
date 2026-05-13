package com.toggle.service;

import com.toggle.dto.auth.AuthTokenResponse;
import com.toggle.dto.auth.AuthUserResponse;
import com.toggle.dto.auth.ChangePasswordRequest;
import com.toggle.dto.auth.SimpleMessageResponse;
import com.toggle.dto.auth.LogoutResponse;
import com.toggle.dto.auth.LoginRequest;
import com.toggle.dto.auth.MeResponse;
import com.toggle.dto.auth.RefreshTokenRequest;
import com.toggle.dto.auth.SignupRequest;
import com.toggle.dto.auth.SignupResponse;
import com.toggle.dto.auth.UpdateNicknameRequest;
import com.toggle.dto.auth.UserProfileResponse;
import com.toggle.dto.user.UpdateMyMapProfileRequest;
import com.toggle.entity.User;
import com.toggle.entity.UserRole;
import com.toggle.entity.UserStatus;
import com.toggle.entity.UserMap;
import com.toggle.global.config.JwtProperties;
import com.toggle.global.exception.ApiException;
import com.toggle.global.util.ImageUrlMapper;
import com.toggle.global.security.CustomUserPrincipal;
import com.toggle.global.security.JwtTokenProvider;
import com.toggle.repository.FavoriteRepository;
import com.toggle.repository.PublicFavoriteRepository;
import com.toggle.repository.UserMapRepository;
import com.toggle.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final FavoriteRepository favoriteRepository;
    private final PublicFavoriteRepository publicFavoriteRepository;
    private final UserMapRepository userMapRepository;
    private final S3FileService s3FileService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;

    public AuthService(
        UserRepository userRepository,
        FavoriteRepository favoriteRepository,
        PublicFavoriteRepository publicFavoriteRepository,
        UserMapRepository userMapRepository,
        S3FileService s3FileService,
        PasswordEncoder passwordEncoder,
        AuthenticationManager authenticationManager,
        JwtTokenProvider jwtTokenProvider,
        JwtProperties jwtProperties
    ) {
        this.userRepository = userRepository;
        this.favoriteRepository = favoriteRepository;
        this.publicFavoriteRepository = publicFavoriteRepository;
        this.userMapRepository = userMapRepository;
        this.s3FileService = s3FileService;
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
            user.getProfileImageUrl(),
            user.getRole().name(),
            user.getStatus().name(),
            new MeResponse.Favorites(getFavoriteStoreIds(user), getFavoritePublicIds(user)),
            buildMapProfile(user)
        );
    }

    @Transactional
    public UserProfileResponse updateNickname(UpdateNicknameRequest request) {
        User user = getAuthenticatedUser();
        String nickname = normalizeNickname(request.nickname());

        if (!Objects.equals(user.getNickname(), nickname) && userRepository.existsByNicknameAndIdNot(nickname, user.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "NICKNAME_ALREADY_EXISTS", "이미 사용 중인 닉네임입니다.");
        }

        user.updateNickname(nickname);
        userRepository.save(user);
        return new UserProfileResponse(user.getId(), user.getNickname(), user.getProfileImageUrl());
    }

    @Transactional
    public UserProfileResponse updateProfileImage(MultipartFile profileImage) {
        User user = getAuthenticatedUser();
        String previousProfileImageUrl = user.getProfileImageUrl();
        String previousDefaultMapProfileImageUrl = user.getDefaultMapId() == null
            ? null
            : userMapRepository.findByIdAndDeletedAtIsNull(user.getDefaultMapId())
                .map(UserMap::getProfileImageUrl)
                .orElse(null);

        S3FileService.StoredFile storedFile = s3FileService.uploadFile(profileImage, "user_profile");
        user.updateProfileImageUrl(storedFile.url());
        userRepository.save(user);

        List<String> keysToDelete = new ArrayList<>();
        addObjectKey(keysToDelete, previousProfileImageUrl);
        addObjectKey(keysToDelete, previousDefaultMapProfileImageUrl);
        deleteAfterCommitDistinct(keysToDelete, storedFile.key());

        syncDefaultMapProfileImage(user, storedFile.url());
        return new UserProfileResponse(user.getId(), user.getNickname(), user.getProfileImageUrl());
    }

    @Transactional
    public SimpleMessageResponse changePassword(ChangePasswordRequest request) {
        User user = getAuthenticatedUser();
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CURRENT_PASSWORD", "현재 비밀번호가 올바르지 않습니다.");
        }

        user.changePassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        return new SimpleMessageResponse("비밀번호가 변경되었습니다.");
    }

    @Transactional
    public SimpleMessageResponse deactivateCurrentUser() {
        User user = getAuthenticatedUser();
        user.changeStatus(UserStatus.INACTIVE);
        userRepository.save(user);
        SecurityContextHolder.clearContext();
        return new SimpleMessageResponse("회원 탈퇴가 완료되었습니다.");
    }

    @Transactional
    public MeResponse.MapProfile updateMyMapProfile(UpdateMyMapProfileRequest request) {
        User user = getAuthenticatedUser();
        UserMap map = ensureDefaultMap(user);
        map.update(
            request.isPublic(),
            normalizeNullableText(request.title()),
            normalizeNullableText(request.description()),
            normalizeNullableText(request.profileImageUrl())
        );
        user.updateMapProfile(
            request.isPublic(),
            normalizeNullableText(request.title()),
            normalizeNullableText(request.description()),
            normalizeNullableText(request.profileImageUrl())
        );
        syncUserMapBridge(user, map);
        userMapRepository.save(map);
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
                user.getProfileImageUrl(),
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
        UserMap defaultMap = ensureDefaultMap(user);
        syncUserMapBridge(user, defaultMap);
        return new MeResponse.MapProfile(
            defaultMap.getPublicMapUuid(),
            defaultMap.isPublic(),
            defaultMap.getTitle(),
            defaultMap.getDescription(),
            defaultMap.getProfileImageUrl()
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

    public UserMap ensureDefaultMap(User user) {
        if (user.getDefaultMapId() != null) {
            return userMapRepository.findByIdAndDeletedAtIsNull(user.getDefaultMapId())
                .orElseGet(() -> createDefaultMap(user));
        }

        if (user.getPublicMapUuid() != null && !user.getPublicMapUuid().isBlank()) {
            UserMap existingByUuid = userMapRepository.findByPublicMapUuidAndDeletedAtIsNull(user.getPublicMapUuid()).orElse(null);
            if (existingByUuid != null) {
                user.setDefaultMapId(existingByUuid.getId());
                userRepository.save(user);
                syncUserMapBridge(user, existingByUuid);
                return existingByUuid;
            }
        }

        return createDefaultMap(user);
    }

    private UserMap createDefaultMap(User user) {
        String publicMapUuid = normalizeNullableText(user.getPublicMapUuid());
        if (publicMapUuid == null) {
            publicMapUuid = UUID.randomUUID().toString();
            user.setPublicMapUuid(publicMapUuid);
        }

        UserMap created = userMapRepository.save(new UserMap(
            user,
            publicMapUuid,
            resolveMapTitle(user),
            user.getMapDescription(),
            user.getProfileImageUrl(),
            user.isPublicMap(),
            true
        ));
        user.setDefaultMapId(created.getId());
        syncUserMapBridge(user, created);
        userRepository.save(user);
        return created;
    }

    private void syncUserMapBridge(User user, UserMap map) {
        user.updateMapProfile(map.isPublic(), map.getTitle(), map.getDescription(), map.getProfileImageUrl());
        user.setPublicMapUuid(map.getPublicMapUuid());
    }

    private void syncDefaultMapProfileImage(User user, String profileImageUrl) {
        if (user.getDefaultMapId() == null) {
            return;
        }

        userMapRepository.findByIdAndDeletedAtIsNull(user.getDefaultMapId()).ifPresent(defaultMap -> {
            defaultMap.update(defaultMap.isPublic(), defaultMap.getTitle(), defaultMap.getDescription(), profileImageUrl);
            userMapRepository.save(defaultMap);
        });
    }

    private String normalizeNickname(String nickname) {
        String normalized = normalizeNullableText(nickname);
        if (normalized == null || normalized.length() < 2 || normalized.length() > 30) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_NICKNAME", "닉네임은 2자 이상 30자 이하여야 합니다.");
        }
        return normalized;
    }

    private void addObjectKey(List<String> keys, String url) {
        String objectKey = ImageUrlMapper.toObjectKey(url);
        if (objectKey != null && !objectKey.isBlank()) {
            keys.add(objectKey);
        }
    }

    private void deleteAfterCommitDistinct(List<String> candidateKeys, String newKey) {
        List<String> keysToDelete = candidateKeys.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(key -> !key.isBlank())
            .filter(key -> !key.equals(newKey))
            .distinct()
            .toList();
        s3FileService.deleteFilesAfterCommit(keysToDelete);
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
