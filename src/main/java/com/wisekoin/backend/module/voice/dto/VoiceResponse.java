package com.wisekoin.backend.module.voice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record VoiceResponse(
        BigDecimal amount,
        String currency,
        String type,
        @JsonProperty("category_id") Long categoryId,
        String category,
        String note,
        String transcript,
        @JsonProperty("occurred_at") LocalDateTime occurredAt,
        @JsonProperty("source_wallet_hint") String sourceWalletHint,
        @JsonProperty("destination_wallet_hint") String destinationWalletHint,
        @JsonProperty("requires_clarification") boolean requiresClarification,
        List<VoiceTransactionResponse> transactions) {}
