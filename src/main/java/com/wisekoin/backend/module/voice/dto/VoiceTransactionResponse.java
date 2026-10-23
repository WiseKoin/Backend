package com.wisekoin.backend.module.voice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wisekoin.backend.module.ai.dto.AiCategoryCandidate;
import com.wisekoin.backend.module.ai.dto.AiDuplicateCandidate;
import com.wisekoin.backend.module.ai.dto.AiFieldAmbiguity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record VoiceTransactionResponse(
        BigDecimal amount,
        String currency,
        String type,
        @JsonProperty("category_id") Long categoryId,
        String category,
        String note,
        @JsonProperty("occurred_at") LocalDateTime occurredAt,
        @JsonProperty("source_wallet_hint") String sourceWalletHint,
        @JsonProperty("destination_wallet_hint") String destinationWalletHint,
        @JsonProperty("category_candidates") List<AiCategoryCandidate> categoryCandidates,
        @JsonProperty("requires_clarification") boolean requiresClarification,
        List<AiFieldAmbiguity> ambiguities,
        @JsonProperty("duplicate_candidates") List<AiDuplicateCandidate> duplicateCandidates) {}
