package com.wisekoin.backend.module.ai.suggestion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wisekoin.backend.module.ai.dto.AiFieldAmbiguity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record AiSuggestionResponse(
        UUID id,
        String source,
        BigDecimal amount,
        String currency,
        String type,
        @JsonProperty("merchant") String merchant,
        @JsonProperty("occurred_at") LocalDateTime occurredAt,
        @JsonProperty("category_id") Long categoryId,
        @JsonProperty("source_wallet_id") Long sourceWalletId,
        @JsonProperty("destination_wallet_id") Long destinationWalletId,
        @JsonProperty("requires_clarification") boolean requiresClarification,
        List<AiFieldAmbiguity> ambiguities,
        String status,
        @JsonProperty("rejection_reason") String rejectionReason,
        @JsonProperty("transaction_id") Long transactionId,
        @JsonProperty("prompt_version") String promptVersion,
        @JsonProperty("model_version") String modelVersion,
        String provider,
        @JsonProperty("parser_version") String parserVersion,
        String locale,
        @JsonProperty("time_zone") String timeZone,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt,
        @JsonProperty("resolved_at") Instant resolvedAt) {}
