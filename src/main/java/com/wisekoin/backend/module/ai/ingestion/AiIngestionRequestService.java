package com.wisekoin.backend.module.ai.ingestion;

import com.wisekoin.backend.module.common.exception.DuplicateAiExtractionException;
import com.wisekoin.backend.module.common.exception.ResourceNotFoundException;
import com.wisekoin.backend.module.user.User;
import com.wisekoin.backend.module.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiIngestionRequestService {
    private final AiIngestionRequestRepository repository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final Duration metadataRetention;
    private final Duration processingTimeout;

    public AiIngestionRequestService(
            AiIngestionRequestRepository repository,
            UserRepository userRepository,
            Clock clock,
            @Value("${app.ai.ingestion.metadata-retention:30d}") Duration metadataRetention,
            @Value("${app.ai.ingestion.processing-timeout:5m}") Duration processingTimeout) {
        if (metadataRetention.isNegative()
                || metadataRetention.isZero()
                || processingTimeout.isNegative()
                || processingTimeout.isZero()) {
            throw new IllegalArgumentException("AI ingestion durations must be positive.");
        }
        this.repository = repository;
        this.userRepository = userRepository;
        this.clock = clock;
        this.metadataRetention = metadataRetention;
        this.processingTimeout = processingTimeout;
    }

    @Transactional
    public AiIngestionAttempt begin(
            Long userId, String source, String idempotencyKey, String mediaFingerprint) {
        User user =
                userRepository
                        .findByIdForUpdate(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        String normalizedSource = normalizeSource(source);
        String keyHash = hashIdempotencyKey(idempotencyKey);
        LocalDate fingerprintDate = LocalDate.now(clock);

        Optional<AiIngestionRequest> existingByKey =
                keyHash == null
                        ? Optional.empty()
                        : repository.findByUserIdAndSourceAndIdempotencyKeyHash(
                                userId, normalizedSource, keyHash);
        if (existingByKey.isPresent()) {
            return restartOrReject(existingByKey.get());
        }

        Optional<AiIngestionRequest> existingByFingerprint =
                repository.findByUserIdAndSourceAndMediaFingerprintAndFingerprintDate(
                        userId, normalizedSource, mediaFingerprint, fingerprintDate);
        if (existingByFingerprint.isPresent()) {
            return restartOrReject(existingByFingerprint.get());
        }

        Instant now = clock.instant();
        AiIngestionRequest request = new AiIngestionRequest();
        request.setId(UUID.randomUUID());
        request.setUser(user);
        request.setSource(normalizedSource);
        request.setIdempotencyKeyHash(keyHash);
        request.setMediaFingerprint(mediaFingerprint);
        request.setFingerprintDate(fingerprintDate);
        request.setStatus("PROCESSING");
        request.setCreatedAt(now);
        request.setUpdatedAt(now);
        repository.save(request);
        return new AiIngestionAttempt(request.getId());
    }

    @Transactional
    public void complete(UUID requestId, UUID firstExtractionId) {
        AiIngestionRequest request = repository.findById(requestId).orElseThrow();
        request.setStatus("COMPLETED");
        request.setFirstExtractionId(firstExtractionId);
        request.setUpdatedAt(clock.instant());
    }

    @Transactional
    public void fail(UUID requestId) {
        repository
                .findById(requestId)
                .ifPresent(
                        request -> {
                            request.setStatus("FAILED");
                            request.setUpdatedAt(clock.instant());
                        });
    }

    @Transactional
    @Scheduled(cron = "${app.ai.ingestion.cleanup-cron:0 30 3 * * *}")
    public void deleteExpiredMetadata() {
        repository.deleteByCreatedAtBefore(clock.instant().minus(metadataRetention));
    }

    private AiIngestionAttempt restartOrReject(AiIngestionRequest request) {
        boolean processingExpired =
                "PROCESSING".equals(request.getStatus())
                        && request.getUpdatedAt() != null
                        && !request.getUpdatedAt().plus(processingTimeout).isAfter(clock.instant());
        if ("FAILED".equals(request.getStatus()) || processingExpired) {
            request.setStatus("PROCESSING");
            request.setFirstExtractionId(null);
            request.setUpdatedAt(clock.instant());
            return new AiIngestionAttempt(request.getId());
        }
        String message =
                "PROCESSING".equals(request.getStatus())
                        ? "This AI extraction request is already being processed."
                        : "This media was already extracted today.";
        throw new DuplicateAiExtractionException(
                message, request.getId(), request.getFirstExtractionId());
    }

    private String normalizeSource(String source) {
        if (!"RECEIPT".equals(source) && !"VOICE".equals(source)) {
            throw new IllegalArgumentException("Unsupported AI ingestion source.");
        }
        return source;
    }

    private String hashIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        String normalized = idempotencyKey.trim();
        if (normalized.length() > 200) {
            throw new IllegalArgumentException("Idempotency-Key cannot exceed 200 characters.");
        }
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    public record AiIngestionAttempt(UUID requestId) {}
}
