package com.toggle.global.security;

import com.toggle.global.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private static final String TOKEN_TYPE_CLAIM = "typ";
    private static final String ROLE_CLAIM = "role";

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.signingKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(CustomUserPrincipal principal) {
        return createToken(principal, "access", jwtProperties.accessTokenExpirationSeconds());
    }

    public String createRefreshToken(CustomUserPrincipal principal) {
        return createToken(principal, "refresh", jwtProperties.refreshTokenExpirationSeconds());
    }

    public boolean isValidAccessToken(String token) {
        return isValidToken(token, "access");
    }

    public boolean isValidRefreshToken(String token) {
        return isValidToken(token, "refresh");
    }

    public Long getUserId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    public String getEmail(String token) {
        return parseClaims(token).get("email", String.class);
    }

    private String createToken(CustomUserPrincipal principal, String tokenType, long expirationSeconds) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(expirationSeconds);

        return Jwts.builder()
            .issuer(jwtProperties.issuer())
            .subject(String.valueOf(principal.getId()))
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .claim(TOKEN_TYPE_CLAIM, tokenType)
            .claim("email", principal.getUsername())
            .claim(ROLE_CLAIM, principal.getAuthorities().iterator().next().getAuthority())
            .signWith(signingKey)
            .compact();
    }

    private boolean isValidToken(String token, String expectedType) {
        try {
            Claims claims = parseClaims(token);
            return expectedType.equals(claims.get(TOKEN_TYPE_CLAIM, String.class));
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
