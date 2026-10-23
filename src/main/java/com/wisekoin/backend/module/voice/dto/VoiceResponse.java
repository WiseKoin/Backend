package com.wisekoin.backend.module.voice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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
        List<VoiceTransactionResponse> transactions,
        @JsonProperty("extraction_id") UUID extractionId,
        String counterparty,
        @JsonProperty("source_wallet_id") Long sourceWalletId,
        @JsonProperty("destination_wallet_id") Long destinationWalletId) {
    public VoiceResponse(
            BigDecimal amount,
            String currency,
            String type,
            Long categoryId,
            String category,
            String note,
            String transcript,
            LocalDateTime occurredAt,
            String sourceWalletHint,
            String destinationWalletHint,
            boolean requiresClarification,
            List<VoiceTransactionResponse> transactions) {
        this(
                amount,
                currency,
                type,
                categoryId,
                category,
                note,
                transcript,
                occurredAt,
                sourceWalletHint,
                destinationWalletHint,
                requiresClarification,
                transactions,
                null,
                "",
                null,
                null);
    }
}
