package com.wisekoin.backend.module.ai.adaptive;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface AiExtractionRepository extends JpaRepository<AiExtraction, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AiExtraction> findByIdAndUserId(UUID id, Long userId);
}
