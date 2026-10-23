package com.wisekoin.backend.module.voice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wisekoin.backend.module.ai.dto.AiCategoryCandidate;
import com.wisekoin.backend.module.ai.dto.AiConfidenceResponse;
import com.wisekoin.backend.module.ai.dto.AiDuplicateCandidate;
import com.wisekoin.backend.module.ai.dto.AiFieldAmbiguity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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
        @JsonProperty("duplicate_candidates") List<AiDuplicateCandidate> duplicateCandidates,
        @JsonProperty("extraction_id") UUID extractionId,
        String counterparty,
        @JsonProperty("source_wallet_id") Long sourceWalletId,
        @JsonProperty("destination_wallet_id") Long destinationWalletId,
        @JsonProperty("prompt_version") String promptVersion,
        @JsonProperty("model_version") String modelVersion,
        String provider,
        String locale,
        @JsonProperty("time_zone") String timeZone,
        @JsonProperty("rule_applied") boolean ruleApplied,
        AiConfidenceResponse confidence) {
    public VoiceTransactionResponse(
            BigDecimal amount,
            String currency,
            String type,
            Long categoryId,
            String category,
            String note,
            LocalDateTime occurredAt,
            String sourceWalletHint,
            String destinationWalletHint,
            List<AiCategoryCandidate> categoryCandidates,
            boolean requiresClarification,
            List<AiFieldAmbiguity> ambiguities,
            List<AiDuplicateCandidate> duplicateCandidates) {
        this(
                amount,
                currency,
                type,
                categoryId,
                category,
                note,
                occurredAt,
                sourceWalletHint,
                destinationWalletHint,
                categoryCandidates,
                requiresClarification,
                ambiguities,
                duplicateCandidates,
                null,
                "",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                null);
    }
}
