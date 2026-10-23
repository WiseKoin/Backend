package com.wisekoin.backend.module.ai.ingestion;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiIngestionRequestRepository extends JpaRepository<AiIngestionRequest, UUID> {
    Optional<AiIngestionRequest> findByUserIdAndSourceAndIdempotencyKeyHash(
            Long userId, String source, String idempotencyKeyHash);

    Optional<AiIngestionRequest> findByUserIdAndSourceAndMediaFingerprintAndFingerprintDate(
            Long userId, String source, String mediaFingerprint, LocalDate fingerprintDate);

    long deleteByCreatedAtBefore(Instant cutoff);
}
