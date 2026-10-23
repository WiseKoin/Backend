package com.wisekoin.backend.module.ai.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wisekoin.backend.module.common.exception.DuplicateAiExtractionException;
import com.wisekoin.backend.module.user.User;
import com.wisekoin.backend.module.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AiIngestionRequestServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-23T10:00:00Z");

    private AiIngestionRequestRepository repository;
    private UserRepository userRepository;
    private AiIngestionRequestService service;

    @BeforeEach
    void setUp() {
        repository = mock(AiIngestionRequestRepository.class);
        userRepository = mock(UserRepository.class);
        User user = new User();
        user.setId(42L);
        when(userRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(user));
        service =
                new AiIngestionRequestService(
                        repository,
                        userRepository,
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        Duration.ofDays(30),
                        Duration.ofMinutes(5));
    }

    @Test
    void storesOnlyHashedIdempotencyKeyAndMediaFingerprint() {
        when(repository.findByUserIdAndSourceAndIdempotencyKeyHash(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(repository.findByUserIdAndSourceAndMediaFingerprintAndFingerprintDate(
                        42L, "RECEIPT", "media-fingerprint", LocalDate.of(2026, 10, 23)))
                .thenReturn(Optional.empty());

        var attempt = service.begin(42L, "RECEIPT", "client-secret-key", "media-fingerprint");

        ArgumentCaptor<AiIngestionRequest> captor =
                ArgumentCaptor.forClass(AiIngestionRequest.class);
        verify(repository).save(captor.capture());
        assertEquals(attempt.requestId(), captor.getValue().getId());
        assertEquals(64, captor.getValue().getIdempotencyKeyHash().length());
        assertNotEquals("client-secret-key", captor.getValue().getIdempotencyKeyHash());
        assertEquals("media-fingerprint", captor.getValue().getMediaFingerprint());
    }

    @Test
    void completedDuplicateReturnsTraceableConflict() {
        UUID requestId = UUID.randomUUID();
        UUID extractionId = UUID.randomUUID();
        AiIngestionRequest existing = new AiIngestionRequest();
        existing.setId(requestId);
        existing.setStatus("COMPLETED");
        existing.setFirstExtractionId(extractionId);
        when(repository.findByUserIdAndSourceAndIdempotencyKeyHash(any(), any(), any()))
                .thenReturn(Optional.of(existing));

        DuplicateAiExtractionException failure =
                assertThrows(
                        DuplicateAiExtractionException.class,
                        () -> service.begin(42L, "VOICE", "same-key", "fingerprint"));

        assertEquals(requestId, failure.getRequestId());
        assertEquals(extractionId, failure.getExtractionId());
    }

    @Test
    void cleanupUsesConfiguredMetadataRetention() {
        service.deleteExpiredMetadata();

        verify(repository).deleteByCreatedAtBefore(NOW.minus(Duration.ofDays(30)));
    }
}
