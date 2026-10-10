package com.wisekoin.backend.module.auth.token;

import com.wisekoin.backend.module.common.exception.BadCredentialsException;
import com.wisekoin.backend.module.user.User;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    @Transactional
    public String issue(User user) {
        return persistNewToken(user);
    }

    @Transactional
    public RefreshSession rotate(String rawToken) {
        RefreshToken storedToken =
                refreshTokenRepository
                        .findByTokenHashForUpdate(hash(rawToken))
                        .orElseThrow(this::invalidRefreshToken);

        if (!storedToken.getExpiresAt().isAfter(Instant.now())) {
            refreshTokenRepository.delete(storedToken);
            throw invalidRefreshToken();
        }

        User user = storedToken.getUser();
        refreshTokenRepository.delete(storedToken);
        return new RefreshSession(user, persistNewToken(user));
    }

    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.deleteByTokenHash(hash(rawToken));
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        refreshTokenRepository.deleteAllByUserId(userId);
    }

    private String persistNewToken(User user) {
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        Instant now = Instant.now();
        refreshTokenRepository.deleteAllByExpiresAtBefore(now);
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(hash(rawToken));
        refreshToken.setCreatedAt(now);
        refreshToken.setExpiresAt(now.plusMillis(refreshExpiration));
        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    private String hash(String rawToken) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private BadCredentialsException invalidRefreshToken() {
        return new BadCredentialsException("Invalid or expired refresh token");
    }

    public record RefreshSession(User user, String token) {}
}
