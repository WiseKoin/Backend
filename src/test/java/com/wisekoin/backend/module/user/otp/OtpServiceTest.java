package com.wisekoin.backend.module.user.otp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wisekoin.backend.module.common.exception.InvalidOtpException;
import com.wisekoin.backend.module.common.exception.TooManyRequestsException;
import com.wisekoin.backend.module.user.User;
import com.wisekoin.backend.module.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class OtpServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");

    private OtpChallengeRepository challengeRepository;
    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private OtpDeliveryService deliveryService;
    private OtpService service;
    private User user;

    @BeforeEach
    void setUp() {
        challengeRepository = mock(OtpChallengeRepository.class);
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        deliveryService = mock(OtpDeliveryService.class);
        service =
                new OtpService(
                        challengeRepository,
                        userRepository,
                        passwordEncoder,
                        deliveryService,
                        Clock.fixed(NOW, ZoneOffset.UTC));
        ReflectionTestUtils.setField(service, "expirationSeconds", 600L);
        ReflectionTestUtils.setField(service, "verificationExpirationSeconds", 600L);
        ReflectionTestUtils.setField(service, "resendCooldownSeconds", 60L);
        ReflectionTestUtils.setField(service, "maxSendsPerHour", 3);
        ReflectionTestUtils.setField(service, "maxAttempts", 5);

        user = new User();
        user.setId(7L);
        user.setEmail("person@example.com");
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(challengeRepository.findForUpdate(7L, OtpPurpose.CHANGE_PASSWORD))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("hashed-code");
    }

    @Test
    void requestHashesCodeAndReturnsOnlyMaskedDestination() {
        OtpService.OtpRequestResult result = service.request(7L, OtpPurpose.CHANGE_PASSWORD);

        ArgumentCaptor<OtpChallenge> challenge = ArgumentCaptor.forClass(OtpChallenge.class);
        verify(challengeRepository).save(challenge.capture());
        assertEquals("hashed-code", challenge.getValue().getCodeHash());
        assertEquals("p***@example.com", result.destination());
        assertEquals(600L, result.expiresInSeconds());
        verify(deliveryService)
                .send(
                        org.mockito.ArgumentMatchers.eq("person@example.com"),
                        org.mockito.ArgumentMatchers.eq(OtpPurpose.CHANGE_PASSWORD),
                        org.mockito.ArgumentMatchers.matches("\\d{6}"),
                        org.mockito.ArgumentMatchers.eq(10L));
    }

    @Test
    void requestRejectsResendDuringCooldown() {
        OtpChallenge challenge = challenge();
        challenge.setResendAvailableAt(NOW.plusSeconds(30));
        when(challengeRepository.findForUpdate(7L, OtpPurpose.CHANGE_PASSWORD))
                .thenReturn(Optional.of(challenge));

        TooManyRequestsException exception =
                assertThrows(
                        TooManyRequestsException.class,
                        () -> service.request(7L, OtpPurpose.CHANGE_PASSWORD));

        assertEquals(30L, exception.getRetryAfterSeconds());
    }

    @Test
    void requestRejectsFourthCodeInsideHourlyWindow() {
        OtpChallenge challenge = challenge();
        challenge.setSendCount(3);
        when(challengeRepository.findForUpdate(7L, OtpPurpose.CHANGE_PASSWORD))
                .thenReturn(Optional.of(challenge));

        TooManyRequestsException exception =
                assertThrows(
                        TooManyRequestsException.class,
                        () -> service.request(7L, OtpPurpose.CHANGE_PASSWORD));

        assertEquals(3590L, exception.getRetryAfterSeconds());
    }

    @Test
    void verifyLocksChallengeAfterMaximumIncorrectAttempts() {
        OtpChallenge challenge = challenge();
        when(challengeRepository.findForUpdate(7L, OtpPurpose.CHANGE_PASSWORD))
                .thenReturn(Optional.of(challenge));
        when(passwordEncoder.matches("000000", "hashed-code")).thenReturn(false);

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThrows(
                    InvalidOtpException.class,
                    () -> service.verify(7L, OtpPurpose.CHANGE_PASSWORD, "000000"));
        }

        assertEquals(5, challenge.getAttemptCount());
        assertThrows(
                InvalidOtpException.class,
                () -> service.verify(7L, OtpPurpose.CHANGE_PASSWORD, "000000"));
    }

    @Test
    void verifyReturnsShortLivedOpaqueToken() {
        OtpChallenge challenge = challenge();
        when(challengeRepository.findForUpdate(7L, OtpPurpose.CHANGE_PASSWORD))
                .thenReturn(Optional.of(challenge));
        when(passwordEncoder.matches("123456", "hashed-code")).thenReturn(true);

        OtpService.OtpVerificationResult result =
                service.verify(7L, OtpPurpose.CHANGE_PASSWORD, "123456");

        assertNotNull(result.verificationToken());
        assertEquals(600L, result.expiresInSeconds());
        assertNotNull(challenge.getVerificationTokenHash());
        assertEquals(NOW.plusSeconds(600), challenge.getVerificationExpiresAt());
    }

    @Test
    void verifyRejectsExpiredCode() {
        OtpChallenge challenge = challenge();
        challenge.setExpiresAt(NOW);
        when(challengeRepository.findForUpdate(7L, OtpPurpose.CHANGE_PASSWORD))
                .thenReturn(Optional.of(challenge));

        InvalidOtpException exception =
                assertThrows(
                        InvalidOtpException.class,
                        () -> service.verify(7L, OtpPurpose.CHANGE_PASSWORD, "123456"));

        assertEquals(
                "The verification code has expired. Request a new code.", exception.getMessage());
    }

    @Test
    void consumeAcceptsVerifiedTokenExactlyOnce() {
        OtpChallenge challenge = challenge();
        when(challengeRepository.findForUpdate(7L, OtpPurpose.CHANGE_PASSWORD))
                .thenReturn(Optional.of(challenge));
        when(passwordEncoder.matches("123456", "hashed-code")).thenReturn(true);
        String token = service.verify(7L, OtpPurpose.CHANGE_PASSWORD, "123456").verificationToken();

        service.consumeVerification(7L, OtpPurpose.CHANGE_PASSWORD, token);

        verify(challengeRepository).delete(challenge);
    }

    private OtpChallenge challenge() {
        OtpChallenge challenge = new OtpChallenge();
        challenge.setId(9L);
        challenge.setUser(user);
        challenge.setPurpose(OtpPurpose.CHANGE_PASSWORD);
        challenge.setCodeHash("hashed-code");
        challenge.setCreatedAt(NOW.minusSeconds(10));
        challenge.setExpiresAt(NOW.plusSeconds(590));
        challenge.setResendAvailableAt(NOW.minusSeconds(1));
        challenge.setSendWindowStartedAt(NOW.minusSeconds(10));
        challenge.setSendCount(1);
        return challenge;
    }
}
