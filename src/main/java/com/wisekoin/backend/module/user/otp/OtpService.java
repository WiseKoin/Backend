package com.wisekoin.backend.module.user.otp;

import com.wisekoin.backend.module.common.exception.BadRequestException;
import com.wisekoin.backend.module.common.exception.InvalidOtpException;
import com.wisekoin.backend.module.common.exception.ResourceNotFoundException;
import com.wisekoin.backend.module.common.exception.TooManyRequestsException;
import com.wisekoin.backend.module.user.User;
import com.wisekoin.backend.module.user.UserRepository;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OtpService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int CODE_BOUND = 1_000_000;
    private static final int TOKEN_BYTES = 32;

    private final OtpChallengeRepository challengeRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpDeliveryService deliveryService;
    private final Clock clock;

    @Value("${app.otp.expiration-seconds:600}")
    private long expirationSeconds;

    @Value("${app.otp.verification-expiration-seconds:600}")
    private long verificationExpirationSeconds;

    @Value("${app.otp.resend-cooldown-seconds:60}")
    private long resendCooldownSeconds;

    @Value("${app.otp.max-sends-per-hour:3}")
    private int maxSendsPerHour;

    @Value("${app.otp.max-attempts:5}")
    private int maxAttempts;

    @Transactional
    public OtpRequestResult request(Long userId, OtpPurpose purpose) {
        User user =
                userRepository
                        .findByIdForUpdate(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Instant now = clock.instant();
        OtpChallenge challenge =
                challengeRepository.findForUpdate(userId, purpose).orElseGet(OtpChallenge::new);

        if (challenge.getId() != null && challenge.getResendAvailableAt().isAfter(now)) {
            throw new TooManyRequestsException(
                    "Please wait before requesting another code.",
                    secondsUntil(now, challenge.getResendAvailableAt()));
        }

        boolean sameWindow =
                challenge.getId() != null
                        && challenge
                                .getSendWindowStartedAt()
                                .plus(1, ChronoUnit.HOURS)
                                .isAfter(now);
        if (sameWindow && challenge.getSendCount() >= maxSendsPerHour) {
            Instant retryAt = challenge.getSendWindowStartedAt().plus(1, ChronoUnit.HOURS);
            throw new TooManyRequestsException(
                    "The hourly verification-code limit has been reached.",
                    secondsUntil(now, retryAt));
        }

        String code = String.format("%06d", SECURE_RANDOM.nextInt(CODE_BOUND));
        challenge.setUser(user);
        challenge.setPurpose(purpose);
        challenge.setCodeHash(passwordEncoder.encode(code));
        challenge.setCreatedAt(now);
        challenge.setExpiresAt(now.plusSeconds(expirationSeconds));
        challenge.setResendAvailableAt(now.plusSeconds(resendCooldownSeconds));
        challenge.setAttemptCount(0);
        challenge.setVerifiedAt(null);
        challenge.setVerificationTokenHash(null);
        challenge.setVerificationExpiresAt(null);
        if (sameWindow) {
            challenge.setSendCount(challenge.getSendCount() + 1);
        } else {
            challenge.setSendCount(1);
            challenge.setSendWindowStartedAt(now);
        }
        challengeRepository.save(challenge);

        deliveryService.send(user.getEmail(), purpose, code, Math.max(1, expirationSeconds / 60));
        return new OtpRequestResult(
                expirationSeconds, resendCooldownSeconds, maskEmail(user.getEmail()));
    }

    @Transactional(dontRollbackOn = InvalidOtpException.class)
    public OtpVerificationResult verify(Long userId, OtpPurpose purpose, String code) {
        OtpChallenge challenge = getChallenge(userId, purpose);
        Instant now = clock.instant();
        if (!challenge.getExpiresAt().isAfter(now)) {
            throw new InvalidOtpException("The verification code has expired. Request a new code.");
        }
        if (challenge.getAttemptCount() >= maxAttempts) {
            throw new InvalidOtpException(
                    "Too many incorrect attempts. Request a new verification code.");
        }
        if (!passwordEncoder.matches(code, challenge.getCodeHash())) {
            challenge.setAttemptCount(challenge.getAttemptCount() + 1);
            challengeRepository.save(challenge);
            int attemptsRemaining = Math.max(0, maxAttempts - challenge.getAttemptCount());
            throw new InvalidOtpException(
                    attemptsRemaining == 0
                            ? "Too many incorrect attempts. Request a new verification code."
                            : "Incorrect verification code. "
                                    + attemptsRemaining
                                    + " attempt(s) remaining.");
        }

        String token = randomToken();
        challenge.setVerifiedAt(now);
        challenge.setVerificationTokenHash(hash(token));
        challenge.setVerificationExpiresAt(now.plusSeconds(verificationExpirationSeconds));
        challengeRepository.save(challenge);
        return new OtpVerificationResult(token, verificationExpirationSeconds);
    }

    @Transactional
    public void consumeVerification(Long userId, OtpPurpose purpose, String verificationToken) {
        OtpChallenge challenge = getChallenge(userId, purpose);
        Instant now = clock.instant();
        if (challenge.getVerifiedAt() == null
                || challenge.getVerificationExpiresAt() == null
                || !challenge.getVerificationExpiresAt().isAfter(now)
                || challenge.getVerificationTokenHash() == null
                || !MessageDigest.isEqual(
                        challenge.getVerificationTokenHash().getBytes(StandardCharsets.UTF_8),
                        hash(verificationToken).getBytes(StandardCharsets.UTF_8))) {
            throw new BadRequestException(
                    "Verification is missing or expired. Request and verify a new code.");
        }
        challengeRepository.delete(challenge);
    }

    @Transactional
    public void deleteAllForUser(Long userId) {
        challengeRepository.deleteAllByUserId(userId);
    }

    private OtpChallenge getChallenge(Long userId, OtpPurpose purpose) {
        return challengeRepository
                .findForUpdate(userId, purpose)
                .orElseThrow(
                        () ->
                                new BadRequestException(
                                        "Request a verification code before continuing."));
    }

    private String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private long secondsUntil(Instant now, Instant target) {
        return Math.max(1, Duration.between(now, target).getSeconds());
    }

    private String maskEmail(String email) {
        int separator = email.indexOf('@');
        if (separator <= 1) {
            return "***" + email.substring(Math.max(0, separator));
        }
        return email.substring(0, 1) + "***" + email.substring(separator);
    }

    public record OtpRequestResult(
            long expiresInSeconds, long resendAfterSeconds, String destination) {}

    public record OtpVerificationResult(String verificationToken, long expiresInSeconds) {}
}
